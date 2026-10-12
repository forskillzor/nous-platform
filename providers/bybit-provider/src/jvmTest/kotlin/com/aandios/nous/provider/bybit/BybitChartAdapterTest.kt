/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.provider.bybit.adapter.BybitChartAdapter
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
import kotlin.test.assertTrue

class BybitChartAdapterTest {
    @Test
    fun `fetches linear candles and returns them oldest first`() = runBlocking {
        val requests = mutableListOf<Pair<String, String>>()
        val engine = MockEngine { request ->
            requests += request.url.encodedPath to request.url.encodedQuery
            respond(
                content = """{"retCode":0,"retMsg":"OK","result":{"category":"linear","symbol":"BTCUSDT","list":[["2000","2","3","1","2.5","10","25"],["1000","1","2","0.5","1.5","5","7.5"]]}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        try {
            val rest = BybitRestClient(client, ProviderConfig())
            val adapter = BybitChartAdapter(rest, BybitStreamHub(client, ProviderConfig()))

            val candles = adapter.getCandles("BTCUSDT", "5m", 2)
            val older = adapter.getCandlesBefore("BTCUSDT", "5m", 3_000L, 2)

            assertEquals(listOf(1_000L, 2_000L), candles.map { it.timestamp })
            assertEquals(listOf(1_000L, 2_000L), older.map { it.timestamp })
            assertEquals(2, requests.size)
            assertTrue(requests.all { (path, query) ->
                path == "/v5/market/kline" &&
                    query.contains("category=linear") &&
                    query.contains("symbol=BTCUSDT") &&
                    query.contains("interval=5") &&
                    query.contains("limit=2")
            })
            assertTrue(requests[1].second.contains("end=3000"))
        } finally {
            client.close()
        }
    }
}
