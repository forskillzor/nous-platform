/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.indicator

import com.aandios.nous.api.market.adapters.LiquidationAdapter
import com.aandios.nous.api.market.model.liquidation.LiquidationOrder
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.core.currentTimeMillis
import com.aandios.nous.core.domain.timeseries.Timeframes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ликвидации для графика:
 *  * история — агрегаты market-data-server за видимый диапазон и текущий timeframe;
 *  * live — поток провайдера для текущего бара;
 *  * раз в минуту закрытые бакеты заменяются точными агрегатами сервера.
 */
class LiquidationFeed(
    private val scope: CoroutineScope,
    private val symbol: String,
    private val exchange: String?,
    private val timeframe: String,
    private val liveAdapter: LiquidationAdapter?,
    private val historySource: LiquidationHistorySource?,
    private val clock: () -> Long = ::currentTimeMillis,
) {

    private data class ClosedBucket(
        val startTime: Long,
        val endTime: Long,
        val orders: List<LiquidationOrder>,
    )

    private val mutex = Mutex()
    private val closed = LinkedHashMap<Long, ClosedBucket>()
    private val live = LinkedHashMap<LiquidationKey, LiquidationOrder>()

    private val _orders = MutableStateFlow<List<LiquidationOrder>>(emptyList())
    val orders: StateFlow<List<LiquidationOrder>> = _orders.asStateFlow()

    private var loadedFrom: Long? = null
    private var loadedTo: Long? = null
    private var started = false
    private var closedFlag = false
    private var visibleFrom: Long? = null
    private var visibleTo: Long? = null
    private var liveJob: Job? = null
    private var reconcileJob: Job? = null
    private var rangeJob: Job? = null
    private var lastReconcileError: String? = null

    fun start() {
        if (started || closedFlag) return
        started = true
        liveJob = scope.launch { collectLive() }
        reconcileJob = scope.launch { reconcileLoop() }
        visibleFrom?.let { from ->
            visibleTo?.let { to ->
                rangeJob = scope.launch { loadVisibleRange(from, to) }
            }
        }
    }

    fun setVisibleRange(from: Long, to: Long) {
        if (closedFlag || from >= to) return
        visibleFrom = from
        visibleTo = to
        if (!started) return
        rangeJob?.cancel()
        rangeJob = scope.launch { loadVisibleRange(from, to) }
    }

    fun close() {
        if (closedFlag) return
        closedFlag = true
        liveJob?.cancel()
        reconcileJob?.cancel()
        rangeJob?.cancel()
        liveJob = null
        reconcileJob = null
        rangeJob = null
    }

    private suspend fun collectLive() {
        val adapter = liveAdapter ?: return
        adapter.subscribeToLiquidations(symbol)
            .catch { e ->
                if (e !is CancellationException) {
                    println("Liquidation live stream unavailable for $symbol: ${e.message}")
                }
            }
            .collect { order -> onLive(order) }
    }

    private suspend fun onLive(order: LiquidationOrder) {
        mutex.withLock {
            if (closedFlag) return@withLock
            val key = liquidationKey(order)
            if (live.containsKey(key)) return@withLock
            live[key] = order
            publishLocked()
        }
    }

    private suspend fun loadVisibleRange(from: Long, to: Long) {
        val source = historySource ?: return
        val exchangeName = exchange?.takeIf { it.isNotBlank() } ?: return
        val duration = Timeframes.millis(timeframe)

        mutex.withLock {
            while (!closedFlag) {
                val missing = missingRange(from - duration, to + duration)
                if (missing == null) return@withLock
                try {
                    val buckets = source.getAggregates(
                        exchange = exchangeName,
                        symbol = symbol,
                        timeframe = timeframe,
                        from = missing.first,
                        to = missing.second,
                        limit = HISTORY_LIMIT,
                    )
                    applyBucketsLocked(buckets)
                    markLoaded(missing.first, missing.second)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    println("Liquidation history unavailable for $symbol: ${e.message}")
                    return@withLock
                }
            }
        }
    }

    private fun missingRange(from: Long, to: Long): Pair<Long, Long>? {
        val lf = loadedFrom
        val lt = loadedTo
        if (lf == null || lt == null) return from to to
        if (from >= lf && to <= lt) return null

        // Сначала догружаем левую часть, затем правую.
        if (from < lf) {
            val end = minOf(to, lf)
            if (from < end) return from to end
        }
        if (to > lt) {
            val start = maxOf(from, lt)
            if (start < to) return start to to
        }
        return null
    }

    private fun markLoaded(from: Long, to: Long) {
        loadedFrom = minOf(loadedFrom ?: from, from)
        loadedTo = maxOf(loadedTo ?: to, to)
    }

    private suspend fun reconcileLoop() {
        while (!closedFlag) {
            val now = clock()
            val nextMinute = (now / MINUTE_MS + 1) * MINUTE_MS + RECONCILE_OFFSET_MS
            delay((nextMinute - now).coerceAtLeast(1L))
            reconcile()
        }
    }

    private suspend fun reconcile() {
        val source = historySource ?: return
        val exchangeName = exchange?.takeIf { it.isNotBlank() } ?: return
        val now = clock()
        val from = now - 2 * Timeframes.millis(timeframe)
        val to = now
        try {
            val buckets = source.getAggregates(
                exchange = exchangeName,
                symbol = symbol,
                timeframe = timeframe,
                from = from,
                to = to,
                limit = RECONCILE_LIMIT,
            )
            mutex.withLock {
                if (closedFlag) return@withLock
                applyBucketsLocked(buckets)
                markLoaded(from, to)
            }
            lastReconcileError = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: "unknown error"
            if (message != lastReconcileError) {
                lastReconcileError = message
                println("Liquidation reconciliation unavailable for $symbol: $message")
            }
        }
    }

    private fun applyBucketsLocked(buckets: List<LiquidationBucket>) {
        for (bucket in buckets) {
            if (bucket.startTime >= bucket.endTime) continue
            closed[bucket.startTime] = ClosedBucket(
                startTime = bucket.startTime,
                endTime = bucket.endTime,
                orders = bucket.orders,
            )
            val iterator = live.entries.iterator()
            while (iterator.hasNext()) {
                val order = iterator.next().value
                if (order.timestamp >= bucket.startTime && order.timestamp < bucket.endTime) {
                    iterator.remove()
                }
            }
        }
        publishLocked()
    }

    private fun publishLocked() {
        val result = buildList {
            closed.values
                .sortedBy { it.startTime }
                .forEach { addAll(it.orders) }
            addAll(live.values.sortedBy { it.timestamp })
        }
        _orders.value = result
    }

    private companion object {
        const val MINUTE_MS = 60_000L
        const val RECONCILE_OFFSET_MS = 500L
        const val HISTORY_LIMIT = 10_000
        const val RECONCILE_LIMIT = 1_000
    }
}

private data class LiquidationKey(
    val timestamp: Long,
    val price: Double,
    val quantity: Double,
    val side: TradeSide,
)

private fun liquidationKey(order: LiquidationOrder) = LiquidationKey(
    timestamp = order.timestamp,
    price = order.price,
    quantity = order.quantity,
    side = order.side,
)
