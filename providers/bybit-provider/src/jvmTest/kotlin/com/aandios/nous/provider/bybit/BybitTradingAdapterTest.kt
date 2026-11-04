/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.trading.OrderRequest
import com.aandios.nous.api.market.model.trading.TradeSide
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

    private val positionListJson = """
        {"retCode":0,"retMsg":"OK","result":{"list":[{"symbol":"BTCUSDT","side":"Buy","size":"0.1","positionIdx":0,"avgPrice":"100","markPrice":"101","leverage":"10","curRealisedPnl":"0","unrealisedPnl":"1","liqPrice":"50","tradeMode":0}]}}
    """.trimIndent()

    private val emptyListJson = """{"retCode":0,"retMsg":"OK","result":{"list":[]}}"""

    private fun stopOrdersJson(takeProfit: String?, stopLoss: String?): String {
        val rows = buildList {
            takeProfit?.let {
                add("""{"orderId":"tp-1","symbol":"BTCUSDT","positionIdx":0,"stopOrderType":"PartialTakeProfit","triggerPrice":"$it"}""")
            }
            stopLoss?.let {
                add("""{"orderId":"sl-1","symbol":"BTCUSDT","positionIdx":0,"stopOrderType":"PartialStopLoss","triggerPrice":"$it"}""")
            }
        }
        return """{"retCode":0,"retMsg":"OK","result":{"list":[${rows.joinToString(",")}]}}"""
    }

    private fun adapter(http: HttpClient) = BybitTradingAdapter(
        http,
        BybitPrivateStreamHub(http, ProviderConfig(), "key", "secret"),
        ProviderConfig(),
        "key",
        "secret",
    )

    @Test
    fun `external TP SL cancellation clears cached position levels`() = runBlocking {
        var stopOrders = stopOrdersJson("120", "90")
        val (http, _) = client { request ->
            when {
                request.path == "/v5/position/list" -> positionListJson
                request.path == "/v5/order/realtime" && request.query.contains("orderFilter=StopOrder") -> stopOrders
                else -> emptyListJson
            }
        }
        try {
            val adapter = adapter(http)
            val first = adapter.getPositions().single()
            assertEquals(120.0, first.takeProfitPrice)
            assertEquals(90.0, first.stopLossPrice)

            stopOrders = emptyListJson
            val second = adapter.getPositions().single()
            assertEquals(null, second.takeProfitPrice)
            assertEquals(null, second.stopLossPrice)
        } finally {
            http.close()
        }
    }

    @Test
    fun `cancelling stale SL after external cancel does not recreate TP`() = runBlocking {
        var stopOrders = stopOrdersJson("120", "90")
        val (http, requests) = client { request ->
            when {
                request.path == "/v5/position/list" -> positionListJson
                request.path == "/v5/order/realtime" && request.query.contains("orderFilter=StopOrder") -> stopOrders
                else -> emptyListJson
            }
        }
        try {
            val adapter = adapter(http)
            val position = adapter.getPositions().single()

            stopOrders = emptyListJson
            assertTrue(adapter.setPositionTpSl(position, takeProfitPrice = null, stopLossPrice = 0.0))
            assertTrue(
                requests.none { it.path == "/v5/position/trading-stop" },
                "stale TP must not be recreated: ${requests.map { it.path }}",
            )
        } finally {
            http.close()
        }
    }

    @Test
    fun `moving TP amends only TP and leaves SL untouched`() = runBlocking {
        var stopOrders = stopOrdersJson("120", "90")
        val (http, requests) = client { request ->
            when {
                request.path == "/v5/position/list" -> positionListJson
                request.path == "/v5/order/amend" -> {
                    stopOrders = stopOrdersJson("130", "90")
                    """{"retCode":0,"retMsg":"OK","result":{"orderId":"tp-1"}}"""
                }
                request.path == "/v5/order/realtime" && request.query.contains("orderFilter=StopOrder") -> stopOrders
                else -> emptyListJson
            }
        }
        try {
            val adapter = adapter(http)
            val position = adapter.getPositions().single()

            assertTrue(adapter.setPositionTpSl(position, takeProfitPrice = 130.0, stopLossPrice = null))
            val amend = requests.single { it.path == "/v5/order/amend" }
            assertTrue(amend.body.contains("\"triggerPrice\":\"130\""), amend.body)
            assertTrue(amend.body.contains("\"price\":\"130\""), amend.body)
            assertFalse(amend.body.contains("stopLoss"), amend.body)
            assertTrue(requests.none { it.path == "/v5/order/cancel" })
            assertTrue(requests.none { it.path == "/v5/position/trading-stop" })
        } finally {
            http.close()
        }
    }

    @Test
    fun `cancel TP cancels only TP and does not touch SL`() = runBlocking {
        var stopOrders = stopOrdersJson("120", "90")
        val (http, requests) = client { request ->
            when {
                request.path == "/v5/position/list" -> positionListJson
                request.path == "/v5/order/cancel" -> {
                    stopOrders = stopOrdersJson(null, "90")
                    """{"retCode":0,"retMsg":"OK","result":{}}"""
                }
                request.path == "/v5/order/realtime" && request.query.contains("orderFilter=StopOrder") -> stopOrders
                else -> emptyListJson
            }
        }
        try {
            val adapter = adapter(http)
            val position = adapter.getPositions().single()

            assertTrue(adapter.setPositionTpSl(position, takeProfitPrice = 0.0, stopLossPrice = null))
            val cancel = requests.single { it.path == "/v5/order/cancel" }
            assertTrue(cancel.body.contains("\"orderId\":\"tp-1\""), cancel.body)
            assertTrue(requests.none { it.path == "/v5/position/trading-stop" })
            assertTrue(requests.none { it.path == "/v5/order/amend" })
            assertEquals(90.0, adapter.getPositions().single().stopLossPrice)
            assertEquals(null, adapter.getPositions().single().takeProfitPrice)
        } finally {
            http.close()
        }
    }

    @Test
    fun `cancel SL cancels only SL and does not touch TP`() = runBlocking {
        var stopOrders = stopOrdersJson("120", "90")
        val (http, requests) = client { request ->
            when {
                request.path == "/v5/position/list" -> positionListJson
                request.path == "/v5/order/cancel" -> {
                    stopOrders = stopOrdersJson("120", null)
                    """{"retCode":0,"retMsg":"OK","result":{}}"""
                }
                request.path == "/v5/order/realtime" && request.query.contains("orderFilter=StopOrder") -> stopOrders
                else -> emptyListJson
            }
        }
        try {
            val adapter = adapter(http)
            val position = adapter.getPositions().single()

            assertTrue(adapter.setPositionTpSl(position, takeProfitPrice = null, stopLossPrice = 0.0))
            val cancel = requests.single { it.path == "/v5/order/cancel" }
            assertTrue(cancel.body.contains("\"orderId\":\"sl-1\""), cancel.body)
            assertTrue(requests.none { it.path == "/v5/position/trading-stop" })
            assertEquals(120.0, adapter.getPositions().single().takeProfitPrice)
            assertEquals(null, adapter.getPositions().single().stopLossPrice)
        } finally {
            http.close()
        }
    }

    @Test
    fun `cancelling both TP and SL uses two cancel calls and no trading stop`() = runBlocking {
        val cancelled = mutableSetOf<String>()
        var stopOrders = stopOrdersJson("120", "90")
        val (http, requests) = client { request ->
            when {
                request.path == "/v5/position/list" -> positionListJson
                request.path == "/v5/order/cancel" -> {
                    if (request.body.contains("\"tp-1\"")) cancelled += "tp"
                    if (request.body.contains("\"sl-1\"")) cancelled += "sl"
                    stopOrders = stopOrdersJson(
                        if ("tp" in cancelled) null else "120",
                        if ("sl" in cancelled) null else "90",
                    )
                    """{"retCode":0,"retMsg":"OK","result":{}}"""
                }
                request.path == "/v5/order/realtime" && request.query.contains("orderFilter=StopOrder") -> stopOrders
                else -> emptyListJson
            }
        }
        try {
            val adapter = adapter(http)
            val position = adapter.getPositions().single()

            assertTrue(adapter.setPositionTpSl(position, takeProfitPrice = 0.0, stopLossPrice = 0.0))
            assertEquals(2, requests.count { it.path == "/v5/order/cancel" })
            assertTrue(requests.none { it.path == "/v5/position/trading-stop" })
            assertEquals(null, adapter.getPositions().single().takeProfitPrice)
            assertEquals(null, adapter.getPositions().single().stopLossPrice)
        } finally {
            http.close()
        }
    }

    @Test
    fun `open orders merge regular and stop order lists without duplicates`() = runBlocking {
        val (http, _) = client { request ->
            when {
                request.path == "/v5/order/realtime" && request.query.contains("orderFilter=StopOrder") ->
                    """{"retCode":0,"retMsg":"OK","result":{"list":[{"orderId":"stop-1","symbol":"BTCUSDT","side":"Sell","orderType":"Market","qty":"0.1","stopOrderType":"PartialTakeProfit","triggerPrice":"120","positionIdx":0,"orderStatus":"Untriggered","reduceOnly":"true"}]}}"""
                request.path == "/v5/order/realtime" ->
                    """{"retCode":0,"retMsg":"OK","result":{"list":[{"orderId":"regular-1","symbol":"BTCUSDT","side":"Buy","orderType":"Limit","qty":"0.1","price":"100","timeInForce":"GTC","orderStatus":"New","reduceOnly":"false"}]}}"""
                else -> emptyListJson
            }
        }
        try {
            val orders = adapter(http).getOpenOrders("BTCUSDT")
            assertEquals(listOf("regular-1", "stop-1"), orders.map { it.orderId })
            assertEquals(true, orders.single { it.orderId == "stop-1" }.isTpSl)
        } finally {
            http.close()
        }
    }

    @Test
    fun `trade history marks closing fills and splits order pnl by closed size`() = runBlocking {
        val (http, _) = client { request ->
            when (request.path) {
                "/v5/execution/list" ->
                    """{"retCode":0,"retMsg":"OK","result":{"list":[
                      {"symbol":"SOLUSDT","orderId":"open","side":"Buy","execType":"Trade","execId":"e1","execPrice":"100","execQty":"1","execFee":"0.1","execTime":"1","closedSize":""},
                      {"symbol":"SOLUSDT","orderId":"close","side":"Sell","execType":"Trade","execId":"e2","execPrice":"110","execQty":"0.6","execFee":"0.06","execTime":"2","closedSize":"0.6"},
                      {"symbol":"SOLUSDT","orderId":"close","side":"Sell","execType":"Trade","execId":"e3","execPrice":"110","execQty":"0.4","execFee":"0.04","execTime":"3","closedSize":"0.4"}
                    ]}}"""
                "/v5/position/closed-pnl" ->
                    """{"retCode":0,"retMsg":"OK","result":{"list":[
                      {"symbol":"SOLUSDT","orderId":"close","closedPnl":"10"}
                    ]}}"""
                else -> emptyListJson
            }
        }
        try {
            val fills = adapter(http).getTradeHistory("SOLUSDT", 10)
            assertEquals(3, fills.size)

            val entry = fills.single { it.id == "e1" }
            assertEquals(TradeSide.BUY, entry.positionSide)
            assertEquals(0.0, entry.closedQuantity)

            val closes = fills.filter { it.id == "e2" || it.id == "e3" }
            assertTrue(closes.all { it.positionSide == TradeSide.BUY && it.closedQuantity > 0.0 })
            assertEquals(10.0, closes.sumOf { it.pnl }, 1e-12)
            assertEquals(6.0, fills.single { it.id == "e2" }.pnl, 1e-12)
            assertEquals(4.0, fills.single { it.id == "e3" }.pnl, 1e-12)
        } finally {
            http.close()
        }
    }
}
