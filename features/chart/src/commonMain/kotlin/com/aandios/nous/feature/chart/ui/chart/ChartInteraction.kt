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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.text.drawText
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.core.ui.format.plainDecimalString
import com.aandios.nous.feature.chart.model.ChartLayout
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.rendering.drawCrosshair
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
import com.aandios.nous.feature.chart.utils.priceFromY
import com.aandios.nous.feature.chart.utils.priceToY
import com.aandios.nous.feature.chart.ui.ChartConfig
import com.aandios.nous.feature.chart.ui.DefaultChartConfig
import kotlin.math.max
import kotlin.math.roundToLong

/** Высота бейджа ордера (перетаскивание/правка qty). */
private val TRADING_BADGE_HEIGHT = 18.dp

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
    onNeedMoreHistory: () -> Unit = {},
    hasMoreHistory: Boolean = true,
    footprintCandles: List<FootprintCandle>? = null,
    indicatorRenderers: List<DrawScope.(Rect, List<Candle>, PriceRange, Float, Float) -> Unit> = emptyList(),
    indicatorHeightDp: Dp = 80.dp,
    drawingHistory: DrawingHistory? = null,
    activeDrawingTool: DrawingToolType = DrawingToolType.NONE,
    onActiveDrawingToolChange: (DrawingToolType) -> Unit = {},
    initialZoomLevel: Float = 1f,
    onZoomChange: ((Float) -> Unit)? = null,
    tradingOrders: List<com.aandios.nous.api.market.model.trading.Order> = emptyList(),
    tradingPositions: List<com.aandios.nous.api.market.model.trading.Position> = emptyList(),
    /** Базовый актив символа (SOL/BTC) — для бейджа позиции. */
    symbolBase: String? = null,
    /** Inverse (COIN-M): номинал контракта в USD и признак — для PnL. */
    contractSize: Double = 1.0,
    inverse: Boolean = false,
    onChartTradingClick: ((Double) -> Unit)? = null,
    /** Отмена ордера с графика (✕ на бейдже). */
    onCancelTradingOrder: ((com.aandios.nous.api.market.model.trading.Order) -> Unit)? = null,
    /** Перемещение ордера: cancel+replace по новой цене (перетаскивание). */
    onMoveTradingOrder: ((com.aandios.nous.api.market.model.trading.Order, Double) -> Unit)? = null,
    /** Изменение qty ордера: cancel+replace (правка прямо на бейдже). */
    onResizeTradingOrder: ((com.aandios.nous.api.market.model.trading.Order, Double) -> Unit)? = null,
    /** Закрытие позиции по рынку (✕ на бейдже позиции). */
    onCloseTradingPosition: ((com.aandios.nous.api.market.model.trading.Position) -> Unit)? = null,
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
    // Перетаскивание торгового ордера (грип бейджа): ордер рисуется по drag-цене
    var draggingOrderId by remember { mutableStateOf<String?>(null) }
    var draggingOrderPrice by remember { mutableStateOf<Double?>(null) }
    // Измеренные размеры бейджей ордеров (для исключения кликов по ним)
    val orderBadgeSizes = remember { mutableStateMapOf<String, IntSize>() }
    // Границы поля qty внутри бейджа (эта зона не инициирует драг)
    val orderQtyRects = remember { mutableStateMapOf<String, Rect>() }
    // Размеры бейджей позиций (тоже поглощают клики по себе)
    val positionBadgeSizes = remember { mutableStateMapOf<String, IntSize>() }

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
    val currentTradingOrders by rememberUpdatedState(tradingOrders)
    val currentTradingPositions by rememberUpdatedState(tradingPositions)
    // Колбэки Trading, включённого ПОСЛЕ старта pointerInput, иначе жест
    // держит старый null и клик «не размещает» до пересоздания композиции
    val currentOnChartTradingClick by rememberUpdatedState(onChartTradingClick)
    val currentOnMoveTradingOrder by rememberUpdatedState(onMoveTradingOrder)

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
                    // Undo/Redo рисунков: потребляем только когда есть что откатить,
                    // иначе событие уходит наверх (undo/redo сплитов workspace)
                    event.key == Key.Z && isCtrlPressed && event.type == KeyEventType.KeyDown -> {
                        val h = drawingHistory
                        if (h != null && h.canUndo) {
                            h.undo(); true
                        } else false
                    }
                    event.key == Key.Y && isCtrlPressed && event.type == KeyEventType.KeyDown -> {
                        val h = drawingHistory
                        if (h != null && h.canRedo) {
                            h.redo(); true
                        } else false
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
            // Обработка жестов: move/resize рисунков, pan / Alt+вертикаль (footprint).
            // Модификаторы читаются из PointerEvent.keyboardModifiers —
            // не зависят от фокуса (раньше onKeyEvent их «терял» после кликов по тулбару).
            .pointerInput(activeDrawingTool, drawingHistory) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
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

                    // 1b. Перетаскивание торгового ордера: вся поверхность
                    // бейджа (центр чарта), кроме поля qty
                    if (currentOnMoveTradingOrder != null && currentTradingOrders.isNotEmpty()) {
                        val badgeH = with(density) { TRADING_BADGE_HEIGHT.toPx() }
                        val hitOrder = currentTradingOrders.firstOrNull { o ->
                            if (o.price <= 0.0) return@firstOrNull false
                            val size = orderBadgeSizes[o.orderId] ?: return@firstOrNull false
                            val y = priceToY(o.price.toFloat(), currentPriceRange, chartHeightPx)
                            if (y < 0f || y > chartHeightPx) return@firstOrNull false
                            val left = chartWidthPx / 2f - size.width / 2f
                            val top = y - badgeH / 2f
                            val inside = down.position.x in left..(left + size.width.toFloat()) &&
                                down.position.y in top..(top + badgeH)
                            if (!inside) return@firstOrNull false
                            val q = orderQtyRects[o.orderId]
                            val overQty = q != null &&
                                down.position.x in (left + q.left)..(left + q.right) &&
                                down.position.y in (top + q.top)..(top + q.bottom)
                            !overQty
                        }
                        if (hitOrder != null) {
                            var moved = false
                            var newPrice = hitOrder.price
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) break
                                if ((change.position - down.position).getDistance() > tapThresholdPx) {
                                    moved = true
                                }
                                if (moved) {
                                    val p = priceFromY(
                                        y = change.position.y,
                                        priceRange = currentPriceRange,
                                        chartHeight = chartHeightPx,
                                    )
                                    if (p > 0f) {
                                        newPrice = p.toDouble()
                                        draggingOrderId = hitOrder.orderId
                                        draggingOrderPrice = newPrice
                                    }
                                }
                                change.consume()
                            } while (true)
                            draggingOrderId = null
                            draggingOrderPrice = null
                            if (moved) currentOnMoveTradingOrder?.invoke(hitOrder, newPrice)
                            return@awaitEachGesture
                        }
                    }

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
                    // Chart trading: клик по области графика размещает ордер.
                    // Клики по бейджам (грип/qty/✕) ордер не размещают; проверка —
                    // по измеренным размерам бейджей (down.isConsumed не годится:
                    // double-tap детектор footprint потребляет down в каждом клике).
                    if (!moved && currentOnChartTradingClick != null) {
                        val badgeH = with(density) { TRADING_BADGE_HEIGHT.toPx() }
                        val overBadge = currentTradingOrders.any { o ->
                            val size = orderBadgeSizes[o.orderId] ?: return@any false
                            if (o.price <= 0.0) return@any false
                            val y = priceToY(o.price.toFloat(), currentPriceRange, chartHeightPx)
                            val left = chartWidthPx / 2f - size.width / 2f
                            val top = y - badgeH / 2f
                            down.position.x in left..(left + size.width.toFloat()) &&
                                down.position.y in top..(top + badgeH)
                        } || currentTradingPositions.any { p ->
                            val key = positionBadgeKey(p)
                            val size = positionBadgeSizes[key] ?: return@any false
                            if (p.avgPrice <= 0.0) return@any false
                            val y = priceToY(p.avgPrice.toFloat(), currentPriceRange, chartHeightPx)
                            val left = chartWidthPx / 2f - size.width / 2f
                            val top = y - badgeH / 2f
                            down.position.x in left..(left + size.width.toFloat()) &&
                                down.position.y in top..(top + badgeH)
                        }
                        // chartMainArea — Rect(0, 0, chartWidthPx, chartHeightPx)
                        if (!overBadge &&
                            down.position.x >= 0f && down.position.x <= chartWidthPx &&
                            down.position.y >= 0f && down.position.y <= chartHeightPx
                        ) {
                            val price = priceFromY(
                                y = down.position.y,
                                priceRange = currentPriceRange,
                                chartHeight = chartHeightPx,
                            )
                            if (price > 0) currentOnChartTradingClick?.invoke(price.toDouble())
                        }
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
            // Hover-трекинг: crosshair всегда включён (как в TradingView),
            // Alt+hover popup для footprint. Модификатор читаем из pointer-события,
            // чтобы не зависеть от фокуса.
            .pointerInput(footprintCandles) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        when (event.type) {
                            PointerEventType.Move -> {
                                mousePosition = change.position
                                isCrosshairVisible = true
                            }
                            PointerEventType.Exit -> {
                                isCrosshairVisible = false
                                mousePosition = null
                                footprintHoverPos = null
                            }
                            else -> Unit
                        }
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

        // При ресайзе панели/окна ширина вьюпорта меняется: если прижаты
        // к последней свече (followLive) — держимся за неё, иначе свеча
        // «отлипает» и новые данные уходят за правый край.
        LaunchedEffect(chartWidthPx) {
            if (followLive) {
                timeScale.scrollToLatest(candles.size, chartWidthPx)
            }
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

            // 2b. Линии открытых торговых ордеров (chart trading)
            if (tradingOrders.isNotEmpty()) {
                drawTradingOrderLines(
                    orders = tradingOrders,
                    priceRange = priceRange,
                    chartArea = layout.chartMainArea,
                    draggingOrderId = draggingOrderId,
                    draggingPrice = draggingOrderPrice,
                )
            }

            // 2c. Линии позиций (Show positions)
            if (tradingPositions.isNotEmpty()) {
                drawTradingPositionLines(
                    positions = tradingPositions,
                    priceRange = priceRange,
                    chartArea = layout.chartMainArea,
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
            if (footprintData != null && footprintHoverPos != null) {
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
            // 9. Crosshair: всегда включён (hover), единый для свечей и footprint
            if (isCrosshairVisible && mousePosition != null) {
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
        // Бейджи открытых ордеров поверх графика (TradingView-style):
        // по центру чарта на линии цены; драг — вся поверхность, кроме qty.
        if (tradingOrders.isNotEmpty()) {
            tradingOrders.forEach { order ->
                val displayPrice = if (draggingOrderId == order.orderId) {
                    draggingOrderPrice ?: order.price
                } else {
                    order.price
                }
                if (displayPrice <= 0.0) return@forEach
                val y = priceToY(displayPrice.toFloat(), priceRange, layout.chartMainArea.height)
                if (y < 0f || y > layout.chartMainArea.height) return@forEach
                // Контейнер шириной с plot-область (без шкалы цен) — центр
                // бейджа ровно посередине чарта
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .width(with(density) { chartWidthPx.toDp() }),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    TradingOrderBadge(
                        order = order,
                        priceText = config.priceFormatter.formatPrice(displayPrice),
                        onCancel = { onCancelTradingOrder?.invoke(order) },
                        onResize = { qty -> onResizeTradingOrder?.invoke(order, qty) },
                        onQtyBounds = { rect -> orderQtyRects[order.orderId] = rect },
                        modifier = Modifier
                            .offset(y = with(density) { y.toDp() } - TRADING_BADGE_HEIGHT / 2)
                            .height(TRADING_BADGE_HEIGHT)
                            .onSizeChanged { orderBadgeSizes[order.orderId] = it },
                    )
                }
            }
        }
        // Бейджи позиций: сторона, qty, база, вход + живой PnL (тики/USDT)
        if (tradingPositions.isNotEmpty()) {
            tradingPositions.forEach { position ->
                if (position.avgPrice <= 0.0) return@forEach
                val y = priceToY(position.avgPrice.toFloat(), priceRange, layout.chartMainArea.height)
                if (y < 0f || y > layout.chartMainArea.height) return@forEach
                // Живая mark-цена графика (PnL обновляется на каждом тике)
                val mark = currentPrice?.toDouble()
                    ?: position.markPrice.takeIf { it > 0.0 }
                    ?: 0.0
                val dir = if (position.side == TradeSide.BUY) 1.0 else -1.0
                val pnl = if (mark > 0.0) {
                    if (inverse) {
                        position.quantity * contractSize * (mark - position.avgPrice) / position.avgPrice * dir
                    } else {
                        (mark - position.avgPrice) * position.quantity * dir
                    }
                } else {
                    0.0
                }
                val pnlLabel = if (mark > 0.0) {
                    val sign = if (pnl >= 0) "+" else ""
                    val deltaText = if (inverse) {
                        val coinDelta = position.quantity * contractSize *
                            (1.0 / position.avgPrice - 1.0 / mark) * dir
                        plainDecimalString(kotlin.math.round(coinDelta * 1e8) / 1e8)
                    } else {
                        // Изменение цены в базовых значениях (без «тиков»)
                        config.priceFormatter.formatPrice((mark - position.avgPrice) * dir)
                    }
                    "$sign$deltaText  $sign${fmtPnl2(pnl)} USDT"
                } else null
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .width(with(density) { chartWidthPx.toDp() }),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    TradingPositionBadge(
                        position = position,
                        baseText = symbolBase,
                        priceText = config.priceFormatter.formatPrice(position.avgPrice),
                        pnlLabel = pnlLabel,
                        pnlUp = pnl >= 0,
                        onClose = onCloseTradingPosition?.let { cb -> { cb(position) } },
                        modifier = Modifier
                            .offset(y = with(density) { y.toDp() } - TRADING_BADGE_HEIGHT / 2)
                            .height(TRADING_BADGE_HEIGHT)
                            .onSizeChanged { positionBadgeSizes[positionBadgeKey(position)] = it },
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

/**
 * Пунктирные линии открытых ордеров (chart trading). Бейджи (перетаскивание,
 * qty, отмена) — Compose-оверлей [TradingOrderBadge]; здесь только линии.
 * Во время перетаскивания линия рисуется по drag-цене.
 */
private fun DrawScope.drawTradingOrderLines(
    orders: List<Order>,
    priceRange: PriceRange,
    chartArea: Rect,
    draggingOrderId: String?,
    draggingPrice: Double?,
) {
    orders.forEach { order ->
        val price = if (order.orderId == draggingOrderId && draggingPrice != null) {
            draggingPrice
        } else {
            order.price
        }
        if (price <= 0.0) return@forEach
        val y = priceToY(price.toFloat(), priceRange, chartArea.height)
        if (y < 0f || y > chartArea.height) return@forEach

        val isBuy = order.side == OrderSide.BUY
        val color = if (isBuy) Color(0xFF26A69A) else Color(0xFFEF5350)
        val dash = 8f

        drawLine(
            color = color.copy(alpha = 0.8f),
            start = Offset(chartArea.left, chartArea.top + y),
            end = Offset(chartArea.right, chartArea.top + y),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
        )
    }
}

/** Сплошные линии позиций по средней цене входа (Show positions). */
private fun DrawScope.drawTradingPositionLines(
    positions: List<com.aandios.nous.api.market.model.trading.Position>,
    priceRange: PriceRange,
    chartArea: Rect,
) {
    positions.forEach { position ->
        if (position.avgPrice <= 0.0) return@forEach
        val y = priceToY(position.avgPrice.toFloat(), priceRange, chartArea.height)
        if (y < 0f || y > chartArea.height) return@forEach
        val isBuy = position.side == TradeSide.BUY
        val color = if (isBuy) Color(0xFF26A69A) else Color(0xFFEF5350)
        drawLine(
            color = color.copy(alpha = 0.95f),
            start = Offset(chartArea.left, chartArea.top + y),
            end = Offset(chartArea.right, chartArea.top + y),
            strokeWidth = 1.5f,
        )
    }
}

/**
 * Бейдж открытого ордера (как в MEXC/TradingView): грип для перетаскивания,
 * «Open Long/Short {price}», редактируемое qty и ✕ отмены.
 */
@Composable
private fun TradingOrderBadge(
    order: Order,
    priceText: String,
    onCancel: () -> Unit,
    onResize: (Double) -> Unit,
    onQtyBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isBuy = order.side == OrderSide.BUY
    val color = if (isBuy) Color(0xFF26A69A) else Color(0xFFEF5350)
    // reduce-only закрывает позицию со стороны ордера, но бейдж пишет, ЧТО
    // закрываем: SELL reduce-only = Close Long, BUY reduce-only = Close Short
    val longTitle = if (order.reduceOnly) !isBuy else isBuy
    val title = buildString {
        append(if (order.reduceOnly) "Close " else "Open ")
        append(if (longTitle) "Long " else "Short ")
        append(priceText)
    }

    val initialQty = remember(order.orderId) { trimQtyText(order.quantity) }
    var qtyText by remember(order.orderId) { mutableStateOf(initialQty) }
    var committed by remember(order.orderId) { mutableStateOf(false) }

    fun commit() {
        if (committed) return
        val q = qtyText.toDoubleOrNull()?.takeIf { it > 0 } ?: return
        if (q == order.quantity) return
        committed = true
        onResize(q)
    }

    Row(
        modifier = modifier
            .background(color, RoundedCornerShape(2.dp))
            // Поглощаем клики по бейджу, чтобы клик не размещал новый ордер
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GripDots(modifier = Modifier.padding(start = 4.dp, end = 3.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(end = 4.dp),
        )
        BasicTextField(
            value = qtyText,
            onValueChange = { qtyText = it },
            singleLine = true,
            textStyle = TextStyle(
                color = Color(0xFF1A1A1A),
                fontSize = 10.sp,
                lineHeight = 11.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(color),
            modifier = Modifier
                .width(44.dp)
                .background(Color.White, RoundedCornerShape(2.dp))
                .padding(horizontal = 3.dp, vertical = 1.dp)
                .onGloballyPositioned { coords ->
                    // Границы поля qty внутри бейджа — исключены из зоны драга
                    onQtyBounds(
                        Rect(
                            offset = coords.positionInParent(),
                            size = coords.size.toSize(),
                        )
                    )
                }
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Enter || event.key == Key.NumPadEnter)
                    ) {
                        commit()
                        true
                    } else {
                        false
                    }
                }
                .onFocusChanged { state -> if (!state.isFocused) commit() },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) { inner() }
            },
        )
        Text(
            text = "✕",
            color = Color.White,
            fontSize = 11.sp,
            lineHeight = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onCancel() }
                .padding(horizontal = 5.dp),
        )
    }
}

/** Грип перетаскивания (две колонки точек). */
@Composable
private fun GripDots(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 6.dp, height = 10.dp)) {
        val r = 0.9f * density
        val step = size.height / 3f
        val xs = listOf(size.width * 0.25f, size.width * 0.75f)
        val ys = listOf(step * 0.5f, step * 1.5f, step * 2.5f)
        xs.forEach { x ->
            ys.forEach { y ->
                drawCircle(Color.White.copy(alpha = 0.9f), radius = r, center = Offset(x, y))
            }
        }
    }
}

private fun trimQtyText(v: Double): String = plainDecimalString(v)

private fun positionBadgeKey(p: com.aandios.nous.api.market.model.trading.Position): String =
    "pos:${p.positionId ?: p.avgPrice}"

/**
 * Бейдж позиции: «Short 10 SOL 120.75» + живой PnL (тики и USDT) на тёмной
 * плашке (светлее фона графика), зелёный/красный по знаку; ✕ — закрыть
 * по рынку (когда trading включён).
 */
@Composable
private fun TradingPositionBadge(
    position: com.aandios.nous.api.market.model.trading.Position,
    baseText: String?,
    priceText: String,
    pnlLabel: String?,
    pnlUp: Boolean,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val isBuy = position.side == TradeSide.BUY
    val sideColor = if (isBuy) Color(0xFF26A69A) else Color(0xFFEF5350)
    val pnlColor = if (pnlUp) Color(0xFF26A69A) else Color(0xFFEF5350)
    val title = buildString {
        append(if (isBuy) "Long " else "Short ")
        append(trimQtyText(position.quantity))
        if (!baseText.isNullOrBlank()) {
            append(" ")
            append(baseText)
        }
        append(" ")
        append(priceText)
    }
    Row(
        modifier = modifier
            .background(sideColor, RoundedCornerShape(2.dp))
            // Поглощаем клики по бейджу, чтобы клик не размещал новый ордер
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp, end = if (pnlLabel != null) 3.dp else 6.dp),
        )
        pnlLabel?.let { label ->
            Box(
                modifier = Modifier
                    .background(Color(0xFF1B222B), RoundedCornerShape(2.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = label,
                    color = pnlColor,
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
        onClose?.let { close ->
            Text(
                text = "✕",
                color = Color.White,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { close() }
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

/** USDT-значение PnL с фиксированными 2 знаками (commonMain, без String.format). */
private fun fmtPnl2(v: Double): String {
    val rounded = kotlin.math.round(v * 100.0) / 100.0
    val s = rounded.toString()
    val neg = s.startsWith("-")
    val body = if (neg) s.substring(1) else s
    val parts = body.split(".")
    val intPart = parts[0]
    val decPart = if (parts.size > 1) parts[1].padEnd(2, '0').take(2) else "00"
    return (if (neg) "-" else "") + intPart + "." + decPart
}