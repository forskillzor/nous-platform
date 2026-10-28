/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.indicator

import com.aandios.nous.api.market.model.trading.TradeSide
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MarketDataLiquidationSourceTest {

    @Test
    fun `maps aggregate buckets to synthetic liquidation orders`() = runBlocking {
        var path = ""
        val params = mutableMapOf<String, String?>()
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            request.url.parameters.names().forEach { name ->
                params[name] = request.url.parameters[name]
            }
            respond(
                content = """
                    [
                      {"exchange":"Binance","symbol":"SOLUSDT","timeframe":"15m",
                       "startTime":1000,"endTime":901000,
                       "longCount":22,"longVolume":"1113.51000000",
                       "shortCount":0,"shortVolume":"0.00000000"},
                      {"exchange":"Binance","symbol":"SOLUSDT","timeframe":"15m",
                       "startTime":901000,"endTime":1801000,
                       "longCount":0,"longVolume":"0.00000000",
                       "shortCount":2,"shortVolume":"4.50000000"},
                      {"exchange":"Binance","symbol":"SOLUSDT","timeframe":"15m",
                       "startTime":1801000,"endTime":2701000,
                       "longCount":0,"longVolume":"0.00000000",
                       "shortCount":0,"shortVolume":"0.00000000"}
                    ]
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        try {
            val source = MarketDataLiquidationSource(client, "http://test")
            val buckets = source.getAggregates(
                exchange = "Binance",
                symbol = "SOLUSDT",
                timeframe = "15m",
                from = 100L,
                to = 3_000_000L,
                limit = 1000,
            )

            assertEquals("/api/liquidation-aggregates", path)
            assertEquals("Binance", params["exchange"])
            assertEquals("SOLUSDT", params["symbol"])
            assertEquals("15m", params["timeframe"])
            assertEquals("100", params["from"])
            assertEquals("3000000", params["to"])
            assertEquals("1000", params["limit"])
            assertEquals("asc", params["order"])

            assertEquals(2, buckets.size)
            assertEquals(1000L, buckets[0].startTime)
            assertEquals(901000L, buckets[0].endTime)
            assertEquals(1, buckets[0].orders.size)
            assertEquals(TradeSide.SELL, buckets[0].orders[0].side)
            assertEquals(1113.51, buckets[0].orders[0].quantity)
            assertEquals(1000L, buckets[0].orders[0].timestamp)
            assertEquals("AGGREGATE", buckets[0].orders[0].orderType)

            assertEquals(1, buckets[1].orders.size)
            assertEquals(TradeSide.BUY, buckets[1].orders[0].side)
            assertEquals(4.5, buckets[1].orders[0].quantity)
        } finally {
            client.close()
        }
    }

    @Test
    fun `non-2xx response throws instead of hiding the failure`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"error":"boom"}""",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        try {
            val source = MarketDataLiquidationSource(client, "http://test")
            val error = assertFailsWith<IllegalStateException> {
                source.getAggregates("Binance", "SOLUSDT", "15m", 0L, 1L, 10)
            }
            assertTrue(error.message?.contains("HTTP 500") == true, error.message.orEmpty())
        } finally {
            client.close()
        }
    }
}
