/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.footprint

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Живой smoke-тест схемы footprint:
 *  1m/5m/15m/1h/4h — с сервера нативно;
 *  30m — 15m×2; batch API — источник для формирующейся свечи.
 *
 * При недоступном market-data-server тест пропускается (assumeTrue),
 * поэтому в CI он не падает, а локально доказывает рабочий конвейер.
 */
class FootprintLiveSmokeTest {

    private suspend fun reachable(api: FootprintApiClient): Boolean = try {
        api.getInstruments()
        true
    } catch (e: Exception) {
        println("Smoke: server unreachable: ${e.message}")
        false
    }

    @Test
    fun footprintPipelineWorksAgainstLiveApi() = runBlocking {
        val client = HttpClient(CIO) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val api = FootprintApiClient(client)
        try {
            assumeTrue(reachable(api), "market-data-server unreachable — skip live smoke")
            val symbol = "BTCUSDT"

            // 1) 1m — с сервера нативно
            val m1 = api.getFootprint(symbol = symbol, timeframe = "1m", limit = 5)
            assumeTrue(m1.isNotEmpty(), "no 1m data on server")

            // 2) 5m/15m/1h/4h — с сервера нативно
            val m5 = api.getFootprint(symbol = symbol, timeframe = "5m", limit = 10)
            assumeTrue(m5.isNotEmpty(), "no 5m data on server")
            m5.forEach {
                assertEquals("5m", it.timeframe)
                assertEquals(0L, it.startTime % 300_000L, "5m bucket misaligned: ${it.startTime}")
            }

            val m15 = api.getFootprint(symbol = symbol, timeframe = "15m", limit = 10)
            assumeTrue(m15.isNotEmpty(), "no 15m data on server")

            val h1 = api.getFootprint(symbol = symbol, timeframe = "1h", limit = 10)
            assumeTrue(h1.isNotEmpty(), "no 1h data on server")
            h1.forEach {
                assertEquals("1h", it.timeframe)
                assertEquals(0L, it.startTime % 3_600_000L, "1h bucket misaligned: ${it.startTime}")
            }

            val h4 = api.getFootprint(symbol = symbol, timeframe = "4h", limit = 10)
            assumeTrue(h4.isNotEmpty(), "no 4h data on server")
            h4.forEach {
                assertEquals("4h", it.timeframe)
                assertEquals(0L, it.startTime % 14_400_000L, "4h bucket misaligned: ${it.startTime}")
            }

            // 3) 30m — агрегация 15m×2
            val m30 = FootprintAggregator.aggregateFootprintCandles(m15, 2, 900_000L, "30m")
            assumeTrue(m30.isNotEmpty(), "30m aggregation produced nothing")
            m30.forEach {
                assertEquals("30m", it.timeframe)
                assertEquals(0L, it.startTime % 1_800_000L, "30m bucket misaligned: ${it.startTime}")
            }

            // 4) Batch API — источник для формирующейся свечи
            val batch = api.getBatchFootprint(
                symbol = symbol,
                queries = listOf(
                    BatchFootprintQuery("1m", limit = 2, order = "asc"),
                    BatchFootprintQuery("5m", limit = 2, order = "asc"),
                    BatchFootprintQuery("15m", limit = 2, order = "asc"),
                ),
            )
            assertEquals(3, batch.results.size)
            val byTimeframe = batch.results.associate { it.timeframe to it.candles }
            assertEquals(2, byTimeframe.getValue("1m").size)
            assertEquals(2, byTimeframe.getValue("5m").size)
            assertEquals(2, byTimeframe.getValue("15m").size)
        } finally {
            client.close()
        }
    }
}
