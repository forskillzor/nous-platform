/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.data.timeseries

import com.aandios.nous.api.market.adapters.ChartAdapter
import com.aandios.nous.api.market.model.Candle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals

class CandleSeriesSourceTest {

    private val source = CandleSeriesSource(FakeChartAdapter(), "BTCUSDT", "1m")

    private fun candle(
        timestamp: Long,
        open: Float = 100f,
        high: Float = 101f,
        low: Float = 99f,
        close: Float = 100f,
        volume: Float = 10f,
    ) = Candle(open = open, high = high, low = low, close = close, timestamp = timestamp, volume = volume)

    @Test
    fun `live update replaces cumulative volume instead of adding it`() {
        val existing = candle(timestamp = 0L, volume = 10f)
        val update = candle(timestamp = 0L, close = 102f, volume = 15f)

        val merged = source.mergeItem(listOf(existing), update)

        assertEquals(1, merged.size)
        assertEquals(15f, merged.single().volume)
        assertEquals(102f, merged.single().close)
    }

    @Test
    fun `live update merges high and low within the same candle`() {
        val existing = candle(timestamp = 0L, high = 101f, low = 99f)
        val update = candle(timestamp = 5_000L, high = 103f, low = 98f)

        val merged = source.mergeItem(listOf(existing), update).single()

        assertEquals(103f, merged.high)
        assertEquals(98f, merged.low)
    }

    @Test
    fun `new candle is appended`() {
        val existing = candle(timestamp = 0L)
        val update = candle(timestamp = 60_000L)

        val merged = source.mergeItem(listOf(existing), update)

        assertEquals(listOf(existing, update), merged)
    }

    private class FakeChartAdapter : ChartAdapter {
        override suspend fun getCandles(symbol: String, interval: String, limit: Int): List<Candle> = emptyList()

        override suspend fun getCandlesBefore(
            symbol: String,
            interval: String,
            endTime: Long,
            limit: Int,
        ): List<Candle> = emptyList()

        override fun subscribeToCandles(symbol: String, interval: String): Flow<Candle> = emptyFlow()
    }
}
