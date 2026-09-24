/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.tools

import androidx.compose.ui.geometry.Offset
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.utils.priceFromY
import com.aandios.nous.feature.chart.utils.priceToY
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

/** Ручка фигуры (для resize). */
enum class DrawingHandle { START, END }

/** Результат hit-test: рисунок и (опционально) ручка. */
data class DrawingHit(
    val id: String,
    val drawing: Drawing,
    val handle: DrawingHandle? = null,
)

/**
 * Находит рисунок под позицией курсора.
 * Сначала проверяются ручки (resize), затем тело фигуры (move).
 */
fun hitTestDrawings(
    drawings: List<Drawing>,
    position: Offset,
    candles: List<Candle>,
    priceRange: PriceRange,
    chartHeight: Float,
    scrollOffset: Float,
    candleWidth: Float,
    candleSpacing: Float,
    threshold: Float = 8f,
): DrawingHit? {
    if (candles.isEmpty()) return null
    val totalW = candleWidth + candleSpacing
    val firstTime = candles.first().timestamp
    val timeRange = (candles.last().timestamp - firstTime).coerceAtLeast(1L)

    for (drawing in drawings) {
        when (drawing) {
            is Drawing.TrendLine -> {
                val x1 = timeToX(drawing.startTimeMs, firstTime, timeRange, candles.size, totalW, scrollOffset)
                val y1 = priceToY(drawing.startPrice, priceRange, chartHeight)
                val x2 = timeToX(drawing.endTimeMs, firstTime, timeRange, candles.size, totalW, scrollOffset)
                val y2 = priceToY(drawing.endPrice, priceRange, chartHeight)
                if ((position - Offset(x1, y1)).getDistance() <= threshold) {
                    return DrawingHit(drawing.id, drawing, DrawingHandle.START)
                }
                if ((position - Offset(x2, y2)).getDistance() <= threshold) {
                    return DrawingHit(drawing.id, drawing, DrawingHandle.END)
                }
                if (distanceToSegment(position, Offset(x1, y1), Offset(x2, y2)) <= threshold) {
                    return DrawingHit(drawing.id, drawing)
                }
            }
            is Drawing.HorizontalLevel -> {
                val y = priceToY(drawing.price, priceRange, chartHeight)
                if (abs(position.y - y) <= threshold) {
                    return DrawingHit(drawing.id, drawing)
                }
            }
            is Drawing.Rectangle -> {
                val x1 = timeToX(drawing.startTimeMs, firstTime, timeRange, candles.size, totalW, scrollOffset)
                val x2 = timeToX(drawing.endTimeMs, firstTime, timeRange, candles.size, totalW, scrollOffset)
                val y1 = priceToY(drawing.topPrice, priceRange, chartHeight)
                val y2 = priceToY(drawing.bottomPrice, priceRange, chartHeight)
                if ((position - Offset(x1, y1)).getDistance() <= threshold) {
                    return DrawingHit(drawing.id, drawing, DrawingHandle.START)
                }
                if ((position - Offset(x2, y2)).getDistance() <= threshold) {
                    return DrawingHit(drawing.id, drawing, DrawingHandle.END)
                }
                if (position.x in minOf(x1, x2)..maxOf(x1, x2) &&
                    position.y in minOf(y1, y2)..maxOf(y1, y2)
                ) {
                    return DrawingHit(drawing.id, drawing)
                }
            }
            is Drawing.VerticalLine -> {
                val x = timeToX(drawing.timeMs, firstTime, timeRange, candles.size, totalW, scrollOffset)
                if (abs(position.x - x) <= threshold) {
                    return DrawingHit(drawing.id, drawing)
                }
            }
        }
    }
    return null
}

/**
 * Перемещает (handle == null) или изменяет размер (handle != null) рисунка:
 * дельта вычисляется от стартовой позиции жеста к текущей, поэтому
 * многократные вызовы во время drag не накапливают ошибку.
 */
fun moveDrawing(
    drawing: Drawing,
    handle: DrawingHandle?,
    from: Offset,
    to: Offset,
    candles: List<Candle>,
    priceRange: PriceRange,
    chartHeight: Float,
    scrollOffset: Float,
    candleWidth: Float,
    candleSpacing: Float,
    formatter: SymbolFormatter,
): Drawing {
    val priceDelta = priceFromY(to.y, priceRange, chartHeight) - priceFromY(from.y, priceRange, chartHeight)
    val timeDelta = candleTimeAt(to.x, candles, scrollOffset, candleWidth, candleSpacing) -
        candleTimeAt(from.x, candles, scrollOffset, candleWidth, candleSpacing)

    return when (drawing) {
        is Drawing.HorizontalLevel -> drawing.copy(price = drawing.price + priceDelta)
        is Drawing.VerticalLine -> drawing.copy(timeMs = drawing.timeMs + timeDelta)
        is Drawing.TrendLine -> when (handle) {
            DrawingHandle.START -> drawing.copy(
                startPrice = drawing.startPrice + priceDelta,
                startTimeMs = drawing.startTimeMs + timeDelta,
            ).withUpdatedLabel(formatter)
            DrawingHandle.END -> drawing.copy(
                endPrice = drawing.endPrice + priceDelta,
                endTimeMs = drawing.endTimeMs + timeDelta,
            ).withUpdatedLabel(formatter)
            null -> drawing.copy(
                startPrice = drawing.startPrice + priceDelta,
                endPrice = drawing.endPrice + priceDelta,
                startTimeMs = drawing.startTimeMs + timeDelta,
                endTimeMs = drawing.endTimeMs + timeDelta,
            ).withUpdatedLabel(formatter)
        }
        is Drawing.Rectangle -> when (handle) {
            DrawingHandle.START -> drawing.copy(
                topPrice = drawing.topPrice + priceDelta,
                startTimeMs = drawing.startTimeMs + timeDelta,
            )
            DrawingHandle.END -> drawing.copy(
                bottomPrice = drawing.bottomPrice + priceDelta,
                endTimeMs = drawing.endTimeMs + timeDelta,
            )
            null -> drawing.copy(
                topPrice = drawing.topPrice + priceDelta,
                bottomPrice = drawing.bottomPrice + priceDelta,
                startTimeMs = drawing.startTimeMs + timeDelta,
                endTimeMs = drawing.endTimeMs + timeDelta,
            )
        }
    }
}

/**
 * Длительность в читаемом виде: компоненты d/h/m выводятся только если ≠ 0,
 * секунды — только если длительность меньше минуты. Примеры:
 * "2 d 12 h 15 m", "1 h 5 m", "15 m", "45 s", "0 s".
 */
fun formatDuration(durationMs: Long): String {
    val totalSec = durationMs / 1000L
    val days = totalSec / 86_400L
    val hours = (totalSec % 86_400L) / 3_600L
    val minutes = (totalSec % 3_600L) / 60L
    val seconds = totalSec % 60L

    val parts = mutableListOf<String>()
    if (days > 0) parts.add("$days d")
    if (hours > 0) parts.add("$hours h")
    if (minutes > 0) parts.add("$minutes m")
    if (days == 0L && hours == 0L && minutes == 0L) parts.add("$seconds s")
    return if (parts.isEmpty()) "0 s" else parts.joinToString(" ")
}

/**
 * Метка линейки: Δ цены (модуль), процент со знаком (end − start) / start × 100
 * и длительность между точками.
 */
fun rulerLabel(
    startPrice: Float,
    endPrice: Float,
    startTimeMs: Long,
    endTimeMs: Long,
    formatter: SymbolFormatter,
): String {
    val priceDiff = endPrice - startPrice
    val absDiff = abs(priceDiff)
    val pctChange = if (startPrice > 0f) priceDiff / startPrice * 100f else 0f
    val duration = formatDuration(abs(endTimeMs - startTimeMs))
    return "\u0394${formatter.formatPrice(absDiff)} (${formatPercent(pctChange)}%) | $duration"
}

/** Процент с двумя знаками после запятой (без усечения-через-toInt). */
private fun formatPercent(value: Float): String {
    val rounded = round(value * 100f) / 100f
    if (rounded == 0f) return "0.00"
    val absValue = abs(rounded)
    val whole = absValue.toInt()
    val frac = ((absValue - whole) * 100f).roundToInt()
    val sign = if (rounded < 0f) "-" else ""
    return "$sign$whole.${frac.toString().padStart(2, '0')}"
}

/** Обновляет label трендовой линии (линейки) после перемещения. */
private fun Drawing.TrendLine.withUpdatedLabel(formatter: SymbolFormatter): Drawing.TrendLine =
    if (label != null) copy(label = rulerLabel(startPrice, endPrice, startTimeMs, endTimeMs, formatter)) else this

/** X-координата времени на виртуальной ленте. */
internal fun timeToX(
    timeMs: Long,
    firstTime: Long,
    timeRange: Long,
    candleCount: Int,
    totalW: Float,
    scrollOffset: Float,
): Float {
    val fraction = (timeMs - firstTime).toFloat() / timeRange.toFloat()
    return fraction * candleCount * totalW - scrollOffset
}

private fun candleTimeAt(
    x: Float,
    candles: List<Candle>,
    scrollOffset: Float,
    candleWidth: Float,
    candleSpacing: Float,
): Long {
    if (candles.isEmpty()) return 0L
    val totalW = candleWidth + candleSpacing
    val index = ((x + scrollOffset) / totalW).toInt().coerceIn(0, candles.size - 1)
    return candles[index].timestamp
}

private fun distanceToSegment(point: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val lengthSq = ab.x * ab.x + ab.y * ab.y
    if (lengthSq == 0f) return (point - a).getDistance()
    val t = (((point.x - a.x) * ab.x + (point.y - a.y) * ab.y) / lengthSq).coerceIn(0f, 1f)
    val projection = Offset(a.x + t * ab.x, a.y + t * ab.y)
    return (point - projection).getDistance()
}
