/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.tools

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.utils.priceToY

/**
 * Renders drawings on a chart Canvas.
 * Each drawing is positioned using price-to-Y and time-to-X coordinates.
 */
object DrawingRenderer {
    /**
     * Render all drawings on the chart.
     *
     * @param drawings  List of drawings to render
     * @param candles   Candles for time-to-X mapping
     * @param priceRange Price range for Y coordinates
     * @param chartWidth Full chart area width
     * @param chartHeight Full chart area height
     * @param scrollOffset Current horizontal scroll offset
     * @param candleWidth Width of a single candle in pixels
     * @param candleSpacing Spacing between candles
     * @param textMeasurer Measurer for drawing labels
     */
    fun DrawScope.drawDrawings(
        drawings: List<Drawing>,
        candles: List<Candle>,
        priceRange: PriceRange,
        chartWidth: Float,
        chartHeight: Float,
        scrollOffset: Float,
        candleWidth: Float,
        candleSpacing: Float,
        textMeasurer: TextMeasurer,
    ) {
        if (candles.isEmpty()) return
        val totalW = candleWidth + candleSpacing
        val firstTime = candles.first().timestamp
        val lastTime = candles.last().timestamp
        val timeRange = (lastTime - firstTime).coerceAtLeast(1L)

        for (drawing in drawings) {
            when (drawing) {
                is Drawing.TrendLine -> drawTrendLine(drawing, candles, priceRange, chartHeight, scrollOffset, totalW, firstTime, timeRange, textMeasurer)
                is Drawing.HorizontalLevel -> drawHorizontal(drawing, priceRange, chartHeight, chartWidth, textMeasurer)
                is Drawing.Rectangle -> drawRect(drawing, candles, priceRange, chartHeight, scrollOffset, totalW, firstTime, timeRange)
                is Drawing.VerticalLine -> drawVertical(drawing, candles, chartHeight, scrollOffset, totalW, firstTime, timeRange, textMeasurer)
            }
        }
    }

    private fun DrawScope.drawTrendLine(
        d: Drawing.TrendLine,
        candles: List<Candle>,
        priceRange: PriceRange,
        chartHeight: Float,
        scrollOffset: Float,
        totalW: Float,
        firstTime: Long,
        timeRange: Long,
        textMeasurer: TextMeasurer,
    ) {
        val x1 = timeToX(d.startTimeMs, firstTime, timeRange, candles.size, totalW, scrollOffset)
        val x2 = timeToX(d.endTimeMs, firstTime, timeRange, candles.size, totalW, scrollOffset)
        val y1 = priceToY(d.startPrice, priceRange, chartHeight)
        val y2 = priceToY(d.endPrice, priceRange, chartHeight)

        drawLine(
            color = d.color,
            start = Offset(x1, y1),
            end = Offset(x2, y2),
            strokeWidth = d.lineWidth * 2f
        )

        // Draw handles at start and end points
        drawCircle(d.color, 5f, Offset(x1, y1), style = Stroke(1.5f))
        drawCircle(d.color, 5f, Offset(x2, y2), style = Stroke(1.5f))

        // Label at midpoint
        d.label?.let { label ->
            val midX = (x1 + x2) / 2f
            val midY = (y1 + y2) / 2f
            val labelY = if (y2 > y1) midY - 14f else midY + 6f
            drawLabel(label, midX + 4f, labelY, textMeasurer)
        }
    }

    private fun DrawScope.drawHorizontal(
        d: Drawing.HorizontalLevel,
        priceRange: PriceRange,
        chartHeight: Float,
        chartWidth: Float,
        textMeasurer: TextMeasurer,
    ) {
        val y = priceToY(d.price, priceRange, chartHeight)

        if (d.isDashed) {
            val pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
            drawLine(
                color = d.color,
                start = Offset(0f, y),
                end = Offset(chartWidth, y),
                strokeWidth = d.lineWidth,
                pathEffect = pathEffect
            )
        } else {
            drawLine(
                color = d.color,
                start = Offset(0f, y),
                end = Offset(chartWidth, y),
                strokeWidth = d.lineWidth
            )
        }

        // Price label on the right
        d.label?.let { label ->
            drawLabel(label, chartWidth, y - 16f, textMeasurer, alignRight = true)
        }
    }

    private fun DrawScope.drawRect(
        d: Drawing.Rectangle,
        candles: List<Candle>,
        priceRange: PriceRange,
        chartHeight: Float,
        scrollOffset: Float,
        totalW: Float,
        firstTime: Long,
        timeRange: Long
    ) {
        val x1 = timeToX(d.startTimeMs, firstTime, timeRange, candles.size, totalW, scrollOffset)
        val x2 = timeToX(d.endTimeMs, firstTime, timeRange, candles.size, totalW, scrollOffset)
        val y1 = priceToY(d.topPrice, priceRange, chartHeight)
        val y2 = priceToY(d.bottomPrice, priceRange, chartHeight)

        drawRect(
            color = d.color,
            topLeft = Offset(x1, y1),
            size = Size(x2 - x1, y2 - y1)
        )
        drawRect(
            color = d.borderColor,
            topLeft = Offset(x1, y1),
            size = Size(x2 - x1, y2 - y1),
            style = Stroke(d.lineWidth)
        )
    }

    private fun DrawScope.drawVertical(
        d: Drawing.VerticalLine,
        candles: List<Candle>,
        chartHeight: Float,
        scrollOffset: Float,
        totalW: Float,
        firstTime: Long,
        timeRange: Long,
        textMeasurer: TextMeasurer,
    ) {
        val x = timeToX(d.timeMs, firstTime, timeRange, candles.size, totalW, scrollOffset)
        drawLine(
            color = d.color,
            start = Offset(x, 0f),
            end = Offset(x, chartHeight),
            strokeWidth = d.lineWidth * 2f
        )

        d.label?.let { label ->
            drawLabel(label, x + 4f, 4f, textMeasurer)
        }
    }

    private fun DrawScope.drawLabel(
        text: String,
        x: Float,
        y: Float,
        textMeasurer: TextMeasurer,
        alignRight: Boolean = false,
    ) {
        val style = TextStyle(
            color = Color.White,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        val layout = textMeasurer.measure(AnnotatedString(text), style)
        val labelX = if (alignRight) x - layout.size.width - 6f else x
        drawRect(
            color = Color.Black.copy(alpha = 0.7f),
            topLeft = Offset(labelX, y),
            size = Size(layout.size.width + 6f, layout.size.height + 4f)
        )
        drawText(layout, topLeft = Offset(labelX + 3f, y + 2f))
    }

    private fun timeToX(
        timeMs: Long,
        firstTime: Long,
        timeRange: Long,
        candleCount: Int,
        totalW: Float,
        scrollOffset: Float
    ): Float {
        val fraction = (timeMs - firstTime).toFloat() / timeRange.toFloat()
        val x = fraction * candleCount * totalW - scrollOffset
        return x
    }
}
