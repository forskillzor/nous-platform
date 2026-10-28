/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.footprint

import com.aandios.nous.api.market.adapters.TradesAdapter
import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.api.market.model.FootprintLevel
import com.aandios.nous.api.market.model.trades.Trade
import com.aandios.nous.api.market.model.trading.TradeSide
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FootprintControllerTest {

    @Test
    fun `live snapshots are published at most every 100 ms`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val trades = MutableSharedFlow<Trade>(extraBufferCapacity = 8)
        var now = 100L
        val controller = FootprintController(
            footprintApiClient = null,
            tradesAdapter = FakeTradesAdapter(trades),
            processingDispatcher = dispatcher,
            clock = { now },
        )

        controller.start("BTCUSDT", "1m")
        runCurrent()

        trades.emit(trade(price = 100.0))
        runCurrent()
        assertEquals(1L, controller.state.value.liveCandle?.totalTicks)
        assertEquals(100f, controller.state.value.currentPrice)

        now = 150L
        trades.emit(trade(price = 101.0))
        runCurrent()
        assertEquals(1L, controller.state.value.liveCandle?.totalTicks)
        assertEquals(100f, controller.state.value.currentPrice)

        now = 200L
        trades.emit(trade(price = 102.0))
        runCurrent()
        assertEquals(3L, controller.state.value.liveCandle?.totalTicks)
        assertEquals(102f, controller.state.value.currentPrice)

        controller.dispose()
    }

    @Test
    fun `forming 30m is assembled despite partial historical bucket`() = runBlocking {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
        val now = 1_740_000L // 29m inside the 0..30m bucket

        fun level() = FootprintLevel(
            price = "100.0",
            bidVolume = "1",
            askVolume = "1",
            bidCount = 1,
            askCount = 1,
        )

        fun candle(start: Long, end: Long, timeframe: String) = FootprintCandle(
            exchange = "Binance",
            symbol = "BTCUSDT",
            timeframe = timeframe,
            startTime = start,
            endTime = end,
            totalTicks = 1,
            minPrice = "100.0",
            maxPrice = "100.0",
            levels = listOf(level()),
        )

        // Server returns DESC. The 0..30m bucket is still partial:
        // only its first closed 15m source exists.
        val history15m = listOf(
            candle(0L, 900_000L, "15m"),
            candle(-900_000L, 0L, "15m"),
            candle(-1_800_000L, -900_000L, "15m"),
        )
        val batch = BatchFootprintResponse(
            exchange = "Binance",
            symbol = "BTCUSDT",
            results = listOf(
                BatchFootprintResult("15m", listOf(candle(0L, 900_000L, "15m"))),
                BatchFootprintResult(
                    "5m",
                    listOf(
                        candle(900_000L, 1_200_000L, "5m"),
                        candle(1_200_000L, 1_500_000L, "5m"),
                    ),
                ),
                BatchFootprintResult(
                    "1m",
                    (25 until 29).map { m ->
                        candle(m * 60_000L, (m + 1) * 60_000L, "1m")
                    },
                ),
            ),
        )

        var historyExchange: String? = null
        var batchBody: String? = null
        val engine = MockEngine { request ->
            val payload = when {
                request.method == HttpMethod.Get && request.url.encodedPath == "/api/footprint" -> {
                    historyExchange = request.url.parameters["exchange"]
                    val timeframe = request.url.parameters["timeframe"]
                    if (timeframe == "15m") json.encodeToString(history15m) else "[]"
                }

                request.method == HttpMethod.Post && request.url.encodedPath == "/api/footprint/batch" -> {
                    batchBody = (request.body as? TextContent)?.text
                    json.encodeToString(batch)
                }

                else -> "{}"
            }
            respond(
                content = payload,
                status = if (payload == "{}") HttpStatusCode.NotFound else HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val controller = FootprintController(
            footprintApiClient = FootprintApiClient(client, "http://test"),
            tradesAdapter = null,
            exchange = "Bybit",
            clock = { now },
        )
        try {
            controller.start("BTCUSDT", "30m")
            val state = withTimeout(5_000) {
                controller.state.first { it.liveCandle != null }
            }

            val live = assertNotNull(state.liveCandle)
            assertEquals(0L, live.startTime)
            // 15m + 2x5m + 4x1m
            assertEquals(7L, live.totalTicks)
            assertTrue(state.candles.none { it.startTime == 0L })
            assertEquals("Bybit", historyExchange)
            assertTrue(batchBody?.contains("\"exchange\":\"Bybit\"") == true)
        } finally {
            controller.dispose()
            client.close()
        }
    }

    @Test
    fun `forming 5m is assembled from closed 1m`() = runBlocking {
        val now = 4 * 60_000L
        assertForming(
            displayTimeframe = "5m",
            historyTimeframe = "5m",
            now = now,
            history = listOf(candle(-300_000L, 0L, "5m")),
            batch = BatchFootprintResponse(
                exchange = "Bybit",
                symbol = "BTCUSDT",
                results = listOf(
                    BatchFootprintResult(
                        "1m",
                        (0 until 4).map { m -> candle(m * 60_000L, (m + 1) * 60_000L, "1m") },
                    ),
                ),
            ),
            expectedStart = 0L,
            expectedTicks = 4L,
        )
    }

    @Test
    fun `forming 15m is assembled from 5m and 1m`() = runBlocking {
        val now = 14 * 60_000L
        assertForming(
            displayTimeframe = "15m",
            historyTimeframe = "15m",
            now = now,
            history = listOf(candle(-900_000L, 0L, "15m")),
            batch = BatchFootprintResponse(
                exchange = "Bybit",
                symbol = "BTCUSDT",
                results = listOf(
                    BatchFootprintResult(
                        "5m",
                        listOf(candle(0L, 300_000L, "5m"), candle(300_000L, 600_000L, "5m")),
                    ),
                    BatchFootprintResult(
                        "1m",
                        (10 until 14).map { m -> candle(m * 60_000L, (m + 1) * 60_000L, "1m") },
                    ),
                ),
            ),
            expectedStart = 0L,
            expectedTicks = 6L,
        )
    }

    @Test
    fun `forming 1h is assembled from 15m 5m and 1m`() = runBlocking {
        val now = 59 * 60_000L
        assertForming(
            displayTimeframe = "1h",
            historyTimeframe = "1h",
            now = now,
            history = listOf(candle(-3_600_000L, 0L, "1h")),
            batch = BatchFootprintResponse(
                exchange = "Bybit",
                symbol = "BTCUSDT",
                results = listOf(
                    BatchFootprintResult(
                        "15m",
                        listOf(
                            candle(0L, 900_000L, "15m"),
                            candle(900_000L, 1_800_000L, "15m"),
                            candle(1_800_000L, 2_700_000L, "15m"),
                        ),
                    ),
                    BatchFootprintResult(
                        "5m",
                        listOf(
                            candle(2_700_000L, 3_000_000L, "5m"),
                            candle(3_000_000L, 3_300_000L, "5m"),
                        ),
                    ),
                    BatchFootprintResult(
                        "1m",
                        (55 until 59).map { m -> candle(m * 60_000L, (m + 1) * 60_000L, "1m") },
                    ),
                ),
            ),
            expectedStart = 0L,
            expectedTicks = 9L,
        )
    }

    @Test
    fun `live trades extend the 15m forming candle`() = runBlocking {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
        val now = 10 * 60_000L
        val history15m = listOf(candle(-900_000L, 0L, "15m"))
        val batch = BatchFootprintResponse(
            exchange = "Bybit",
            symbol = "BTCUSDT",
            results = listOf(
                BatchFootprintResult(
                    "5m",
                    listOf(
                        candle(0L, 300_000L, "5m"),
                        candle(300_000L, 600_000L, "5m"),
                    ),
                ),
            ),
        )
        val trades = MutableSharedFlow<Trade>(replay = 1, extraBufferCapacity = 8)
        val engine = MockEngine { request ->
            val payload = when {
                request.method == HttpMethod.Get && request.url.encodedPath == "/api/footprint" ->
                    if (request.url.parameters["timeframe"] == "15m") {
                        json.encodeToString(history15m)
                    } else {
                        "[]"
                    }

                request.method == HttpMethod.Post && request.url.encodedPath == "/api/footprint/batch" ->
                    json.encodeToString(batch)

                else -> "{}"
            }
            respond(
                content = payload,
                status = if (payload == "{}") HttpStatusCode.NotFound else HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val controller = FootprintController(
            footprintApiClient = FootprintApiClient(client, "http://test"),
            tradesAdapter = FakeTradesAdapter(trades),
            exchange = "Bybit",
            clock = { now },
        )
        try {
            controller.start("BTCUSDT", "15m")
            trades.emit(
                Trade(
                    id = 1L,
                    symbol = "BTCUSDT",
                    price = 100.0,
                    quantity = 1.0,
                    timestamp = now + 10_000L,
                    isBuyerMaker = false,
                    side = TradeSide.BUY,
                ),
            )

            val state = withTimeout(5_000) {
                controller.state.first { s ->
                    s.liveCandle?.totalTicks == 3L && s.liveCandle?.startTime == 0L
                }
            }
            assertEquals(3L, state.liveCandle?.totalTicks)
        } finally {
            controller.dispose()
            client.close()
        }
    }

    @Test
    fun `closed local 1m is replaced by the server candle`() = runBlocking {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
        var now = 30_000L
        val serverCandle = candle(0L, 60_000L, "1m").copy(totalTicks = 10L)
        val trades = MutableSharedFlow<Trade>(replay = 1, extraBufferCapacity = 8)
        val engine = MockEngine { request ->
            val payload = when {
                request.method != HttpMethod.Get || request.url.encodedPath != "/api/footprint" -> "{}"
                request.url.parameters["from"] != null || request.url.parameters["to"] != null ->
                    json.encodeToString(listOf(serverCandle))
                else -> "[]"
            }
            respond(
                content = payload,
                status = if (payload == "{}") HttpStatusCode.NotFound else HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val controller = FootprintController(
            footprintApiClient = FootprintApiClient(client, "http://test"),
            tradesAdapter = FakeTradesAdapter(trades),
            exchange = "Bybit",
            clock = { now },
        )
        try {
            controller.start("BTCUSDT", "1m")
            trades.emit(
                Trade(
                    id = 1L,
                    symbol = "BTCUSDT",
                    price = 100.0,
                    quantity = 1.0,
                    timestamp = 30_000L,
                    isBuyerMaker = false,
                    side = TradeSide.BUY,
                ),
            )
            withTimeout(5_000) {
                controller.state.first { it.liveCandle != null }
            }

            // New minute: local minute 0 is finalized, then server 1m replaces it.
            now = 90_000L
            trades.emit(
                Trade(
                    id = 2L,
                    symbol = "BTCUSDT",
                    price = 101.0,
                    quantity = 1.0,
                    timestamp = 90_000L,
                    isBuyerMaker = false,
                    side = TradeSide.BUY,
                ),
            )
            val state = withTimeout(5_000) {
                controller.state.first { s ->
                    s.candles.any { it.startTime == 0L && it.totalTicks == 10L }
                }
            }
            assertEquals(10L, state.candles.first { it.startTime == 0L }.totalTicks)
        } finally {
            controller.dispose()
            client.close()
        }
    }

    @Test
    fun `1m does not run the forming job`() = runBlocking {
        assertNoForming("1m")
    }

    private suspend fun assertForming(
        displayTimeframe: String,
        historyTimeframe: String,
        now: Long,
        history: List<FootprintCandle>,
        batch: BatchFootprintResponse,
        expectedStart: Long,
        expectedTicks: Long,
    ) {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
        var historyExchange: String? = null
        var batchBody: String? = null
        var batchCalls = 0
        val engine = MockEngine { request ->
            val payload = when {
                request.method == HttpMethod.Get && request.url.encodedPath == "/api/footprint" -> {
                    historyExchange = request.url.parameters["exchange"]
                    if (request.url.parameters["timeframe"] == historyTimeframe) {
                        json.encodeToString(history)
                    } else {
                        "[]"
                    }
                }

                request.method == HttpMethod.Post && request.url.encodedPath == "/api/footprint/batch" -> {
                    batchCalls++
                    batchBody = (request.body as? TextContent)?.text
                    json.encodeToString(batch)
                }

                else -> "{}"
            }
            respond(
                content = payload,
                status = if (payload == "{}") HttpStatusCode.NotFound else HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val controller = FootprintController(
            footprintApiClient = FootprintApiClient(client, "http://test"),
            tradesAdapter = null,
            exchange = "Bybit",
            clock = { now },
        )
        try {
            controller.start("BTCUSDT", displayTimeframe)
            val state = withTimeout(5_000) {
                controller.state.first { it.liveCandle != null }
            }
            val live = assertNotNull(state.liveCandle)
            assertEquals(expectedStart, live.startTime)
            assertEquals(expectedTicks, live.totalTicks)
            assertTrue(state.candles.none { it.startTime == expectedStart })
            assertEquals("Bybit", historyExchange)
            assertTrue(batchBody?.contains("\"exchange\":\"Bybit\"") == true)
            assertEquals(1, batchCalls)
        } finally {
            controller.dispose()
            client.close()
        }
    }

    private suspend fun assertNoForming(displayTimeframe: String) {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
        var historyExchange: String? = null
        var batchCalls = 0
        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Get && request.url.encodedPath == "/api/footprint" -> {
                    historyExchange = request.url.parameters["exchange"]
                    respond(
                        content = json.encodeToString(
                            listOf(candle(0L, 60_000L, displayTimeframe)),
                        ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

                request.method == HttpMethod.Post && request.url.encodedPath == "/api/footprint/batch" -> {
                    batchCalls++
                    respond(
                        content = "{}",
                        status = HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

                else -> respond(
                    content = "{}",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val controller = FootprintController(
            footprintApiClient = FootprintApiClient(client, "http://test"),
            tradesAdapter = FakeTradesAdapter(emptyFlow()),
            exchange = "Bybit",
            clock = { 90_000L },
        )
        try {
            controller.start("BTCUSDT", displayTimeframe)
            val state = withTimeout(5_000) {
                controller.state.first { !it.loading && it.candles.isNotEmpty() }
            }
            assertEquals(null, state.liveCandle)
            assertEquals(0, batchCalls)
            assertEquals("Bybit", historyExchange)
        } finally {
            controller.dispose()
            client.close()
        }
    }

    private fun level() = FootprintLevel(
        price = "100.0",
        bidVolume = "1",
        askVolume = "1",
        bidCount = 1,
        askCount = 1,
    )

    private fun candle(start: Long, end: Long, timeframe: String) = FootprintCandle(
        exchange = "Binance",
        symbol = "BTCUSDT",
        timeframe = timeframe,
        startTime = start,
        endTime = end,
        totalTicks = 1,
        minPrice = "100.0",
        maxPrice = "100.0",
        levels = listOf(level()),
    )

    private fun trade(price: Double) = Trade(
        id = 1L,
        symbol = "BTCUSDT",
        price = price,
        quantity = 1.0,
        timestamp = 0L,
        isBuyerMaker = false,
        side = TradeSide.BUY,
    )

    private class FakeTradesAdapter(
        private val trades: Flow<Trade>,
    ) : TradesAdapter {
        override fun subscribeToTrades(symbol: String): Flow<Trade> = trades

        override suspend fun getRecentTrades(symbol: String, limit: Int): List<Trade> = emptyList()
    }
}
