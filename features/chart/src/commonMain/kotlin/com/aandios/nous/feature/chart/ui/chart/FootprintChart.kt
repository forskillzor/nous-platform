/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.feature.chart.model.ChartLayout
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.rendering.drawCrosshairForFootprint
import com.aandios.nous.feature.chart.rendering.drawFootprintChart
import com.aandios.nous.feature.chart.rendering.drawPriceScale
import com.aandios.nous.feature.chart.rendering.drawTimeScaleForFootprint
import com.aandios.nous.feature.chart.rendering.drawCurrentPriceLine
import com.aandios.nous.feature.chart.ui.ChartConfig
import com.aandios.nous.feature.chart.ui.DefaultChartConfig
import com.aandios.nous.feature.chart.utils.calculateCandleMetrics
import com.aandios.nous.feature.chart.utils.calculateMaxScroll
import com.aandios.nous.feature.chart.utils.calculatePriceRangeWithFootprint
import com.aandios.nous.feature.chart.utils.calculateZoomScrollOffset
import com.aandios.nous.feature.chart.utils.priceToY
import kotlin.math.max

@Composable
fun FootprintChart(
    completedCandles: List<FootprintCandle>,
    liveCandle: FootprintCandle? = null,
    currentPrice: Float? = null,
    modifier: Modifier = Modifier,
    config: ChartConfig = DefaultChartConfig,
    crosshairEnabled: Boolean = false,
) {
    val allCandles = remember(completedCandles, liveCandle) {
        if (liveCandle != null) completedCandles + liveCandle else completedCandles
    }
    // Актуальный список для обработчиков жестов (pointerInput не перезапускается)
    val currentAllCandles by rememberUpdatedState(allCandles)

    if (allCandles.isEmpty()) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            Text(text = "No footprint data available.", color = androidx.compose.ui.graphics.Color.Gray, fontSize = 12.sp)
        }
        return
    }

    var mousePosition by remember { mutableStateOf<Offset?>(null) }
    var isCrosshairVisible by remember { mutableStateOf(false) }
    var scrollOffset by remember { mutableFloatStateOf(0f) }
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    var verticalScroll by remember { mutableFloatStateOf(0f) }
    var chartWidthPx by remember { mutableFloatStateOf(0f) }
    var chartHeightPx by remember { mutableFloatStateOf(0f) }
    var maxScroll by remember { mutableFloatStateOf(0f) }
    var isCtrlPressed by remember { mutableStateOf(false) }
    var isAltPressed by remember { mutableStateOf(false) }
    val maxScrollLeft = 300f
    val maxZoom = 10f
    val minZoom = 0.05f
    val zoomStep = 1.25f

    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .onKeyEvent { event ->
                when {
                    event.key == Key.CtrlLeft || event.key == Key.CtrlRight -> { isCtrlPressed = event.type == KeyEventType.KeyDown; true }
                    event.key == Key.AltLeft || event.key == Key.AltRight -> { isAltPressed = event.type == KeyEventType.KeyDown; true }
                    else -> false
                }
            }
            .pointerInput(isAltPressed) {
                detectDragGestures(onDrag = { change, dragAmount ->
                    if (isAltPressed) {
                        verticalScroll = (verticalScroll + dragAmount.y).coerceIn(-chartHeightPx * 2, chartHeightPx * 2)
                    } else if (!crosshairEnabled) {
                        scrollOffset = (scrollOffset - change.position.x + change.previousPosition.x).coerceIn(-maxScrollLeft, maxScroll)
                    }
                })
            }
            .pointerInput(crosshairEnabled) {
                if (crosshairEnabled) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isCrosshairVisible = true; mousePosition = down.position
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) { isCrosshairVisible = true; mousePosition = change.position; change.consume() }
                            else { change.consume(); break }
                        } while (true)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    zoomLevel = 1f; scrollOffset = maxScroll; verticalScroll = 0f
                })
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val sd = change.scrollDelta
                        if (event.type == PointerEventType.Scroll && sd != Offset.Zero) {
                            val factor = if (sd.y < 0) zoomStep else 1f / zoomStep
                            val oldZoom = zoomLevel
                            val newZoom = (oldZoom * factor).coerceIn(minZoom, maxZoom)
                            val actualFactor = newZoom / oldZoom
                            val newScrollOffset = calculateZoomScrollOffset(
                                scrollOffset = scrollOffset,
                                chartWidth = chartWidthPx,
                                mouseX = change.position.x,
                                actualFactor = actualFactor,
                                anchorAtMouse = isCtrlPressed,
                            )
                            val newMaxScroll = calculateMaxScroll(
                                candleCount = currentAllCandles.size,
                                candleMetrics = calculateCandleMetrics(newZoom),
                                chartWidth = chartWidthPx,
                            )
                            zoomLevel = newZoom
                            scrollOffset = newScrollOffset.coerceIn(-maxScrollLeft, newMaxScroll)
                            change.consume()
                        }
                    }
                }
            }
    ) {
        val canvasWidth = maxWidth; val canvasHeight = maxHeight
        val density = LocalDensity.current

        val layout = remember(config.priceScaleWidth, canvasWidth, canvasHeight) {
            val wp = with(density) { canvasWidth.toPx() }; val hp = with(density) { canvasHeight.toPx() }
            val cp = 8f; val tsh = (hp * 0.04f).coerceAtLeast(20f).coerceAtMost(40f)
            val psw = with(density) { config.priceScaleWidth.toPx() }
            val chartMainArea = Rect(0f, 0f, wp - psw - cp, hp - tsh)
            val priceScaleArea = Rect(wp - psw, chartMainArea.top, wp, chartMainArea.bottom)
            val timeScaleArea = Rect(0f, hp - tsh, wp - psw - cp, hp)
            ChartLayout(
                canvasWidth = wp,
                canvasHeight = hp,
                priceScaleWidth = psw,
                priceScaleArea = priceScaleArea,
                chartPadding = cp,
                timeScaleHeight = tsh,
                chartMainArea = chartMainArea,
                timeScaleArea = timeScaleArea
            )
        }

        chartWidthPx = layout.chartMainArea.width; chartHeightPx = layout.chartMainArea.height
        val candleMetrics = remember(zoomLevel) { calculateCandleMetrics(zoomLevel) }
        val totalW = candleMetrics.width + candleMetrics.spacing
        maxScroll = max(0f, allCandles.size * totalW - chartWidthPx)

        LaunchedEffect(allCandles.size) { scrollOffset = maxScroll }

        val clampedOffset = scrollOffset.coerceIn(-maxScrollLeft, maxScroll)
        val startIdx = (clampedOffset / totalW).toInt().coerceIn(0, max(0, allCandles.size - 1))
        val endIdx = ((clampedOffset + chartWidthPx) / totalW + 1).toInt().coerceIn(startIdx + 1, allCandles.size)

        val visibleCandles = remember(startIdx, endIdx, allCandles) {
            allCandles.subList(startIdx, endIdx.coerceAtMost(allCandles.size))
        }
        val basePriceRange = remember(visibleCandles) { calculatePriceRangeWithFootprint(visibleCandles) }
        val shiftedPriceRange = remember(basePriceRange, verticalScroll, chartHeightPx) {
            val ratio = verticalScroll / chartHeightPx.coerceAtLeast(1f)
            val shift = basePriceRange.range * ratio
            PriceRange(basePriceRange.max + shift, basePriceRange.min + shift, basePriceRange.visibleMax + shift, basePriceRange.visibleMin + shift, basePriceRange.range)
        }

        Canvas(
            modifier = Modifier.fillMaxSize().clipToBounds()
        ) {
            drawFootprintChart(
                candles = allCandles, priceRange = shiftedPriceRange, config = config,
                chartArea = layout.chartMainArea, textMeasurer = textMeasurer,
                scrollOffset = clampedOffset, zoomLevel = zoomLevel,
                visibleStartIndex = startIdx, visibleEndIndex = endIdx,
            )

            // Current price line (reused from CandleRenderer)
            if (currentPrice != null) {
                drawCurrentPriceLine(currentPrice, shiftedPriceRange, config, layout.chartMainArea.height, layout.chartMainArea.width)
            }

            // Price scale
            if (config.showPriceScale) {
                drawPriceScale(shiftedPriceRange, config, layout.priceScaleArea, currentPrice, textMeasurer)
                drawLine(config.gridColor.copy(alpha = 0.5f), Offset(layout.priceScaleArea.left, layout.chartMainArea.top), Offset(layout.priceScaleArea.left, layout.chartMainArea.bottom), 1f)
            }

            // Time scale
            drawTimeScaleForFootprint(allCandles, config, layout.timeScaleArea, textMeasurer, clampedOffset, zoomLevel)

            // Crosshair (reused from candlestick)
            if (crosshairEnabled && isCrosshairVisible && mousePosition != null) {
                drawCrosshairForFootprint(
                    mousePosition = mousePosition!!, candles = allCandles, priceRange = shiftedPriceRange,
                    config = config, chartLayout = layout, textMeasurer = textMeasurer,
                    scrollOffset = clampedOffset, zoomLevel = zoomLevel,
                )
            }
        }
    }
}
