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
import com.aandios.nous.core.domain.cache.FootprintCacheStore
import kotlinx.coroutines.CoroutineDispatcher
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
    private val footprintCache: FootprintCacheStore? = null,
    /** Пространство имён биржи для кэша и запросов (displayName провайдера). */
    private val exchange: String = "Binance",
    private val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = ::currentTimeMillis,
) : Disposable {

    private val scope = CoroutineScope(processingDispatcher + SupervisorJob())
    private var footprintJob: Job? = null
    private var pollingJob: Job? = null
    private var formingJob: Job? = null
    private var isLoadingMore = false
    private var lastFormingError: String? = null

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
        formingJob?.cancel()
        _state.update {
            it.copy(
                loading = true,
                error = null,
                candles = emptyList(),
                liveCandle = null,
                hasMoreHistory = true,
            )
        }
        isLoadingMore = false

        // Быстрый показ из кэша, пока грузится свежий footprint.
        // Проверка пустоты — внутри update: кэш не может перетереть свежие данные
        scope.launch {
            val cache = footprintCache ?: return@launch
            try {
                val now = clock()
                val cached = cache.getFootprintCandles(exchange, symbol, CACHE_LIMIT)
                    .filter { it.endTime <= now }
                _state.update { s ->
                    if (cached.isNotEmpty() && s.candles.isEmpty()) s.copy(candles = cached) else s
                }
            } catch (_: Exception) {
                // кэш не критичен
            }
        }

        val (sourceTf, aggCount) = FootprintAggregator.resolveFootprintSourceTimeframe(displayTimeframe)
        val isLiveTrades = (displayTimeframe == "1m" || displayTimeframe == "5m") && tradesAdapter != null
        val sourceMs = FootprintAggregator.sourceTimeframeMs(sourceTf)

        footprintJob = scope.launch {
            try {
                // 1. Load history from server
                val history = fetchHistoricalFootprint()
                _state.update { it.copy(candles = history, loading = false) }
                saveToCache(history)

                if (isLiveTrades) {
                    // ---- Live trade accumulation (1m or 5m) ----
                    val displayMs = sourceMs * aggCount

                    var liveCandle: MutableFootprintCandle? = null
                    var lastCandleStart = 0L
                    var tickCount = 0L
                    var lastUiUpdateMs = 0L

                    tradesAdapter.subscribeToTrades(this@FootprintController.symbol).collect { trade ->
                        val candleStart = trade.timestamp / displayMs * displayMs

                        if (lastCandleStart > 0L && candleStart != lastCandleStart) {
                            val completed = liveCandle?.toFootprintCandle(tickCount)
                            if (completed != null) {
                                _state.update { it.copy(candles = it.candles + completed) }
                                saveToCache(listOf(completed))

                                // 1m and 5m are persisted natively; replace the
                                // locally accumulated candle with the server one.
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
                            lastUiUpdateMs = 0L
                        }

                        val candle = liveCandle ?: MutableFootprintCandle(
                            symbol = this@FootprintController.symbol,
                            startTime = candleStart,
                            endTime = candleStart + displayMs,
                        ).also { liveCandle = it }
                        candle.addTrade(trade.price.toFloat(), trade.quantity.toFloat(), !trade.isBuyerMaker)
                        tickCount++

                        lastCandleStart = candleStart

                        val now = clock()
                        if (now - lastUiUpdateMs >= LIVE_SNAPSHOT_INTERVAL_MS) {
                            lastUiUpdateMs = now
                            val liveSnapshot = candle.toFootprintCandle(tickCount)
                            _state.update {
                                it.copy(
                                    liveCandle = if (liveSnapshot.levels.isNotEmpty()) liveSnapshot else null,
                                    currentPrice = candle.lastPrice.takeIf { p -> p > 0f },
                                )
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

        // Periodically refresh the latest *closed* display candle from the
        // server. Covers native 1m/5m replacement and corrects the partial
        // candle persisted at a 15m/30m/1h/4h/1d/1w boundary.
        if (footprintApiClient != null) {
            pollingJob = scope.launch {
                while (isActive) {
                    delay(60_000)
                    val now = clock()
                    val displayMs = sourceMs * aggCount
                    val completedStart = (now / displayMs * displayMs) - displayMs
                    val completedEnd = completedStart + displayMs
                    val serverCandle = fetchCompletedCandle(completedStart, completedEnd)
                    if (serverCandle != null && serverCandle.levels.isNotEmpty()) {
                        _state.update { s ->
                            val list = s.candles.toMutableList()
                            val index = list.indexOfFirst { it.startTime == completedStart }
                            if (index >= 0) list[index] = serverCandle else list.add(serverCandle)
                            s.copy(candles = list.sortedBy { it.startTime })
                        }
                        saveToCache(listOf(serverCandle))
                    }
                }
            }
        }

        // Формирующаяся (незакрытая) свеча для старших таймфреймов: живёт в liveCandle
        if (!isLiveTrades && footprintApiClient != null &&
            FootprintAggregator.formingSourceTimeframes(displayTimeframe).isNotEmpty()
        ) {
            formingJob = scope.launch {
                while (isActive) {
                    updateFormingCandle(displayTimeframe)
                    delay(FORMING_POLL_INTERVAL_MS)
                }
            }
        }
    }

    /**
     * Обновляет формирующуюся свечу текущего display-бакета для 15m+.
     * Сервер отдаёт только закрытые свечи, поэтому текущий бакет собирается
     * mixed-запросом: сначала крупный нижний TF (15m/1h/4h/1d), затем 5m и 1m
     * добирают хвост до текущего момента. Результат кладётся в liveCandle; при
     * закрытии бакета полная свеча уходит в историю, а нативный агрегат
     * заменит её позже по polling.
     */
    private suspend fun updateFormingCandle(timeframe: String) {
        val api = footprintApiClient ?: return
        val (sourceTf, aggCount) = FootprintAggregator.resolveFootprintSourceTimeframe(timeframe)
        val sourceMs = FootprintAggregator.sourceTimeframeMs(sourceTf)
        val bucketMs = sourceMs * aggCount
        val now = clock()
        val bucketStart = FootprintAggregator.bucketStart(now, sourceMs, aggCount)

        val previousLive = _state.value.liveCandle
        if (previousLive != null && previousLive.startTime < bucketStart) {
            _state.update { s ->
                val list = s.candles.toMutableList()
                if (list.none { it.startTime == previousLive.startTime }) list.add(previousLive)
                s.copy(candles = list.sortedBy { it.startTime }, liveCandle = null)
            }
            saveToCache(listOf(previousLive))
        }

        val historyAlreadyHasBucket = _state.value.candles.lastOrNull()
            ?.let { FootprintAggregator.isCompleteBucket(it, bucketStart, now) } == true
        if (historyAlreadyHasBucket) {
            lastFormingError = null
            _state.update { s ->
                s.copy(
                    liveCandle = null,
                    error = s.error?.takeUnless { it.startsWith(FORMING_ERROR_PREFIX) },
                )
            }
            return
        }

        try {
            val sources = FootprintAggregator.formingSourceTimeframes(timeframe)
            if (sources.isEmpty()) return

            val queries = sources.map { source ->
                val step = FootprintAggregator.sourceTimeframeMs(source)
                val limit = ((bucketMs + step - 1) / step)
                    .toInt()
                    .coerceIn(1, FORMING_SOURCE_LIMIT_MAX)
                BatchFootprintQuery(
                    timeframe = source,
                    start = bucketStart,
                    end = now,
                    limit = limit,
                    order = "asc",
                )
            }
            val response = api.getBatchFootprint(
                exchange = exchange,
                symbol = symbol,
                queries = queries,
            )
            val byTimeframe = response.results.associate { it.timeframe to it.candles }
            val agg = FootprintAggregator.assembleFormingCandle(
                displayTimeframe = timeframe,
                bucketStart = bucketStart,
                bucketEnd = bucketStart + bucketMs,
                sourceCandles = byTimeframe,
            ) ?: throw IllegalStateException(
                if (byTimeframe.values.all { it.isEmpty() }) {
                    "batch returned no source candles"
                } else {
                    "source candles did not assemble into $timeframe"
                },
            )

            lastFormingError = null
            _state.update {
                it.copy(
                    // Частичный бакет из истории/кэша не должен дублировать
                    // формирующуюся свечу (важно для standalone FootprintChart).
                    candles = it.candles.filterNot { c -> c.startTime == agg.startTime },
                    liveCandle = agg,
                    currentPrice = agg.close.takeIf { p -> p > 0f },
                    error = it.error?.takeUnless { message ->
                        message.startsWith(FORMING_ERROR_PREFIX)
                    },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message?.takeIf { it.isNotBlank() } ?: "unknown error"
            if (message != lastFormingError) {
                lastFormingError = message
                println("❌ Footprint forming ($symbol, $timeframe): $message")
            }
            _state.update { s ->
                if (s.error == null || s.error.startsWith(FORMING_ERROR_PREFIX)) {
                    s.copy(error = "$FORMING_ERROR_PREFIX: $message")
                } else {
                    s
                }
            }
        }
    }

    fun stop() {
        footprintJob?.cancel()
        footprintJob = null
        pollingJob?.cancel()
        pollingJob = null
        formingJob?.cancel()
        formingJob = null
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
                    exchange = exchange,
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

                val aggregated = if (aggCount > 1) {
                    FootprintAggregator.aggregateFootprintCandles(
                        historical,
                        aggCount,
                        sourceMs,
                        displayTimeframe,
                    )
                } else {
                    historical
                }
                _state.update { s ->
                    s.copy(candles = (aggregated + s.candles).distinctBy { it.startTime })
                }
                saveToCache(aggregated)
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
        formingJob?.cancel()
        scope.cancel()
    }

    // Load historical footprint from server, aggregates if needed
    private suspend fun fetchHistoricalFootprint(): List<FootprintCandle> {
        if (footprintApiClient == null) return emptyList()
        val (sourceTf, aggCount) = FootprintAggregator.resolveFootprintSourceTimeframe(displayTimeframe)
        val sourceMs = FootprintAggregator.sourceTimeframeMs(sourceTf)
        return try {
            val raw = footprintApiClient.getFootprint(
                exchange = exchange,
                symbol = symbol,
                timeframe = sourceTf,
                limit = 20 * aggCount,
            ).reversed() // server returns DESC, we store ASC
            val now = clock()
            val result = if (aggCount > 1) {
                FootprintAggregator.aggregateFootprintCandles(
                    raw,
                    aggCount,
                    sourceMs,
                    displayTimeframe,
                )
            } else {
                raw
            }
            FootprintAggregator.completedDisplayCandles(result, now)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Fetch one completed candle from server
    private suspend fun fetchCompletedCandle(startTime: Long, endTime: Long): FootprintCandle? {
        if (footprintApiClient == null) return null
        val (sourceTf, aggCount) = FootprintAggregator.resolveFootprintSourceTimeframe(displayTimeframe)
        val sourceMs = FootprintAggregator.sourceTimeframeMs(sourceTf)

        // Always request the source candles covering the display-tf window.
        // One source bar before the window is enough to locate the aligned chunk.
        val from = startTime - sourceMs
        val to = endTime
        val limit = aggCount + 2 // margin for alignment

        return try {
            val raw = footprintApiClient.getFootprint(
                exchange = exchange,
                symbol = symbol,
                timeframe = sourceTf,
                from = from,
                to = to,
                limit = limit
            ).reversed()

            if (raw.isEmpty()) return null

            // For display 1m (aggCount=1): just return the single source candle.
            // Терпимое сравнение: достаточно, чтобы свеча покрывала startTime
            if (aggCount == 1) return raw.firstOrNull { it.startTime >= startTime && it.startTime < startTime + sourceMs }

            // For aggregated timeframes: find the chunk that covers the exact display window
            for (i in 0..raw.size - aggCount) {
                val chunk = raw.subList(i, i + aggCount)
                val chunkStart = chunk.firstOrNull()?.startTime ?: continue
                if (chunkStart >= startTime && chunkStart < startTime + sourceMs) {
                    return FootprintAggregator.aggregateFootprintCandles(
                        chunk,
                        aggCount,
                        sourceMs,
                        displayTimeframe,
                    ).firstOrNull()
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
            _state.update { it.copy(loading = true) }
            val data = fetchHistoricalFootprint()
            _state.update { s ->
                s.copy(
                    candles = data,
                    loading = false,
                    // не затираем более осмысленную ошибку («Neither trades adapter…»)
                    error = if (data.isEmpty()) s.error ?: "No footprint data in DB" else s.error,
                )
            }
            saveToCache(data)
        }
    }

    private fun saveToCache(candles: List<FootprintCandle>) {
        val cache = footprintCache ?: return
        if (candles.isEmpty()) return
        val currentSymbol = symbol
        scope.launch {
            try {
                cache.saveFootprintCandles(exchange, currentSymbol, candles)
            } catch (_: Exception) {
                // кэш не критичен
            }
        }
    }

    companion object {
        private const val CACHE_LIMIT = 200
        private const val FORMING_POLL_INTERVAL_MS = 30_000L
        private const val LIVE_SNAPSHOT_INTERVAL_MS = 100L
        private const val FORMING_SOURCE_LIMIT_MAX = 300
        private const val FORMING_ERROR_PREFIX = "Footprint forming"
    }
}
