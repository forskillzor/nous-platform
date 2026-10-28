/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.indicator

import com.aandios.nous.api.market.adapters.LiquidationAdapter
import com.aandios.nous.api.market.model.liquidation.LiquidationOrder
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.core.currentTimeMillis
import com.aandios.nous.core.domain.timeseries.TimeSeriesSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge

/**
 * TimeSeriesSource ликвидаций:
 *  * история — market-data-server (или fallback провайдера);
 *  * live — WebSocket провайдера;
 *  * каждую закрытую минуту — сверка с сервером, чтобы добрать пропущенные
 *    live-события и не зависеть от обрывов WS.
 */
class LiquidationSeriesSource(
    private val liveAdapter: LiquidationAdapter?,
    private val symbol: String,
    private val historySource: LiquidationHistorySource? = null,
    private val exchange: String? = null,
    private val historyWindowMs: Long = 60 * 60 * 1000L,
    private val maxOrders: Int = 1000,
    private val clock: () -> Long = ::currentTimeMillis,
) : TimeSeriesSource<LiquidationOrder> {

    companion object {
        private const val RECONCILE_OVERLAP_MS = 30_000L
        private const val RECONCILE_OFFSET_MS = 500L
    }

    private var lastKnownTimestamp = 0L
    private var lastReconcileError: String? = null

    override suspend fun loadInitial(): List<LiquidationOrder> {
        val endTime = clock()
        val startTime = endTime - historyWindowMs

        if (historySource != null && !exchange.isNullOrBlank()) {
            try {
                return normalize(
                    historySource.getLiquidations(
                        exchange = exchange,
                        symbol = symbol,
                        from = startTime,
                        to = endTime,
                        limit = maxOrders,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Market-data liquidations unavailable for $symbol: ${e.message}")
            }
        }

        return try {
            normalize(
                liveAdapter?.getHistoricalLiquidations(
                    symbol = symbol,
                    startTime = startTime,
                    endTime = endTime,
                    limit = maxOrders,
                ).orEmpty(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Provider liquidation history unavailable for $symbol: ${e.message}")
            emptyList()
        }
    }

    /** Пагинация назад для ликвидаций не поддерживается. */
    override suspend fun loadBefore(beforeTimestamp: Long, limit: Int): List<LiquidationOrder> = emptyList()

    override fun liveUpdates(): Flow<LiquidationOrder> {
        val live = liveAdapter?.subscribeToLiquidations(symbol)
            ?.catch { e -> println("Liquidation live stream unavailable for $symbol: ${e.message}") }
            ?: emptyFlow()
        return merge(live, reconciliationFlow())
    }

    override fun mergeItem(
        items: List<LiquidationOrder>,
        update: LiquidationOrder,
    ): List<LiquidationOrder> {
        val key = liquidationKey(update)
        if (items.any { liquidationKey(it) == key }) return items
        lastKnownTimestamp = maxOf(lastKnownTimestamp, update.timestamp)
        val updated = items + update
        return if (updated.size > maxOrders) updated.takeLast(maxOrders) else updated
    }

    override fun timestampOf(item: LiquidationOrder): Long = item.timestamp

    private fun normalize(orders: List<LiquidationOrder>): List<LiquidationOrder> {
        val normalized = orders
            .distinctBy(::liquidationKey)
            .sortedBy { it.timestamp }
        if (normalized.isNotEmpty()) {
            lastKnownTimestamp = maxOf(lastKnownTimestamp, normalized.last().timestamp)
        }
        return normalized
    }

    /**
     * Каждую минуту добирает с сервера всё, что могло потеряться в live,
     * начиная от последнего известного события (с перекрытием).
     */
    private fun reconciliationFlow(): Flow<LiquidationOrder> = flow {
        val source = historySource ?: return@flow
        val exchangeName = exchange?.takeIf { it.isNotBlank() } ?: return@flow

        while (true) {
            val now = clock()
            val nextMinute = (now / 60_000 + 1) * 60_000
            delay((nextMinute - now).coerceAtLeast(1L) + RECONCILE_OFFSET_MS)

            val to = clock()
            val from = maxOf(
                lastKnownTimestamp - RECONCILE_OVERLAP_MS,
                to - historyWindowMs,
            )
            try {
                source.getLiquidations(
                    exchange = exchangeName,
                    symbol = symbol,
                    from = from,
                    to = to,
                    limit = maxOrders,
                )
                    .distinctBy(::liquidationKey)
                    .sortedBy { it.timestamp }
                    .forEach { emit(it) }
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
