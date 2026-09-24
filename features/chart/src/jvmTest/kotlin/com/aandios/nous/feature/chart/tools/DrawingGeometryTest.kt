/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.tools

import androidx.compose.ui.geometry.Offset
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.feature.chart.model.PriceRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrawingGeometryTest {

    private val candles = (0 until 100).map { Candle(100f, 110f, 105f, 95f, it * 60_000L, 10f) }
    private val range = PriceRange(max = 120f, min = 80f, visibleMax = 120f, visibleMin = 80f, range = 40f)
    private val chartHeight = 400f
    private val formatter = SymbolFormatter()

    private fun trendLine() = Drawing.TrendLine(
        id = "tl",
        startPrice = 90f,
        startTimeMs = 60_000L,
        endPrice = 110f,
        endTimeMs = 300_000L,
    )

    private fun horizontal() = Drawing.HorizontalLevel(id = "h", price = 100f, label = "100.00")

    private fun rectangle() = Drawing.Rectangle(
        id = "r",
        topPrice = 110f,
        bottomPrice = 90f,
        startTimeMs = 60_000L,
        endTimeMs = 300_000L,
    )

    private fun vertical() = Drawing.VerticalLine(id = "v", timeMs = 180_000L)

    // ===== hit-test =====

    @Test
    fun `hitTest detects trendline body near segment`() {
        // x-координаты: candle 1 → x=1*11.43 - 0 + 4 = 15.4; candle 5 → 61.1 (zoom 1)
        // точка между ними рядом с сегментом
        val startX = candleX(1)
        val endX = candleX(5)
        val startY = priceToY(90f)
        val endY = priceToY(110f)
        val mid = Offset((startX + endX) / 2f, (startY + endY) / 2f + 1f)

        val hit = hitTestDrawings(
            drawings = listOf(trendLine()),
            position = mid,
            candles = candles,
            priceRange = range,
            chartHeight = chartHeight,
            scrollOffset = 0f,
            candleWidth = 8f,
            candleSpacing = 8f * 0.3f / 0.7f,
        )

        assertEquals("tl", hit?.id)
        assertNull(hit?.handle)
    }

    @Test
    fun `hitTest detects trendline endpoint handle`() {
        val end = Offset(candleX(5), priceToY(110f))

        val hit = hitTestDrawings(
            drawings = listOf(trendLine()),
            position = end,
            candles = candles,
            priceRange = range,
            chartHeight = chartHeight,
            scrollOffset = 0f,
            candleWidth = 8f,
            candleSpacing = 8f * 0.3f / 0.7f,
        )

        assertEquals(DrawingHandle.END, hit?.handle)
    }

    @Test
    fun `hitTest detects horizontal level`() {
        val hit = hitTestDrawings(
            drawings = listOf(horizontal()),
            position = Offset(200f, priceToY(100f) + 2f),
            candles = candles,
            priceRange = range,
            chartHeight = chartHeight,
            scrollOffset = 0f,
            candleWidth = 8f,
            candleSpacing = 8f * 0.3f / 0.7f,
        )

        assertEquals("h", hit?.id)
    }

    @Test
    fun `hitTest detects rectangle body and corners`() {
        val inside = Offset((candleX(1) + candleX(5)) / 2f, (priceToY(110f) + priceToY(90f)) / 2f)
        val corner = Offset(candleX(5), priceToY(90f))

        val bodyHit = hitTestDrawings(
            listOf(rectangle()), inside, candles, range, chartHeight, 0f, 8f, 8f * 0.3f / 0.7f,
        )
        val cornerHit = hitTestDrawings(
            listOf(rectangle()), corner, candles, range, chartHeight, 0f, 8f, 8f * 0.3f / 0.7f,
        )

        assertEquals("r", bodyHit?.id)
        assertNull(bodyHit?.handle)
        assertEquals(DrawingHandle.END, cornerHit?.handle)
    }

    @Test
    fun `hitTest detects vertical line`() {
        val hit = hitTestDrawings(
            listOf(vertical()),
            Offset(candleX(3) + 1f, 150f),
            candles, range, chartHeight, 0f, 8f, 8f * 0.3f / 0.7f,
        )

        assertEquals("v", hit?.id)
    }

    @Test
    fun `hitTest misses far away from drawings`() {
        val hit = hitTestDrawings(
            listOf(trendLine(), horizontal()),
            Offset(5f, 5f),
            candles, range, chartHeight, 0f, 8f, 8f * 0.3f / 0.7f,
        )

        assertNull(hit)
    }

    // ===== move/resize =====

    @Test
    fun `moveDrawing shifts horizontal level by price delta`() {
        val from = Offset(0f, priceToY(100f))
        val to = Offset(0f, priceToY(105f))

        val moved = moveDrawing(
            drawing = horizontal(),
            handle = null,
            from = from, to = to,
            candles = candles, priceRange = range, chartHeight = chartHeight,
            scrollOffset = 0f, candleWidth = 8f, candleSpacing = 8f * 0.3f / 0.7f,
            formatter = formatter,
        ) as Drawing.HorizontalLevel

        assertEquals(105f, moved.price, 0.001f)
    }

    @Test
    fun `moveDrawing shifts vertical line by time delta`() {
        val from = Offset(candleX(3), 0f)
        val to = Offset(candleX(7), 0f)

        val moved = moveDrawing(
            drawing = vertical(),
            handle = null,
            from = from, to = to,
            candles = candles, priceRange = range, chartHeight = chartHeight,
            scrollOffset = 0f, candleWidth = 8f, candleSpacing = 8f * 0.3f / 0.7f,
            formatter = formatter,
        ) as Drawing.VerticalLine

        assertEquals(420_000L, moved.timeMs)
    }

    @Test
    fun `moveDrawing moves both trendline endpoints when body dragged`() {
        val from = Offset(candleX(3), priceToY(100f))
        val to = Offset(candleX(7), priceToY(110f))

        val moved = moveDrawing(
            drawing = trendLine(),
            handle = null,
            from = from, to = to,
            candles = candles, priceRange = range, chartHeight = chartHeight,
            scrollOffset = 0f, candleWidth = 8f, candleSpacing = 8f * 0.3f / 0.7f,
            formatter = formatter,
        ) as Drawing.TrendLine

        assertEquals(100f, moved.startPrice, 0.001f) // 90 + 10
        assertEquals(120f, moved.endPrice, 0.001f)   // 110 + 10
        assertEquals(300_000L, moved.startTimeMs)    // 60_000 + 240_000
        assertEquals(540_000L, moved.endTimeMs)      // 300_000 + 240_000
    }

    @Test
    fun `moveDrawing resizes trendline end handle only`() {
        val from = Offset(candleX(5), priceToY(110f))
        val to = Offset(candleX(5), priceToY(100f))

        val moved = moveDrawing(
            drawing = trendLine(),
            handle = DrawingHandle.END,
            from = from, to = to,
            candles = candles, priceRange = range, chartHeight = chartHeight,
            scrollOffset = 0f, candleWidth = 8f, candleSpacing = 8f * 0.3f / 0.7f,
            formatter = formatter,
        ) as Drawing.TrendLine

        assertEquals(90f, moved.startPrice, 0.001f) // не изменилась
        assertEquals(100f, moved.endPrice, 0.001f)  // 110 - 10
        assertEquals(60_000L, moved.startTimeMs)
    }

    @Test
    fun `moveDrawing resizes rectangle start corner`() {
        val from = Offset(candleX(1), priceToY(110f))
        val to = Offset(candleX(2), priceToY(105f))

        val moved = moveDrawing(
            drawing = rectangle(),
            handle = DrawingHandle.START,
            from = from, to = to,
            candles = candles, priceRange = range, chartHeight = chartHeight,
            scrollOffset = 0f, candleWidth = 8f, candleSpacing = 8f * 0.3f / 0.7f,
            formatter = formatter,
        ) as Drawing.Rectangle

        assertEquals(105f, moved.topPrice, 0.001f)
        assertEquals(120_000L, moved.startTimeMs)
        assertEquals(90f, moved.bottomPrice, 0.001f)  // не изменился
        assertEquals(300_000L, moved.endTimeMs)
    }

    // ===== ruler label =====

    @Test
    fun `rulerLabel contains delta percent and time`() {
        val label = rulerLabel(
            startPrice = 100f,
            endPrice = 105f,
            startTimeMs = 0L,
            endTimeMs = 3_600_000L + 120_000L,
            formatter = formatter,
        )

        assertTrue(label.startsWith("\u03945.00"))
        assertTrue(label.contains("5.0%"))
        assertTrue(label.contains("1h 2m"))
    }

    // ===== helpers =====

    private fun candleX(index: Int): Float {
        val totalW = 8f + 8f * 0.3f / 0.7f
        return index * totalW + 8f / 2f
    }

    private fun priceToY(price: Float): Float =
        chartHeight - ((price - range.visibleMin) / range.range) * chartHeight
}
