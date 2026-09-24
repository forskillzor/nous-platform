/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.feature.dom.domain.model.AggregationLevel

/**
 * Единое UI-состояние графика. Заменяет набор из 17 отдельных StateFlow
 * в ChartViewModel: UI подписывается на один поток и передаёт значения вниз аргументами.
 */
data class ChartUiState(
    val chartState: ChartState = ChartState.Loading,
    val currentSymbol: String = "BTCUSDT",
    val currentTimeframe: String = "1h",
    // TODO this hardcode need change to repository/datalayer initialisation symbol list
    val symbols: List<String> = listOf("BTCUSDT", "ETHUSDT"),
    val currentSymbolFormatter: SymbolFormatter = SymbolFormatter(),
    val historyLoadCount: Int = 0,
    val historyGeneration: Int = 0,
    val hasMoreHistory: Boolean = true,
    val footprintCandles: List<FootprintCandle> = emptyList(),
    val liveFootprintCandle: FootprintCandle? = null,
    val footprintCurrentPrice: Float? = null,
    val footprintLoading: Boolean = false,
    val footprintError: String? = null,
    val chartMode: ChartMode = ChartMode.CANDLESTICK,
    val symbolsWithFootprint: Set<String> = emptySet(),
    val fpAggregation: AggregationLevel = AggregationLevel.BaseTick,
    val hasMoreFootprintHistory: Boolean = true,
    val footprintHistoryLoadCount: Int = 0,
    val footprintHistoryGeneration: Int = 0,
)

sealed interface ChartState {
    object Loading : ChartState
    data class Success(val candles: List<Candle>, val currentPrice: Float? = null) : ChartState
    data class Error(val message: String) : ChartState
}
