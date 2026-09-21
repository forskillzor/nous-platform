/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.api.market.model.liquidation.LiquidationOrder
import com.aandios.nous.feature.chart.model.CandleMetrics
import com.aandios.nous.feature.chart.model.ChartLayout
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.rendering.drawChart
import com.aandios.nous.feature.chart.rendering.drawCrosshair
import com.aandios.nous.feature.chart.rendering.drawFootprintChart
import com.aandios.nous.feature.chart.rendering.drawFootprintPopup
import com.aandios.nous.feature.chart.rendering.drawPriceScale
import com.aandios.nous.feature.chart.rendering.drawTimeScale
import com.aandios.nous.feature.chart.tools.DrawingHistory
import com.aandios.nous.feature.chart.tools.DrawingRenderer.drawDrawings
import com.aandios.nous.feature.chart.tools.DrawingToolType
import com.aandios.nous.feature.chart.ui.ChartConfig
import com.aandios.nous.feature.chart.ui.DefaultChartConfig
import com.aandios.nous.feature.chart.utils.calculateCandleMetrics
import com.aandios.nous.feature.chart.utils.calculateMaxScroll
import com.aandios.nous.feature.chart.utils.calculatePriceRangeWithCurrentPrice
import com.aandios.nous.feature.chart.utils.calculateZoomScrollOffset
import kotlin.math.max

/**
 * Композабл, управляющий интерактивным поведением графика: скролл, зум, crosshair,
 * ленивая загрузка истории, расчёт layout и рендеринг холста.
 *
 * Выделен из CandleStickChart для соблюдения SRP — сам график становится тонкой
 * обёрткой, а вся сложная логика взаимодействия находится здесь.
 */
@Composable
fun CandleStickChartInteraction(
    candles: List<Candle>,
    currentPrice: Float? = null,
    modifier: Modifier = Modifier,
    config: ChartConfig = DefaultChartConfig,
    crosshairEnabled: Boolean = false,
    onNeedMoreHistory: () -> Unit = {},
    historyLoadCount: Int = 0,
    hasMoreHistory: Boolean = true,
    footprintCandles: List<FootprintCandle>? = null,
    liquidationOrders: List<LiquidationOrder> = emptyList(),
    indicatorRenderers: List<DrawScope.(Rect, List<Candle>, PriceRange, Float, Float) -> Unit> = emptyList(),
    indicatorHeightDp: Dp = 80.dp,
    drawingHistory: DrawingHistory? = null,
    activeDrawingTool: DrawingToolType = DrawingToolType.NONE,
    onActiveDrawingToolChange: (DrawingToolType) -> Unit = {},
    initialZoomLevel: Float = 1f,
    onZoomChange: ((Float) -> Unit)? = null,
) {
    if (candles.isEmpty()) return

    var mousePosition by remember { mutableStateOf<Offset?>(null) }
    var isCrosshairVisible by remember { mutableStateOf(false) }
    var scrollOffset by remember { mutableFloatStateOf(0f) }
    var zoomLevel by remember { mutableFloatStateOf(initialZoomLevel) }
    var chartWidthPx by remember { mutableFloatStateOf(0f) }
    var maxScroll by remember { mutableFloatStateOf(0f) }
    var isCtrlPressed by remember { mutableStateOf(false) }
    var isAltPressed by remember { mutableStateOf(false) }
    // Alt+hover popup position for footprint
    var footprintHoverPos by remember { mutableStateOf<Offset?>(null) }
    // Актуальный список свечей для обработчиков жестов (pointerInput не перезапускается)
    val currentCandles by rememberUpdatedState(candles)
    val maxScrollLeft = 300f
    val maxZoom = if (footprintCandles != null) 30f else 4f
    val minZoom = 0.05f
    val zoomStep = 1.25f

    // TextMeasurer для измерения текста
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* no-op: make composable focusable for onKeyEvent */ }
            .onKeyEvent { event ->
                when {
                    event.key == Key.CtrlLeft || event.key == Key.CtrlRight -> {
                        isCtrlPressed = event.type == KeyEventType.KeyDown
                        true
                    }
                    event.key == Key.AltLeft || event.key == Key.AltRight -> {
                        isAltPressed = event.type == KeyEventType.KeyDown
                        true
                    }
                    // Undo/Redo
                    event.key == Key.Z && isCtrlPressed && event.type == KeyEventType.KeyDown -> {
                        drawingHistory?.undo(); true
                    }
                    event.key == Key.Y && isCtrlPressed && event.type == KeyEventType.KeyDown -> {
                        drawingHistory?.redo(); true
                    }
                    else -> false
                }
            }
            // Обработка жестов: pan (crosshair off) или crosshair (crosshair on)
            .pointerInput(crosshairEnabled) {
                if (crosshairEnabled) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isCrosshairVisible = true
                        mousePosition = down.position
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                isCrosshairVisible = true
                                mousePosition = change.position
                                change.consume()
                            } else {
                                change.consume()
                                break
                            }
                        } while (true)
                    }
                } else {
                    detectDragGestures(
                        onDrag = { change, _ ->
                            val deltaX = change.position.x - change.previousPosition.x
                            scrollOffset = (scrollOffset - deltaX).coerceIn(-maxScrollLeft, maxScroll)
                        },
                    )
                }
            }
            // Зум: без Ctrl — от правого края, с Ctrl — от свечи под курсором
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

                            val mouseX = change.position.x
                            val newScrollOffset = calculateZoomScrollOffset(
                                scrollOffset = scrollOffset,
                                chartWidth = chartWidthPx,
                                mouseX = mouseX,
                                actualFactor = actualFactor,
                                anchorAtMouse = isCtrlPressed,
                            )

                            // maxScroll считаем для НОВОГО зума: кламп по устаревшему значению
                            // ломает якорь (правый край/курсор) при приближении
                            val newMaxScroll = calculateMaxScroll(
                                candleCount = currentCandles.size,
                                candleMetrics = calculateCandleMetrics(newZoom),
                                chartWidth = chartWidthPx,
                            )

                            zoomLevel = newZoom
                            scrollOffset = newScrollOffset.coerceIn(-maxScrollLeft, newMaxScroll)
                            onZoomChange?.invoke(zoomLevel)

                            change.consume()
                        }
                    }
                }
            }
            // Track mouse position for footprint popup (Alt+hover)
            .pointerInput(isAltPressed, footprintCandles) {
                if (footprintCandles != null && isAltPressed) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            footprintHoverPos = change.position
                            change.consume()
                        }
                    }
                } else {
                    footprintHoverPos = null
                }
            }
    ) {
        // Рассчитываем layout графика
        val canvasWidth = maxWidth
        val canvasHeight = maxHeight

        val density = LocalDensity.current

        val layout = remember(config.priceScaleWidth, canvasWidth, canvasHeight, indicatorRenderers.size, indicatorHeightDp) {
            val widthPx = with(density) { canvasWidth.toPx() }
            val heightPx = with(density) { canvasHeight.toPx() }
            val chartPadding = 8f

            val timeScaleHeight = (heightPx * 0.04f).coerceAtLeast(20f).coerceAtMost(40f)

            val priceScaleWidthPx = with(density) {
                config.priceScaleWidth.toPx()
            }

            val indicatorH = with(density) { indicatorHeightDp.toPx() }
            val indicatorTotalH = indicatorH * indicatorRenderers.size

            // Единое Y-пространство для свечей, crosshair и шкалы цен: chartMainArea
            val chartMainArea = Rect(
                left = 0f,
                top = 0f,
                right = widthPx - priceScaleWidthPx - chartPadding,
                bottom = heightPx - timeScaleHeight - indicatorTotalH
            )

            val priceScaleArea = Rect(
                left = widthPx - priceScaleWidthPx,
                top = chartMainArea.top,
                right = widthPx,
                bottom = chartMainArea.bottom
            )

            val timeScaleArea = Rect(
                left = 0f,
                top = heightPx - timeScaleHeight,
                right = widthPx - priceScaleWidthPx - chartPadding,
                bottom = heightPx
            )

            val indicatorAreas = (0 until indicatorRenderers.size).map { i ->
                Rect(
                    left = 0f,
                    top = chartMainArea.bottom + i * indicatorH,
                    right = widthPx - priceScaleWidthPx - chartPadding,
                    bottom = chartMainArea.bottom + (i + 1) * indicatorH
                )
            }

            ChartLayout(
                canvasWidth = widthPx,
                canvasHeight = heightPx,
                priceScaleWidth = priceScaleWidthPx,
                priceScaleArea = priceScaleArea,
                chartPadding = chartPadding,
                timeScaleHeight = timeScaleHeight,
                chartMainArea = chartMainArea,
                timeScaleArea = timeScaleArea,
                indicatorAreas = indicatorAreas
            )
        }

        // Расчет метрик свечей и скролла
        chartWidthPx = layout.chartMainArea.width
        val candleMetrics = remember(zoomLevel) {
            calculateCandleMetrics(zoomLevel)
        }
        val totalW = candleMetrics.width + candleMetrics.spacing
        maxScroll = max(0f, candles.size * totalW - chartWidthPx)

        // Автоскролл к последней свече при добавлении новых (realtime flow или footprint)
        LaunchedEffect(candles.size) {
            if (historyLoadCount == 0) {
                scrollOffset = maxScroll
            }
        }

        // Клиппинг scrollOffset
        val clampedOffset = scrollOffset.coerceIn(-maxScrollLeft, maxScroll)

        // Вычисление видимого диапазона свечей
        val startIdx = (clampedOffset / totalW).toInt().coerceIn(0, max(0, candles.size - 1))
        val endIdx = ((clampedOffset + chartWidthPx) / totalW + 1).toInt().coerceIn(startIdx + 1, candles.size)

        // PriceRange только по видимым свечам (Y-масштаб адаптируется при зум/скролле).
        // Текущая цена НЕ влияет на диапазон: иначе при уходе в историю
        // график вырождается в горизонтальную линию.
        val visibleCandles = remember(startIdx, endIdx, candles) {
            candles.subList(startIdx, endIdx.coerceAtMost(candles.size))
        }
        val priceRange = remember(visibleCandles) {
            calculatePriceRangeWithCurrentPrice(visibleCandles, currentPrice = null)
        }
        // Линия и badge текущей цены рисуются, только если цена попадает в видимый диапазон
        val visibleCurrentPrice = currentPrice?.takeIf { it in priceRange.visibleMin..priceRange.visibleMax }

        // Lazy loading historical candles: когда пользователь скроллит левее первой свечи
        // (clampedOffset < 0) — появляется пустое место, вызываем загрузку истории
        LaunchedEffect(clampedOffset, hasMoreHistory) {
            if (hasMoreHistory && clampedOffset < 0f) {
                onNeedMoreHistory()
            }
        }

        // Коррекция scrollOffset после загрузки исторических свечей
        LaunchedEffect(historyLoadCount, candles.size) {
            if (historyLoadCount > 0) {
                val oldScrollOffset = scrollOffset
                val added = historyLoadCount * totalW
                scrollOffset += added
                scrollOffset = scrollOffset.coerceIn(-maxScrollLeft, maxScroll)
            }
        }

        // Основной Canvas для графика
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            if (footprintCandles != null) {
                drawFootprintChart(
                    candles = footprintCandles,
                    priceRange = priceRange,
                    config = config,
                    chartArea = layout.chartMainArea,
                    textMeasurer = textMeasurer,
                    scrollOffset = clampedOffset,
                    zoomLevel = zoomLevel,
                    visibleStartIndex = startIdx,
                    visibleEndIndex = endIdx,
                )
            } else {
                drawChart(
                    candles = candles,
                    priceRange = priceRange,
                    config = config,
                    chartArea = layout.chartMainArea,
                    currentPrice = visibleCurrentPrice,
                    textMeasurer = textMeasurer,
                    scrollOffset = clampedOffset,
                    zoomLevel = zoomLevel,
                    visibleStartIndex = startIdx,
                    visibleEndIndex = endIdx,
                )
            }

            drawTimeScale(
                candles = candles,
                config = config,
                timeScaleArea = layout.timeScaleArea,
                textMeasurer = textMeasurer,
                scrollOffset = clampedOffset,
                zoomLevel = zoomLevel,
            )

            // Liquidation markers overlay
            if (liquidationOrders.isNotEmpty()) {
                val tfMs = if (candles.size >= 2) candles[1].timestamp - candles[0].timestamp else 3_600_000L
                drawLiquidationMarkers(
                    orders = liquidationOrders,
                    priceRange = priceRange,
                    chartWidth = layout.chartMainArea.width,
                    chartHeight = layout.chartMainArea.height,
                    scrollOffset = clampedOffset,
                    candles = candles,
                    timeframeMs = tfMs.coerceAtLeast(1L),
                    zoomLevel = zoomLevel
                )
            }

            // Indicator panels (below main chart, above timescale)
            layout.indicatorAreas.forEachIndexed { idx, area ->
                indicatorRenderers.getOrNull(idx)?.invoke(this, area, candles, priceRange, clampedOffset, zoomLevel)
                // Separator line below each indicator
                drawLine(
                    color = config.gridColor.copy(alpha = 0.3f),
                    start = Offset(area.left, area.bottom),
                    end = Offset(area.right, area.bottom),
                    strokeWidth = 1f
                )
            }

            // Рисуем шкалу цен
            if (config.showPriceScale) {
                drawPriceScale(
                    priceRange = priceRange,
                    config = config,
                    priceScaleArea = layout.priceScaleArea,
                    currentPrice = visibleCurrentPrice,
                    textMeasurer = textMeasurer
                )

                // Разделительная линия между графиком и шкалой
                drawLine(
                    color = config.gridColor.copy(alpha = 0.5f),
                    start = Offset(layout.priceScaleArea.left, layout.chartMainArea.top),
                    end = Offset(layout.priceScaleArea.left, layout.chartMainArea.bottom),
                    strokeWidth = 1f
                )
            }
            // Alt+hover popup for footprint
            if (footprintCandles != null && isAltPressed && !crosshairEnabled && footprintHoverPos != null) {
                drawFootprintPopup(
                    mousePosition = footprintHoverPos!!,
                    candles = footprintCandles,
                    priceRange = priceRange,
                    config = config,
                    chartLayout = layout,
                    textMeasurer = textMeasurer,
                    scrollOffset = clampedOffset,
                    zoomLevel = zoomLevel,
                )
            }
            // Drawings
            drawingHistory?.let { history ->
                drawDrawings(
                    drawings = history.drawings, candles = candles,
                    priceRange = priceRange,
                    chartWidth = layout.chartMainArea.width,
                    chartHeight = layout.chartMainArea.height,
                    scrollOffset = clampedOffset,
                    candleWidth = candleMetrics.width,
                    candleSpacing = candleMetrics.spacing,
                    textMeasurer = textMeasurer,
                )
            }
            // Рисуем перекрестие если crosshair включен и есть позиция курсора
            if (crosshairEnabled && isCrosshairVisible && mousePosition != null) {
                drawCrosshair(
                    mousePosition = mousePosition!!,
                    candles = candles,
                    priceRange = priceRange,
                    config = config,
                    chartLayout = layout,
                    textMeasurer = textMeasurer,
                    scrollOffset = clampedOffset,
                    zoomLevel = zoomLevel,
                )
            }
        }
        // Кнопка «к последней свече» в правом нижнем углу области графика
        val controlsBottomPadding = with(density) {
            (layout.canvasHeight - layout.chartMainArea.bottom).toDp()
        } + 8.dp
        val isAtRightEdge = maxScroll - clampedOffset < 1f
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = config.priceScaleWidth + 10.dp, bottom = controlsBottomPadding)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                .clickable { scrollOffset = maxScroll }
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = "\u21E5",
                color = if (isAtRightEdge) Color(0xFF6B7A88) else Color(0xFF5B9BD5),
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        // Drawing overlay (only when drawing tool active)
        if (activeDrawingTool != DrawingToolType.NONE) {
            DrawingOverlay(
                activeDrawingTool = activeDrawingTool,
                drawingHistory = drawingHistory,
                candles = candles,
                priceRange = priceRange,
                layout = layout,
                scrollOffset = clampedOffset,
                zoomLevel = zoomLevel,
                priceFormatter = config.priceFormatter,
                onToolChange = onActiveDrawingToolChange,
            )
        }
    }
}
