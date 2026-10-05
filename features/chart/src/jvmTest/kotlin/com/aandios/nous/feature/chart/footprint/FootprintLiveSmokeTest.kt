/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.footprint

import com.aandios.nous.feature.chart.footprint.FootprintApiClient
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
 *  1m — с сервера как есть;
 *  5m — агрегация из 1m на клиенте;
 *  15m — с сервера как есть;
 *  всё что выше 15m — агрегация из 15m на клиенте.
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

            // 2) 5m — собирается из 1m на клиенте
            val raw1m = api.getFootprint(symbol = symbol, timeframe = "1m", limit = 100)
            val m5 = FootprintAggregator.aggregateFootprintCandles(raw1m, 5, 60_000L)
            assumeTrue(m5.isNotEmpty(), "5m aggregation produced nothing")
            m5.forEach { assertEquals(0L, it.startTime % 300_000L, "5m bucket misaligned: ${it.startTime}") }

            // 3) 15m — с сервера нативно
            val m15 = api.getFootprint(symbol = symbol, timeframe = "15m", limit = 10)
            assumeTrue(m15.isNotEmpty(), "no 15m data on server")

            // 4) 1h — из 15m на клиенте
            val h1 = FootprintAggregator.aggregateFootprintCandles(
                api.getFootprint(symbol = symbol, timeframe = "15m", limit = 4 * 20),
                4, 900_000L
            )
            assumeTrue(h1.isNotEmpty(), "1h aggregation produced nothing")
            h1.forEach { assertEquals(0L, it.startTime % 3_600_000L, "1h bucket misaligned: ${it.startTime}") }

            // 5) 4h — из 15m на клиенте
            val h4 = FootprintAggregator.aggregateFootprintCandles(
                api.getFootprint(symbol = symbol, timeframe = "15m", limit = 16 * 20),
                16, 900_000L
            )
            assumeTrue(h4.isNotEmpty(), "4h aggregation produced nothing")
            h4.forEach { assertEquals(0L, it.startTime % 14_400_000L, "4h bucket misaligned: ${it.startTime}") }
        } finally {
            client.close()
        }
    }
}
