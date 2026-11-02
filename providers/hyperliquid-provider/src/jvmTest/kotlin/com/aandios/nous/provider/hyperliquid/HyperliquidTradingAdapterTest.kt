/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.trading.OrderRequest
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.provider.hyperliquid.adapter.HyperliquidTradingAdapter
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HyperliquidTradingAdapterTest {
    private class FakeTradingInfo(
        private val infoHandler: suspend (JsonObject) -> JsonElement,
        private val exchangeHandler: suspend (JsonObject) -> JsonObject,
    ) : HyperliquidInfoApi {
        val requests = mutableListOf<JsonObject>()
        val exchanges = mutableListOf<JsonObject>()

        override suspend fun info(body: JsonObject): JsonElement {
            requests += body
            return infoHandler(body)
        }

        override suspend fun exchange(payload: JsonObject): JsonObject {
            exchanges += payload
            return exchangeHandler(payload)
        }
    }

    private class FakeSigner(
        override val canSign: Boolean = true,
    ) : HyperliquidActionSigner {
        val actions = mutableListOf<HlAction>()

        override suspend fun accountAddress(): String = "0x1111111111111111111111111111111111111111"

        override suspend fun signPayload(action: HlAction, vaultAddress: String?): JsonObject {
            actions += action
            return buildJsonObject {
                put("action", hyperliquidActionToJson(action))
                put("nonce", 1L)
                put(
                    "signature",
                    buildJsonObject {
                        put("r", "0x1")
                        put("s", "0x2")
                        put("v", 27)
                    },
                )
            }
        }
    }

    private fun emptyState(): JsonObject = jsonObject(
        """{"marginSummary":{"accountValue":"0","totalMarginUsed":"0"},"assetPositions":[],"withdrawable":"0"}""",
    )

    private fun positionState(): JsonObject = jsonObject(
        """
        {
          "marginSummary":{"accountValue":"1000","totalMarginUsed":"100"},
          "withdrawable":"900",
          "assetPositions":[{"type":"oneWay","position":{
            "coin":"BTC","szi":"0.5","entryPx":"70000",
            "leverage":{"type":"cross","value":10},
            "unrealizedPnl":"50","liquidationPx":"60000"
          }}]
        }
        """.trimIndent(),
    )

    private fun tpSlOrders(): JsonElement = jsonElement(
        """
        [
          {"coin":"BTC","oid":10,"side":"A","sz":"0.5","origSz":"0.5","limitPx":"80000",
           "timestamp":1,"triggerPx":"80000","isTrigger":true,"isPositionTpsl":false,
           "reduceOnly":true,"orderType":"Take Profit Limit","triggerCondition":"Price above 80000"},
          {"coin":"BTC","oid":11,"side":"A","sz":"0.5","origSz":"0.5","limitPx":"65000",
           "timestamp":2,"triggerPx":"65000","isTrigger":true,"isPositionTpsl":false,
           "reduceOnly":true,"orderType":"Stop Loss Market","triggerCondition":"Price below 65000"}
        ]
        """.trimIndent(),
    )

    private fun triggeredTpSlOrders(): JsonElement = jsonElement(
        """
        [
          {"coin":"BTC","oid":10,"side":"A","sz":"0.5","origSz":"0.5","limitPx":"80000",
           "timestamp":1,"triggerCondition":"Triggered","isTrigger":false,"triggerPx":"0.0",
           "isPositionTpsl":false,"reduceOnly":true,"orderType":"Take Profit Limit","tif":"Gtc"},
          {"coin":"BTC","oid":11,"side":"A","sz":"0.5","origSz":"0.5","limitPx":"65000",
           "timestamp":2,"triggerPx":"65000","isTrigger":true,"isPositionTpsl":false,
           "reduceOnly":true,"orderType":"Stop Loss Market","triggerCondition":"Price below 65000"}
        ]
        """.trimIndent(),
    )

    private fun defaultInfoHandler(
        clearinghouse: suspend (JsonObject) -> JsonObject = { emptyState() },
        openOrders: suspend (JsonObject) -> JsonElement = { jsonElement("[]") },
        abstraction: suspend (JsonObject) -> JsonElement = { JsonPrimitive("default") },
        spot: suspend (JsonObject) -> JsonElement = {
            jsonElement("""{"balances":[],"tokenToAvailableAfterMaintenance":[]}""")
        },
        portfolio: suspend (JsonObject) -> JsonElement = { jsonElement("[]") },
        orderStatus: suspend (JsonObject) -> JsonElement = { jsonElement("""{"status":"order","order":null}""") },
    ): suspend (JsonObject) -> JsonElement = { body ->
        when (body.string("type")) {
            "perpDexs" -> HyperliquidFixtures.perpDexs
            "allPerpMetas" -> HyperliquidFixtures.allPerpMetas
            "spotMeta" -> HyperliquidFixtures.spotMeta
            "userRole" -> jsonElement("""{"role":"user"}""")
            "userAbstraction" -> abstraction(body)
            "spotClearinghouseState" -> spot(body)
            "portfolio" -> portfolio(body)
            "clearinghouseState" -> clearinghouse(body)
            "frontendOpenOrders" -> openOrders(body)
            "orderStatus" -> orderStatus(body)
            "activeAssetData" -> jsonElement(
                """{"leverage":{"type":"cross","value":10},"markPx":"78065"}""",
            )
            "l2Book" -> jsonElement(
                """{"coin":"BTC","time":1,"levels":[[{"px":"100","sz":"1"}],[{"px":"101","sz":"1"}]]}""",
            )
            "userFills" -> jsonElement(
                """
                [
                  {"coin":"BTC","px":"78000","sz":"0.1","side":"B","time":10,"oid":5,"fee":"0.1",
                   "feeToken":"USDC","closedPnl":"1.5","tid":100},
                  {"coin":"BTC","px":"79000","sz":"0.1","side":"A","time":11,"oid":6,"fee":"0.2",
                   "feeToken":"USDC","closedPnl":"-0.5","tid":101}
                ]
                """.trimIndent(),
            )
            "userFillsByTime" -> jsonElement(
                """
                [
                  {"coin":"BTC","px":"78000","sz":"0.1","side":"B","time":10,"oid":5,"closedPnl":"1.5"},
                  {"coin":"BTC","px":"79000","sz":"0.1","side":"A","time":11,"oid":6,"closedPnl":"-0.5"}
                ]
                """.trimIndent(),
            )
            "userFees" -> jsonElement("""{"userAddRate":"0.0001","userCrossRate":"0.0005"}""")
            else -> error("Unexpected Hyperliquid info request: ${body.string("type")}")
        }
    }

    private fun defaultExchangeHandler(): suspend (JsonObject) -> JsonObject = { payload ->
        val action = payload["action"]?.asObject()
        when (action?.string("type")) {
            "order" -> {
                val count = action["orders"]?.asArray()?.size ?: 1
                buildJsonObject {
                    put("status", "ok")
                    put(
                        "response",
                        buildJsonObject {
                            put("type", "order")
                            put(
                                "data",
                                buildJsonObject {
                                    put(
                                        "statuses",
                                        buildJsonArray {
                                            repeat(count) { index ->
                                                add(
                                                    buildJsonObject {
                                                        put(
                                                            "resting",
                                                            buildJsonObject {
                                                                put("oid", (index + 1).toLong())
                                                            },
                                                        )
                                                    },
                                                )
                                            }
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
            }
            "cancel" -> {
                val count = action["cancels"]?.asArray()?.size ?: 1
                buildJsonObject {
                    put("status", "ok")
                    put(
                        "response",
                        buildJsonObject {
                            put("type", "cancel")
                            put(
                                "data",
                                buildJsonObject {
                                    put(
                                        "statuses",
                                        buildJsonArray { repeat(count) { add(JsonPrimitive("success")) } },
                                    )
                                },
                            )
                        },
                    )
                }
            }
            else -> jsonObject("""{"status":"ok","response":{"type":"default"}}""")
        }
    }

    private fun adapter(
        info: FakeTradingInfo,
        streams: FakeHyperliquidStreams = FakeHyperliquidStreams(),
        signer: FakeSigner = FakeSigner(),
    ): Pair<HyperliquidTradingAdapter, FakeSigner> {
        val marketData = HyperliquidFixtures.marketData(info)
        return HyperliquidTradingAdapter(
            info = info,
            streams = streams,
            marketData = marketData,
            signer = signer,
            config = ProviderConfig(),
            clock = { 1_000_000_000L },
        ) to signer
    }

    private fun fakeInfo(
        infoHandler: suspend (JsonObject) -> JsonElement = defaultInfoHandler(),
        exchangeHandler: suspend (JsonObject) -> JsonObject = defaultExchangeHandler(),
    ): FakeTradingInfo = FakeTradingInfo(infoHandler, exchangeHandler)

    @Test
    fun `limit order builds oco parent and tp sl children`() = runTest {
        val info = fakeInfo()
        val (adapter, signer) = adapter(info)

        val response = adapter.placeOrder(
            OrderRequest(
                symbol = "BTC",
                side = OrderSide.BUY,
                orderType = OrderType.LIMIT,
                quantity = 0.001,
                price = 78_000.0,
                takeProfitPrice = 80_000.0,
                stopLossPrice = 75_000.0,
            ),
        )

        assertTrue(response.success, response.message)
        assertEquals("1", response.orderId)
        val action = signer.actions.single()
        assertEquals("order", action["type"])
        assertEquals("normalTpsl", action["grouping"])
        val orders = action["orders"] as List<*>
        assertEquals(3, orders.size)
        val parent = orders[0] as Map<*, *>
        assertEquals(0, parent["a"])
        assertEquals(true, parent["b"])
        assertEquals("78000", parent["p"])
        assertEquals("0.001", parent["s"])
        assertEquals(false, parent["r"])
        assertEquals(
            linkedMapOf<String, Any?>("limit" to linkedMapOf<String, Any?>("tif" to "Gtc")),
            parent["t"],
        )
        val tp = orders[1] as Map<*, *>
        assertEquals(false, tp["b"])
        assertEquals(true, tp["r"])
        assertEquals(
            linkedMapOf<String, Any?>(
                "trigger" to linkedMapOf<String, Any?>(
                    "isMarket" to false,
                    "triggerPx" to "80000",
                    "tpsl" to "tp",
                ),
            ),
            tp["t"],
        )
        val sl = orders[2] as Map<*, *>
        assertEquals("sl", ((sl["t"] as Map<*, *>)["trigger"] as Map<*, *>)["tpsl"])
    }

    @Test
    fun `only isolated hip3 asset forces cross margin off`() = runTest {
        val info = fakeInfo()
        val (adapter, signer) = adapter(info)

        val response = adapter.placeOrder(
            OrderRequest(
                symbol = "xyz:SP500",
                side = OrderSide.BUY,
                orderType = OrderType.LIMIT,
                quantity = 0.01,
                price = 7_600.0,
                leverage = 10,
                marginMode = 2,
            ),
        )

        assertTrue(response.success, response.message)
        assertEquals(2, signer.actions.size)
        val leverage = signer.actions[0]
        assertEquals("updateLeverage", leverage["type"])
        assertEquals(false, leverage["isCross"])
        assertEquals(10, leverage["leverage"])
        val order = signer.actions[1]
        assertEquals(110_001, (order["orders"] as List<*>).first().let { (it as Map<*, *>)["a"] })
    }

    @Test
    fun `fok is rejected before signing`() = runTest {
        val info = fakeInfo()
        val (adapter, signer) = adapter(info)

        val response = adapter.placeOrder(
            OrderRequest(
                symbol = "BTC",
                side = OrderSide.BUY,
                orderType = OrderType.FOK,
                quantity = 0.001,
                price = 78_000.0,
            ),
        )

        assertFalse(response.success)
        assertTrue(response.message.orEmpty().contains("FOK"))
        assertTrue(info.exchanges.isEmpty())
        assertTrue(signer.actions.isEmpty())
    }

    @Test
    fun `market order uses aggressive ioc limit price`() = runTest {
        val info = fakeInfo()
        val (adapter, signer) = adapter(info)

        val response = adapter.placeOrder(
            OrderRequest(
                symbol = "BTC",
                side = OrderSide.BUY,
                orderType = OrderType.MARKET,
                quantity = 0.001,
            ),
        )

        assertTrue(response.success, response.message)
        val order = (signer.actions.single()["orders"] as List<*>).first() as Map<*, *>
        assertEquals("106.1", order["p"])
        assertEquals("Ioc", ((order["t"] as Map<*, *>)["limit"] as Map<*, *>)["tif"])
    }

    @Test
    fun `positions map clearinghouse marks and position tp sl`() = runTest {
        val info = fakeInfo(
            infoHandler = defaultInfoHandler(
                clearinghouse = { body ->
                    if (body.string("dex").isNullOrEmpty()) positionState() else emptyState()
                },
                openOrders = { body ->
                    if (body.string("dex").isNullOrEmpty()) tpSlOrders() else jsonElement("[]")
                },
            ),
        )
        val (adapter, _) = adapter(info)

        val positions = adapter.getPositions()
        assertEquals(1, positions.size)
        val position = positions.single()
        assertEquals("BTC", position.symbol)
        assertEquals(TradeSide.BUY, position.side)
        assertEquals(0.5, position.quantity)
        assertEquals(70_000.0, position.avgPrice)
        assertEquals(78_065.0, position.markPrice)
        assertEquals(10, position.leverage)
        assertEquals(2, position.marginMode)
        assertEquals(0.0, position.pnl)
        assertEquals(50.0, position.unrealizedPnl)
        assertEquals(80_000.0, position.takeProfitPrice)
        assertEquals(65_000.0, position.stopLossPrice)
    }

    @Test
    fun `triggered tp limit remains position tp`() = runTest {
        val info = fakeInfo(
            infoHandler = defaultInfoHandler(
                clearinghouse = { body ->
                    if (body.string("dex").isNullOrEmpty()) positionState() else emptyState()
                },
                openOrders = { body ->
                    if (body.string("dex").isNullOrEmpty()) triggeredTpSlOrders() else jsonElement("[]")
                },
            ),
        )
        val (adapter, signer) = adapter(info)

        val position = adapter.getPositions().single()
        assertEquals(80_000.0, position.takeProfitPrice)
        assertEquals(65_000.0, position.stopLossPrice)

        val tp = adapter.getOpenOrders("BTC").single { it.orderId == "10" }
        assertTrue(tp.isTpSl)
        assertEquals(true, tp.isTakeProfit)
        assertEquals(OrderType.LIMIT, tp.orderType)
        assertEquals(80_000.0, tp.triggerPrice)

        assertTrue(adapter.setPositionTpSl(position, takeProfitPrice = 0.0, stopLossPrice = null))
        val cancel = signer.actions.single { it["type"] == "cancel" }
        val cancels = cancel["cancels"] as List<*>
        assertEquals(1, cancels.size)
        assertEquals(10L, (cancels.single() as Map<*, *>)["o"])
    }

    @Test
    fun `cancel triggered tp retries transient failure`() = runTest {
        val defaultExchange = defaultExchangeHandler()
        var cancelAttempts = 0
        val info = fakeInfo(
            infoHandler = defaultInfoHandler(
                clearinghouse = { body ->
                    if (body.string("dex").isNullOrEmpty()) positionState() else emptyState()
                },
                openOrders = { body ->
                    if (body.string("dex").isNullOrEmpty()) triggeredTpSlOrders() else jsonElement("[]")
                },
            ),
            exchangeHandler = { payload ->
                val action = payload["action"]?.asObject()
                if (action?.string("type") == "cancel") {
                    cancelAttempts += 1
                    if (cancelAttempts == 1) {
                        jsonObject(
                            """{"status":"ok","response":{"type":"cancel","data":{"statuses":[{"error":"temporary failure"}]}}}""",
                        )
                    } else {
                        defaultExchange(payload)
                    }
                } else {
                    defaultExchange(payload)
                }
            },
        )
        val (adapter, signer) = adapter(info)
        adapter.getOpenOrders("BTC")

        assertTrue(adapter.cancelOrder("10"))
        assertEquals(2, cancelAttempts)
        val cancel = signer.actions.last { it["type"] == "cancel" }
        val cancels = cancel["cancels"] as List<*>
        assertEquals(10L, (cancels.single() as Map<*, *>)["o"])
    }

    @Test
    fun `cancel order refreshes open orders when cache misses`() = runTest {
        val info = fakeInfo(
            infoHandler = defaultInfoHandler(
                clearinghouse = { body ->
                    if (body.string("dex").isNullOrEmpty()) positionState() else emptyState()
                },
                openOrders = { body ->
                    if (body.string("dex").isNullOrEmpty()) triggeredTpSlOrders() else jsonElement("[]")
                },
            ),
        )
        val (adapter, _) = adapter(info)

        assertTrue(adapter.cancelOrder("10"))
    }

    @Test
    fun `oco child tp is not treated as position tp`() = runTest {
        val parentOrders = jsonElement(
            """
            [{"coin":"BTC","oid":20,"side":"B","sz":"0.001","origSz":"0.001","limitPx":"78000",
              "timestamp":1,"reduceOnly":false,"orderType":"Limit","children":[
                {"coin":"BTC","oid":21,"side":"A","sz":"0.001","origSz":"0.001","limitPx":"80000",
                 "timestamp":2,"triggerPx":"80000","isTrigger":true,"reduceOnly":true,
                 "triggerCondition":"Price above 80000"}
              ]}]
            """.trimIndent(),
        )
        val info = fakeInfo(
            infoHandler = defaultInfoHandler(
                clearinghouse = { body ->
                    if (body.string("dex").isNullOrEmpty()) positionState() else emptyState()
                },
                openOrders = { body ->
                    if (body.string("dex").isNullOrEmpty()) parentOrders else jsonElement("[]")
                },
            ),
        )
        val (adapter, _) = adapter(info)

        val position = adapter.getPositions().single()
        assertNull(position.takeProfitPrice)
    }

    @Test
    fun `position tp sl clears only the changed side`() = runTest {
        val info = fakeInfo(
            infoHandler = defaultInfoHandler(
                clearinghouse = { body ->
                    if (body.string("dex").isNullOrEmpty()) positionState() else emptyState()
                },
                openOrders = { body ->
                    if (body.string("dex").isNullOrEmpty()) tpSlOrders() else jsonElement("[]")
                },
            ),
        )
        val (adapter, signer) = adapter(info)
        val position = adapter.getPositions().single()

        assertTrue(adapter.setPositionTpSl(position, takeProfitPrice = 0.0, stopLossPrice = null))

        val cancel = signer.actions.single { it["type"] == "cancel" }
        val cancels = cancel["cancels"] as List<*>
        assertEquals(1, cancels.size)
        assertEquals(10L, (cancels.single() as Map<*, *>)["o"])
        assertTrue(signer.actions.none { it["type"] == "order" })
    }

    @Test
    fun `position tp sl places only the changed side`() = runTest {
        val info = fakeInfo(
            infoHandler = defaultInfoHandler(
                clearinghouse = { body ->
                    if (body.string("dex").isNullOrEmpty()) positionState() else emptyState()
                },
                openOrders = { body ->
                    if (body.string("dex").isNullOrEmpty()) {
                        jsonElement(
                            """
                            [{"coin":"BTC","oid":11,"side":"A","sz":"0.5","origSz":"0.5","limitPx":"65000",
                              "timestamp":2,"triggerPx":"65000","isTrigger":true,"isPositionTpsl":false,
                              "reduceOnly":true,"orderType":"Stop Loss Market","triggerCondition":"Price below 65000"}]
                            """.trimIndent(),
                        )
                    } else {
                        jsonElement("[]")
                    }
                },
            ),
        )
        val (adapter, signer) = adapter(info)
        val position = adapter.getPositions().single()

        assertTrue(adapter.setPositionTpSl(position, takeProfitPrice = 81_000.0, stopLossPrice = null))

        val action = signer.actions.single { it["type"] == "order" }
        assertEquals("positionTpsl", action["grouping"])
        val order = (action["orders"] as List<*>).single() as Map<*, *>
        assertEquals(false, order["b"])
        assertEquals(true, order["r"])
        assertEquals(
            linkedMapOf<String, Any?>(
                "trigger" to linkedMapOf<String, Any?>(
                    "isMarket" to false,
                    "triggerPx" to "81000",
                    "tpsl" to "tp",
                ),
            ),
            order["t"],
        )
    }

    @Test
    fun `balances aggregate collateral across dexs`() = runTest {
        val info = fakeInfo(
            infoHandler = defaultInfoHandler(
                clearinghouse = { body ->
                    when (body.string("dex")) {
                        null, "" -> positionState()
                        "xyz" -> jsonObject(
                            """{"marginSummary":{"accountValue":"200","totalMarginUsed":"50"},"assetPositions":[],"withdrawable":"150"}""",
                        )
                        else -> emptyState()
                    }
                },
            ),
        )
        val (adapter, _) = adapter(info)

        val balance = adapter.getBalances().single { it.currency == "USDC" }
        assertEquals("1050", balance.amount)
        assertEquals("150", balance.margin)
        assertEquals("1200", balance.equity)
        assertEquals("50", balance.unrealizedPnl)
    }

    @Test
    fun `unified account balance uses portfolio equity and maintenance margin`() = runTest {
        val info = fakeInfo(
            infoHandler = defaultInfoHandler(
                clearinghouse = { body ->
                    when (body.string("dex")) {
                        "xyz" -> jsonObject(
                            """
                            {
                              "marginSummary":{"accountValue":"3.660144","totalMarginUsed":"3.660144"},
                              "crossMaintenanceMarginUsed":"1.830072",
                              "assetPositions":[{"type":"oneWay","position":{
                                "coin":"BTC","szi":"0.01","entryPx":"70000",
                                "leverage":{"type":"cross","value":10},
                                "unrealizedPnl":"0.3336","liquidationPx":"0"
                              }}],
                              "withdrawable":"0.032928"
                            }
                            """.trimIndent(),
                        )
                        else -> emptyState()
                    }
                },
                abstraction = { JsonPrimitive("unifiedAccount") },
                spot = {
                    jsonObject(
                        """
                        {
                          "balances":[{"coin":"USDC","token":0,"total":"39.386729","hold":"3.660816"}],
                          "tokenToAvailableAfterMaintenance":[[0,"37.556321"]]
                        }
                        """.trimIndent(),
                    )
                },
                portfolio = {
                    jsonElement("""[["day",{"accountValueHistory":[[1,"39.393929"]]}]]""")
                },
            ),
        )
        val (adapter, _) = adapter(info)

        val balance = adapter.getBalances().single()
        assertEquals("USDC", balance.currency)
        assertEquals("37.556321", balance.amount)
        assertEquals("3.660816", balance.frozen)
        assertEquals("1.830072", balance.margin)
        assertEquals("39.393929", balance.equity)
        assertEquals("0.3336", balance.unrealizedPnl)
    }

    @Test
    fun `trade history daily pnl and fees map from info endpoints`() = runTest {
        val info = fakeInfo()
        val (adapter, _) = adapter(info)

        val fills = adapter.getTradeHistory("BTC", 10)
        assertEquals(2, fills.size)
        assertEquals("101", fills.first().id)
        assertEquals(-0.5, fills.first().pnl)
        assertEquals(1.5, fills.last().pnl)

        val pnl = adapter.getDailyRealizedPnl()
        assertNotNull(pnl)
        assertEquals(1.0, pnl)

        val fees = adapter.getFeeRates("BTC")
        assertNotNull(fees)
        assertEquals(0.0001, fees.maker)
        assertEquals(0.0005, fees.taker)
    }

    @Test
    fun `cancel all orders batches cancels`() = runTest {
        val openOrders = jsonElement(
            """
            [
              {"coin":"BTC","oid":20,"side":"B","sz":"0.001","origSz":"0.001","limitPx":"78000",
               "timestamp":1,"reduceOnly":false,"orderType":"Limit"},
              {"coin":"BTC","oid":21,"side":"A","sz":"0.001","origSz":"0.001","limitPx":"79000",
               "timestamp":2,"reduceOnly":false,"orderType":"Limit"}
            ]
            """.trimIndent(),
        )
        val info = fakeInfo(
            infoHandler = defaultInfoHandler(openOrders = { openOrders }),
        )
        val (adapter, signer) = adapter(info)

        assertTrue(adapter.cancelAllOrders("BTC"))
        val cancel = signer.actions.single { it["type"] == "cancel" }
        assertEquals(2, (cancel["cancels"] as List<*>).size)
    }

    @Test
    fun `set order tp sl cancels parent and replaces oco`() = runTest {
        val parentOrders = jsonElement(
            """
            [{"coin":"BTC","oid":20,"side":"B","sz":"0.001","origSz":"0.001","limitPx":"78000",
              "timestamp":1,"reduceOnly":false,"orderType":"Limit","children":[
                {"coin":"BTC","oid":21,"side":"A","sz":"0.001","origSz":"0.001","limitPx":"80000",
                 "timestamp":2,"triggerPx":"80000","isTrigger":true,"reduceOnly":true,
                 "triggerCondition":"Take Profit"}
              ]}]
            """.trimIndent(),
        )
        val info = fakeInfo(
            infoHandler = defaultInfoHandler(openOrders = { parentOrders }),
        )
        val (adapter, signer) = adapter(info)
        val parent = adapter.getOpenOrders("BTC").first { !it.isTpSl }

        assertTrue(adapter.setOrderTpSl(parent, takeProfitPrice = 81_000.0, stopLossPrice = null))

        assertTrue(signer.actions.any { it["type"] == "cancel" })
        val order = signer.actions.single { it["type"] == "order" }
        assertEquals("normalTpsl", order["grouping"])
        assertEquals(2, (order["orders"] as List<*>).size)
    }
}
