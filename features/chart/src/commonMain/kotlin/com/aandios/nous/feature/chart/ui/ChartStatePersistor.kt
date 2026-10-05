/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.core.storage.StateStore
import com.aandios.nous.feature.dom.domain.model.AggregationLevel

/**
 * Сохранение и восстановление состояния графика (символ, таймфрейм, режим, агрегация).
 * Ключи совместимы с прежним форматом ChartViewModel.
 */
class ChartStatePersistor(private val store: StateStore) {

    data class SavedState(
        val symbol: String? = null,
        val timeframe: String? = null,
        val chartMode: ChartMode? = null,
        val fpAggregation: AggregationLevel? = null,
        val zoomLevel: Float? = null,
        val providerId: String? = null,
    )

    suspend fun save(
        symbol: String,
        timeframe: String,
        chartMode: ChartMode,
        fpAggregation: AggregationLevel,
        providerId: String,
    ) {
        store.putString(KEY_SYMBOL, symbol)
        store.putString(KEY_TIMEFRAME, timeframe)
        store.putString(KEY_CHART_MODE, chartMode.name)
        store.putString(KEY_PROVIDER_ID, providerId)
        store.putString(KEY_FP_AGGREGATION, when (fpAggregation) {
            AggregationLevel.BaseTick -> "BaseTick"
            AggregationLevel.TenTick -> "TenTick"
            AggregationLevel.HundredTick -> "HundredTick"
        })
    }

    /** Зум живёт в UI-слое и сохраняется отдельно. */
    suspend fun saveZoom(zoomLevel: Float) {
        store.putString(KEY_ZOOM, zoomLevel.toString())
    }

    suspend fun restoreZoom(): Float? =
        store.getString(KEY_ZOOM)?.toFloatOrNull()

    suspend fun restore(): SavedState {
        val mode = store.getString(KEY_CHART_MODE)?.let { raw ->
            try {
                ChartMode.valueOf(raw)
            } catch (e: Exception) {
                ChartMode.CANDLESTICK
            }
        }
        val aggregation = store.getString(KEY_FP_AGGREGATION)?.let { raw ->
            try {
                AggregationLevel.fromString(raw)
            } catch (e: Exception) {
                AggregationLevel.BaseTick
            }
        }
        return SavedState(
            symbol = store.getString(KEY_SYMBOL),
            timeframe = store.getString(KEY_TIMEFRAME),
            chartMode = mode,
            fpAggregation = aggregation,
            providerId = store.getString(KEY_PROVIDER_ID),
        )
    }

    companion object {
        const val KEY_SYMBOL = "chart_symbol"
        const val KEY_TIMEFRAME = "chart_timeframe"
        const val KEY_CHART_MODE = "chart_mode"
        const val KEY_PROVIDER_ID = "chart_provider_id"
        const val KEY_FP_AGGREGATION = "fp_aggregation"
        const val KEY_ZOOM = "chart_zoom"
    }
}
