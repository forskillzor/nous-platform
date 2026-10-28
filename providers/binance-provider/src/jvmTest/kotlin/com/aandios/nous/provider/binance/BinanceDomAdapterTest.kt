/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.provider.binance.adapter.BinanceDomAdapter
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
import kotlin.test.assertNotNull

class BinanceDomAdapterTest {

    @Test
    fun `snapshot uses an allowed limit that covers requested depth`() {
        val requested = mutableListOf<String?>()
        var requestedPath = ""
        val engine = MockEngine { request ->
            requestedPath = request.url.encodedPath
            requested += request.url.parameters["limit"]
            respond(
                content = """{"lastUpdateId":42,"bids":[["100.0","3"]],"asks":[["100.5","2"]]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val config = ProviderConfig(displayName = "Binance")
        val adapter = BinanceDomAdapter(
            client = client,
            config = config,
            streamHub = BinanceStreamHub(client, config),
            restGate = BinanceRestGate(),
        )

        val snapshot = runBlocking { adapter.fetchDepthSnapshot("BTCUSDT", 200) }

        assertNotNull(snapshot)
        assertEquals(42L, snapshot.lastUpdateId)
        assertEquals(1, snapshot.bids.size)
        assertEquals(1, snapshot.asks.size)
        assertEquals("/fapi/v1/depth", requestedPath)
        assertEquals(listOf<String?>("500"), requested)
        client.close()
    }

    @Test
    fun `snapshot limit is capped at one thousand levels`() {
        val requested = mutableListOf<String?>()
        val engine = MockEngine { request ->
            requested += request.url.parameters["limit"]
            respond(
                content = """{"lastUpdateId":1,"bids":[],"asks":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val config = ProviderConfig(displayName = "Binance")
        val adapter = BinanceDomAdapter(
            client = client,
            config = config,
            streamHub = BinanceStreamHub(client, config),
            restGate = BinanceRestGate(),
        )

        runBlocking { adapter.fetchDepthSnapshot("BTCUSDT", 2000) }

        assertEquals(listOf<String?>("1000"), requested)
        client.close()
    }
}
