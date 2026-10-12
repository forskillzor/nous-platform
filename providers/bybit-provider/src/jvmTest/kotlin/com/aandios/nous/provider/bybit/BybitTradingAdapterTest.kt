/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.trading.OrderRequest
import com.aandios.nous.provider.bybit.adapter.BybitTradingAdapter
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BybitTradingAdapterTest {
    private data class Captured(val method: String, val path: String, val query: String, val body: String, val headers: Map<String, String>)

    private fun client(handler: suspend (Captured) -> String): Pair<HttpClient, MutableList<Captured>> {
        val requests = mutableListOf<Captured>()
        val engine = MockEngine { request ->
            val body = (request.body as? io.ktor.http.content.TextContent)?.text.orEmpty()
            val captured = Captured(
                method = request.method.value,
                path = request.url.encodedPath,
                query = request.url.encodedQuery,
                body = body,
                headers = request.headers.entries().associate { it.key to it.value.joinToString(";") },
            )
            requests += captured
            respond(
                content = handler(captured),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        } to requests
    }

    @Test
    fun `place order signs canonical json and maps reduce-only post-only fields`() = runBlocking {
        val (http, requests) = client {
            assertEquals("POST", it.method)
            assertEquals("/v5/order/create", it.path)
            """{"retCode":0,"retMsg":"OK","result":{"orderId":"order-1","orderLinkId":"client-1"}}"""
        }
        try {
            val apiKey = "test-key"
            val secret = "test-secret"
            val adapter = BybitTradingAdapter(
                http,
                BybitPrivateStreamHub(http, ProviderConfig(), apiKey, secret),
                ProviderConfig(),
                apiKey,
                secret,
            )

            val response = adapter.placeOrder(
                OrderRequest(
                    symbol = "BTCUSDT",
                    side = OrderSide.SELL,
                    orderType = OrderType.POST_ONLY,
                    quantity = 0.00001,
                    price = 62000.0,
                    reduceOnly = true,
                    positionMode = 1,
                    clientOrderId = "client-1",
                )
            )

            assertTrue(response.success, response.message.orEmpty())
            assertEquals("order-1", response.orderId)
            val request = requests.single()
            assertTrue(request.body.contains("\"category\":\"linear\""), request.body)
            assertTrue(request.body.contains("\"side\":\"Sell\""), request.body)
            assertTrue(request.body.contains("\"orderType\":\"Limit\""), request.body)
            assertTrue(request.body.contains("\"qty\":\"0.00001\""), request.body)
            assertTrue(request.body.contains("\"timeInForce\":\"PostOnly\""), request.body)
            assertTrue(request.body.contains("\"positionIdx\":2"), request.body)
            assertTrue(request.body.contains("\"reduceOnly\":true"), request.body)
            val timestamp = request.headers["X-BAPI-TIMESTAMP"] ?: error("Missing timestamp header")
            assertEquals("test-key", request.headers["X-BAPI-API-KEY"])
            assertEquals(
                bybitHmacSha256Hex(secret, timestamp + apiKey + "5000" + request.body),
                request.headers["X-BAPI-SIGN"],
            )
        } finally {
            http.close()
        }
    }

    @Test
    fun `refuses per-order margin mode changes that conflict with account-wide mode`() = runBlocking {
        val (http, requests) = client {
            assertEquals("/v5/account/info", it.path)
            """{"retCode":0,"retMsg":"OK","result":{"marginMode":"REGULAR_MARGIN"}}"""
        }
        try {
            val adapter = BybitTradingAdapter(
                http,
                BybitPrivateStreamHub(http, ProviderConfig(), "key", "secret"),
                ProviderConfig(),
                "key",
                "secret",
            )

            val response = adapter.placeOrder(
                OrderRequest(
                    symbol = "BTCUSDT",
                    side = OrderSide.BUY,
                    orderType = OrderType.MARKET,
                    quantity = 0.001,
                    marginMode = 1,
                )
            )

            assertFalse(response.success)
            assertTrue(response.message.orEmpty().contains("account-wide"))
            assertEquals(1, requests.size, "mismatched margin mode must block order placement")
        } finally {
            http.close()
        }
    }

    @Test
    fun `caches position mode when Bybit reports that requested mode is already active`() = runBlocking {
        val (http, requests) = client {
            assertEquals("/v5/position/switch-mode", it.path)
            """{"retCode":110025,"retMsg":"Position mode is not modified","result":{}}"""
        }
        try {
            val adapter = BybitTradingAdapter(
                http,
                BybitPrivateStreamHub(http, ProviderConfig(), "key", "secret"),
                ProviderConfig(),
                "key",
                "secret",
            )

            assertEquals(null, adapter.getPositionMode())
            assertTrue(adapter.setPositionMode(1))
            assertEquals(1, adapter.getPositionMode())
            assertTrue(requests.single().body.contains("\"mode\":3"))
        } finally {
            http.close()
        }
    }

    @Test
    fun `does not call private endpoints without credentials`() = runBlocking {
        var called = false
        val (http, _) = client {
            called = true
            """{"retCode":0,"retMsg":"OK","result":{}}"""
        }
        try {
            val adapter = BybitTradingAdapter(
                http,
                BybitPrivateStreamHub(http, ProviderConfig(), "", ""),
                ProviderConfig(),
                "",
                "",
            )

            val response = adapter.placeOrder(
                OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.MARKET, 0.001)
            )

            assertFalse(response.success)
            assertTrue(response.message.orEmpty().contains("BYBIT_API_KEY"))
            assertFalse(called)
            assertEquals(null, adapter.subscribeToOrders())
        } finally {
            http.close()
        }
    }
}
