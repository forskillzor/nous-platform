/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.footprint

import com.aandios.nous.api.market.adapters.TradesAdapter
import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.api.market.model.MutableFootprintCandle
import com.aandios.nous.api.market.model.trades.Trade
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private var liveTradeJob: Job? = null
    private var isLoadingMore = false
    private var lastFormingError: String? = null

    // Live-хвост: незакрытая 1m из сделок, независимая от display-ТФ.
    private val liveMutex = Mutex()
    private var liveMinute: MutableFootprintCandle? = null
    private var liveMinuteTicks = 0L
    private var lastLivePublishMs = 0L

    // Точная закрытая часть текущего display-бакета с сервера.
    private var closedPrefix: FootprintCandle? = null
    private var closedPrefixBucketStart = -1L
    private val localFallback1m = mutableMapOf<Long, FootprintCandle>()

    private var lastRefreshMinute = -1L
    private var lastServerMinute = -1L
    private var refreshRetryUntil = 0L

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
        liveTradeJob?.cancel()
        footprintJob = null
        pollingJob = null
        formingJob = null
        liveTradeJob = null
        liveMinute = null
        liveMinuteTicks = 0L
        lastLivePublishMs = 0L
        closedPrefix = null
        closedPrefixBucketStart = -1L
        localFallback1m.clear()
        lastRefreshMinute = -1L
        lastServerMinute = -1L
        refreshRetryUntil = 0L
        lastFormingError = null
        _state.update {
            it.copy(
                loading = true,
                error = null,
                candles = emptyList(),
                liveCandle = null,
                currentPrice = null,
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
        val sourceMs = FootprintAggregator.sourceTimeframeMs(sourceTf)

        footprintJob = scope.launch {
            try {
                val history = fetchHistoricalFootprint()
                _state.update { it.copy(candles = history, loading = false) }
                saveToCache(history)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Footprint history error: ${e.message}")
                _state.update { it.copy(error = "Footprint history error: ${e.message}", loading = false) }
            }
        }

        // Live 1m tail для всех display-ТФ (1m/5m/15m/...).
        if (tradesAdapter != null) {
            liveTradeJob = scope.launch {
                try {
                    tradesAdapter.subscribeToTrades(this@FootprintController.symbol).collect { trade ->
                        handleTrade(trade)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    println("Live footprint error: ${e.message}")
                    _state.update { it.copy(error = "Live footprint error: ${e.message}") }
                }
            }
        }

        if (footprintApiClient != null) {
            // Periodically refresh the latest *closed* display candle from the
            // server (final accuracy for the just-closed bucket).
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

            // Минутный тик: live-цена идёт из сделок, а закрытая часть —
            // с сервера; на закрытии минуты пересобираем closedPrefix.
            formingJob = scope.launch {
                var lastMinute = -1L
                while (isActive) {
                    val now = clock()
                    val minute = now / 60_000
                    if (minute != lastMinute) {
                        lastMinute = minute
                        refreshRetryUntil = now + FORMING_RETRY_WINDOW_MS
                        liveMutex.withLock {
                            val current = liveMinute
                            if (current != null && current.startTime < minute * 60_000) {
                                finalizeLiveMinuteLocked()
                                publishLiveLocked()
                            }
                        }
                    }
                    val needRetry = now < refreshRetryUntil && lastServerMinute < minute - 1
                    if (minute != lastRefreshMinute || needRetry) {
                        lastRefreshMinute = minute
                        refreshDisplay(now)
                    }
                    delay(FORMING_TICK_MS)
                }
            }
        }
    }

    private suspend fun handleTrade(trade: Trade) {
        val minuteStart = trade.timestamp / 60_000 * 60_000
        liveMutex.withLock {
            val current = liveMinute
            if (current == null || current.startTime != minuteStart) {
                if (current != null) finalizeLiveMinuteLocked()
                liveMinute = MutableFootprintCandle(
                    exchange = exchange,
                    symbol = symbol,
                    startTime = minuteStart,
                    endTime = minuteStart + 60_000,
                )
                liveMinuteTicks = 0L
            }
            liveMinute?.addTrade(
                price = trade.price.toFloat(),
                quantity = trade.quantity.toFloat(),
                isBuy = !trade.isBuyerMaker,
            )
            liveMinuteTicks++
            val now = clock()
            if (now - lastLivePublishMs >= LIVE_SNAPSHOT_INTERVAL_MS) {
                lastLivePublishMs = now
                publishLiveLocked()
            }
        }
    }

    private fun finalizeLiveMinuteLocked() {
        val current = liveMinute ?: return
        val completed = current.toFootprintCandle(liveMinuteTicks)
        liveMinute = null
        liveMinuteTicks = 0L
        if (completed.levels.isEmpty()) return

        if (displayTimeframe == "1m") {
            _state.update { s ->
                s.copy(
                    candles = (s.candles.filterNot { it.startTime == completed.startTime } + completed)
                        .sortedBy { it.startTime },
                )
            }
            saveToCache(listOf(completed))
        } else {
            localFallback1m[completed.startTime] = completed
            while (localFallback1m.size > FALLBACK_MINUTE_LIMIT) {
                val oldest = localFallback1m.keys.minOrNull() ?: break
                localFallback1m.remove(oldest)
            }
        }
    }

    private fun publishLiveLocked() {
        val now = clock()
        val (sourceTf, aggCount) = FootprintAggregator.resolveFootprintSourceTimeframe(displayTimeframe)
        val sourceMs = FootprintAggregator.sourceTimeframeMs(sourceTf)
        val bucketMs = sourceMs * aggCount
        val bucketStart = FootprintAggregator.bucketStart(now, sourceMs, aggCount)
        val live = liveMinute?.toFootprintCandle(liveMinuteTicks)
        val prefix = closedPrefix?.takeIf { closedPrefixBucketStart == bucketStart }

        val candle = when {
            displayTimeframe == "1m" -> live
            live == null -> prefix
            prefix == null -> FootprintAggregator.mergeForDisplay(
                candles = listOf(live),
                timeframe = displayTimeframe,
                startTime = bucketStart,
                endTime = minOf(bucketStart + bucketMs, now),
            )

            else -> FootprintAggregator.mergeForDisplay(
                candles = listOf(prefix, live),
                timeframe = displayTimeframe,
                startTime = bucketStart,
                endTime = minOf(bucketStart + bucketMs, now),
            )
        }
        val price = liveMinute?.lastPrice?.takeIf { it > 0f } ?: candle?.close
        _state.update { it.copy(liveCandle = candle, currentPrice = price) }
    }

    private suspend fun refreshDisplay(now: Long) {
        val api = footprintApiClient ?: return
        if (displayTimeframe == "1m") {
            val lastClosed = (now / 60_000 - 1) * 60_000
            if (lastClosed <= lastServerMinute) return
            val server = fetchCompletedCandle(lastClosed, lastClosed + 60_000)
            if (server != null && server.levels.isNotEmpty()) {
                _state.update { s ->
                    s.copy(
                        candles = (s.candles.filterNot { it.startTime == server.startTime } + server)
                            .sortedBy { it.startTime },
                        error = s.error?.takeUnless { it.startsWith(FORMING_ERROR_PREFIX) },
                    )
                }
                saveToCache(listOf(server))
                lastServerMinute = lastClosed
            }
            return
        }

        val (sourceTf, aggCount) = FootprintAggregator.resolveFootprintSourceTimeframe(displayTimeframe)
        val sourceMs = FootprintAggregator.sourceTimeframeMs(sourceTf)
        val bucketMs = sourceMs * aggCount
        val bucketStart = FootprintAggregator.bucketStart(now, sourceMs, aggCount)

        if (bucketStart != closedPrefixBucketStart) {
            val previous = _state.value.liveCandle
            if (previous != null && previous.startTime < bucketStart) {
                _state.update { s ->
                    s.copy(
                        candles = (s.candles.filterNot { it.startTime == previous.startTime } + previous)
                            .sortedBy { it.startTime },
                    )
                }
                saveToCache(listOf(previous))
            }
            liveMutex.withLock {
                closedPrefix = null
                closedPrefixBucketStart = bucketStart
            }
        }

        val historyBucket = _state.value.candles.lastOrNull()
        if (historyBucket != null && FootprintAggregator.isCompleteBucket(historyBucket, bucketStart, now)) {
            lastFormingError = null
            liveMutex.withLock {
                closedPrefix = historyBucket
                publishLiveLocked()
            }
            _state.update { s ->
                s.copy(error = s.error?.takeUnless { it.startsWith(FORMING_ERROR_PREFIX) })
            }
            return
        }

        try {
            val sources = FootprintAggregator.formingSourceTimeframes(displayTimeframe)
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
            val byTimeframe = response.results
                .associate { it.timeframe to it.candles }
                .toMutableMap()

            val server1m = byTimeframe["1m"].orEmpty()
            val fallback = liveMutex.withLock {
                localFallback1m.values
                    .filter { it.startTime >= bucketStart && it.startTime < now }
            }
            val merged1m = (server1m + fallback)
                .distinctBy { it.startTime }
                .sortedBy { it.startTime }
            if (merged1m.isNotEmpty()) byTimeframe["1m"] = merged1m

            val latestServer1m = server1m.maxOfOrNull { it.startTime } ?: -1L
            if (latestServer1m > 0L) {
                liveMutex.withLock {
                    localFallback1m.keys.removeAll { it <= latestServer1m }
                }
                lastServerMinute = latestServer1m
            }

            val agg = FootprintAggregator.assembleFormingCandle(
                displayTimeframe = displayTimeframe,
                bucketStart = bucketStart,
                bucketEnd = bucketStart + bucketMs,
                sourceCandles = byTimeframe,
                availableUntil = now,
            )
            lastFormingError = null
            liveMutex.withLock {
                if (closedPrefixBucketStart == bucketStart) {
                    closedPrefix = agg
                    publishLiveLocked()
                }
            }
            _state.update { s ->
                s.copy(error = s.error?.takeUnless { it.startsWith(FORMING_ERROR_PREFIX) })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message?.takeIf { it.isNotBlank() } ?: "unknown error"
            if (message != lastFormingError) {
                lastFormingError = message
                println("❌ Footprint forming ($symbol, $displayTimeframe): $message")
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
        liveTradeJob?.cancel()
        liveTradeJob = null
        liveMinute = null
        liveMinuteTicks = 0L
        closedPrefix = null
        closedPrefixBucketStart = -1L
        localFallback1m.clear()
        lastRefreshMinute = -1L
        lastServerMinute = -1L
        refreshRetryUntil = 0L
        lastLivePublishMs = 0L
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
        liveTradeJob?.cancel()
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
        private const val FORMING_TICK_MS = 1_000L
        private const val LIVE_SNAPSHOT_INTERVAL_MS = 100L
        private const val FORMING_SOURCE_LIMIT_MAX = 300
        private const val FALLBACK_MINUTE_LIMIT = 300
        private const val FORMING_RETRY_WINDOW_MS = 10_000L
        private const val FORMING_ERROR_PREFIX = "Footprint forming"
    }
}
