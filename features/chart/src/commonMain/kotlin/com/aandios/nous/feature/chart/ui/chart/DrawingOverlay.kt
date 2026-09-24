/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui.chart

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.feature.chart.model.ChartLayout
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.tools.Drawing
import com.aandios.nous.feature.chart.tools.DrawingHistory
import com.aandios.nous.feature.chart.tools.DrawingToolType
import com.aandios.nous.feature.chart.tools.rulerLabel
import com.aandios.nous.feature.chart.utils.findNearestCandleIndex
import com.aandios.nous.feature.chart.utils.formatPrice
import com.aandios.nous.feature.chart.utils.priceFromY
import kotlin.math.max
import kotlin.math.min

/**
 * Transparent overlay that intercepts pointer events when a drawing tool is active.
 * Во время drag вызывает [onPreviewChange] — движок рисует фигуру «на лету»
 * с актуальными вычислениями (для линейки — Δ/%/время).
 */
@Composable
fun DrawingOverlay(
    activeDrawingTool: DrawingToolType,
    drawingHistory: DrawingHistory?,
    candles: List<Candle>,
    priceRange: PriceRange,
    layout: ChartLayout,
    scrollOffset: Float,
    zoomLevel: Float,
    priceFormatter: SymbolFormatter = SymbolFormatter.DEFAULT,
    onToolChange: (DrawingToolType) -> Unit,
    onPreviewChange: (Drawing?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (activeDrawingTool == DrawingToolType.NONE || drawingHistory == null) return

    val currentCandles by rememberUpdatedState(candles)
    val currentPriceRange by rememberUpdatedState(priceRange)
    val currentLayout by rememberUpdatedState(layout)
    val currentScroll by rememberUpdatedState(scrollOffset)
    val currentZoom by rememberUpdatedState(zoomLevel)

    Box(modifier = modifier.fillMaxSize().pointerInput(activeDrawingTool) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startPos = down.position

            when (activeDrawingTool) {
                DrawingToolType.HORIZONTAL, DrawingToolType.VERTICAL -> {
                    // Single-click: показываем превью сразу, коммитим на release
                    val draft = buildDrawing(
                        activeDrawingTool, startPos, startPos,
                        currentCandles, currentPriceRange, currentLayout, currentScroll, currentZoom, priceFormatter
                    )
                    onPreviewChange(draft)
                    var released = false
                    while (!released) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            change.consume()
                            released = true
                        }
                    }
                    onPreviewChange(null)
                    draft?.let { drawingHistory.add(it) }
                    onToolChange(DrawingToolType.NONE)
                }
                else -> {
                    // Drag tools: превью на каждом движении, коммит на release
                    var endPos = startPos
                    var released = false
                    var draft: Drawing? = null
                    while (!released) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            endPos = change.position
                            draft = buildDrawing(
                                activeDrawingTool, startPos, endPos,
                                currentCandles, currentPriceRange, currentLayout, currentScroll, currentZoom, priceFormatter
                            )
                            onPreviewChange(draft)
                            change.consume()
                        } else {
                            change.consume()
                            released = true
                        }
                    }
                    onPreviewChange(null)
                    draft?.let { drawingHistory.add(it) }
                }
            }
        }
    })
}

/** Чистое построение фигуры по жесту (для превью и коммита). */
internal fun buildDrawing(
    tool: DrawingToolType,
    start: Offset, end: Offset,
    candles: List<Candle>,
    priceRange: PriceRange,
    layout: ChartLayout,
    scrollOffset: Float,
    zoomLevel: Float,
    priceFormatter: SymbolFormatter,
): Drawing? {
    val chartH = layout.chartMainArea.height
    if (chartH <= 0f || candles.isEmpty()) return null

    val ts = com.aandios.nous.core.currentTimeMillis()

    fun candleIdx(x: Float) = findNearestCandleIndex(x, candles, scrollOffset, zoomLevel)
        .coerceIn(0, (candles.lastIndex).coerceAtLeast(0))

    fun candleTs(x: Float) = candles[candleIdx(x)].timestamp

    return when (tool) {
        DrawingToolType.HORIZONTAL -> {
            val price = priceFromY(start.y, priceRange, chartH)
            Drawing.HorizontalLevel(
                id = "h_$ts", price = price,
                color = androidx.compose.ui.graphics.Color(0xFF2196F3),
                label = formatPrice(price, priceFormatter)
            )
        }
        DrawingToolType.VERTICAL -> {
            Drawing.VerticalLine(
                id = "v_$ts", timeMs = candleTs(start.x),
                color = androidx.compose.ui.graphics.Color(0xFFFF5722)
            )
        }
        DrawingToolType.TREND_LINE -> {
            val p1 = priceFromY(start.y, priceRange, chartH)
            val p2 = priceFromY(end.y, priceRange, chartH)
            Drawing.TrendLine(
                id = "tl_$ts", startPrice = p1, endPrice = p2,
                startTimeMs = candleTs(start.x), endTimeMs = candleTs(end.x)
            )
        }
        DrawingToolType.RECTANGLE -> {
            val top = priceFromY(min(start.y, end.y), priceRange, chartH)
            val bot = priceFromY(max(start.y, end.y), priceRange, chartH)
            Drawing.Rectangle(
                id = "r_$ts", topPrice = top, bottomPrice = bot,
                startTimeMs = candleTs(min(start.x, end.x)),
                endTimeMs = candleTs(max(start.x, end.x))
            )
        }
        DrawingToolType.RULER -> {
            val p1 = priceFromY(start.y, priceRange, chartH)
            val p2 = priceFromY(end.y, priceRange, chartH)
            val t1 = candleTs(start.x); val t2 = candleTs(end.x)
            Drawing.TrendLine(
                id = "ruler_$ts", startPrice = p1, endPrice = p2,
                startTimeMs = t1, endTimeMs = t2,
                color = androidx.compose.ui.graphics.Color(0xFFFFEB00),
                label = rulerLabel(p1, p2, t1, t2, priceFormatter)
            )
        }
        else -> null
    }
}
