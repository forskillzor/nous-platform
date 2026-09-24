/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.footprint

import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.api.market.model.FootprintLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FootprintAggregatorTest {

    @Test
    fun `resolveFootprintSourceTimeframe for all display timeframes`() {
        assertEquals("1m" to 1, FootprintAggregator.resolveFootprintSourceTimeframe("1m"))
        assertEquals("1m" to 5, FootprintAggregator.resolveFootprintSourceTimeframe("5m"))
        assertEquals("15m" to 1, FootprintAggregator.resolveFootprintSourceTimeframe("15m"))
        assertEquals("15m" to 2, FootprintAggregator.resolveFootprintSourceTimeframe("30m"))
        assertEquals("15m" to 4, FootprintAggregator.resolveFootprintSourceTimeframe("1h"))
        assertEquals("15m" to 16, FootprintAggregator.resolveFootprintSourceTimeframe("4h"))
        assertEquals("15m" to 96, FootprintAggregator.resolveFootprintSourceTimeframe("1d"))
        assertEquals("15m" to 672, FootprintAggregator.resolveFootprintSourceTimeframe("1w"))
    }

    @Test
    fun `resolveFootprintSourceTimeframe fallback for unknown timeframe`() {
        assertEquals("1m" to 1, FootprintAggregator.resolveFootprintSourceTimeframe("unknown"))
        assertEquals("1m" to 1, FootprintAggregator.resolveFootprintSourceTimeframe(""))
    }

    @Test
    fun `aggregateFootprintCandles returns input when count is not greater than one`() {
        val candles = listOf(candle(0L, 60_000L, level("100.0", "1.0", "2.0")))
        assertEquals(candles, FootprintAggregator.aggregateFootprintCandles(candles, 1, 60_000L))
        assertEquals(candles, FootprintAggregator.aggregateFootprintCandles(candles, 0, 60_000L))
    }

    @Test
    fun `aggregateFootprintCandles returns empty for empty input`() {
        assertTrue(FootprintAggregator.aggregateFootprintCandles(emptyList(), 5, 60_000L).isEmpty())
    }

    @Test
    fun `aggregateFootprintCandles sums volumes and counts by price`() {
        val first = candle(
            0L, 60_000L,
            level("100.0", "1.5", "2.0", bidCount = 3, askCount = 4),
            level("101.0", "0.5", "0.0", bidCount = 1, askCount = 0),
        )
        val second = candle(
            60_000L, 120_000L,
            level("100.0", "2.5", "1.0", bidCount = 5, askCount = 2),
            level("102.0", "0.0", "3.0", bidCount = 0, askCount = 6),
        )

        val result = FootprintAggregator.aggregateFootprintCandles(listOf(first, second), 2, 60_000L)

        assertEquals(1, result.size)
        val agg = result.first()
        assertEquals(0L, agg.startTime)
        assertEquals(120_000L, agg.endTime)

        // Levels sorted descending by price: 102, 101, 100
        assertEquals(listOf("102.0", "101.0", "100.0"), agg.levels.map { it.price })

        val at100 = agg.levels.first { it.price == "100.0" }
        assertEquals(4.0f, at100.bidVolumeFloat)
        assertEquals(3.0f, at100.askVolumeFloat)
        assertEquals(8, at100.bidCount)
        assertEquals(6, at100.askCount)

        val at101 = agg.levels.first { it.price == "101.0" }
        assertEquals(0.5f, at101.bidVolumeFloat)
        assertEquals(0.0f, at101.askVolumeFloat)

        val at102 = agg.levels.first { it.price == "102.0" }
        assertEquals(3.0f, at102.askVolumeFloat)
        assertEquals(6, at102.askCount)
    }

    @Test
    fun `aggregateFootprintCandles chunks sequentially`() {
        val candles = (0 until 5).map { i ->
            candle(i * 60_000L, (i + 1) * 60_000L, level("100.0", "1.0", "1.0"))
        }

        val result = FootprintAggregator.aggregateFootprintCandles(candles, 2, 60_000L)

        assertEquals(3, result.size)
        assertEquals(0L, result[0].startTime)
        assertEquals(120_000L, result[0].endTime)
        assertEquals(120_000L, result[1].startTime)
        assertEquals(240_000L, result[1].endTime)
        assertEquals(240_000L, result[2].startTime)
        assertEquals(360_000L, result[2].endTime)
    }

    @Test
    fun `aggregateFootprintCandles aligns buckets to absolute time boundaries`() {
        // 05:26 and 05:27 source candles must fall into the 05:25–05:30 bucket
        val fiveMinutes = 300_000L
        val base = 1_500_000_000_000L
        val first = candle(base + 26 * 60_000L, base + 27 * 60_000L, level("100.0", "1.0", "1.0"))
        val second = candle(base + 27 * 60_000L, base + 28 * 60_000L, level("101.0", "2.0", "2.0"))
        val alignedStart = first.startTime / fiveMinutes * fiveMinutes

        val result = FootprintAggregator.aggregateFootprintCandles(listOf(first, second), 5, 60_000L)

        assertEquals(1, result.size)
        assertEquals(alignedStart, result.first().startTime)
        assertEquals(alignedStart + fiveMinutes, result.first().endTime)
        assertEquals(0L, result.first().startTime % fiveMinutes)
        assertTrue(result.first().startTime < first.startTime)
    }

    @Test
    fun `sourceTimeframeMs returns duration of source timeframe`() {
        assertEquals(60_000L, FootprintAggregator.sourceTimeframeMs("1m"))
        assertEquals(900_000L, FootprintAggregator.sourceTimeframeMs("15m"))
        assertEquals(60_000L, FootprintAggregator.sourceTimeframeMs("unknown"))
    }

    @Test
    fun `bucketStart aligns to absolute boundaries`() {
        // 5m-бакеты: 05:26 → 05:25
        assertEquals(0L, FootprintAggregator.bucketStart(65_000L, 60_000L, 5))
        assertEquals(300_000L, FootprintAggregator.bucketStart(310_000L, 60_000L, 5))
        assertEquals(300_000L, FootprintAggregator.bucketStart(599_999L, 60_000L, 5))
        assertEquals(600_000L, FootprintAggregator.bucketStart(600_000L, 60_000L, 5))
        // 1h-бакеты из 15m source
        assertEquals(0L, FootprintAggregator.bucketStart(900_000L, 900_000L, 4))
        assertEquals(3_600_000L, FootprintAggregator.bucketStart(3_600_000L, 900_000L, 4))
        assertEquals(3_600_000L, FootprintAggregator.bucketStart(7_199_999L, 900_000L, 4))
    }

    @Test
    fun `aggregateFootprintCandles computes min and max prices from levels`() {
        val candles = listOf(
            candle(0L, 60_000L, level("99.5", "1.0", "1.0", bidCount = 1, askCount = 1)),
            candle(60_000L, 120_000L, level("101.5", "1.0", "1.0", bidCount = 1, askCount = 1)),
        )

        val agg = FootprintAggregator.aggregateFootprintCandles(candles, 2, 60_000L).first()

        assertEquals("99.5", agg.minPrice)
        assertEquals("101.5", agg.maxPrice)
        assertEquals(4L, agg.totalTicks)
    }

    private fun level(
        price: String,
        bidVolume: String,
        askVolume: String,
        bidCount: Int = 0,
        askCount: Int = 0,
    ) = FootprintLevel(
        price = price,
        bidVolume = bidVolume,
        askVolume = askVolume,
        bidCount = bidCount,
        askCount = askCount,
    )

    private fun candle(
        startTime: Long,
        endTime: Long,
        vararg levels: FootprintLevel,
    ) = FootprintCandle(
        exchange = "Binance",
        symbol = "BTCUSDT",
        timeframe = "1m",
        startTime = startTime,
        endTime = endTime,
        totalTicks = levels.sumOf { (it.bidCount + it.askCount).toLong() },
        minPrice = levels.minOfOrNull { it.priceFloat }?.toString() ?: "0",
        maxPrice = levels.maxOfOrNull { it.priceFloat }?.toString() ?: "0",
        levels = levels.toList(),
    )
}
