/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.api.market.model.ContractType
import com.aandios.nous.feature.dom.domain.model.AggregationLevel
import com.aandios.nous.research.indicator.IndicatorPaneLayout
import com.aandios.nous.research.indicator.IndicatorType

/**
 * Пользовательские намерения графика (MVI).
 * Единственная публичная точка входа в ChartViewModel — dispatch(intent).
 */
sealed interface ChartIntent {
    data class SelectSymbol(val symbol: String) : ChartIntent
    data class SelectTimeframe(val timeframe: String) : ChartIntent
    data class SelectProvider(val providerId: String) : ChartIntent

    /** USDT-M / COIN-M — фильтр списка символов панели. */
    data class SelectContractType(val type: ContractType) : ChartIntent
    data object ToggleChartMode : ChartIntent
    data class SelectChartMode(val mode: ChartMode) : ChartIntent
    data class SetFpAggregation(val level: AggregationLevel) : ChartIntent
    data class SetFpProfileScale(val scale: FootprintProfileScale) : ChartIntent
    data class SetFpRingWidth(val width: FootprintRingWidth) : ChartIntent
    data class SetFpRingDiameter(val diameter: FootprintRingDiameter) : ChartIntent

    data class AddIndicatorOverlay(val type: IndicatorType) : ChartIntent
    data class AddIndicatorToPane(
        val type: IndicatorType,
        val paneId: String? = null,
        val createPane: Boolean = false,
    ) : ChartIntent
    data class RemoveIndicator(val instanceId: String) : ChartIntent
    data class UpdateIndicatorParams(
        val instanceId: String,
        val params: Map<String, Double>,
    ) : ChartIntent
    data class SetIndicatorLineColor(
        val instanceId: String,
        val lineId: String,
        /** ARGB override; `null` restores the theme color. */
        val colorArgb: Long?,
    ) : ChartIntent
    data class SelectIndicatorPane(val paneId: String?) : ChartIntent
    data class SetIndicatorPaneLayout(val layout: IndicatorPaneLayout) : ChartIntent
    data class SetIndicatorPaneHeight(
        val paneId: String,
        val heightDp: Float,
        /** Live resize passes false; the final drag value persists. */
        val persist: Boolean = true,
    ) : ChartIntent
    data class MoveIndicatorPane(val paneId: String, val targetIndex: Int) : ChartIntent
    data class ApplyIndicatorPreset(val preset: IndicatorPreset) : ChartIntent

    /** null — оставить текущие значения. */
    data class LoadChart(val symbol: String? = null, val timeframe: String? = null) : ChartIntent

    data object LoadMoreHistory : ChartIntent
    data object LoadMoreFootprintHistory : ChartIntent
    data object RestoreState : ChartIntent
}
