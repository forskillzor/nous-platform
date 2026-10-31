/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit.adapter

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.TradingAdapter
import com.aandios.nous.api.market.model.Balance
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.trading.FeeRates
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.OrderRequest
import com.aandios.nous.api.market.model.trading.OrderResponse
import com.aandios.nous.api.market.model.trading.OrderStatus
import com.aandios.nous.api.market.model.trading.Position
import com.aandios.nous.api.market.model.trading.TradeFill
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.provider.bybit.BybitPrivateStreamHub
import com.aandios.nous.provider.bybit.BybitTradingClient
import com.aandios.nous.provider.bybit.asArray
import com.aandios.nous.provider.bybit.asObject
import com.aandios.nous.provider.bybit.asObjectList
import com.aandios.nous.provider.bybit.bybitCurrentTimeMillis
import com.aandios.nous.provider.bybit.double
import com.aandios.nous.provider.bybit.long
import com.aandios.nous.provider.bybit.string
import com.aandios.nous.provider.bybit.plainBybitDecimal
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.CancellationException

class BybitTradingAdapter internal constructor(
    client: HttpClient,
    private val privateStreams: BybitPrivateStreamHub,
    config: ProviderConfig,
    apiKey: String,
    apiSecret: String,
) : TradingAdapter {
    private val tradingClient = BybitTradingClient(client, config, apiKey, apiSecret)
    private val positionIdentity = mutableMapOf<Long, Pair<String, Int>>()
    private val positionTpSlCache = mutableMapOf<Long, Pair<Double?, Double?>>()
    private var knownPositionMode: Int? = null

    private data class PositionStopOrder(val orderId: String, val price: Double, val symbol: String)

    private data class PositionTpSlState(
        val takeProfits: List<PositionStopOrder> = emptyList(),
        val stopLosses: List<PositionStopOrder> = emptyList(),
    ) {
        val takeProfitPrice: Double? get() = takeProfits.lastOrNull()?.price
        val stopLossPrice: Double? get() = stopLosses.lastOrNull()?.price
    }

    override suspend fun placeOrder(request: OrderRequest): OrderResponse {
        if (!tradingClient.hasCredentials) return noKeys()
        return try {
            validateMarginMode(request.marginMode)
            request.leverage?.let {
                if (getLeverage(request.symbol) != it) {
                    check(setLeverage(request.symbol, it)) { "Failed to set Bybit leverage to $it" }
                }
            }
            val type = request.orderType
            val body = buildJsonObject {
                put("category", CATEGORY)
                put("symbol", request.symbol.uppercase())
                put("side", if (request.side == OrderSide.BUY) "Buy" else "Sell")
                put("orderType", if (type == OrderType.MARKET || type == OrderType.STOP) "Market" else "Limit")
                put("qty", decimal(request.quantity))
                if (type == OrderType.STOP) {
                    put("triggerPrice", decimal(request.price))
                    put("triggerBy", "LastPrice")
                } else if (type != OrderType.MARKET) {
                    put("price", decimal(request.price))
                    put("timeInForce", when (type) {
                        OrderType.POST_ONLY -> "PostOnly"
                        OrderType.IOC -> "IOC"
                        OrderType.FOK -> "FOK"
                        else -> "GTC"
                    })
                }
                val positionIdx = request.positionId?.let { positionIdentity[it]?.second }
                    ?: positionIndex(request.side, request.positionMode)
                put("positionIdx", positionIdx)
                if (request.reduceOnly) put("reduceOnly", true)
                if (!request.reduceOnly && (request.stopLossPrice != null || request.takeProfitPrice != null)) {
                    put("tpslMode", "Partial")
                    request.takeProfitPrice?.let {
                        put("takeProfit", decimal(it))
                        put("tpOrderType", "Limit")
                        put("tpLimitPrice", decimal(it))
                        put("tpTriggerBy", "LastPrice")
                    }
                    request.stopLossPrice?.let {
                        put("stopLoss", decimal(it))
                        put("slOrderType", "Market")
                        put("slTriggerBy", "LastPrice")
                    }
                }
                request.clientOrderId?.takeIf { it.isNotBlank() }?.let { put("orderLinkId", it) }
            }
            val result = tradingClient.signedPost("/v5/order/create", body)
            val orderId = result.string("orderId").orEmpty()
            OrderResponse(
                orderId = orderId,
                price = request.price,
                success = orderId.isNotEmpty(),
                message = if (orderId.isEmpty()) "Bybit accepted no order id" else null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OrderResponse(orderId = "", price = request.price, success = false, message = e.message)
        }
    }

    override suspend fun cancelOrder(orderId: String): Boolean {
        if (!tradingClient.hasCredentials) return false
        val row = findRealtimeOrder(orderId) ?: return false
        val symbol = row.string("symbol") ?: return false
        tradingClient.signedPost(
            "/v5/order/cancel",
            buildJsonObject {
                put("category", CATEGORY)
                put("symbol", symbol)
                put("orderId", orderId)
            },
        )
        val stopOrderType = row.string("stopOrderType")
        if (stopOrderType?.let {
                it.contains("TakeProfit", true) || it.contains("StopLoss", true)
            } == true
        ) {
            val index = row.string("positionIdx")?.toIntOrNull() ?: 0
            val positionId = positionIdentityId(symbol, index)
            val cached = positionTpSlCache[positionId]
            positionTpSlCache[positionId] = if (stopOrderType.contains("TakeProfit", true)) {
                null to cached?.second
            } else {
                cached?.first to null
            }
        }
        return true
    }

    override suspend fun cancelAllOrders(symbol: String?): Boolean {
        if (!tradingClient.hasCredentials) return false
        val openOrders = getOpenOrders(symbol)
        if (openOrders.isEmpty()) return true
        openOrders.groupBy { it.symbol }.forEach { (orderSymbol, _) ->
            tradingClient.signedPost(
                "/v5/order/cancel-all",
                buildJsonObject {
                    put("category", CATEGORY)
                    put("symbol", orderSymbol)
                },
            )
        }
        return true
    }

    override suspend fun closePosition(
        symbol: String,
        positionId: Long?,
        quantity: Double?,
    ): OrderResponse? {
        if (!tradingClient.hasCredentials) return noKeys()
        val position = getPositions().firstOrNull {
            it.symbol.equals(symbol, ignoreCase = true) && (positionId == null || it.positionId == positionId)
        } ?: return OrderResponse("", success = false, message = "Position not found for $symbol")
        return placeOrder(
            OrderRequest(
                symbol = position.symbol,
                side = if (position.side == TradeSide.BUY) OrderSide.SELL else OrderSide.BUY,
                orderType = OrderType.MARKET,
                quantity = quantity ?: position.quantity,
                reduceOnly = true,
                positionId = position.positionId,
                positionMode = if (position.positionId?.and(3L) in listOf(1L, 2L)) 1 else 2,
            )
        )
    }

    override suspend fun getBalances(): List<Balance> {
        if (!tradingClient.hasCredentials) return emptyList()
        val result = tradingClient.signedGet(
            "/v5/account/wallet-balance",
            mapOf("accountType" to "UNIFIED"),
        )
        val account = result["list"]?.asArray()?.firstOrNull()?.asObject() ?: return emptyList()
        return account["coin"]?.asObjectList()?.map { coin ->
            val wallet = coin.double("walletBalance") ?: 0.0
            val locked = coin.double("locked") ?: 0.0
            val positionMargin = coin.double("totalPositionIM") ?: 0.0
            val available = coin.double("availableToWithdraw") ?: (wallet - locked - positionMargin).coerceAtLeast(0.0)
            Balance(
                currency = coin.string("coin").orEmpty(),
                amount = decimal(available),
                frozen = decimal(locked),
                margin = decimal(positionMargin),
                equity = decimal(coin.double("equity") ?: wallet),
                unrealizedPnl = decimal(coin.double("unrealisedPnl") ?: 0.0),
            )
        }.orEmpty()
    }

    override suspend fun getPositions(): List<Position> {
        if (!tradingClient.hasCredentials) return emptyList()
        val rows = getPaged("/v5/position/list", mapOf("category" to CATEGORY, "settleCoin" to SETTLE_COIN))
        positionIdentity.clear()
        rows.mapNotNull { it.string("positionIdx")?.toIntOrNull() }
            .firstOrNull { it == 1 || it == 2 }
            ?.let { knownPositionMode = HEDGE_MODE }
            ?: rows.mapNotNull { it.string("positionIdx")?.toIntOrNull() }
                .firstOrNull { it == 0 }
                ?.let { knownPositionMode = ONE_WAY_MODE }
        val positions = rows.mapNotNull { it.toPosition() }.filter { it.quantity > 0.0 }
        val extraTpSl = fetchPositionTpSlState()
        if (extraTpSl != null) {
            val activeIds = positions.mapNotNull { it.positionId }.toSet()
            positionTpSlCache.keys.retainAll(activeIds)
        }
        return positions.map { position ->
            val id = position.positionId
            val extra = id?.let { extraTpSl?.get(it) }
            val updated = if (extraTpSl != null) {
                position.copy(
                    takeProfitPrice = extra?.takeProfitPrice,
                    stopLossPrice = extra?.stopLossPrice,
                )
            } else {
                position
            }
            if (id != null) positionTpSlCache[id] = updated.takeProfitPrice to updated.stopLossPrice
            updated
        }
    }

    /** TP/SL Partial приходят как conditional-ордера, а не в position/list. */
    private suspend fun fetchPositionTpSlState(): Map<Long, PositionTpSlState>? {
        val rows = runCatching {
            getPaged(
                "/v5/order/realtime",
                mapOf(
                    "category" to CATEGORY,
                    "settleCoin" to SETTLE_COIN,
                    "orderFilter" to "StopOrder",
                    "limit" to "50",
                ),
            )
        }.getOrNull() ?: return null
        val takeProfits = mutableMapOf<Long, MutableList<PositionStopOrder>>()
        val stopLosses = mutableMapOf<Long, MutableList<PositionStopOrder>>()
        rows.forEach { row ->
            val stopType = row.string("stopOrderType")
            if (stopType?.let {
                    it.contains("TakeProfit", true) || it.contains("StopLoss", true)
                } != true
            ) return@forEach
            val symbol = row.string("symbol") ?: return@forEach
            val index = row.string("positionIdx")?.toIntOrNull() ?: 0
            val price = row.double("triggerPrice")?.takeIf { it > 0.0 } ?: return@forEach
            val orderId = row.string("orderId")?.takeIf { it.isNotBlank() } ?: return@forEach
            val id = positionIdentityId(symbol, index)
            val order = PositionStopOrder(orderId, price, symbol)
            if (stopType.contains("TakeProfit", true)) {
                takeProfits.getOrPut(id) { mutableListOf() } += order
            } else {
                stopLosses.getOrPut(id) { mutableListOf() } += order
            }
        }
        return (takeProfits.keys + stopLosses.keys).associateWith { id ->
            PositionTpSlState(
                takeProfits = takeProfits[id].orEmpty(),
                stopLosses = stopLosses[id].orEmpty(),
            )
        }
    }

    override suspend fun getOpenOrders(symbol: String?): List<Order> {
        if (!tradingClient.hasCredentials) return emptyList()
        val params = buildMap {
            put("category", CATEGORY)
            put(if (symbol == null) "settleCoin" else "symbol", symbol?.uppercase() ?: SETTLE_COIN)
        }
        val regular = getPaged("/v5/order/realtime", params).mapNotNull { it.toOrder() }
        val conditional = getPaged(
            "/v5/order/realtime",
            params + ("orderFilter" to "StopOrder"),
        ).mapNotNull { it.toOrder() }
        return (regular + conditional).distinctBy { it.orderId }
    }

    override suspend fun getTradeHistory(symbol: String?, limit: Int): List<TradeFill> {
        if (!tradingClient.hasCredentials) return emptyList()

        // 1. Получаем историю исполнений (филлы)
        val execParams = buildMap {
            put("category", CATEGORY)
            symbol?.let { put("symbol", it.uppercase()) }
            put("limit", limit.coerceIn(1, 100).toString())
        }
        val executions = getPaged("/v5/execution/list", execParams, maxPages = 1)
            .filter { it.string("execType") == "Trade" }
            .take(limit.coerceIn(1, 100))

        if (executions.isEmpty()) return emptyList()

        // 2. Получаем PnL по закрытым позициям, чтобы потом сопоставить
        val pnlParams = buildMap {
            put("category", CATEGORY)
            // Bybit требует либо symbol, либо settleCoin.
            // Если symbol null, берем все USDT-пары.
            if (symbol != null) put("symbol", symbol.uppercase()) else put("settleCoin", SETTLE_COIN)
            put("limit", "100")
        }

        // Строим мапу: orderId -> closedPnl
        val orderIdToPnl = mutableMapOf<String, Double>()
        runCatching {
            getPaged("/v5/position/closed-pnl", pnlParams, maxPages = 1).forEach { row ->
                val orderId = row.string("orderId")
                val pnl = row.double("closedPnl")
                if (orderId != null && pnl != null) {
                    orderIdToPnl[orderId] = pnl
                }
            }
        }

        // 3. Мапим филлы, подставляя PnL по orderId
        return executions.mapNotNull { exec ->
            val orderId = exec.string("orderId")
            // Если нашли PnL для этого orderId, подставляем. Иначе 0.0 (или execPnl, если вдруг он там будет)
            val actualPnl = orderId?.let { orderIdToPnl[it] } ?: 0.0

            exec.toTradeFill()?.copy(pnl = actualPnl)
        }
    }
    override suspend fun setLeverage(symbol: String, leverage: Int, positionId: Long?): Boolean {
        if (!tradingClient.hasCredentials || leverage < 1) return false
        tradingClient.signedPost(
            "/v5/position/set-leverage",
            buildJsonObject {
                put("category", CATEGORY)
                put("symbol", symbol.uppercase())
                put("buyLeverage", leverage.toString())
                put("sellLeverage", leverage.toString())
            },
        )
        return true
    }

    override suspend fun getLeverage(symbol: String): Int? {
        if (!tradingClient.hasCredentials) return null
        val result = tradingClient.signedGet(
            "/v5/position/list",
            mapOf("category" to CATEGORY, "symbol" to symbol.uppercase()),
        )
        return result["list"]?.asObjectList()?.firstNotNullOfOrNull {
            it.string("leverage")?.toDoubleOrNull()?.toInt()
        }
    }

    override suspend fun getPositionMode(): Int? = knownPositionMode

    override suspend fun setPositionMode(mode: Int): Boolean {
        if (!tradingClient.hasCredentials || mode !in 1..2) return false
        try {
            tradingClient.signedPost(
                "/v5/position/switch-mode",
                buildJsonObject {
                    put("category", CATEGORY)
                    put("coin", SETTLE_COIN)
                    put("mode", if (mode == HEDGE_MODE) 3 else 0)
                },
            )
        } catch (e: Exception) {
            if (e !is CancellationException && e.message?.contains("retCode=110025") == true) {
                knownPositionMode = mode
                return true
            }
            throw e
        }
        knownPositionMode = mode
        return true
    }

    override suspend fun adjustMargin(positionId: Long, amount: Double, add: Boolean): Boolean {
        if (!tradingClient.hasCredentials || amount <= 0.0) return false
        getPositions()
        val (symbol, index) = positionIdentity[positionId] ?: return false
        tradingClient.signedPost(
            "/v5/position/add-margin",
            buildJsonObject {
                put("category", CATEGORY)
                put("symbol", symbol)
                put("margin", decimal(if (add) amount else -amount))
                put("positionIdx", index)
            },
        )
        return true
    }

    override suspend fun setOrderTpSl(
        order: Order,
        takeProfitPrice: Double?,
        stopLossPrice: Double?,
    ): Boolean {
        if (!tradingClient.hasCredentials || order.reduceOnly || order.isTpSl) return false
        val current = findRealtimeOrder(order.orderId, order.symbol)
        val currentTp = current?.double("takeProfit")?.takeIf { it > 0.0 } ?: order.takeProfitPrice
        val currentSl = current?.double("stopLoss")?.takeIf { it > 0.0 } ?: order.stopLossPrice
        val finalTp = when {
            takeProfitPrice == null -> currentTp
            takeProfitPrice == 0.0 -> null
            else -> takeProfitPrice
        }
        val finalSl = when {
            stopLossPrice == null -> currentSl
            stopLossPrice == 0.0 -> null
            else -> stopLossPrice
        }
        val tpChanged = !samePrice(finalTp, currentTp)
        val slChanged = !samePrice(finalSl, currentSl)
        if (!tpChanged && !slChanged) return true
        return runCatching {
            tradingClient.signedPost(
                "/v5/order/amend",
                buildJsonObject {
                    put("category", CATEGORY)
                    put("symbol", order.symbol.uppercase())
                    put("orderId", order.orderId)
                    put("tpslMode", "Partial")
                    if (tpChanged) {
                        put("takeProfit", finalTp?.let(::decimal) ?: "0")
                        put("tpTriggerBy", "LastPrice")
                        finalTp?.let {
                            put("tpOrderType", "Limit")
                            put("tpLimitPrice", decimal(it))
                        }
                    }
                    if (slChanged) {
                        put("stopLoss", finalSl?.let(::decimal) ?: "0")
                        put("slTriggerBy", "LastPrice")
                        finalSl?.let { put("slOrderType", "Market") }
                    }
                },
            )
            true
        }.getOrDefault(false)
    }

    override suspend fun setPositionTpSl(
        position: Position,
        takeProfitPrice: Double?,
        stopLossPrice: Double?,
    ): Boolean {
        if (!tradingClient.hasCredentials) return false
        val (symbol, index) = positionIdentity[position.positionId]
            ?: run {
                getPositions()
                positionIdentity[position.positionId]
            }
            ?: return false
        val positionId = position.positionId ?: return false
        val fresh = fetchPositionTpSlState() ?: return false
        val existing = fresh[positionId] ?: PositionTpSlState()
        val existingTp = existing.takeProfitPrice
        val existingSl = existing.stopLossPrice
        val finalTp = when {
            takeProfitPrice == null -> existingTp
            takeProfitPrice == 0.0 -> null
            else -> takeProfitPrice
        }
        val finalSl = when {
            stopLossPrice == null -> existingSl
            stopLossPrice == 0.0 -> null
            else -> stopLossPrice
        }
        val tpChanged = !samePrice(finalTp, existingTp)
        val slChanged = !samePrice(finalSl, existingSl)
        if (!tpChanged && !slChanged) {
            positionTpSlCache[positionId] = finalTp to finalSl
            return true
        }

        val ok = if (tpChanged && slChanged) {
            val canceled = cancelPositionStopOrders(existing.takeProfits + existing.stopLosses)
            if (!canceled) {
                false
            } else if (finalTp == null && finalSl == null) {
                true
            } else {
                postPositionTpSl(
                    symbol, index, position.quantity, finalTp, finalSl,
                    includeTp = true, includeSl = true,
                )
            }
        } else if (tpChanged) {
            applyPositionStopSide(
                symbol, index, position.quantity,
                existing.takeProfits, finalTp, isTakeProfit = true,
            )
        } else {
            applyPositionStopSide(
                symbol, index, position.quantity,
                existing.stopLosses, finalSl, isTakeProfit = false,
            )
        }
        if (!ok) {
            positionTpSlCache.remove(positionId)
            return false
        }
        positionTpSlCache[positionId] = finalTp to finalSl
        return true
    }

    private suspend fun applyPositionStopSide(
        symbol: String,
        positionIdx: Int,
        quantity: Double,
        existing: List<PositionStopOrder>,
        finalPrice: Double?,
        isTakeProfit: Boolean,
    ): Boolean {
        if (existing.size == 1 && finalPrice != null &&
            amendPositionStop(existing.single(), symbol, finalPrice, isTakeProfit)
        ) {
            return true
        }
        if (!cancelPositionStopOrders(existing)) return false
        if (finalPrice == null) return true
        return postPositionTpSl(
            symbol,
            positionIdx,
            quantity,
            takeProfitPrice = if (isTakeProfit) finalPrice else null,
            stopLossPrice = if (isTakeProfit) null else finalPrice,
            includeTp = isTakeProfit,
            includeSl = !isTakeProfit,
        )
    }

    private suspend fun cancelPositionStopOrders(orders: List<PositionStopOrder>): Boolean {
        for (order in orders) {
            if (!cancelStopOrder(order.symbol, order.orderId)) return false
        }
        return true
    }

    private suspend fun cancelStopOrder(symbol: String, orderId: String): Boolean = runCatching {
        tradingClient.signedPost(
            "/v5/order/cancel",
            buildJsonObject {
                put("category", CATEGORY)
                put("symbol", symbol.uppercase())
                put("orderId", orderId)
            },
        )
        true
    }.getOrDefault(false)

    private suspend fun amendPositionStop(
        order: PositionStopOrder,
        symbol: String,
        price: Double,
        isTakeProfit: Boolean,
    ): Boolean = runCatching {
        tradingClient.signedPost(
            "/v5/order/amend",
            buildJsonObject {
                put("category", CATEGORY)
                put("symbol", symbol.uppercase())
                put("orderId", order.orderId)
                put("triggerPrice", decimal(price))
                put("triggerBy", "LastPrice")
                if (isTakeProfit) put("price", decimal(price))
            },
        )
        true
    }.getOrDefault(false)

    private suspend fun postPositionTpSl(
        symbol: String,
        positionIdx: Int,
        quantity: Double,
        takeProfitPrice: Double?,
        stopLossPrice: Double?,
        includeTp: Boolean,
        includeSl: Boolean,
    ): Boolean {
        if (!includeTp && !includeSl) return true
        return runCatching {
            tradingClient.signedPost(
                "/v5/position/trading-stop",
                buildJsonObject {
                    put("category", CATEGORY)
                    put("symbol", symbol)
                    put("positionIdx", positionIdx)
                    put("tpslMode", "Partial")
                    put("tpSize", decimal(quantity))
                    put("slSize", decimal(quantity))
                    if (includeTp) {
                        put("takeProfit", takeProfitPrice?.let(::decimal) ?: "0")
                        put("tpTriggerBy", "LastPrice")
                        takeProfitPrice?.let {
                            put("tpOrderType", "Limit")
                            put("tpLimitPrice", decimal(it))
                        }
                    }
                    if (includeSl) {
                        put("stopLoss", stopLossPrice?.let(::decimal) ?: "0")
                        put("slTriggerBy", "LastPrice")
                        stopLossPrice?.let { put("slOrderType", "Market") }
                    }
                },
            )
            true
        }.getOrDefault(false)
    }

    private fun samePrice(a: Double?, b: Double?): Boolean = when {
        a == null && b == null -> true
        a == null || b == null -> false
        else -> kotlin.math.abs(a - b) < 1e-9
    }

    override fun subscribeToPositions(): Flow<Position>? {
        if (!tradingClient.hasCredentials) return null
        return privateStreams.subscribe("position.linear").transform { text ->
            eventObjects(text).mapNotNull { it.toPosition() }.forEach { emit(it) }
        }
    }

    override fun subscribeToOrders(): Flow<Order>? {
        if (!tradingClient.hasCredentials) return null
        return privateStreams.subscribe("order.linear").transform { text ->
            eventObjects(text).mapNotNull { it.toOrder() }.forEach { emit(it) }
        }
    }

    override fun subscribeToBalances(): Flow<Balance>? {
        if (!tradingClient.hasCredentials) return null
        return privateStreams.subscribe("wallet").transform { text ->
            val root = JsonParser.parseObject(text) ?: return@transform
            root["data"]?.asArray()?.firstOrNull()?.asObject()?.get("coin")
                ?.asObjectList()?.map { it.toBalance() }?.forEach { emit(it) }
        }
    }

    override suspend fun getFeeRates(symbol: String): FeeRates? {
        if (!tradingClient.hasCredentials) return null
        val result = tradingClient.signedGet(
            "/v5/account/fee-rate",
            mapOf("category" to CATEGORY, "symbol" to symbol.uppercase()),
        )
        val row = result["list"]?.asArray()?.firstOrNull()?.asObject() ?: return null
        val maker = row.double("makerFeeRate") ?: return null
        val taker = row.double("takerFeeRate") ?: return null
        return FeeRates(maker = maker, taker = taker)
    }

    override suspend fun getDailyRealizedPnl(): Double? {
        if (!tradingClient.hasCredentials) return null
        val dayStart = bybitCurrentTimeMillis() / DAY_MS * DAY_MS
        val rows = getPaged(
            "/v5/position/closed-pnl",
            mapOf(
                "category" to CATEGORY,
                "startTime" to dayStart.toString(),
                "endTime" to bybitCurrentTimeMillis().toString(),
                "limit" to "100",
            ),
        )
        return rows.sumOf { it.double("closedPnl") ?: 0.0 }
    }

    private suspend fun validateMarginMode(requested: Int?) {
        if (requested == null) return
        require(requested == 1 || requested == 2) { "Unsupported margin mode: $requested" }
        val info = tradingClient.signedGet("/v5/account/info")
        val actual = info.string("marginMode")
        val matches = when (requested) {
            1 -> actual == "ISOLATED_MARGIN"
            else -> actual == "REGULAR_MARGIN"
        }
        check(matches) {
            val desired = if (requested == 1) "isolated" else "cross"
            "Bybit uses an account-wide margin mode. Select $desired margin in Bybit account settings; " +
                "the order was not sent."
        }
    }

    private suspend fun findRealtimeOrder(orderId: String, symbol: String? = null): JsonObject? {
        val base = buildMap {
            put("category", CATEGORY)
            put("orderId", orderId)
            put("limit", "1")
            if (symbol != null) put("symbol", symbol.uppercase()) else put("settleCoin", SETTLE_COIN)
        }
        val regular = runCatching {
            tradingClient.signedGet("/v5/order/realtime", base)["list"]?.asObjectList()?.firstOrNull()
        }.getOrNull()
        if (regular != null) return regular
        return runCatching {
            tradingClient.signedGet(
                "/v5/order/realtime",
                base + ("orderFilter" to "StopOrder"),
            )["list"]?.asObjectList()?.firstOrNull()
        }.getOrNull()
    }

    private suspend fun getPaged(
        path: String,
        baseParams: Map<String, String>,
        maxPages: Int = 20,
    ): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        var cursor: String? = null
        repeat(maxPages) {
            val params = baseParams.toMutableMap()
            cursor?.let { params["cursor"] = it }
            val response = tradingClient.signedGet(path, params)
            result += response["list"]?.asObjectList().orEmpty()
            cursor = response.string("nextPageCursor").orEmpty().ifEmpty { null }
            if (cursor == null) return result
        }
        return result
    }

    private fun positionIndex(side: OrderSide, positionMode: Int?): Int =
        if ((positionMode ?: knownPositionMode) == HEDGE_MODE) {
            if (side == OrderSide.BUY) 1 else 2
        } else {
            0
        }

    private fun JsonObject.toPosition(): Position? {
        val symbol = string("symbol") ?: return null
        val size = double("size") ?: return null
        // positionIdx: из поля, или из side, или 0 (one-way). Никогда не теряем.
        val positionIdx = string("positionIdx")?.toIntOrNull()
            ?: when (string("side")) {
                "Buy" -> 1
                "Sell" -> 2
                else -> 0
            }
        // side: из поля, или из positionIdx. Никогда не теряем.
        val side = when (string("side")) {
            "Buy" -> TradeSide.BUY
            "Sell" -> TradeSide.SELL
            else -> when (positionIdx) {
                2 -> TradeSide.SELL
                else -> TradeSide.BUY
            }
        }
        val positionId = positionIdentityId(symbol, positionIdx)
        val cached = positionTpSlCache[positionId]
        return Position(
            symbol = symbol,
            side = side,
            positionId = positionId,
            quantity = size,
            avgPrice = double("avgPrice") ?: double("entryPrice") ?: 0.0,
            markPrice = double("markPrice") ?: 0.0,
            leverage = string("leverage")?.toDoubleOrNull()?.toInt(),
            pnl = double("curRealisedPnl") ?: 0.0,
            unrealizedPnl = double("unrealisedPnl") ?: 0.0,
            liquidatePrice = double("liqPrice")?.takeIf { it > 0.0 },
            marginMode = string("tradeMode")?.toIntOrNull()?.takeIf { it == 1 }?.let { 1 },
            takeProfitPrice = double("takeProfit")?.takeIf { it > 0.0 } ?: cached?.first,
            stopLossPrice = double("stopLoss")?.takeIf { it > 0.0 } ?: cached?.second,
        )
    }

    private fun JsonObject.toOrder(): Order? {
        val sideText = string("side") ?: return null
        val side = when (sideText) {
            "Buy" -> OrderSide.BUY
            "Sell" -> OrderSide.SELL
            else -> return null
        }
        val orderTypeText = string("orderType") ?: return null
        val stopOrderType = string("stopOrderType")
        val isTpSl = stopOrderType?.let {
            it.contains("TakeProfit", true) || it.contains("StopLoss", true)
        } == true
        val isTakeProfit = if (isTpSl) stopOrderType.contains("TakeProfit", true) else null
        val reduceOnly = string("reduceOnly") == "true"
        val triggerPrice = double("triggerPrice")?.takeIf { it > 0.0 }
        val orderPrice = double("price")?.takeIf { it > 0.0 }
        val isStop = !isTpSl && triggerPrice != null &&
            (orderTypeText == "Market" || string("stopOrderType")?.contains("Stop", true) == true)
        val orderType = if (isStop) {
            OrderType.STOP
        } else when (string("timeInForce")) {
            "PostOnly" -> OrderType.POST_ONLY
            "IOC" -> OrderType.IOC
            "FOK" -> OrderType.FOK
            else -> if (orderTypeText == "Market") OrderType.MARKET else OrderType.LIMIT
        }
        val symbol = string("symbol").orEmpty()
        val parentPositionId = if (isTpSl) {
            string("positionIdx")?.toIntOrNull()?.let { positionIdentityId(symbol, it) }
        } else null
        return Order(
            orderId = string("orderId").orEmpty(),
            symbol = symbol,
            side = side,
            orderType = orderType,
            price = if (isTpSl) (orderPrice ?: 0.0) else (triggerPrice ?: orderPrice ?: 0.0),
            quantity = double("qty") ?: 0.0,
            filledQuantity = double("cumExecQty") ?: 0.0,
            reduceOnly = reduceOnly,
            status = when (string("orderStatus")) {
                "New", "PartiallyFilled", "Untriggered", "Triggered" -> OrderStatus.OPEN
                "Filled" -> OrderStatus.FILLED
                "Cancelled", "PartiallyFilledCanceled" -> OrderStatus.CANCELED
                else -> OrderStatus.REJECTED
            },
            clientOrderId = string("orderLinkId"),
            takeProfitPrice = if (reduceOnly || isTpSl) null else double("takeProfit")?.takeIf { it > 0.0 },
            stopLossPrice = if (reduceOnly || isTpSl) null else double("stopLoss")?.takeIf { it > 0.0 },
            isTpSl = isTpSl,
            isTakeProfit = isTakeProfit,
            triggerPrice = triggerPrice,
            parentPositionId = parentPositionId,
            timestamp = long("createdTime") ?: 0L,
            updatedAt = long("updatedTime") ?: long("createdTime") ?: 0L,
        )
    }

    private fun JsonObject.toTradeFill(): TradeFill? {
        val side = when (string("side")) {
            "Buy" -> OrderSide.BUY
            "Sell" -> OrderSide.SELL
            else -> return null
        }
        return TradeFill(
            id = string("execId") ?: return null,
            orderId = string("orderId"),
            symbol = string("symbol") ?: return null,
            side = side,
            price = double("execPrice") ?: return null,
            quantity = double("execQty") ?: return null,
            fee = double("execFee") ?: 0.0,
            feeCurrency = string("feeCurrency").orEmpty(),
            pnl = double("execPnl") ?: 0.0,
            timestamp = long("execTime") ?: 0L,
        )
    }

    private fun JsonObject.toBalance(): Balance {
        val wallet = double("walletBalance") ?: 0.0
        val locked = double("locked") ?: 0.0
        val positionMargin = double("totalPositionIM") ?: 0.0
        return Balance(
            currency = string("coin").orEmpty(),
            amount = decimal(double("availableToWithdraw") ?: (wallet - locked - positionMargin).coerceAtLeast(0.0)),
            frozen = decimal(locked),
            margin = decimal(positionMargin),
            equity = decimal(double("equity") ?: wallet),
            unrealizedPnl = decimal(double("unrealisedPnl") ?: 0.0),
        )
    }

    private fun eventObjects(text: String): List<JsonObject> {
        val root = JsonParser.parseObject(text) ?: return emptyList()
        val data = root["data"] ?: return emptyList()
        return data.asObjectList().ifEmpty { listOfNotNull(data.asObject()) }
    }

    private fun positionIdentityId(symbol: String, index: Int): Long {
        var hash = 0x811C9DC5.toInt()
        symbol.forEach { char ->
            hash = (hash xor char.code) * 0x01000193
        }
        val id = ((hash.toLong() and 0xFFFFFFFFL) shl 2) or (index.toLong() and 3L)
        positionIdentity[id] = symbol to index
        return id
    }

    private fun noKeys() = OrderResponse(
        orderId = "",
        success = false,
        message = "Bybit API keys not set (BYBIT_API_KEY / BYBIT_SECRET_KEY)",
    )

    private fun decimal(value: Double): String = plainBybitDecimal(value)

    private companion object {
        const val CATEGORY = "linear"
        const val SETTLE_COIN = "USDT"
        const val HEDGE_MODE = 1
        const val ONE_WAY_MODE = 2
        const val DAY_MS = 86_400_000L
    }
}

private object JsonParser {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseObject(text: String): JsonObject? =
        runCatching { json.parseToJsonElement(text).asObject() }.getOrNull()
}
