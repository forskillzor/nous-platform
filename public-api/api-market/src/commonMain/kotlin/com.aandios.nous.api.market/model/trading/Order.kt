/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.model.trading

import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType

/**
 * Ордер (открытый или из истории) в едином формате.
 */
data class Order(
    val orderId: String,
    val symbol: String,
    val side: OrderSide,
    val orderType: OrderType,
    val price: Double,
    val quantity: Double,
    /** Исполненный объём. */
    val filledQuantity: Double = 0.0,
    val reduceOnly: Boolean = false,
    val status: OrderStatus = OrderStatus.OPEN,
    val clientOrderId: String? = null,
    val timestamp: Long = 0,
)

enum class OrderStatus {
    OPEN, FILLED, CANCELED, REJECTED
}
