/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.model.trading

/**
 * Ставки комиссий по инструменту (доля, например 0.0002 = 0.02%).
 * Источник — активный провайдер ([TradingAdapter.getFeeRates]): каждая биржа
 * отдаёт свои реальные ставки (MEXC — tiered_fee_rate, Binance — exchange
 * info/account API и т.д.).
 */
data class FeeRates(
    val maker: Double,
    val taker: Double,
)
