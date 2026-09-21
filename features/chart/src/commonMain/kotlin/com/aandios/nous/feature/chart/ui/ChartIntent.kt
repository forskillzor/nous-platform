/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.feature.dom.domain.model.AggregationLevel

/**
 * Пользовательские намерения графика (MVI).
 * Единственная публичная точка входа в ChartViewModel — dispatch(intent).
 */
sealed interface ChartIntent {
    data class SelectSymbol(val symbol: String) : ChartIntent
    data class SelectTimeframe(val timeframe: String) : ChartIntent
    data object ToggleChartMode : ChartIntent
    data class SetFpAggregation(val level: AggregationLevel) : ChartIntent

    /** null — оставить текущие значения. */
    data class LoadChart(val symbol: String? = null, val timeframe: String? = null) : ChartIntent

    data object LoadMoreHistory : ChartIntent
    data object LoadMoreFootprintHistory : ChartIntent
    data object RestoreState : ChartIntent
}
