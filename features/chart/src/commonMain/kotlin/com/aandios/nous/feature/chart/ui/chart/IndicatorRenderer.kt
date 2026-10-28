/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.core.ui.theme.TerminalSemanticColors
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.utils.calculateCandleMetrics
import com.aandios.nous.feature.chart.utils.priceToY
import com.aandios.nous.research.indicator.HistogramColorMode
import com.aandios.nous.research.indicator.IndicatorColorRole
import com.aandios.nous.research.indicator.IndicatorLine
import com.aandios.nous.research.indicator.IndicatorLineStyle
import com.aandios.nous.research.indicator.IndicatorOutput
import kotlin.math.abs

data class IndicatorChartColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val bullish: Color,
    val bearish: Color,
    val warn: Color,
    val neutral: Color,
    val grid: Color,
) {
    fun color(role: IndicatorColorRole): Color = when (role) {
        IndicatorColorRole.PRIMARY -> primary
        IndicatorColorRole.SECONDARY -> secondary
        IndicatorColorRole.TERTIARY -> tertiary
        IndicatorColorRole.BULLISH -> bullish
        IndicatorColorRole.BEARISH -> bearish
        IndicatorColorRole.WARN -> warn
        IndicatorColorRole.NEUTRAL -> neutral
    }
}

fun indicatorChartColors(colors: TerminalSemanticColors): IndicatorChartColors =
    IndicatorChartColors(
        primary = colors.sma,
        secondary = colors.ema,
        tertiary = colors.bollinger,
        bullish = colors.bullish,
        bearish = colors.bearish,
        warn = colors.warning,
        neutral = colors.label,
        grid = colors.gridLine,
    )

fun indicatorLineColor(line: IndicatorLine, colors: IndicatorChartColors): Color =
    line.colorArgb?.let { Color(it) } ?: colors.color(line.colorRole)

fun overlayIndicatorRenderer(
    outputs: List<IndicatorOutput>,
    colors: IndicatorChartColors,
): DrawScope.(Rect, List<Candle>, PriceRange, Float, Float) -> Unit =
    { area, candles, priceRange, scrollOffset, zoomLevel ->
        drawIndicatorOverlay(area, candles, priceRange, outputs, colors, scrollOffset, zoomLevel)
    }

fun paneIndicatorRenderer(
    outputs: List<IndicatorOutput>,
    colors: IndicatorChartColors,
): DrawScope.(Rect, List<Candle>, PriceRange, Float, Float) -> Unit =
    { area, candles, _, scrollOffset, zoomLevel ->
        if (outputs.isNotEmpty()) {
            drawIndicatorPane(area, candles, outputs, colors, scrollOffset, zoomLevel)
        }
    }

fun DrawScope.drawIndicatorOverlay(
    area: Rect,
    candles: List<Candle>,
    priceRange: PriceRange,
    outputs: List<IndicatorOutput>,
    colors: IndicatorChartColors,
    scrollOffset: Float,
    zoomLevel: Float,
) {
    if (area.width <= 0f || area.height <= 0f || candles.isEmpty()) return
    val metrics = calculateCandleMetrics(zoomLevel)
    val totalWidth = metrics.width + metrics.spacing
    val xAt: (Int) -> Float = { index ->
        area.left + metrics.width / 2f + index * totalWidth - scrollOffset
    }
    val yAt: (Float) -> Float = { value ->
        area.top + priceToY(value, priceRange, area.height)
    }

    clipRect(area.left, area.top, area.right, area.bottom) {
        for (output in outputs) {
            output.bandFill?.let { band ->
                val upper = output.lines.firstOrNull { it.id == band.upperLineId }
                val lower = output.lines.firstOrNull { it.id == band.lowerLineId }
                if (upper != null && lower != null) {
                    drawBandFill(
                        upper = upper,
                        lower = lower,
                        candleCount = candles.size,
                        xAt = xAt,
                        yAt = yAt,
                        color = upper.colorArgb?.let { Color(it) }
                            ?: colors.color(band.colorRole),
                    )
                }
            }
            for (line in output.lines) {
                if (line.style == IndicatorLineStyle.HISTOGRAM) continue
                drawLineSeries(
                    line = line,
                    candleCount = candles.size,
                    xAt = xAt,
                    yAt = yAt,
                    color = indicatorLineColor(line, colors),
                )
            }
        }
    }
}

fun DrawScope.drawIndicatorPane(
    area: Rect,
    candles: List<Candle>,
    outputs: List<IndicatorOutput>,
    colors: IndicatorChartColors,
    scrollOffset: Float,
    zoomLevel: Float,
) {
    if (area.width <= 0f || area.height <= 0f || candles.isEmpty() || outputs.isEmpty()) return
    val metrics = calculateCandleMetrics(zoomLevel)
    val totalWidth = metrics.width + metrics.spacing
    val visibleStart = (scrollOffset / totalWidth).toInt()
        .coerceIn(0, (candles.size - 1).coerceAtLeast(0))
    val visibleEnd = ((scrollOffset + area.width) / totalWidth + 1)
        .toInt()
        .coerceIn(visibleStart + 1, candles.size)
    val (rangeMin, rangeMax) = indicatorPaneRange(outputs, visibleStart, visibleEnd)
    val yAt: (Float) -> Float = { value ->
        val span = if (rangeMax <= rangeMin) 1f else rangeMax - rangeMin
        area.top + (1f - (value - rangeMin) / span) * area.height
    }
    val xAt: (Int) -> Float = { index ->
        area.left + metrics.width / 2f + index * totalWidth - scrollOffset
    }
    val zeroY = yAt(0f).coerceIn(area.top, area.bottom)
    val barWidth = (metrics.width * 0.6f).coerceAtLeast(1f)

    clipRect(area.left, area.top, area.right, area.bottom) {
        for (output in outputs) {
            for (level in output.levels) {
                val y = yAt(level.value).coerceIn(area.top, area.bottom)
                drawLine(
                    color = colors.grid.copy(alpha = 0.5f),
                    start = Offset(area.left, y),
                    end = Offset(area.right, y),
                    strokeWidth = 1f,
                )
            }
            if (output.zeroLine) {
                drawLine(
                    color = colors.grid.copy(alpha = 0.75f),
                    start = Offset(area.left, zeroY),
                    end = Offset(area.right, zeroY),
                    strokeWidth = 1f,
                )
            }
            for (line in output.lines) {
                if (line.style != IndicatorLineStyle.HISTOGRAM) continue
                drawHistogram(
                    line = line,
                    candles = candles,
                    xAt = xAt,
                    yAt = yAt,
                    zeroY = zeroY,
                    barWidth = barWidth,
                    colors = colors,
                )
            }
            for (line in output.lines) {
                if (line.style == IndicatorLineStyle.HISTOGRAM) continue
                drawLineSeries(
                    line = line,
                    candleCount = candles.size,
                    xAt = xAt,
                    yAt = yAt,
                    color = indicatorLineColor(line, colors),
                )
            }
        }
    }
}

private fun DrawScope.drawLineSeries(
    line: IndicatorLine,
    candleCount: Int,
    xAt: (Int) -> Float,
    yAt: (Float) -> Float,
    color: Color,
) {
    val count = minOf(line.values.size, candleCount)
    if (count <= 0) return
    val path = Path()
    var started = false
    for (index in 0 until count) {
        val value = line.values[index]
        if (!value.isFinite()) {
            started = false
            continue
        }
        val x = xAt(index)
        val y = yAt(value)
        if (!x.isFinite() || !y.isFinite()) {
            started = false
            continue
        }
        if (started) path.lineTo(x, y) else path.moveTo(x, y)
        started = true
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = 1.2.dp.toPx(),
            pathEffect = if (line.style == IndicatorLineStyle.DASHED) {
                PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f)
            } else {
                null
            },
        ),
    )
}

private fun DrawScope.drawBandFill(
    upper: IndicatorLine,
    lower: IndicatorLine,
    candleCount: Int,
    xAt: (Int) -> Float,
    yAt: (Float) -> Float,
    color: Color,
) {
    val count = minOf(upper.values.size, lower.values.size, candleCount)
    var index = 0
    while (index < count) {
        if (!upper.values[index].isFinite() || !lower.values[index].isFinite()) {
            index++
            continue
        }
        val start = index
        val path = Path()
        path.moveTo(xAt(start), yAt(upper.values[start]))
        index++
        while (index < count &&
            upper.values[index].isFinite() &&
            lower.values[index].isFinite()
        ) {
            path.lineTo(xAt(index), yAt(upper.values[index]))
            index++
        }
        for (back in index - 1 downTo start) {
            path.lineTo(xAt(back), yAt(lower.values[back]))
        }
        path.close()
        drawPath(path = path, color = color.copy(alpha = 0.10f))
    }
}

private fun DrawScope.drawHistogram(
    line: IndicatorLine,
    candles: List<Candle>,
    xAt: (Int) -> Float,
    yAt: (Float) -> Float,
    zeroY: Float,
    barWidth: Float,
    colors: IndicatorChartColors,
) {
    val count = minOf(line.values.size, candles.size)
    for (index in 0 until count) {
        val value = line.values[index]
        if (!value.isFinite()) continue
        val y = yAt(value)
        val top = minOf(y, zeroY)
        val height = abs(zeroY - y)
        if (height < 0.5f) continue
        val color = line.colorArgb?.let { Color(it) } ?: when (line.histogramColor) {
            HistogramColorMode.SIGN -> if (value >= 0f) colors.bullish else colors.bearish
            HistogramColorMode.CANDLE -> {
                val candle = candles[index]
                if (candle.close >= candle.open) colors.bullish else colors.bearish
            }
        }
        drawRect(
            color = color.copy(alpha = 0.75f),
            topLeft = Offset(xAt(index) - barWidth / 2f, top),
            size = Size(barWidth, height),
        )
    }
}

fun indicatorPaneRange(
    outputs: List<IndicatorOutput>,
    startIndex: Int = 0,
    endIndex: Int = Int.MAX_VALUE,
): Pair<Float, Float> {
    var minValue = Float.POSITIVE_INFINITY
    var maxValue = Float.NEGATIVE_INFINITY
    for (output in outputs) {
        if (output.range.fixed) {
            if (output.range.min.isFinite()) minValue = minOf(minValue, output.range.min)
            if (output.range.max.isFinite()) maxValue = maxOf(maxValue, output.range.max)
            continue
        }

        var hasVisibleValue = false
        for (line in output.lines) {
            val from = startIndex.coerceAtLeast(0)
            val to = minOf(endIndex, line.values.size)
            for (index in from until to) {
                val value = line.values[index]
                if (!value.isFinite()) continue
                hasVisibleValue = true
                minValue = minOf(minValue, value)
                maxValue = maxOf(maxValue, value)
            }
        }
        if (!hasVisibleValue) {
            if (output.range.min.isFinite()) minValue = minOf(minValue, output.range.min)
            if (output.range.max.isFinite()) maxValue = maxOf(maxValue, output.range.max)
        } else if (output.range.min <= 0f && output.range.max >= 0f) {
            minValue = minOf(minValue, 0f)
            maxValue = maxOf(maxValue, 0f)
        }
    }
    if (!minValue.isFinite() || !maxValue.isFinite()) return 0f to 1f
    if (maxValue <= minValue) maxValue = minValue + 1f
    return minValue to maxValue
}
