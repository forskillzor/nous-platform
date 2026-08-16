/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.domain

import com.aandios.nous.feature.dom.domain.model.AggregationLevel
import com.aandios.nous.feature.dom.domain.model.DepthLimit

/**
 * Единый стейт всех настроек DOM.
 * Используется для централизованного управления подписками.
 */
data class DomOptions(
    val provider: TradingProvider = TradingProvider.BINANCE,
    val symbol: TradingSymbol = TradingSymbol.defaultForProvider(TradingProvider.BINANCE),
    val depth: DepthLimit = DepthLimit.default(),
    val aggregation: AggregationLevel = AggregationLevel.BaseTick,
    val collapsed: Boolean = false
) {
    companion object {
        fun default() = DomOptions()
    }
    
    /**
     * Ключ для подписки: комбинация provider + symbol + depth.
     * При изменении любого из этих параметров — переподписка.
     */
    val subscriptionKey: String get() = "${provider.name}:${symbol.symbol}:${depth.value}"
}