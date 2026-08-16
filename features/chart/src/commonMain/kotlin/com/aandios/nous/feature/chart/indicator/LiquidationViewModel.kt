/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.indicator

import com.aandios.nous.api.market.adapters.LiquidationAdapter
import com.aandios.nous.api.market.model.liquidation.LiquidationOrder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class LiquidationState(
    val orders: List<LiquidationOrder> = emptyList(),
    val connected: Boolean = false,
    val error: String? = null
)

class LiquidationViewModel(
    private val liquidationAdapter: LiquidationAdapter?
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var subscriptionJob: Job? = null

    private val _state = MutableStateFlow(LiquidationState())
    val state: StateFlow<LiquidationState> = _state.asStateFlow()

    fun subscribe(symbol: String) {
        unsubscribe()
        if (liquidationAdapter == null) {
            _state.value = _state.value.copy(error = "Liquidation adapter not available")
            return
        }

        _state.value = LiquidationState(connected = true)

        subscriptionJob = scope.launch {
            try {
                // 1. Load historical data first
                val endTime = com.aandios.nous.core.currentTimeMillis()
                val startTime = endTime - 60 * 60 * 1000L // last hour
                val history = liquidationAdapter.getHistoricalLiquidations(
                    symbol = symbol,
                    startTime = startTime,
                    endTime = endTime,
                    limit = 100
                )
                _state.value = _state.value.copy(orders = history)

                // 2. Then subscribe to real-time WebSocket
                liquidationAdapter.subscribeToLiquidations(symbol)
                    .catch { e ->
                        _state.value = _state.value.copy(connected = false, error = e.message)
                    }
                    .collect { order ->
                        val current = _state.value.orders.toMutableList()
                        current.add(order)
                        if (current.size > 1000) {
                            current.removeAt(0)
                        }
                        _state.value = _state.value.copy(orders = current)
                    }
            } catch (e: CancellationException) {
                // normal stop
            } catch (e: Exception) {
                _state.value = _state.value.copy(connected = false, error = e.message)
            }
        }
    }

    fun unsubscribe() {
        subscriptionJob?.cancel()
        subscriptionJob = null
        _state.value = LiquidationState()
    }

    fun clear() {
        unsubscribe()
        scope.cancel()
    }
}
