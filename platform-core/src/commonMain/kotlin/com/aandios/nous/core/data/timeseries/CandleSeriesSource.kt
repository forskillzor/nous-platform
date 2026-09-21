/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.data.timeseries

import com.aandios.nous.api.market.adapters.ChartAdapter
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.core.domain.timeseries.TimeSeriesSource
import com.aandios.nous.core.domain.timeseries.Timeframes
import kotlinx.coroutines.flow.Flow

/**
 * TimeSeriesSource для свечей поверх ChartAdapter любого провайдера (Binance/Bybit/MEXC).
 * Содержит логику слияния realtime-свечи с последней исторической.
 */
class CandleSeriesSource(
    private val chartAdapter: ChartAdapter,
    private val symbol: String,
    private val timeframe: String,
) : TimeSeriesSource<Candle> {

    private val interval = Timeframes.toExchangeInterval(timeframe)
    private val timeframeMs = Timeframes.millis(timeframe)

    override suspend fun loadInitial(): List<Candle> =
        chartAdapter.getCandles(symbol = symbol, interval = interval, limit = 200)

    override suspend fun loadBefore(beforeTimestamp: Long, limit: Int): List<Candle> =
        chartAdapter.getCandlesBefore(symbol = symbol, interval = interval, endTime = beforeTimestamp, limit = limit)

    override fun liveUpdates(): Flow<Candle> = chartAdapter.subscribeToCandles(symbol, interval)

    override fun mergeItem(items: List<Candle>, update: Candle): List<Candle> {
        if (items.isEmpty()) return listOf(update)

        val lastCandle = items.last()
        val isSameCandle = update.timestamp / timeframeMs == lastCandle.timestamp / timeframeMs

        return if (isSameCandle) {
            items.dropLast(1) + update.copy(
                high = maxOf(lastCandle.high, update.high),
                low = minOf(lastCandle.low, update.low),
                close = update.close,
                volume = lastCandle.volume + update.volume,
            )
        } else {
            items + update
        }
    }

    override fun timestampOf(item: Candle): Long = item.timestamp
}
