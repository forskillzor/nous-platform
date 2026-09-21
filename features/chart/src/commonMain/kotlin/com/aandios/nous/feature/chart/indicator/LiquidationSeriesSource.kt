/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.indicator

import com.aandios.nous.api.market.adapters.LiquidationAdapter
import com.aandios.nous.api.market.model.liquidation.LiquidationOrder
import com.aandios.nous.core.currentTimeMillis
import com.aandios.nous.core.domain.timeseries.TimeSeriesSource
import kotlinx.coroutines.flow.Flow

/**
 * TimeSeriesSource для ликвидаций поверх LiquidationAdapter любого провайдера
 * (Binance/MEXC): история за окно + realtime WebSocket.
 */
class LiquidationSeriesSource(
    private val adapter: LiquidationAdapter,
    private val symbol: String,
    private val historyWindowMs: Long = 60 * 60 * 1000L,
    private val maxOrders: Int = 1000,
) : TimeSeriesSource<LiquidationOrder> {

    override suspend fun loadInitial(): List<LiquidationOrder> {
        val endTime = currentTimeMillis()
        return adapter.getHistoricalLiquidations(
            symbol = symbol,
            startTime = endTime - historyWindowMs,
            endTime = endTime,
            limit = 100,
        )
    }

    /** Пагинация назад адаптерами ликвидаций не поддерживается. */
    override suspend fun loadBefore(beforeTimestamp: Long, limit: Int): List<LiquidationOrder> = emptyList()

    override fun liveUpdates(): Flow<LiquidationOrder> = adapter.subscribeToLiquidations(symbol)

    override fun mergeItem(items: List<LiquidationOrder>, update: LiquidationOrder): List<LiquidationOrder> {
        val updated = items + update
        return if (updated.size > maxOrders) updated.takeLast(maxOrders) else updated
    }

    override fun timestampOf(item: LiquidationOrder): Long = item.timestamp
}
