/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.indicator

import com.aandios.nous.api.market.adapters.LiquidationAdapter
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.liquidation.LiquidationOrder
import com.aandios.nous.core.domain.timeseries.TimeSeriesController
import com.aandios.nous.research.indicator.IndicatorInput
import com.aandios.nous.research.indicator.IndicatorInputSource
import com.aandios.nous.research.indicator.InputKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Feed source for chart indicators. Currently owns the liquidation series;
 * future feeds (funding, open interest) extend [snapshot] here without touching
 * the engine or renderer.
 */
class ChartIndicatorInputSource(
    private val scope: CoroutineScope,
    private val liquidationHistory: LiquidationHistorySource? = null,
) : IndicatorInputSource {

    private val marketMutex = Mutex()
    private var generation = 0L
    private var closed = false

    private val _liquidations = MutableStateFlow<List<LiquidationOrder>>(emptyList())
    val liquidations: StateFlow<List<LiquidationOrder>> = _liquidations.asStateFlow()

    private val _updates = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var controller: TimeSeriesController<LiquidationOrder>? = null
    private var stateJob: Job? = null

    /**
     * Switches the active market and resubscribes. A null/blank market clears
     * the feed so stale data cannot survive a provider or symbol change.
     */
    suspend fun setMarket(
        symbol: String,
        adapter: LiquidationAdapter?,
        exchange: String? = null,
    ) {
        marketMutex.withLock {
            if (closed) return@withLock
            generation += 1
            val currentGeneration = generation
            stateJob?.cancel()
            stateJob = null
            controller?.dispose()
            controller = null

            val hadData = _liquidations.value.isNotEmpty()
            _liquidations.value = emptyList()
            if (hadData) _updates.tryEmit(Unit)

            if (symbol.isBlank()) return@withLock
            if (adapter == null && liquidationHistory == null) return@withLock

            val seriesController = TimeSeriesController(
                source = LiquidationSeriesSource(
                    liveAdapter = adapter,
                    symbol = symbol,
                    historySource = liquidationHistory,
                    exchange = exchange,
                ),
                scope = scope,
            )
            controller = seriesController
            stateJob = scope.launch {
                var previous = emptyList<LiquidationOrder>()
                seriesController.state.collect { state ->
                    if (currentGeneration != generation) return@collect
                    if (previous == state.items) return@collect
                    previous = state.items
                    _liquidations.value = state.items
                    _updates.tryEmit(Unit)
                }
            }
            seriesController.start()
        }
    }

    override fun snapshot(
        required: Set<InputKind>,
        candles: List<Candle>,
    ): IndicatorInput = IndicatorInput(
        candles = candles,
        liquidations = if (InputKind.LIQUIDATIONS in required) {
            _liquidations.value
        } else {
            emptyList()
        },
    )

    override fun updates(): SharedFlow<Unit> = _updates.asSharedFlow()

    fun close() {
        if (closed) return
        closed = true
        generation += 1
        stateJob?.cancel()
        stateJob = null
        controller?.dispose()
        controller = null
        _liquidations.value = emptyList()
    }
}
