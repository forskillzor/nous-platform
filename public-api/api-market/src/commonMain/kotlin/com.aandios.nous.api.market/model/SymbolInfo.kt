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
    /**
     * Размер одного контракта в базовом активе (MEXC futures: BTC_USDT =
     * 0.0001). Для рынков, где qty сразу в базовом активе, — 1.0.
     * Для inverse-контрактов (COIN-M) — размер контракта в USD-номинале.
     */
    val contractSize: Double = 1.0,
) {
    /**
     * Inverse-контракт (COIN-M, напр. MEXC BTC_USD): маржа в монете,
     * qty в контрактах, номинал контракта — [contractSize] USD.
     * Для USDT-M/USDC-M (settleCoin — стейбл) — false.
     */
    val isInverse: Boolean
        get() {
            val margin = marginAsset?.takeIf { it.isNotBlank() } ?: return false
            return margin.uppercase() !in STABLE_MARGIN_ASSETS
        }

    companion object {
        private val STABLE_MARGIN_ASSETS = setOf("USDT", "USDC", "USD1", "DAI", "USD")
    }
}
