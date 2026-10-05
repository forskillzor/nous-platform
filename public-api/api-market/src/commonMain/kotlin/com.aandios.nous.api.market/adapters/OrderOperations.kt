/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.adapters

import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.OrderRequest
import com.aandios.nous.api.market.model.trading.OrderResponse

/**
 * Общая бизнес-логика торговых панелей (chart trading и DOM): «перемещение»
 * или правка ордера — отмена старого и размещение нового (cancel+replace,
 * как на MEXC). Цена/плечо/маржа берутся из вызывающей панели.
 */
suspend fun TradingAdapter.replaceOrder(
    order: Order,
    price: Double,
    quantity: Double,
    leverage: Int? = null,
    marginMode: Int? = null,
): OrderResponse? {
    if (quantity <= 0.0) return null
    runCatching { cancelOrder(order.orderId) }
    return runCatching {
        placeOrder(
            OrderRequest(
                symbol = order.symbol.uppercase(),
                side = order.side,
                orderType = order.orderType,
                quantity = quantity,
                price = if (order.orderType == OrderType.MARKET) 0.0 else price,
                reduceOnly = order.reduceOnly,
                leverage = leverage,
                marginMode = marginMode,
            )
        )
    }.getOrNull()
}
