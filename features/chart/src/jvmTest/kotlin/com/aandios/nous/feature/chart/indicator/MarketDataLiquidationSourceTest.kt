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
    fun `maps market-data liquidations to LiquidationOrder`() = runBlocking {
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
                      {"exchange":"Binance","symbol":"SOLUSDT","timestamp":1000,
                       "price":"101.12000000","quantity":"2.53000000",
                       "isLong":true,"orderType":"LIMIT"},
                      {"exchange":"Binance","symbol":"SOLUSDT","timestamp":2000,
                       "price":"101.00000000","quantity":"2.30000000",
                       "isLong":false,"orderType":"MARKET"}
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
            val orders = source.getLiquidations(
                exchange = "Binance",
                symbol = "SOLUSDT",
                from = 100L,
                to = 200L,
                limit = 1000,
            )

            assertEquals("/api/liquidations", path)
            assertEquals("Binance", params["exchange"])
            assertEquals("SOLUSDT", params["symbol"])
            assertEquals("100", params["from"])
            assertEquals("200", params["to"])
            assertEquals("1000", params["limit"])

            assertEquals(2, orders.size)
            assertEquals(TradeSide.SELL, orders[0].side)
            assertEquals(101.12, orders[0].price)
            assertEquals(2.53, orders[0].quantity)
            assertEquals("LIMIT", orders[0].orderType)
            assertEquals(TradeSide.BUY, orders[1].side)
            assertEquals("MARKET", orders[1].orderType)
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
                source.getLiquidations("Binance", "SOLUSDT", 0L, 1L, 10)
            }
            assertTrue(error.message?.contains("HTTP 500") == true, error.message.orEmpty())
        } finally {
            client.close()
        }
    }
}
