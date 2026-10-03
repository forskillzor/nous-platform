/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.model

import kotlinx.serialization.Serializable

/**
 * Информация о торговом символе (пара) с биржи.
 * Содержит спецификации символа: tick size, step size, минимальный объём и т.д.
 */
@Serializable
data class SymbolInfo(
    /** Тикер символа (например, "BTCUSDT") */
    val symbol: String,
    /** Минимальный шаг цены (tick size) */
    val tickSize: Double,
    /** Минимальный шаг объёма (step size) */
    val stepSize: Double,
    /** Минимальный объём для ордера */
    val minQty: Double,
    /** Минимальная сумма ордера (notional) */
    val minNotional: Double,
    /** Статус символа (TRADING, HALT и т.д.) */
    val status: String,
    /** Базовый актив (например, "BTC") */
    val baseAsset: String,
    /** Котируемый актив (например, "USDT") */
    val quoteAsset: String,
    /** Тип контракта с биржи (например, "PERPETUAL") */
    val contractType: String? = null,
    /** Маржинальный актив (например, "USDT" для USD-M) */
    val marginAsset: String? = null,
)
