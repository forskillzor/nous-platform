/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.footprint

import com.aandios.nous.api.market.adapters.TradesAdapter
import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.api.market.model.MutableFootprintCandle
import com.aandios.nous.core.Disposable
import com.aandios.nous.core.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.cancellation.CancellationException

data class FootprintUiState(
    val candles: List<FootprintCandle> = emptyList(),
    val liveCandle: FootprintCandle? = null,
    val currentPrice: Float? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val hasMoreHistory: Boolean = true,
    val historyLoadCount: Int = 0,
)

/**
 * Вся логика footprint-режима: история с REST, live-накопление из ленты сделок,
 * серверный polling для старших таймфреймов и пагинация.
 *
 * ChartViewModel оркестрирует: start/stop по смене режима, loadMore по скроллу,
 * а состояние мержит в общий ChartUiState.
 */
class FootprintController(
    private val footprintApiClient: FootprintApiClient?,
    private val tradesAdapter: TradesAdapter?,
) : Disposable {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var footprintJob: Job? = null
    private var pollingJob: Job? = null
    private var isLoadingMore = false

    private var symbol: String = ""
    private var displayTimeframe: String = "1h"

    private val _state = MutableStateFlow(FootprintUiState())
    val state: StateFlow<FootprintUiState> = _state.asStateFlow()

    fun start(symbol: String, displayTimeframe: String) {
        this.symbol = symbol
        this.displayTimeframe = displayTimeframe

        if (tradesAdapter == null && footprintApiClient == null) {
            _state.update { it.copy(error = "Neither trades adapter nor footprint API available") }
            loadData()
            return
        }

        footprintJob?.cancel()
        pollingJob?.cancel()
        _state.update {
            it.copy(
                loading = true,
                error = null,
                candles = emptyList(),
                liveCandle = null,
                hasMoreHistory = true,
                historyLoadCount = 0,
            )
        }
        isLoadingMore = false

        val (sourceTf, aggCount) = FootprintAggregator.resolveFootprintSourceTimeframe(displayTimeframe)
        val isLiveTrades = sourceTf == "1m" && tradesAdapter != null
        val sourceMs = FootprintAggregator.sourceTimeframeMs(sourceTf)

        footprintJob = scope.launch {
            try {
                // 1. Load history from server
                val history = fetchHistoricalFootprint()
                _state.update { it.copy(candles = history, loading = false) }

                if (isLiveTrades) {
                    // ---- Live trade accumulation (1m or 5m) ----
                    val displayMs = when (displayTimeframe) {
                        "1m" -> 60_000L; "5m" -> 300_000L; else -> 60_000L
                    }

                    var liveCandle: MutableFootprintCandle? = null
                    var lastCandleStart = 0L
                    var tickCount = 0L

                    tradesAdapter.subscribeToTrades(this@FootprintController.symbol).collect { trade ->
                        val candleStart = trade.timestamp / displayMs * displayMs

                        if (lastCandleStart > 0L && candleStart != lastCandleStart) {
                            val completed = liveCandle?.toFootprintCandle(tickCount)
                            if (completed != null) {
                                _state.update { it.copy(candles = it.candles + completed) }

                                // For 1m display: fetch authoritative version from server
                                // For 5m: local trade accumulation is authoritative (no server override)
                                if (aggCount == 1) {
                                    val serverCandle = fetchCompletedCandle(lastCandleStart, candleStart)
                                    if (serverCandle != null && serverCandle.levels.isNotEmpty()) {
                                        _state.update { s ->
                                            val updated = s.candles.toMutableList()
                                            if (updated.isNotEmpty()) updated[updated.lastIndex] = serverCandle
                                            s.copy(candles = updated)
                                        }
                                    }
                                }
                            }
                            liveCandle = null
                            tickCount = 0
                        }

                        val candle = liveCandle ?: MutableFootprintCandle(
                            symbol = this@FootprintController.symbol,
                            startTime = candleStart,
                            endTime = candleStart + displayMs,
                        ).also { liveCandle = it }
                        candle.addTrade(trade.price.toFloat(), trade.quantity.toFloat(), !trade.isBuyerMaker)
                        tickCount++

                        lastCandleStart = candleStart

                        val liveSnapshot = candle.toFootprintCandle(tickCount)
                        _state.update {
                            it.copy(
                                liveCandle = if (liveSnapshot.levels.isNotEmpty()) liveSnapshot else null,
                                currentPrice = candle.lastPrice.takeIf { p -> p > 0f },
                            )
                        }
                    }
                } else {
                    // ---- Server polling (15m, 30m, 1h, 4h) ----
                    while (isActive) {
                        delay(sourceMs)

                        val now = currentTimeMillis()
                        val displayEnd = now / (sourceMs * aggCount) * (sourceMs * aggCount)
                        val displayStart = displayEnd - sourceMs * aggCount

                        // Fetch exact range of source candles covering the display window
                        if (footprintApiClient != null) {
                            val raw = footprintApiClient.getFootprint(
                                symbol = this@FootprintController.symbol,
                                timeframe = sourceTf,
                                from = displayStart - sourceMs, // margin
                                to = displayEnd,
                                limit = aggCount + 2
                            ).reversed()

                            if (raw.isNotEmpty()) {
                                // Find the exact chunk that aligns with the display window
                                for (i in 0..raw.size - aggCount) {
                                    val chunk = raw.subList(i, i + aggCount)
                                    val chunkStart = chunk.firstOrNull()?.startTime ?: continue
                                    if (chunkStart >= displayStart - sourceMs && chunkStart <= displayStart + sourceMs) {
                                        val agg = FootprintAggregator.aggregateFootprintCandles(chunk, aggCount, sourceMs).firstOrNull() ?: continue
                                        _state.update { s ->
                                            val list = s.candles.toMutableList()
                                            val existIdx = list.indexOfFirst { it.startTime == agg.startTime }
                                            if (existIdx >= 0) list[existIdx] = agg else list.add(agg)
                                            s.copy(candles = list.sortedBy { it.startTime })
                                        }
                                        break // one display candle per poll cycle
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                // normal stop
            } catch (e: Exception) {
                println("Live footprint error: ${e.message}")
                _state.update { it.copy(error = "Live footprint error: ${e.message}", loading = false) }
            }
        }

        // Periodic server polling for latest completed candle (1m display only, not aggregated)
        if (sourceTf == "1m" && aggCount == 1) {
            pollingJob = scope.launch {
                while (isActive) {
                    delay(60_000)
                    val now = currentTimeMillis()
                    val completedStart = (now / 60_000 * 60_000) - 60_000
                    val completedEnd = completedStart + 60_000
                    val serverCandle = fetchCompletedCandle(completedStart, completedEnd)
                    if (serverCandle != null && serverCandle.levels.isNotEmpty()) {
                        _state.update { s ->
                            val list = s.candles.toMutableList()
                            if (list.isNotEmpty() && list.last().startTime == completedStart) {
                                list[list.lastIndex] = serverCandle
                            } else {
                                list.add(serverCandle)
                            }
                            s.copy(candles = list)
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        footprintJob?.cancel()
        footprintJob = null
        pollingJob?.cancel()
        pollingJob = null
        _state.update { it.copy(liveCandle = null) }
    }

    fun loadMore() {
        if (isLoadingMore || !_state.value.hasMoreHistory) return
        isLoadingMore = true

        scope.launch {
            try {
                val oldestTime = _state.value.candles.firstOrNull()?.startTime ?: run {
                    isLoadingMore = false; return@launch
                }
                val (sourceTf, aggCount) = FootprintAggregator.resolveFootprintSourceTimeframe(displayTimeframe)
                val sourceMs = FootprintAggregator.sourceTimeframeMs(sourceTf)

                val historical = (footprintApiClient?.getFootprint(
                    symbol = symbol,
                    timeframe = sourceTf,
                    to = oldestTime - 1,
                    limit = 20 * aggCount
                ) ?: emptyList()).reversed() // server DESC → ASC

                if (historical.isEmpty()) {
                    _state.update { it.copy(hasMoreHistory = false) }
                    isLoadingMore = false
                    return@launch
                }

                val aggregated = if (aggCount > 1) FootprintAggregator.aggregateFootprintCandles(historical, aggCount, sourceMs) else historical
                _state.update { s ->
                    s.copy(
                        candles = (aggregated + s.candles).distinctBy { it.startTime },
                        historyLoadCount = aggregated.size,
                    )
                }
            } catch (e: Exception) {
                println("Failed to load more footprint history: ${e.message}")
            } finally {
                isLoadingMore = false
            }
        }
    }

    override fun dispose() {
        footprintJob?.cancel()
        pollingJob?.cancel()
        scope.cancel()
    }

    // Load historical footprint from server, aggregates if needed
    private suspend fun fetchHistoricalFootprint(): List<FootprintCandle> {
        if (footprintApiClient == null) return emptyList()
        val (sourceTf, aggCount) = FootprintAggregator.resolveFootprintSourceTimeframe(displayTimeframe)
        val sourceMs = FootprintAggregator.sourceTimeframeMs(sourceTf)
        return try {
            val raw = footprintApiClient.getFootprint(
                symbol = symbol,
                timeframe = sourceTf,
                limit = when (sourceTf) {
                    "1m" -> 20 * aggCount
                    "15m" -> 20 * aggCount
                    else -> 20
                }
            ).reversed() // server returns DESC, we store ASC
            if (aggCount > 1) FootprintAggregator.aggregateFootprintCandles(raw, aggCount, sourceMs) else raw
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Fetch one completed candle from server
    private suspend fun fetchCompletedCandle(startTime: Long, endTime: Long): FootprintCandle? {
        if (footprintApiClient == null) return null
        val (sourceTf, aggCount) = FootprintAggregator.resolveFootprintSourceTimeframe(displayTimeframe)
        val sourceMs = FootprintAggregator.sourceTimeframeMs(sourceTf)

        // Always request the source candles covering the display-tf window:
        // For 1m → 1 source candle; for 5m → 5 source 1m candles; for 1h → 4 source 15m candles
        val from = startTime - sourceMs * (aggCount - 1)
        val to = endTime
        val limit = aggCount + 2 // margin for alignment

        return try {
            val raw = footprintApiClient.getFootprint(
                symbol = symbol,
                timeframe = sourceTf,
                from = from,
                to = to,
                limit = limit
            ).reversed()

            if (raw.isEmpty()) return null

            // For display 1m (aggCount=1): just return the single source candle
            if (aggCount == 1) return raw.firstOrNull { it.startTime == startTime || it.endTime == startTime + sourceMs }

            // For aggregated timeframes: find the chunk that covers the exact display window
            for (i in 0..raw.size - aggCount) {
                val chunk = raw.subList(i, i + aggCount)
                val chunkStart = chunk.firstOrNull()?.startTime ?: continue
                if (chunkStart >= startTime && chunkStart < startTime + sourceMs) {
                    return FootprintAggregator.aggregateFootprintCandles(chunk, aggCount, sourceMs).firstOrNull()
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun loadData() {
        footprintJob?.cancel()
        footprintJob = null
        _state.update { it.copy(liveCandle = null) }

        scope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val data = fetchHistoricalFootprint()
            _state.update {
                it.copy(
                    candles = data,
                    loading = false,
                    error = if (data.isEmpty()) "No footprint data in DB" else null,
                )
            }
        }
    }
}
