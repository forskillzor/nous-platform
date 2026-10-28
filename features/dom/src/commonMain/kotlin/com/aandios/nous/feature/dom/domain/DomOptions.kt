/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.domain

import com.aandios.nous.api.market.model.ContractType
import com.aandios.nous.feature.dom.domain.model.AggregationLevel
import com.aandios.nous.feature.dom.domain.model.DepthLimit

/**
 * Единый стейт всех настроек DOM.
 * Используется для централизованного управления подписками.
 *
 * [provider] — id провайдера из ProviderRegistry (только реально
 * реализованные провайдеры; UI выбирает из реестра).
 * [contractType] — USDT-M / COIN-M: фильтр списка символов.
 * [depth] — число АГРЕГИРОВАННЫХ уровней в лесенке.
 * [aggregation] — размер корзины агрегации цены.
 * [freqMs] — частота эмитов структуры уровней из DOM-модели (мс).
 */
data class DomOptions(
    val provider: String = "binance-nous-0.0.1",
    val symbol: TradingSymbol = TradingSymbol("BTCUSDT", "BTC/USDT", "binance-nous-0.0.1"),
    val depth: DepthLimit = DepthLimit.default(),
    val aggregation: AggregationLevel = AggregationLevel.BaseTick,
    val collapsed: Boolean = false,
    val contractType: ContractType = ContractType.USDT_M,
    val freqMs: Int = 100,
) {
    companion object {
        /** Варианты частоты обновления структуры уровней (мс). */
        val frequencyOptions: List<Int> = listOf(100, 150, 200, 250, 300)

        fun default() = DomOptions()
    }
    
    /**
     * Глубина подписки на «сырые» уровни книги с учётом агрегации:
     * `depth × multiplier` (например, agg 10× и depth 20 → 200 уровней).
     * UI показывает до [depth] агрегированных уровней.
     */
    val rawDepth: Int get() = (depth.value * aggregation.multiplier).toInt()

    /**
     * Ключ для подписки: provider + symbol + фактическая (сырая) глубина.
     * При изменении любого из этих параметров — переподписка.
     */
    val subscriptionKey: String get() = "$provider:${symbol.symbol}:$rawDepth"
}
