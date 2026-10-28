/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.indicator

import com.aandios.nous.api.market.adapters.LiquidationAdapter
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.liquidation.LiquidationOrder
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
    private var visibleFrom: Long? = null
    private var visibleTo: Long? = null

    private val _liquidations = MutableStateFlow<List<LiquidationOrder>>(emptyList())
    val liquidations: StateFlow<List<LiquidationOrder>> = _liquidations.asStateFlow()

    private val _updates = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var feed: LiquidationFeed? = null
    private var feedJob: Job? = null

    /**
     * Switches the active market and resubscribes. A null/blank market clears
     * the feed so stale data cannot survive a provider or symbol change.
     */
    suspend fun setMarket(
        symbol: String,
        adapter: LiquidationAdapter?,
        exchange: String? = null,
        timeframe: String = "1h",
    ) {
        marketMutex.withLock {
            if (closed) return@withLock
            generation += 1
            val currentGeneration = generation
            feedJob?.cancel()
            feedJob = null
            feed?.close()
            feed = null

            val hadData = _liquidations.value.isNotEmpty()
            _liquidations.value = emptyList()
            if (hadData) _updates.tryEmit(Unit)

            if (symbol.isBlank()) return@withLock
            if (adapter == null && liquidationHistory == null) return@withLock

            val newFeed = LiquidationFeed(
                scope = scope,
                symbol = symbol,
                exchange = exchange,
                timeframe = timeframe,
                liveAdapter = adapter,
                historySource = liquidationHistory,
            )
            feed = newFeed
            feedJob = scope.launch {
                newFeed.orders.collect { orders ->
                    if (currentGeneration != generation) return@collect
                    _liquidations.value = orders
                    _updates.tryEmit(Unit)
                }
            }
            visibleFrom?.let { from ->
                visibleTo?.let { to -> newFeed.setVisibleRange(from, to) }
            }
            newFeed.start()
        }
    }

    fun setVisibleRange(from: Long, to: Long) {
        if (closed || from >= to) return
        visibleFrom = from
        visibleTo = to
        feed?.setVisibleRange(from, to)
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
        feedJob?.cancel()
        feedJob = null
        feed?.close()
        feed = null
        _liquidations.value = emptyList()
    }
}
