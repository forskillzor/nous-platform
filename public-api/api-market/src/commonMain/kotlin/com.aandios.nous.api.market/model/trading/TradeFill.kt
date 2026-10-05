/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.model.trading

import com.aandios.nous.api.market.model.orderbook.OrderSide

/**
 * Запись истории сделок (fill).
 */
data class TradeFill(
    val id: String,
    val orderId: String? = null,
    val symbol: String,
    val side: OrderSide,
    val price: Double,
    val quantity: Double,
    val fee: Double = 0.0,
    val feeCurrency: String = "",
    /** Реализованный PnL по сделке. */
    val pnl: Double = 0.0,
    val timestamp: Long = 0,
)
