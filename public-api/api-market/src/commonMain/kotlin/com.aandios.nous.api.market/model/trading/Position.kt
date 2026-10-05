/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.model.trading

/**
 * Позиция (perpetual).
 */
data class Position(
    val symbol: String = "",
    val side: TradeSide = TradeSide.BUY,
    val positionId: Long? = null,
    val quantity: Double = 0.0,
    /** Средняя цена входа. */
    val avgPrice: Double = 0.0,
    val markPrice: Double = 0.0,
    val leverage: Int? = null,
    /** Реализованный PnL. */
    val pnl: Double = 0.0,
    /** Нереализованный PnL (если провайдер отдаёт). */
    val unrealizedPnl: Double = 0.0,
    val liquidatePrice: Double? = null,
    /** Режим маржи: 1 — изолированная, 2 — кросс. */
    val marginMode: Int? = null,
)
