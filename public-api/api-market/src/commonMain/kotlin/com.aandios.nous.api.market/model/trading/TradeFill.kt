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
    /** Сторона позиции, к которой относится fill: LONG (BUY) / SHORT (SELL). */
    val positionSide: TradeSide? = null,
    /** Сколько из этого fill закрыло позицию (0 — только открытие/увеличение). */
    val closedQuantity: Double = 0.0,
)
