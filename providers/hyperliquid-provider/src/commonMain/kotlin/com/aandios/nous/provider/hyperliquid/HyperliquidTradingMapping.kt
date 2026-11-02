/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import com.aandios.nous.api.market.model.Balance
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.OrderStatus
import com.aandios.nous.api.market.model.trading.Position
import com.aandios.nous.api.market.model.trading.TradeFill
import com.aandios.nous.api.market.model.trading.TradeSide
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlin.math.abs

internal fun hyperliquidPositionId(coin: String): Long {
    var hash = 0x811C9DC5.toInt()
    coin.forEach { char -> hash = (hash xor char.code) * 0x01000193 }
    return hash.toLong() and 0xFFFF_FFFFL
}

internal fun hyperliquidOrder(
    row: JsonObject,
    status: OrderStatus = OrderStatus.OPEN,
    updatedAt: Long? = null,
    positionEntryPrice: Double? = null,
    positionSide: TradeSide? = null,
    existing: Order? = null,
): Order? {
    val coin = row.string("coin") ?: existing?.symbol ?: return null
    val side = when (row.string("side")) {
        "B" -> OrderSide.BUY
        "A" -> OrderSide.SELL
        else -> existing?.side ?: return null
    }
    val oid = row.long("oid") ?: existing?.orderId?.toLongOrNull() ?: return null
    val size = row.double("sz") ?: row.double("origSz") ?: existing?.quantity ?: 0.0
    val originalSize = row.double("origSz")
        ?: existing?.let { it.quantity + it.filledQuantity }
        ?: size
    val triggerPrice = row.double("triggerPx")?.takeIf { it > 0.0 } ?: existing?.triggerPrice
    val limitPrice = row.double("limitPx")?.takeIf { it > 0.0 } ?: existing?.price ?: 0.0
    val reduceOnly = row.bool("reduceOnly") ?: existing?.reduceOnly ?: false
    val isPositionTpsl = row.bool("isPositionTpsl")
        ?: (existing?.isTpSl == true && existing.parentPositionId != null)
    val isTrigger = row.bool("isTrigger") ?: (triggerPrice != null)
    val isTpSl = isTrigger && reduceOnly
    val tif = row.string("tif")
    val orderTypeText = row.string("orderType")
    val orderType = when {
        isTrigger -> OrderType.STOP
        tif == "Alo" -> OrderType.POST_ONLY
        tif == "Ioc" -> OrderType.IOC
        tif == "Gtc" -> OrderType.LIMIT
        orderTypeText == "Market" -> OrderType.MARKET
        else -> existing?.orderType ?: OrderType.LIMIT
    }
    val isTakeProfit = if (isTpSl) {
        when {
            row.string("tpsl") == "tp" -> true
            row.string("tpsl") == "sl" -> false
            row.string("triggerCondition")?.contains("Take Profit", ignoreCase = true) == true -> true
            row.string("triggerCondition")?.contains("Stop Loss", ignoreCase = true) == true -> false
            existing?.isTakeProfit != null -> existing.isTakeProfit
            triggerPrice != null && positionEntryPrice != null && positionSide != null ->
                if (positionSide == TradeSide.BUY) {
                    triggerPrice > positionEntryPrice
                } else {
                    triggerPrice < positionEntryPrice
                }
            else -> null
        }
    } else {
        null
    }
    val parentPositionId = if (isPositionTpsl) {
        hyperliquidPositionId(coin)
    } else {
        existing?.parentPositionId
    }
    val timestamp = row.long("timestamp") ?: existing?.timestamp ?: 0L
    return Order(
        orderId = oid.toString(),
        symbol = coin,
        side = side,
        orderType = orderType,
        price = triggerPrice ?: limitPrice,
        quantity = size,
        filledQuantity = (originalSize - size).coerceAtLeast(0.0),
        reduceOnly = reduceOnly,
        status = status,
        clientOrderId = row.string("cloid") ?: existing?.clientOrderId,
        takeProfitPrice = existing?.takeProfitPrice,
        stopLossPrice = existing?.stopLossPrice,
        isTpSl = isTpSl,
        isTakeProfit = isTakeProfit,
        triggerPrice = triggerPrice,
        parentPositionId = parentPositionId,
        timestamp = timestamp,
        updatedAt = updatedAt ?: existing?.updatedAt ?: timestamp,
    )
}

internal data class HyperliquidFlattenedOrders(
    val orders: List<Order>,
    val childOrderIds: Set<String>,
)

internal fun hyperliquidFlattenOpenOrders(
    rows: List<JsonObject>,
    positionsByCoin: Map<String, Position>,
): HyperliquidFlattenedOrders {
    val result = LinkedHashMap<String, Order>()
    val childOrderIds = LinkedHashSet<String>()
    rows.forEach { row ->
        val coin = row.string("coin")
        val position = coin?.let { positionsByCoin[it.lowercase()] }
        val parent = hyperliquidOrder(
            row = row,
            positionEntryPrice = position?.avgPrice,
            positionSide = position?.side,
        )
        val childRows = row["children"]?.let { it.asArray() }.orEmpty()
        childRows.forEach { child ->
            child.asObject()?.long("oid")?.toString()?.let { childOrderIds += it }
        }
        val children = childRows.mapNotNull { child ->
            child.asObject()?.let {
                hyperliquidOrder(
                    row = it,
                    positionEntryPrice = position?.avgPrice,
                    positionSide = position?.side,
                )
            }
        }.map { child ->
            if (child.isTakeProfit != null || parent == null) {
                child
            } else {
                val trigger = child.triggerPrice
                if (trigger == null) {
                    child
                } else {
                    val takeProfit = if (parent.side == OrderSide.BUY) {
                        trigger > parent.price
                    } else {
                        trigger < parent.price
                    }
                    child.copy(isTakeProfit = takeProfit)
                }
            }
        }
        val takeProfit = children.firstOrNull { it.isTpSl && it.isTakeProfit == true }?.triggerPrice
        val stopLoss = children.firstOrNull { it.isTpSl && it.isTakeProfit == false }?.triggerPrice
        if (parent != null) {
            result[parent.orderId] = parent.copy(
                takeProfitPrice = takeProfit ?: parent.takeProfitPrice,
                stopLossPrice = stopLoss ?: parent.stopLossPrice,
            )
        }
        children.forEach { child ->
            val current = result[child.orderId]
            result[child.orderId] = if (current != null && current.isTakeProfit != null) current else child
        }
    }
    return HyperliquidFlattenedOrders(
        orders = result.values.toList(),
        childOrderIds = childOrderIds,
    )
}

internal fun hyperliquidPositionFromState(
    row: JsonObject,
    markPrice: Double?,
    takeProfitPrice: Double?,
    stopLossPrice: Double?,
): Position? {
    val coin = row.string("coin") ?: return null
    val size = row.double("szi") ?: return null
    if (size == 0.0) return null
    val side = if (size > 0.0) TradeSide.BUY else TradeSide.SELL
    val leverage = row["leverage"]?.asObject()
    return Position(
        symbol = coin,
        side = side,
        positionId = hyperliquidPositionId(coin),
        quantity = abs(size),
        avgPrice = row.double("entryPx") ?: 0.0,
        markPrice = markPrice ?: 0.0,
        leverage = leverage?.int("value"),
        pnl = 0.0,
        unrealizedPnl = row.double("unrealizedPnl") ?: 0.0,
        liquidatePrice = row.double("liquidationPx")?.takeIf { it > 0.0 },
        marginMode = if (leverage?.string("type") == "isolated") 1 else 2,
        takeProfitPrice = takeProfitPrice,
        stopLossPrice = stopLossPrice,
    )
}

internal fun hyperliquidBalanceFromState(
    state: JsonObject,
    currency: String,
): Balance? {
    val marginSummary = state["marginSummary"]?.asObject() ?: return null
    val accountValue = marginSummary.double("accountValue") ?: 0.0
    val marginUsed = marginSummary.double("totalMarginUsed") ?: 0.0
    val withdrawable = state.double("withdrawable") ?: 0.0
    val unrealized = state["assetPositions"]?.let { it.asArray() }.orEmpty().sumOf { element ->
        element.asObject()?.get("position")?.asObject()?.double("unrealizedPnl") ?: 0.0
    }
    if (accountValue == 0.0 && withdrawable == 0.0 && marginUsed == 0.0 && unrealized == 0.0) {
        return null
    }
    return Balance(
        currency = currency,
        amount = plainHyperliquidDecimal(withdrawable),
        frozen = "0",
        margin = plainHyperliquidDecimal(marginUsed),
        equity = plainHyperliquidDecimal(accountValue),
        unrealizedPnl = plainHyperliquidDecimal(unrealized),
    )
}

internal fun hyperliquidFill(row: JsonObject): TradeFill? {
    val coin = row.string("coin") ?: return null
    val side = when (row.string("side")) {
        "B" -> OrderSide.BUY
        "A" -> OrderSide.SELL
        else -> return null
    }
    val price = row.double("px") ?: return null
    val size = row.double("sz") ?: return null
    val timestamp = row.long("time") ?: 0L
    val id = row.long("tid")?.toString()
        ?: "${row.string("hash").orEmpty()}:$timestamp:$coin:$price:$size"
    return TradeFill(
        id = id,
        orderId = row.long("oid")?.toString(),
        symbol = coin,
        side = side,
        price = price,
        quantity = size,
        fee = row.double("fee") ?: 0.0,
        feeCurrency = row.string("feeToken").orEmpty(),
        pnl = row.double("closedPnl") ?: 0.0,
        timestamp = timestamp,
    )
}

internal fun hyperliquidClearinghouseStates(data: JsonObject): Map<String, JsonObject> {
    val result = LinkedHashMap<String, JsonObject>()
    data["clearinghouseStates"]?.let { it.asArray() }.orEmpty().forEach { element ->
        val pair = element.asArray() ?: return@forEach
        val dex = pair.getOrNull(0)?.asString().orEmpty()
        val state = pair.getOrNull(1)?.asObject() ?: return@forEach
        result[dex] = state
    }
    return result
}

internal fun hyperliquidAssetCtxs(data: JsonObject): Map<String, List<JsonObject>> {
    val result = LinkedHashMap<String, List<JsonObject>>()
    data["ctxs"]?.let { it.asArray() }.orEmpty().forEach { element ->
        val pair = element.asArray() ?: return@forEach
        val dex = pair.getOrNull(0)?.asString().orEmpty()
        val rows = pair.getOrNull(1)?.let { it.asArray() }.orEmpty().mapNotNull { it.asObject() }
        result[dex] = rows
    }
    return result
}

internal fun hyperliquidOrderStatus(status: String?): OrderStatus = when (status) {
    "open", "triggered" -> OrderStatus.OPEN
    "filled" -> OrderStatus.FILLED
    "canceled", "marginCanceled", "vaultWithdrawalCanceled", "openInterestCapCanceled",
    "selfTradeCanceled", "reduceOnlyCanceled", "siblingFilledCanceled", "delistedCanceled",
    "liquidatedCanceled", "scheduledCancel" -> OrderStatus.CANCELED
    "rejected", "tickRejected", "minTradeNtlRejected", "perpMarginRejected", "reduceOnlyRejected",
    "badAloPxRejected", "iocCancelRejected", "badTriggerPxRejected",
    "marketOrderNoLiquidityRejected", "positionIncreaseAtOpenInterestCapRejected",
    "positionFlipAtOpenInterestCapRejected", "tooAggressiveAtOpenInterestCapRejected",
    "openInterestIncreaseRejected", "insufficientSpotBalanceRejected", "oracleRejected",
    "perpMaxPositionRejected" -> OrderStatus.REJECTED
    else -> OrderStatus.CANCELED
}

internal fun hyperliquidOidFromStatus(status: JsonObject): Long? {
    listOf("resting", "filled", "waitingForTrigger", "triggered", "open").forEach { key ->
        status[key]?.asObject()?.long("oid")?.let { return it }
    }
    return status.long("oid")
}

private fun kotlinx.serialization.json.JsonElement.asString(): String? =
    (this as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
