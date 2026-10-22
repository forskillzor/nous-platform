/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.api.market.model.ContractType
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.paper.PaperTrading
import com.aandios.nous.core.storage.StateStore
import com.aandios.nous.feature.dom.domain.model.AggregationLevel
import com.aandios.nous.research.indicator.IndicatorState
import kotlinx.serialization.json.Json

/**
 * Сохранение и восстановление состояния графика (символ, таймфрейм, режим, агрегация).
 * Ключи совместимы с прежним форматом ChartViewModel.
 *
 * [panelPrefix] — префикс ключей панельных настроек: каждая панель графика
 * хранит свои настройки (trading/paper/order type/show orders/positions и
 * footprint) отдельно (chart_{workspace}_{panel}_...).
 */
class ChartStatePersistor(
    private val store: StateStore,
    private val panelPrefix: String = "",
) {

    private fun panelKey(key: String) = panelPrefix + key

    private suspend fun panelOrLegacy(key: String): String? =
        store.getString(panelKey(key)) ?: store.getString(key)

    data class SavedState(
        val symbol: String? = null,
        val timeframe: String? = null,
        val chartMode: ChartMode? = null,
        val fpAggregation: AggregationLevel? = null,
        val fpProfileScale: FootprintProfileScale? = null,
        val fpRingWidth: FootprintRingWidth? = null,
        val fpRingDiameter: FootprintRingDiameter? = null,
        val zoomLevel: Float? = null,
        val providerId: String? = null,
        val contractType: ContractType? = null,
        val indicators: IndicatorState? = null,
    )

    data class TradingState(
        val enabled: Boolean = false,
        val confirmOrders: Boolean = false,
        val quantity: Double? = null,
        val orderType: OrderType = OrderType.LIMIT,
        val reduceOnly: Boolean = false,
        val leverage: Int? = null,
        val marginMode: Int = 2,
        val showOrders: Boolean = false,
        val showPositions: Boolean = false,
        /** Свёрнута ли панель chart trading (компактный вид). */
        val panelCollapsed: Boolean = false,
    )

    suspend fun saveTrading(
        enabled: Boolean,
        confirmOrders: Boolean,
        quantity: Double?,
        orderType: OrderType,
        reduceOnly: Boolean,
        leverage: Int?,
        marginMode: Int,
        showOrders: Boolean,
        showPositions: Boolean,
        panelCollapsed: Boolean,
    ) {
        store.putString(panelKey(KEY_TRADING_ENABLED), if (enabled) "1" else "0")
        store.putString(panelKey(KEY_CONFIRM_ORDERS), if (confirmOrders) "1" else "0")
        store.putString(panelKey(KEY_TRADING_QUANTITY), quantity?.toString() ?: "")
        store.putString(panelKey(KEY_ORDER_TYPE), orderType.name)
        store.putString(panelKey(KEY_REDUCE_ONLY), if (reduceOnly) "1" else "0")
        store.putString(panelKey(KEY_LEVERAGE), leverage?.toString() ?: "")
        store.putString(panelKey(KEY_MARGIN_MODE), marginMode.toString())
        store.putString(panelKey(KEY_SHOW_ORDERS), if (showOrders) "1" else "0")
        store.putString(panelKey(KEY_SHOW_POSITIONS), if (showPositions) "1" else "0")
        store.putString(panelKey(KEY_PANEL_COLLAPSED), if (panelCollapsed) "1" else "0")
    }

    suspend fun restoreTrading(): TradingState {
        val enabled = store.getString(panelKey(KEY_TRADING_ENABLED)) == "1"
        val confirm = store.getString(panelKey(KEY_CONFIRM_ORDERS)) == "1"
        val quantity = store.getString(panelKey(KEY_TRADING_QUANTITY))?.toDoubleOrNull()?.takeIf { it > 0 }
        val orderType = store.getString(panelKey(KEY_ORDER_TYPE))?.let { raw ->
            try {
                OrderType.valueOf(raw)
            } catch (e: Exception) {
                OrderType.LIMIT
            }
        } ?: OrderType.LIMIT
        val reduceOnly = store.getString(panelKey(KEY_REDUCE_ONLY)) == "1"
        val leverage = store.getString(panelKey(KEY_LEVERAGE))?.toIntOrNull()?.takeIf { it > 0 }
        val marginMode = store.getString(panelKey(KEY_MARGIN_MODE))?.toIntOrNull()?.takeIf { it == 1 || it == 2 } ?: 2
        // Изначально выключены (как trading/paper); включаются только вручную
        val showOrders = store.getString(panelKey(KEY_SHOW_ORDERS)) == "1"
        val showPositions = store.getString(panelKey(KEY_SHOW_POSITIONS)) == "1"
        val panelCollapsed = store.getString(panelKey(KEY_PANEL_COLLAPSED)) == "1"
        return TradingState(
            enabled, confirm, quantity, orderType, reduceOnly, leverage, marginMode,
            showOrders, showPositions, panelCollapsed,
        )
    }

    /** Персист глобального тумблера Paper (демо-торговля). */
    suspend fun savePaperEnabled(enabled: Boolean) {
        store.putString(panelKey(PaperTrading.STORE_KEY), if (enabled) "1" else "0")
    }

    suspend fun restorePaperEnabled(): Boolean =
        store.getString(panelKey(PaperTrading.STORE_KEY)) == "1"

    suspend fun save(
        symbol: String,
        timeframe: String,
        chartMode: ChartMode,
        providerId: String,
        contractType: ContractType,
    ) {
        store.putString(KEY_SYMBOL, symbol)
        store.putString(KEY_TIMEFRAME, timeframe)
        store.putString(KEY_CHART_MODE, chartMode.name)
        store.putString(KEY_PROVIDER_ID, providerId)
        store.putString(KEY_CONTRACT_TYPE, contractType.name)
    }

    /** Footprint-настройки панели: префикс не даёт им перетекать между chart-панелями. */
    suspend fun saveFootprint(
        fpAggregation: AggregationLevel,
        fpProfileScale: FootprintProfileScale,
        fpRingWidth: FootprintRingWidth,
        fpRingDiameter: FootprintRingDiameter,
    ) {
        store.putString(panelKey(KEY_FP_AGGREGATION), when (fpAggregation) {
            AggregationLevel.BaseTick -> "BaseTick"
            AggregationLevel.TenTick -> "TenTick"
            AggregationLevel.HundredTick -> "HundredTick"
        })
        store.putString(panelKey(KEY_FP_PROFILE_SCALE), fpProfileScale.name)
        store.putString(panelKey(KEY_FP_RING_WIDTH), fpRingWidth.name)
        store.putString(panelKey(KEY_FP_RING_DIAMETER), fpRingDiameter.name)
    }

    suspend fun saveIndicators(indicators: IndicatorState) {
        store.putString(panelKey(KEY_INDICATORS), indicatorsJson.encodeToString(indicators))
    }

    suspend fun restoreIndicators(): IndicatorState? =
        store.getString(panelKey(KEY_INDICATORS))?.let { raw ->
            runCatching { indicatorsJson.decodeFromString<IndicatorState>(raw) }.getOrNull()
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
        val aggregation = panelOrLegacy(KEY_FP_AGGREGATION)?.let { raw ->
            try {
                AggregationLevel.fromString(raw)
            } catch (e: Exception) {
                AggregationLevel.BaseTick
            }
        }
        val profileScale = panelOrLegacy(KEY_FP_PROFILE_SCALE)
            ?.let { FootprintProfileScale.fromString(it) }
        val ringWidth = panelOrLegacy(KEY_FP_RING_WIDTH)
            ?.let { FootprintRingWidth.fromString(it) }
        val ringDiameter = panelOrLegacy(KEY_FP_RING_DIAMETER)
            ?.let { FootprintRingDiameter.fromString(it) }
        return SavedState(
            symbol = store.getString(KEY_SYMBOL),
            timeframe = store.getString(KEY_TIMEFRAME),
            chartMode = mode,
            fpAggregation = aggregation,
            fpProfileScale = profileScale,
            fpRingWidth = ringWidth,
            fpRingDiameter = ringDiameter,
            providerId = store.getString(KEY_PROVIDER_ID),
            contractType = store.getString(KEY_CONTRACT_TYPE)?.let { raw ->
                runCatching { ContractType.valueOf(raw) }.getOrNull()
            },
            indicators = restoreIndicators(),
        )
    }

    companion object {
        private val indicatorsJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        const val KEY_SYMBOL = "chart_symbol"
        const val KEY_TIMEFRAME = "chart_timeframe"
        const val KEY_CHART_MODE = "chart_mode"
        const val KEY_PROVIDER_ID = "chart_provider_id"
        const val KEY_CONTRACT_TYPE = "chart_contract_type"
        const val KEY_FP_AGGREGATION = "fp_aggregation"
        const val KEY_FP_PROFILE_SCALE = "fp_profile_scale"
        const val KEY_FP_RING_WIDTH = "fp_ring_width"
        const val KEY_FP_RING_DIAMETER = "fp_ring_diameter"
        const val KEY_ZOOM = "chart_zoom"
        const val KEY_TRADING_ENABLED = "chart_trading_enabled"
        const val KEY_CONFIRM_ORDERS = "chart_confirm_orders"
        const val KEY_TRADING_QUANTITY = "chart_trading_quantity"
        const val KEY_ORDER_TYPE = "chart_order_type"
        const val KEY_REDUCE_ONLY = "chart_reduce_only"
        const val KEY_LEVERAGE = "chart_leverage"
        const val KEY_MARGIN_MODE = "chart_margin_mode"
        const val KEY_SHOW_ORDERS = "chart_show_orders"
        const val KEY_SHOW_POSITIONS = "chart_show_positions"
        const val KEY_PANEL_COLLAPSED = "chart_panel_collapsed"
        const val KEY_INDICATORS = "chart_indicators"
    }
}
