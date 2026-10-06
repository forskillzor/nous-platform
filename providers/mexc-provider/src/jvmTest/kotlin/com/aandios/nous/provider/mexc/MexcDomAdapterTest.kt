/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.provider.mexc.adapter.MexcDomAdapter
import com.aandios.nous.provider.mexc.model.MexcDepthPush
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MexcDomAdapterTest {

    @Test
    fun `fetches depth snapshot with version via rest gate`() {
        var requestedPath = ""
        val engine = MockEngine { request ->
            requestedPath = request.url.encodedPath
            respond(
                content = """{"success":true,"code":0,"data":{"asks":[[100.5,2,1]],"bids":[[100.0,3,2]],"version":42,"timestamp":1}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine)
        val config = ProviderConfig(displayName = "MEXC")
        val adapter = MexcDomAdapter(
            client = client,
            config = config,
            restGate = MexcRestGate(),
            streamHub = MexcStreamHub(client, config),
        )

        val snapshot = runBlocking { adapter.fetchDepthSnapshot("BTC_USDT") }

        assertNotNull(snapshot)
        assertEquals(42L, snapshot.version)
        assertEquals(1, snapshot.asks.size)
        assertEquals(100.5, snapshot.asks[0][0])
        assertEquals(3.0, snapshot.bids[0][1])
        assertEquals("/api/v1/contract/depth/BTC_USDT", requestedPath)
        client.close()
    }

    @Test
    fun `depth push parses increment version`() {
        val push = Json { ignoreUnknownKeys = true }.decodeFromString<MexcDepthPush>(
            """{"channel":"push.depth","symbol":"BTC_USDT","ts":1,"data":{"asks":[[100.5,2,1]],"bids":[],"version":96801927}}"""
        )
        assertEquals(96801927L, push.data?.version)
        assertEquals(100.5, push.data?.asks?.first()?.first())
    }

    @Test
    fun `dom quantity scale converts contracts to symbol units`() {
        val linear = SymbolInfo(
            symbol = "SOLUSDT",
            tickSize = 0.01,
            stepSize = 0.1, // volUnit 1 * contractSize 0.1 (базовый актив)
            minQty = 0.1,
            minNotional = 0.0,
            status = "TRADING",
            baseAsset = "SOL",
            quoteAsset = "USDT",
            contractType = "PERPETUAL",
            marginAsset = "USDT",
            contractSize = 0.1,
        )
        // Linear: контракты → базовый актив (10 контрактов = 1 SOL)
        assertEquals(0.1, domQuantityScale(linear))
        // Inverse (COIN-M): платформа уже в контрактах
        assertEquals(1.0, domQuantityScale(linear.copy(marginAsset = "BTC", contractSize = 100.0)))
        // Метаданные неизвестны — не додумываем
        assertEquals(1.0, domQuantityScale(null))
    }
}
