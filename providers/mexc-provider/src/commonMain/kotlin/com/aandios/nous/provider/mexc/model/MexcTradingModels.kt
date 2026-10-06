/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc.model

import com.aandios.nous.api.market.model.Balance
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.OrderStatus
import com.aandios.nous.api.market.model.trading.Position
import com.aandios.nous.api.market.model.trading.TradeFill
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.provider.mexc.fromMexcSymbol
import kotlinx.serialization.Serializable

// ── Общее ────────────────────────────────────────────────────────────────────

@Serializable
data class MexcEnvelope(
    val success: Boolean = false,
    val code: Int = -1,
    val message: String? = null,
    val data: kotlinx.serialization.json.JsonElement? = null,
)

// ── Ордер: submit/cancel ─────────────────────────────────────────────────────

@Serializable
data class MexcCancelResult(
    val orderId: Long? = null,
    val errorCode: Int = -1,
    val errorMsg: String? = null,
)

// ── Активы ───────────────────────────────────────────────────────────────────

@Serializable
data class MexcAsset(
    val currency: String = "",
    val positionMargin: Double = 0.0,
    val frozenBalance: Double = 0.0,
    val availableBalance: Double = 0.0,
    val cashBalance: Double = 0.0,
    val equity: Double = 0.0,
    val unrealized: Double = 0.0,
    val bonus: Double = 0.0,
) {
    fun toBalance(): Balance = Balance(
        currency = currency,
        amount = availableBalance.toString(),
        frozen = frozenBalance.toString(),
        margin = positionMargin.toString(),
        equity = equity.toString(),
        unrealizedPnl = unrealized.toString(),
    )
}

// ── Позиции ──────────────────────────────────────────────────────────────────

@Serializable
data class MexcPosition(
    val positionId: Long = 0,
    val symbol: String = "",
    val positionType: Int = 0, // 1 long, 2 short
    val openType: Int = 0,     // 1 isolated, 2 cross
    val state: Int = 0,        // 1 holding, 2 system, 3 closed
    val holdVol: Double = 0.0,
    val holdAvgPrice: Double = 0.0,
    val liquidatePrice: Double = 0.0,
    val realised: Double = 0.0,
    val leverage: Int = 0,
) {
    fun toPosition(): Position = Position(
        symbol = fromMexcSymbol(symbol),
        side = if (positionType == 2) TradeSide.SELL else TradeSide.BUY,
        positionId = positionId,
        quantity = holdVol,
        avgPrice = holdAvgPrice,
        pnl = realised,
        liquidatePrice = liquidatePrice.takeIf { it > 0 },
        leverage = leverage.takeIf { it > 0 },
        marginMode = openType.takeIf { it > 0 },
    )
}

// ── Ордера ───────────────────────────────────────────────────────────────────

@Serializable
data class MexcOrder(
    val orderId: Long = 0,
    val symbol: String = "",
    val price: Double = 0.0,
    val vol: Double = 0.0,
    val dealVol: Double = 0.0,
    val side: Int = 0,      // 1 open long, 2 close short, 3 open short, 4 close long
    val orderType: Int = 0, // 1-6
    val state: Int = 0,     // 1 new, 2 partial, 3 filled, 4 canceled, 5 invalid
    val externalOid: String? = null,
    val openType: Int = 0,
    val createTime: Long = 0,
    val leverage: Int = 0,
) {
    fun toOrder(): Order = Order(
        orderId = orderId.toString(),
        symbol = fromMexcSymbol(symbol),
        // 1 open long / 2 close short — покупка; 3 open short / 4 close long — продажа
        side = if (side == 1 || side == 2) OrderSide.BUY else OrderSide.SELL,
        orderType = when (orderType) {
            1 -> OrderType.LIMIT
            2 -> OrderType.POST_ONLY
            3 -> OrderType.IOC
            4 -> OrderType.FOK
            else -> OrderType.MARKET
        },
        price = price,
        quantity = vol,
        filledQuantity = dealVol,
        reduceOnly = side == 2 || side == 4,
        status = when (state) {
            1, 2 -> OrderStatus.OPEN
            3 -> OrderStatus.FILLED
            4 -> OrderStatus.CANCELED
            else -> OrderStatus.REJECTED
        },
        clientOrderId = externalOid,
        timestamp = createTime,
    )
}

// ── Сделки (fills) ───────────────────────────────────────────────────────────

@Serializable
data class MexcOrderDeal(
    val id: Long = 0,
    val orderId: Long = 0,
    val symbol: String = "",
    val side: Int = 0, // 1 open long, 2 close short, 3 open short, 4 close long
    val vol: Double = 0.0,
    val price: Double = 0.0,
    val fee: Double = 0.0,
    val feeCurrency: String = "",
    val profit: Double = 0.0,
    val timestamp: Long = 0,
) {
    fun toTradeFill(): TradeFill = TradeFill(
        id = id.toString(),
        orderId = orderId.toString(),
        symbol = fromMexcSymbol(symbol),
        // 1 open long / 2 close short — BUY, 3 open short / 4 close long — SELL
        side = if (side == 1 || side == 2) OrderSide.BUY else OrderSide.SELL,
        price = price,
        quantity = vol,
        fee = fee,
        feeCurrency = feeCurrency,
        pnl = profit,
        timestamp = timestamp,
    )
}

// ── История ордеров (history_orders; symbol необязателен) ───────────────────

/**
 * Ордер из `GET api/v1/private/order/list/history_orders`: используется для
 * History-таба (когда symbol не задан) и для дневного реализованного PnL.
 */
@Serializable
data class MexcHistoryOrder(
    val orderId: Long = 0,
    val symbol: String = "",
    val price: Double = 0.0,
    val vol: Double = 0.0,
    val dealAvgPrice: Double = 0.0,
    val dealVol: Double = 0.0,
    val side: Int = 0, // 1 open long, 2 close short, 3 open short, 4 close long
    val orderType: Int = 0,
    val state: Int = 0,
    val takerFee: Double = 0.0,
    val makerFee: Double = 0.0,
    val profit: Double = 0.0,
    val feeCurrency: String = "",
    val externalOid: String? = null,
    val createTime: Long = 0,
) {
    /** Реализованный PnL ордера за вычетом комиссий (для дневного итога). */
    val netProfit: Double get() = profit - takerFee - makerFee

    fun toTradeFill(): TradeFill = TradeFill(
        id = orderId.toString(),
        orderId = orderId.toString(),
        symbol = fromMexcSymbol(symbol),
        // 1 open long / 2 close short — BUY, 3 open short / 4 close long — SELL
        side = if (side == 1 || side == 2) OrderSide.BUY else OrderSide.SELL,
        price = if (dealAvgPrice > 0.0) dealAvgPrice else price,
        quantity = if (dealVol > 0.0) dealVol else vol,
        fee = takerFee + makerFee,
        feeCurrency = feeCurrency,
        pnl = profit,
        timestamp = createTime,
    )
}

// ── Плечо ────────────────────────────────────────────────────────────────────

@Serializable
data class MexcLeverageInfo(
    val symbol: String = "",
    val positionType: Int = 0,
    val leverage: Int = 0,
    val imr: Double = 0.0,
    val mmr: Double = 0.0,
)
