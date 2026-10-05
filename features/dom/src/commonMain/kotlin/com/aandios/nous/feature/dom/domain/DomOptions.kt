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
 *
 * [provider] — id провайдера из ProviderRegistry (только реально
 * реализованные провайдеры; UI выбирает из реестра).
 */
data class DomOptions(
    val provider: String = "binance-nous-0.0.1",
    val symbol: TradingSymbol = TradingSymbol("BTCUSDT", "BTC/USDT", "binance-nous-0.0.1"),
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
    val subscriptionKey: String get() = "$provider:${symbol.symbol}:${depth.value}"
}
