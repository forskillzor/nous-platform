/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.utils

import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.api.market.model.FootprintLevel
import com.aandios.nous.feature.chart.model.PriceRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartCalculatorTest {

    private val range = PriceRange(max = 110f, min = 90f, visibleMax = 110f, visibleMin = 90f, range = 20f)

    @Test
    fun `calculateCandleMetrics scales with zoom`() {
        val base = calculateCandleMetrics(1f)
        assertEquals(8f, base.width)
        assertEquals(8f * 0.3f / 0.7f, base.spacing)

        val zoomed = calculateCandleMetrics(2f)
        assertEquals(16f, zoomed.width)
        assertEquals(16f * 0.3f / 0.7f, zoomed.spacing)
    }

    @Test
    fun `priceToY maps visible range to chart height`() {
        assertEquals(0f, priceToY(110f, range, 100f))
        assertEquals(100f, priceToY(90f, range, 100f))
        assertEquals(50f, priceToY(100f, range, 100f))
    }

    @Test
    fun `priceFromY is inverse of priceToY`() {
        val y = priceToY(97.5f, range, 200f)
        assertEquals(97.5f, priceFromY(y, range, 200f), 0.001f)
    }

    @Test
    fun `priceToY handles zero range without NaN`() {
        val flat = PriceRange(max = 100f, min = 100f, visibleMax = 100f, visibleMin = 100f, range = 0f)
        val y = priceToY(100f, flat, 100f)
        assertTrue(y.isFinite())
    }

    @Test
    fun `generatePriceLevels descends from max`() {
        assertEquals(listOf(200f, 175f, 150f, 125f, 100f), generatePriceLevels(100f, 200f, 5))
        assertEquals(listOf(200f), generatePriceLevels(100f, 200f, 1))
        assertTrue(generatePriceLevels(100f, 200f, 0).isEmpty())
    }

    @Test
    fun `findNearestCandleIndex clamps to candle bounds`() {
        val candles = (0 until 10).map { Candle(1f, 2f, 1.5f, 0.5f, it * 60_000L, 10f) }

        assertEquals(0, findNearestCandleIndex(mouseX = 0f, candles = candles))
        assertEquals(9, findNearestCandleIndex(mouseX = 10_000f, candles = candles))
        assertEquals(-1, findNearestCandleIndex(mouseX = 0f, candles = emptyList()))
    }

    @Test
    fun `price range includes padding and current price`() {
        val candles = listOf(
            Candle(open = 95f, high = 100f, close = 99f, low = 94f, timestamp = 0L),
            Candle(open = 99f, high = 105f, close = 104f, low = 98f, timestamp = 60_000L),
        )

        val result = calculatePriceRangeWithCurrentPrice(candles, currentPrice = 110f)

        assertEquals(110f, result.max)
        assertEquals(94f, result.min)
        assertEquals(110.8f, result.visibleMax, 0.001f)
        assertEquals(93.2f, result.visibleMin, 0.001f)
        assertEquals(17.6f, result.range, 0.001f)
    }

    @Test
    fun `low price symbol keeps positive range`() {
        val candles = listOf(
            Candle(open = 0.000020f, high = 0.000030f, close = 0.000025f, low = 0.000018f, timestamp = 0L),
        )

        val result = calculatePriceRangeWithCurrentPrice(candles, currentPrice = null)

        assertTrue(result.range > 0f)
        assertTrue(result.visibleMin < result.min)
        assertTrue(result.visibleMax > result.max)
    }

    @Test
    fun `price range for empty candles without current price is zero`() {
        val result = calculatePriceRangeWithCurrentPrice(emptyList(), currentPrice = null)
        assertEquals(0f, result.range)
    }

    @Test
    fun `price range uses current price when candles are empty`() {
        val result = calculatePriceRangeWithCurrentPrice(emptyList(), currentPrice = 50f)
        assertEquals(50f, result.max)
        assertEquals(50f, result.min)
    }

    @Test
    fun `price range ignores non-finite candle prices`() {
        val candles = listOf(
            Candle(open = 95f, high = 100f, close = 99f, low = 94f, timestamp = 0L),
            Candle(open = 1f, high = Float.POSITIVE_INFINITY, close = 1f, low = Float.NaN, timestamp = 60_000L),
        )

        val result = calculatePriceRangeWithCurrentPrice(candles, currentPrice = null)

        assertEquals(100f, result.max, 0.001f)
        assertEquals(94f, result.min, 0.001f)
        assertTrue(result.range.isFinite())
    }

    @Test
    fun `price range for footprint uses levels and ignores missing min and max`() {
        val candles = listOf(
            FootprintCandle(
                exchange = "Binance",
                symbol = "BTCUSDT",
                timeframe = "1m",
                startTime = 0L,
                endTime = 60_000L,
                minPrice = "0",
                maxPrice = "0",
                levels = listOf(
                    FootprintLevel(price = "99.0", bidVolume = "1", askVolume = "1"),
                    FootprintLevel(price = "101.0", bidVolume = "1", askVolume = "1"),
                ),
            ),
        )

        val result = calculatePriceRangeFromLevels(candles)

        assertEquals(101f, result.max, 0.001f)
        assertEquals(99f, result.min, 0.001f)
        assertTrue(result.range > 0f)
        assertTrue(result.visibleMax > result.max)
    }

    @Test
    fun `price range for footprint handles empty levels`() {
        val candles = listOf(
            FootprintCandle(exchange = "Binance", symbol = "BTCUSDT", timeframe = "1m", levels = emptyList()),
        )

        val result = calculatePriceRangeFromLevels(candles)

        assertEquals(0f, result.range)
    }

    @Test
    fun `shiftPriceRange returns base for zero scroll or zero height`() {
        assertEquals(range, shiftPriceRange(range, verticalScroll = 0f, chartHeight = 100f))
        assertEquals(range, shiftPriceRange(range, verticalScroll = 50f, chartHeight = 0f))
    }

    @Test
    fun `shiftPriceRange shifts range proportionally to scroll`() {
        val shifted = shiftPriceRange(range, verticalScroll = 50f, chartHeight = 100f)

        // ratio = 0.5, shift = range * 0.5 = 10
        assertEquals(120f, shifted.max, 0.001f)
        assertEquals(100f, shifted.min, 0.001f)
        assertEquals(120f, shifted.visibleMax, 0.001f)
        assertEquals(100f, shifted.visibleMin, 0.001f)
        assertEquals(20f, shifted.range, 0.001f)
    }

    @Test
    fun `shiftPriceRange handles negative scroll`() {
        val shifted = shiftPriceRange(range, verticalScroll = -50f, chartHeight = 100f)

        assertEquals(100f, shifted.max, 0.001f)
        assertEquals(80f, shifted.min, 0.001f)
        assertEquals(20f, shifted.range, 0.001f)
    }

    @Test
    fun `zoom without ctrl keeps right edge fixed`() {
        val scroll = 500f
        val chartWidth = 800f
        val rightEdgeBefore = scroll + chartWidth

        val newScroll = calculateZoomScrollOffset(
            scrollOffset = scroll,
            chartWidth = chartWidth,
            mouseX = 100f,
            actualFactor = 1.25f,
            anchorAtMouse = false,
        )

        assertEquals(rightEdgeBefore * 1.25f, newScroll + chartWidth, 0.001f)
    }

    @Test
    fun `zoom with ctrl keeps point under cursor fixed`() {
        val scroll = 500f
        val mouseX = 200f
        val virtualBefore = mouseX + scroll

        val newScroll = calculateZoomScrollOffset(
            scrollOffset = scroll,
            chartWidth = 800f,
            mouseX = mouseX,
            actualFactor = 1.25f,
            anchorAtMouse = true,
        )

        assertEquals(virtualBefore * 1.25f, mouseX + newScroll, 0.001f)
    }

    @Test
    fun `zoom at latest candle keeps right edge after clamp`() {
        val metrics = calculateCandleMetrics(1f)
        val chartWidth = 800f
        val candleCount = 500
        val maxScrollBefore = calculateMaxScroll(candleCount, metrics, chartWidth)
        val actualFactor = 0.8f

        val newScroll = calculateZoomScrollOffset(
            scrollOffset = maxScrollBefore,
            chartWidth = chartWidth,
            mouseX = 0f,
            actualFactor = actualFactor,
            anchorAtMouse = false,
        )
        val newMaxScroll = calculateMaxScroll(candleCount, calculateCandleMetrics(actualFactor), chartWidth)

        assertEquals(newMaxScroll, newScroll.coerceIn(-300f, newMaxScroll), 0.001f)
    }

    @Test
    fun `calculateMaxScroll is zero when content fits`() {
        val metrics = calculateCandleMetrics(1f)

        assertEquals(0f, calculateMaxScroll(candleCount = 10, candleMetrics = metrics, chartWidth = 10_000f))
        assertTrue(calculateMaxScroll(candleCount = 1000, candleMetrics = metrics, chartWidth = 100f) > 0f)
    }

    @Test
    fun `prependedCount counts candles added to the left`() {
        val candles = (0 until 10).map { Candle(1f, 2f, 1.5f, 0.5f, it * 60_000L, 10f) }

        // Опорная свеча 240_000: слева появились 0, 60_000, 120_000, 180_000 → 4
        assertEquals(4, prependedCount(candles, prevFirstTimestamp = 240_000L))
        // Первая та же (append справа)
        assertEquals(0, prependedCount(candles, prevFirstTimestamp = 0L))
        // Нет предыдущего значения
        assertEquals(0, prependedCount(candles, prevFirstTimestamp = null))
        // Опорной свечи нет в списке
        assertEquals(0, prependedCount(candles, prevFirstTimestamp = -60_000L))
        assertEquals(0, prependedCount(emptyList(), prevFirstTimestamp = 0L))
    }
}
