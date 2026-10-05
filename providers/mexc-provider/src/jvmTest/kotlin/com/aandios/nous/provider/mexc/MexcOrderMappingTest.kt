/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.trading.OrderRequest
import com.aandios.nous.provider.mexc.adapter.MexcTradingAdapter
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

/**
 * Проверяет подписанные запросы MexcTradingAdapter: заголовки, тела,
 * маппинг OrderRequest → MEXC-параметры (side/type/reduceOnly).
 */
class MexcOrderMappingTest {

    private class CapturedRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String,
    )

    private fun runAdapter(
        config: ProviderConfig,
        handler: suspend (CapturedRequest) -> String,
        block: suspend (MexcTradingAdapter) -> Unit,
    ) {
        val engine = MockEngine { request ->
            val body = (request.body as? io.ktor.http.content.TextContent)?.text.orEmpty()
            val headers = request.headers.entries().associate { it.key to it.value.joinToString(";") }
            val captured = CapturedRequest(
                method = request.method.value,
                path = request.url.encodedPath,
                headers = headers,
                body = body,
            )
            val responseBody = handler(captured)
            respond(
                content = responseBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        }
        val adapter = MexcTradingAdapter(
            client = client,
            config = config,
            restGate = MexcRestGate(),
            streamHub = MexcStreamHub(client, config),
        )
        runBlocking { block(adapter) }
        client.close()
    }

    @Test
    fun `place limit buy maps to open long with signature headers`() {
        val bodies = mutableListOf<String>()
        var headersOk = false
        runAdapter(
            config = ProviderConfig(apiKey = "key1", secretKey = "secret1", displayName = "MEXC"),
            handler = { req ->
                headersOk = req.headers["ApiKey"] == "key1" &&
                    req.headers["Request-Time"]?.toLongOrNull() != null &&
                    req.headers["Signature"]?.isNotEmpty() == true
                bodies += req.body
                """{"success":true,"code":0,"data":102057569836905984}"""
            },
        ) { adapter ->
            val response = adapter.placeOrder(
                OrderRequest(
                    symbol = "BTCUSDT",
                    side = OrderSide.BUY,
                    orderType = OrderType.LIMIT,
                    quantity = 1.0,
                    price = 11.0,
                    clientOrderId = "oid-1",
                )
            )
            assertTrue(response.success)
            assertEquals("102057569836905984", response.orderId)
        }
        assertTrue(headersOk, "заголовки ApiKey/Request-Time/Signature обязательны")
        val body = bodies.single()
        assertTrue(body.contains("\"symbol\":\"BTC_USDT\""), body)
        assertTrue(body.contains("\"side\":1"), body)          // open long
        assertTrue(body.contains("\"type\":1"), body)          // limit
        assertTrue(body.contains("\"price\":\"11\""), body)
        assertTrue(body.contains("\"externalOid\":\"oid-1\""), body)
    }

    @Test
    fun `market sell reduce-only maps to close long`() {
        var body = ""
        runAdapter(
            config = ProviderConfig(apiKey = "k", secretKey = "s", displayName = "MEXC"),
            handler = { req ->
                body = req.body
                """{"success":true,"code":0,"data":42}"""
            },
        ) { adapter ->
            val response = adapter.placeOrder(
                OrderRequest(
                    symbol = "BTCUSDT",
                    side = OrderSide.SELL,
                    orderType = OrderType.MARKET,
                    quantity = 0.5,
                    reduceOnly = true,
                    positionId = 777,
                )
            )
            assertTrue(response.success)
        }
        assertTrue(body.contains("\"side\":4"), body)  // close long
        assertTrue(body.contains("\"type\":5"), body)  // market
        assertTrue(body.contains("\"reduceOnly\":true"), body)
        assertTrue(body.contains("\"positionId\":777"), body)
        assertFalse(body.contains("\"price\""), body)  // market — без цены
    }

    @Test
    fun `post only and fok order types`() {
        val types = mutableListOf<String>()
        runAdapter(
            config = ProviderConfig(apiKey = "k", secretKey = "s", displayName = "MEXC"),
            handler = { req ->
                types += req.body
                """{"success":true,"code":0,"data":1}"""
            },
        ) { adapter ->
            adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.POST_ONLY, 1.0, price = 10.0))
            adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.FOK, 1.0, price = 10.0))
        }
        assertTrue(types[0].contains("\"type\":2"), types[0])
        assertTrue(types[1].contains("\"type\":4"), types[1])
    }

    @Test
    fun `without keys returns error without network call`() {
        var called = false
        runAdapter(
            config = ProviderConfig(displayName = "MEXC"), // ключей нет
            handler = {
                called = true
                """{"success":true,"code":0,"data":1}"""
            },
        ) { adapter ->
            val response = adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.MARKET, 1.0))
            assertFalse(response.success)
            assertTrue(response.message?.contains("MEXC_API_KEY") == true, response.message.orEmpty())
        }
        assertFalse(called, "без ключей запрос не должен уйти в сеть")
    }

    @Test
    fun `cancel order posts id array`() {
        var body = ""
        runAdapter(
            config = ProviderConfig(apiKey = "k", secretKey = "s", displayName = "MEXC"),
            handler = { req ->
                body = req.body
                """{"success":true,"code":0,"data":[{"orderId":5,"errorCode":0}]}"""
            },
        ) { adapter ->
            assertTrue(adapter.cancelOrder("5"))
        }
        assertEquals("[5]", body)
    }
}
