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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
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
import com.aandios.nous.feature.chart.model.ChartLayout
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.rendering.drawCrosshair
import com.aandios.nous.feature.chart.rendering.drawCrosshairForFootprint
import com.aandios.nous.feature.chart.rendering.drawCurrentPriceLine
import com.aandios.nous.feature.chart.rendering.drawFootprintPopup
import com.aandios.nous.feature.chart.rendering.drawPriceScale
import com.aandios.nous.feature.chart.rendering.drawTimeScale
import com.aandios.nous.feature.chart.scale.PriceScale
import com.aandios.nous.feature.chart.scale.TimeScale
import com.aandios.nous.feature.chart.tools.Drawing
import com.aandios.nous.feature.chart.tools.DrawingHistory
import com.aandios.nous.feature.chart.tools.DrawingRenderer.drawDrawings
import com.aandios.nous.feature.chart.tools.drawDrawingProjections
import com.aandios.nous.feature.chart.tools.drawDrawingSelection
import com.aandios.nous.feature.chart.tools.drawProjectionPriceTags
import com.aandios.nous.feature.chart.tools.DrawingToolType
import com.aandios.nous.feature.chart.tools.hitTestDrawings
import com.aandios.nous.feature.chart.tools.moveDrawing
import com.aandios.nous.feature.chart.utils.prependedCount
import com.aandios.nous.feature.chart.ui.ChartConfig
import com.aandios.nous.feature.chart.ui.DefaultChartConfig
import kotlin.math.max

/**
 * Единый движок графика: layout, жесты (pan/зум/Ctrl-зум/Alt-вертикаль/double-tap),
 * ленивая история и Canvas-конвейер. Работает с любой [ChartSeries] —
 * свечи и footprint используют один и тот же код скролла/зума/шкал
 * (архитектура в духе lightweight-charts: pane + price scale + time scale + series).
 */
@Composable
fun CandleStickChartInteraction(
    candles: List<Candle>,
    currentPrice: Float? = null,
    modifier: Modifier = Modifier,
    config: ChartConfig = DefaultChartConfig,
    crosshairEnabled: Boolean = false,
    onNeedMoreHistory: () -> Unit = {},
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
    var chartWidthPx by remember { mutableFloatStateOf(0f) }
    var chartHeightPx by remember { mutableFloatStateOf(0f) }
    var verticalScroll by remember { mutableFloatStateOf(0f) }
    var isCtrlPressed by remember { mutableStateOf(false) }
    // Alt+hover popup position for footprint
    var footprintHoverPos by remember { mutableStateOf<Offset?>(null) }
    // Превью фигуры во время рисования / последний диапазон для жестов
    var previewDrawing by remember { mutableStateOf<Drawing?>(null) }
    var currentPriceRange by remember { mutableStateOf<PriceRange>(PriceRange(0f, 0f, 0f, 0f, 0f)) }
    // Выделенный рисунок (клик по фигуре; удаление по Delete/Backspace)
    var selectedDrawingId by remember { mutableStateOf<String?>(null) }
    // Follow-live: следуем за новой свечой, пока пользователь у правого края
    var followLive by remember { mutableStateOf(true) }
    var prevFirstTs by remember { mutableStateOf<Long?>(null) }

    // Шкалы и серия — единая модель для свечей и footprint
    val timeScale = remember { TimeScale(initialZoom = initialZoomLevel) }
    val priceScale = remember { PriceScale() }
    val series: ChartSeries = remember(candles, footprintCandles, config) {
        if (footprintCandles != null) {
            FootprintSeries(skeleton = candles, footprintCandles = footprintCandles, config = config)
        } else {
            CandlestickSeries(skeleton = candles, config = config)
        }
    }

    // Актуальные данные для обработчиков жестов (pointerInput не перезапускается)
    val currentCandles by rememberUpdatedState(candles)

    val zoomStep = 1.25f
    val minZoom = config.minZoom
    val maxZoom = if (footprintCandles != null) config.maxZoomFootprint else config.maxZoom
    // Порог «клик» vs «drag» для выделения рисунков
    val tapThresholdPx = 4f

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
                    // Undo/Redo
                    event.key == Key.Z && isCtrlPressed && event.type == KeyEventType.KeyDown -> {
                        drawingHistory?.undo(); true
                    }
                    event.key == Key.Y && isCtrlPressed && event.type == KeyEventType.KeyDown -> {
                        drawingHistory?.redo(); true
                    }
                    // Удаление выделенного рисунка
                    (event.key == Key.Delete || event.key == Key.Backspace) &&
                        event.type == KeyEventType.KeyDown -> {
                        val id = selectedDrawingId
                        if (id != null && drawingHistory != null) {
                            drawingHistory.drawings.firstOrNull { it.id == id }?.let { drawingHistory.remove(it) }
                            selectedDrawingId = null
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            // Обработка жестов: move/resize рисунков, pan / Alt+вертикаль (footprint) / crosshair.
            // Модификаторы читаются из PointerEvent.keyboardModifiers —
            // не зависят от фокуса (раньше onKeyEvent их «терял» после кликов по тулбару).
            .pointerInput(crosshairEnabled, activeDrawingTool, drawingHistory) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (crosshairEnabled) {
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
                    } else {
                        // 1. Перемещение/изменение размера существующего рисунка
                        val history = drawingHistory
                        if (history != null && activeDrawingTool == DrawingToolType.NONE) {
                            val hitMetrics = timeScale.metrics()
                            val hit = hitTestDrawings(
                                drawings = history.drawings,
                                position = down.position,
                                candles = currentCandles,
                                priceRange = currentPriceRange,
                                chartHeight = chartHeightPx,
                                scrollOffset = timeScale.scrollOffset,
                                candleWidth = hitMetrics.width,
                                candleSpacing = hitMetrics.spacing,
                            )
                            if (hit != null) {
                                var moved = false
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) break
                                    if ((change.position - down.position).getDistance() > tapThresholdPx) {
                                        moved = true
                                    }
                                    if (moved) {
                                        val updated = moveDrawing(
                                            drawing = hit.drawing,
                                            handle = hit.handle,
                                            from = down.position,
                                            to = change.position,
                                            candles = currentCandles,
                                            priceRange = currentPriceRange,
                                            chartHeight = chartHeightPx,
                                            scrollOffset = timeScale.scrollOffset,
                                            candleWidth = hitMetrics.width,
                                            candleSpacing = hitMetrics.spacing,
                                            formatter = config.priceFormatter,
                                        )
                                        history.update(hit.id, updated)
                                    }
                                    change.consume()
                                } while (true)
                                if (moved) {
                                    history.commit()
                                } else {
                                    // Клик без движения — выделяем рисунок
                                    selectedDrawingId = hit.id
                                }
                                return@awaitEachGesture
                            }
                        }
                        // Активный инструмент рисования — жесты обрабатывает DrawingOverlay
                        if (activeDrawingTool != DrawingToolType.NONE) return@awaitEachGesture

                        // 2. Панорамирование / Alt+вертикаль (footprint)
                        var previous = down.position
                        var moved = false
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break

                            if ((change.position - down.position).getDistance() > tapThresholdPx) {
                                moved = true
                            }

                            val alt = event.keyboardModifiers.isAltPressed
                            val deltaX = change.position.x - previous.x
                            val deltaY = change.position.y - previous.y
                            previous = change.position

                            if (alt && footprintCandles != null) {
                                // Вертикальный скролл уровней footprint
                                verticalScroll = (verticalScroll + deltaY)
                                    .coerceIn(-chartHeightPx * 2f, chartHeightPx * 2f)
                            } else {
                                timeScale.panBy(deltaX, currentCandles.size, chartWidthPx)
                                // Ушёл от правого края — открепляем follow; вернулся — прикрепляем
                                followLive = timeScale.isAtLatest(currentCandles.size, chartWidthPx)
                            }
                            change.consume()
                        } while (true)
                        // Клик по пустому месту — снимаем выделение рисунка
                        if (!moved) selectedDrawingId = null
                    }
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
                            timeScale.zoomAt(
                                factor = factor,
                                mouseX = change.position.x,
                                anchorAtMouse = event.keyboardModifiers.isCtrlPressed,
                                chartWidth = chartWidthPx,
                                candleCount = currentCandles.size,
                                minZoom = minZoom,
                                maxZoom = maxZoom,
                            )
                            onZoomChange?.invoke(timeScale.zoomLevel)
                            // Если остались у правого края — follow сохраняется
                            followLive = timeScale.isAtLatest(currentCandles.size, chartWidthPx)
                            change.consume()
                        }
                    }
                }
            }
            // Double-tap: сброс зума/скролла (footprint)
            .pointerInput(footprintCandles) {
                if (footprintCandles != null) {
                    detectTapGestures(onDoubleTap = {
                        timeScale.setZoom(1f)
                        timeScale.scrollToLatest(currentCandles.size, chartWidthPx)
                        verticalScroll = 0f
                        followLive = true
                    })
                }
            }
            // Track mouse position for footprint popup (Alt+hover) —
            // модификатор читаем из pointer-события, чтобы не зависеть от фокуса
            .pointerInput(footprintCandles) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        footprintHoverPos = if (footprintCandles != null && event.keyboardModifiers.isAltPressed) {
                            change.position
                        } else {
                            null
                        }
                    }
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

            // Единое Y-пространство для серии, crosshair и шкалы цен: chartMainArea
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

        // Метрики свечей и скролл — из TimeScale
        chartWidthPx = layout.chartMainArea.width
        chartHeightPx = layout.chartMainArea.height
        val candleMetrics = timeScale.metrics()
        val totalW = candleMetrics.width + candleMetrics.spacing
        val maxScroll = timeScale.maxScroll(candles.size, chartWidthPx)

        // Единый эффект на изменение списка свечей:
        //  * prepend истории (первая свеча стала старше) — удерживаем позицию;
        //  * иначе новая realtime-свеча — следуем за ней, если стоим у правого края.
        val firstCandleTs = candles.firstOrNull()?.timestamp
        LaunchedEffect(candles.size, firstCandleTs) {
            val prepended = prependedCount(candles, prevFirstTs)
            if (prepended > 0) {
                // История: первая свеча стала старше → удерживаем позицию
                timeScale.offsetAfterPrepend(prepended, candles.size, chartWidthPx)
            } else if (followLive) {
                // Новая свеча: следуем безусловно (без tolerance-математики)
                timeScale.scrollToLatest(candles.size, chartWidthPx)
            }
            prevFirstTs = firstCandleTs
        }

        // Автозаполнение вьюпорта: если свечей меньше, чем помещается на экран
        // (например, восстановлен зум «вдаль»), догружаем недостающие
        LaunchedEffect(candles.size, hasMoreHistory) {
            if (hasMoreHistory && candles.isNotEmpty() &&
                timeScale.maxScroll(candles.size, chartWidthPx) == 0f
            ) {
                onNeedMoreHistory()
            }
        }

        // Клиппинг scrollOffset
        val clampedOffset = timeScale.scrollOffset.coerceIn(-TimeScale.MAX_SCROLL_LEFT, maxScroll)

        // Вычисление видимого диапазона свечей
        val startIdx = (clampedOffset / totalW).toInt().coerceIn(0, max(0, candles.size - 1))
        val endIdx = ((clampedOffset + chartWidthPx) / totalW + 1).toInt().coerceIn(startIdx + 1, candles.size)

        // Autoscale по видимым свечам (текущая цена НЕ влияет на диапазон)
        priceScale.fit { series.priceRange(startIdx, endIdx) }
        val priceRange = priceScale.range(verticalScroll, chartHeightPx)
        currentPriceRange = priceRange
        // Линия и badge текущей цены рисуются, только если цена попадает в видимый диапазон
        val visibleCurrentPrice = currentPrice?.takeIf { it in priceRange.visibleMin..priceRange.visibleMax }

        // Lazy loading historical candles: когда пользователь скроллит левее первой свечи
        LaunchedEffect(clampedOffset, hasMoreHistory) {
            if (hasMoreHistory && clampedOffset < 0f) {
                onNeedMoreHistory()
            }
        }

        // Контекст для серии и оверлеев
        val chartCanvas = ChartCanvas(
            layout = layout,
            config = config,
            textMeasurer = textMeasurer,
            scrollOffset = clampedOffset,
            zoomLevel = timeScale.zoomLevel,
            priceRange = priceRange,
            visibleStartIndex = startIdx,
            visibleEndIndex = endIdx,
            currentPrice = visibleCurrentPrice,
        )

        // Выделенный рисунок и проекция линейки/трендовой на шкалу цен
        val selectedDrawing = selectedDrawingId?.let { id ->
            drawingHistory?.drawings?.firstOrNull { it.id == id }
        }
        val projectionTrend = (previewDrawing as? Drawing.TrendLine)
            ?: (selectedDrawing as? Drawing.TrendLine)

        // Основной Canvas для графика
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            // 1. Серия (свечи или footprint)
            series.draw(this, chartCanvas)

            // 2. Пунктирная линия текущей цены в footprint-режиме
            val footprintData = series.footprintCandles
            if (footprintData != null && visibleCurrentPrice != null) {
                drawCurrentPriceLine(
                    currentPrice = visibleCurrentPrice,
                    priceRange = priceRange,
                    config = config,
                    chartHeight = layout.chartMainArea.height,
                    chartWidth = layout.chartMainArea.width,
                    alpha = 0.5f,
                )
            }

            // 3. Шкала времени (по каркасным свечам — общая для обоих режимов)
            drawTimeScale(
                candles = candles,
                config = config,
                timeScaleArea = layout.timeScaleArea,
                textMeasurer = textMeasurer,
                scrollOffset = clampedOffset,
                zoomLevel = timeScale.zoomLevel,
            )

            // 4. Liquidation markers overlay
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
                    zoomLevel = timeScale.zoomLevel
                )
            }

            // 5. Indicator panels (below main chart, above timescale)
            layout.indicatorAreas.forEachIndexed { idx, area ->
                indicatorRenderers.getOrNull(idx)?.invoke(this, area, candles, priceRange, clampedOffset, timeScale.zoomLevel)
                // Separator line below each indicator
                drawLine(
                    color = config.gridColor.copy(alpha = 0.3f),
                    start = Offset(area.left, area.bottom),
                    end = Offset(area.right, area.bottom),
                    strokeWidth = 1f
                )
            }

            // 5b. Проекции линейки/трендовой на шкалу цен (под тиками шкалы)
            if (projectionTrend != null) {
                drawDrawingProjections(
                    drawing = projectionTrend,
                    priceRange = priceRange,
                    layout = layout,
                )
            }

            // 6. Шкала цен
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
            // 6b. Ценовые теги проекций (поверх шкалы)
            if (config.showPriceScale && projectionTrend != null) {
                drawProjectionPriceTags(
                    drawing = projectionTrend,
                    priceRange = priceRange,
                    layout = layout,
                    textMeasurer = textMeasurer,
                    formatter = config.priceFormatter,
                )
            }
            // 7. Alt+hover popup for footprint
            if (footprintData != null && !crosshairEnabled && footprintHoverPos != null) {
                drawFootprintPopup(
                    mousePosition = footprintHoverPos!!,
                    candles = footprintData,
                    priceRange = priceRange,
                    config = config,
                    chartLayout = layout,
                    textMeasurer = textMeasurer,
                    scrollOffset = clampedOffset,
                    zoomLevel = timeScale.zoomLevel,
                )
            }
            // 8. Drawings
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
            // 8b. Превью рисуемой фигуры (линейка с актуальными вычислениями)
            previewDrawing?.let { preview ->
                drawDrawings(
                    drawings = listOf(preview), candles = candles,
                    priceRange = priceRange,
                    chartWidth = layout.chartMainArea.width,
                    chartHeight = layout.chartMainArea.height,
                    scrollOffset = clampedOffset,
                    candleWidth = candleMetrics.width,
                    candleSpacing = candleMetrics.spacing,
                    textMeasurer = textMeasurer,
                )
            }
            // 8c. Ручки выделенного рисунка
            if (selectedDrawing != null) {
                drawDrawingSelection(
                    drawing = selectedDrawing,
                    candles = candles,
                    priceRange = priceRange,
                    chartHeight = layout.chartMainArea.height,
                    chartWidth = layout.chartMainArea.width,
                    scrollOffset = clampedOffset,
                    candleWidth = candleMetrics.width,
                    candleSpacing = candleMetrics.spacing,
                )
            }
            // 9. Crosshair: в footprint-режиме — footprint-панель, в свечах — свечная
            if (crosshairEnabled && isCrosshairVisible && mousePosition != null) {
                if (footprintData != null) {
                    drawCrosshairForFootprint(
                        mousePosition = mousePosition!!,
                        candles = footprintData,
                        priceRange = priceRange,
                        config = config,
                        chartLayout = layout,
                        textMeasurer = textMeasurer,
                        scrollOffset = clampedOffset,
                        zoomLevel = timeScale.zoomLevel,
                    )
                } else {
                    drawCrosshair(
                        mousePosition = mousePosition!!,
                        candles = candles,
                        priceRange = priceRange,
                        config = config,
                        chartLayout = layout,
                        textMeasurer = textMeasurer,
                        scrollOffset = clampedOffset,
                        zoomLevel = timeScale.zoomLevel,
                    )
                }
            }
        }
        // Кнопка «к последней свече» в правом нижнем углу области графика
        val controlsBottomPadding = with(density) {
            (layout.canvasHeight - layout.chartMainArea.bottom).toDp()
        } + 8.dp
        val isAtRightEdge = timeScale.isAtLatest(candles.size, chartWidthPx)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = config.priceScaleWidth + 10.dp, bottom = controlsBottomPadding)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                .clickable {
                    followLive = true
                    timeScale.scrollToLatest(candles.size, chartWidthPx)
                }
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = "\u21E5",
                color = if (isAtRightEdge) Color(0xFF6B7A88) else Color(0xFF5B9BD5),
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        // Сброс превью при деактивации инструмента рисования
        LaunchedEffect(activeDrawingTool) {
            if (activeDrawingTool == DrawingToolType.NONE) previewDrawing = null
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
                zoomLevel = timeScale.zoomLevel,
                priceFormatter = config.priceFormatter,
                onToolChange = { tool ->
                    previewDrawing = null
                    onActiveDrawingToolChange(tool)
                },
                onPreviewChange = { previewDrawing = it },
            )
        }
    }
}
