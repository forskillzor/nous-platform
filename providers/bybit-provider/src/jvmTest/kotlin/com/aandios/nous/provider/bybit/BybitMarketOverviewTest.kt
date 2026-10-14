/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.provider.bybit.adapter.BybitBookTickerAdapter
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

class BybitMarketOverviewTest {
    @Test
    fun `maps 24 hour ticker percent and turnover`() = runBlocking {
        val client = HttpClient(MockEngine {
            respond(
                content = """{"retCode":0,"retMsg":"OK","result":{"list":[{"symbol":"BTCUSDT","lastPrice":"65000","price24hPcnt":"0.025","turnover24h":"1234567"}]}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        try {
            val adapter = BybitBookTickerAdapter(
                BybitRestClient(client, ProviderConfig()),
                BybitStreamHub(client, ProviderConfig()),
            )

            val row = adapter.getMarketOverview().single()
            assertEquals("BTCUSDT", row.symbol)
            assertEquals(2.5, row.changePercent24h)
            assertEquals(1_234_567.0, row.quoteVolume24h)
        } finally {
            client.close()
        }
    }
}
