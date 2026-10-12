/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.provider.bybit.adapter.BybitSymbolInfoAdapter
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

class BybitSymbolInfoAdapterTest {
    @Test
    fun `maps Bybit linear perpetual contract type to shared platform type`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """
                    {"retCode":0,"retMsg":"OK","result":{"list":[{
                      "symbol":"BTCUSDT","contractType":"LinearPerpetual","status":"Trading",
                      "baseCoin":"BTC","quoteCoin":"USDT","settleCoin":"USDT",
                      "priceFilter":{"tickSize":"0.1"},
                      "lotSizeFilter":{"qtyStep":"0.001","minOrderQty":"0.001","minNotionalValue":"5"}
                    }],"nextPageCursor":""}}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        try {
            val adapter = BybitSymbolInfoAdapter(BybitRestClient(client, ProviderConfig()))
            val info = adapter.getSymbolInfo("BTCUSDT")

            assertNotNull(info)
            assertEquals("PERPETUAL", info.contractType)
            assertEquals("USDT", info.marginAsset)
        } finally {
            client.close()
        }
    }
}
