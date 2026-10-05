/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.indicator

import com.aandios.nous.api.market.adapters.LiquidationAdapter
import com.aandios.nous.api.market.model.liquidation.LiquidationOrder
import com.aandios.nous.core.domain.timeseries.TimeSeriesController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class LiquidationState(
    val orders: List<LiquidationOrder> = emptyList(),
    val connected: Boolean = false,
    val error: String? = null
)

/**
 * Состояние ликвидаций поверх обобщённого TimeSeriesController:
 * история за последний час + realtime WebSocket.
 */
class LiquidationViewModel(
    private var liquidationAdapter: LiquidationAdapter?
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var controller: TimeSeriesController<LiquidationOrder>? = null
    private var stateJob: Job? = null

    private val _state = MutableStateFlow(LiquidationState())
    val state: StateFlow<LiquidationState> = _state.asStateFlow()

    /** Переключение адаптера (смена провайдера): переподписываемся на текущий символ. */
    fun setAdapter(adapter: LiquidationAdapter?) {
        if (adapter === liquidationAdapter) return
        val currentSymbol = lastSymbol
        liquidationAdapter = adapter
        if (currentSymbol != null) subscribe(currentSymbol)
    }

    private var lastSymbol: String? = null

    fun subscribe(symbol: String) {
        lastSymbol = symbol
        unsubscribe()
        val adapter = liquidationAdapter
        if (adapter == null) {
            _state.value = _state.value.copy(error = "Liquidation adapter not available")
            return
        }

        _state.value = LiquidationState(connected = true)

        val seriesController = TimeSeriesController(
            source = LiquidationSeriesSource(adapter, symbol),
            scope = scope,
        )
        controller = seriesController

        stateJob = scope.launch {
            seriesController.state.collect { series ->
                _state.value = _state.value.copy(
                    orders = series.items,
                    connected = series.error == null,
                    error = series.error,
                )
            }
        }

        seriesController.start()
    }

    fun unsubscribe() {
        stateJob?.cancel()
        stateJob = null
        controller?.dispose()
        controller = null
        _state.value = LiquidationState()
    }

    fun clear() {
        unsubscribe()
        scope.cancel()
    }
}
