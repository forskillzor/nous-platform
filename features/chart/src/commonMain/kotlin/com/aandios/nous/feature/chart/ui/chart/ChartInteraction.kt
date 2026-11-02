/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui.chart
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.Position
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.core.ui.format.plainDecimalString
import com.aandios.nous.feature.chart.model.ChartLayout
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.rendering.*
import com.aandios.nous.feature.chart.scale.PriceScale
import com.aandios.nous.feature.chart.scale.TimeScale
import com.aandios.nous.feature.chart.tools.*
import com.aandios.nous.feature.chart.tools.DrawingRenderer.drawDrawings
import com.aandios.nous.feature.chart.ui.ChartConfig
import com.aandios.nous.feature.chart.ui.DefaultChartConfig
import com.aandios.nous.feature.chart.utils.prependedCount
import com.aandios.nous.feature.chart.utils.priceFromY
import com.aandios.nous.feature.chart.utils.priceToY
import kotlin.math.max

/** Высота бейджа ордера (перетаскивание/правка qty). */
private val TRADING_BADGE_HEIGHT = 18.dp

/** Границы вертикального зума цены (Alt+scroll): 1 = autoscale. */
private const val MIN_VERTICAL_ZOOM = 0.1f
private const val MAX_VERTICAL_ZOOM = 10f

/** Окно двойного клика для сброса зумов. */
private const val DOUBLE_CLICK_TIMEOUT_MS = 400L

/** Превью цены TP/SL при драге (ключ привязан к ордеру/позиции). */
private data class TpSlPreview(
    val key: String,
    val isTakeProfit: Boolean,
    val price: Double,
)

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
    overlayRenderers: List<DrawScope.(Rect, List<Candle>, PriceRange, Float, Float) -> Unit> = emptyList(),
    indicatorRenderers: List<DrawScope.(Rect, List<Candle>, PriceRange, Float, Float) -> Unit> = emptyList(),
    indicatorHeightsDp: List<Dp> = emptyList(),
    indicatorValueRanges: List<Pair<Float, Float>?> = emptyList(),
    indicatorTabsHeightDp: Dp = 18.dp,
    indicatorHeaderHeightDp: Dp = 18.dp,
    onLayoutChanged: ((ChartLayout) -> Unit)? = null,
    onVisibleTimeRangeChanged: ((Long, Long) -> Unit)? = null,
    onVisibleCandleRangeChanged: ((Int, Int) -> Unit)? = null,
    drawingHistory: DrawingHistory? = null,
    activeDrawingTool: DrawingToolType = DrawingToolType.NONE,
    onActiveDrawingToolChange: (DrawingToolType) -> Unit = {},
    initialZoomLevel: Float = 1f,
    onZoomChange: ((Float) -> Unit)? = null,
    tradingOrders: List<com.aandios.nous.api.market.model.trading.Order> = emptyList(),
    tradingPositions: List<Position> = emptyList(),

    /** Базовый актив символа (SOL/BTC) — для бейджа позиции. */
    symbolBase: String? = null,

    /** Inverse (COIN-M): номинал контракта в USD и признак — для PnL. */
    contractSize: Double = 1.0,
    inverse: Boolean = false,

    /** Тип ордера для `+` (LIMIT/STOP), выбирается ПКМ. */
    chartOrderType: OrderType = OrderType.LIMIT,
    onOrderTypeChanged: (OrderType) -> Unit = {},
    onPlaceTradingOrderAt: ((Double, OrderSide, OrderType) -> Unit)? = null,

    /** Отмена ордера с графика (✕ на бейдже). */
    onCancelTradingOrder: ((Order) -> Unit)? = null,

    /** Перемещение ордера: cancel+replace по новой цене (перетаскивание). */
    onMoveTradingOrder: ((Order, Double) -> Unit)? = null,

    /** Изменение qty ордера: cancel+replace (правка прямо на бейдже). */
    onResizeTradingOrder: ((Order, Double) -> Unit)? = null,

    /** Закрытие позиции по рынку (✕ на бейдже позиции). */
    onCloseTradingPosition: ((Position) -> Unit)? = null,

    /** TP/SL лимитного ордера с графика (drag мини-бейджа; null — снять). */
    onSetTradingOrderTpSl: ((Order, Double?, Double?) -> Unit)? = null,

    /** TP/SL позиции с графика (drag мини-бейджа; null — снять). */
    onSetTradingPositionTpSl: ((Position, Double?, Double?) -> Unit)? = null,
) {
    if (candles.isEmpty()) return

    var mousePosition by remember { mutableStateOf<Offset?>(null) }
    var isCrosshairVisible by remember { mutableStateOf(false) }
    var chartWidthPx by remember { mutableFloatStateOf(0f) }
    var chartHeightPx by remember { mutableFloatStateOf(0f) }
    var verticalScroll by remember { mutableFloatStateOf(0f) }
    var verticalZoom by remember { mutableFloatStateOf(1f) }
    var verticalAnchorPrice by remember { mutableStateOf<Float?>(null) }
    var lastClickTimeMs by remember { mutableStateOf(0L) }
    var lastClickPos by remember { mutableStateOf(Offset.Zero) }
    var isCtrlPressed by remember { mutableStateOf(false) }
    var chartOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    /** Превью TP/SL при драге чипа. */
    var tpSlPreview by remember { mutableStateOf<TpSlPreview?>(null) }
    val tpSlChipRects = remember { mutableMapOf<String, Rect>() }

    /** Alt+hover popup position for footprint */
    var footprintHoverPos by remember { mutableStateOf<Offset?>(null) }

    /** Превью фигуры во время рисования / последний диапазон для жестов */
    var previewDrawing by remember { mutableStateOf<Drawing?>(null) }
    var currentPriceRange by remember { mutableStateOf(PriceRange(0f, 0f, 0f, 0f, 0f)) }

    /** Выделенный рисунок (клик по фигуре; удаление по Delete/Backspace) */
    var selectedDrawingId by remember { mutableStateOf<String?>(null) }

    /** Follow-live: следуем за новой свечой, пока пользователь у правого края */
    var followLive by remember { mutableStateOf(true) }
    var prevFirstTs by remember { mutableStateOf<Long?>(null) }

    /** Перетаскивание торгового ордера (грип бейджа): ордер рисуется по drag-цен */
    var draggingOrderId by remember { mutableStateOf<String?>(null) }
    var draggingOrderPrice by remember { mutableStateOf<Double?>(null) }

    /** Измеренные размеры бейджей ордеров (для исключения кликов по ним) */

    val orderBadgeSizes = remember { mutableStateMapOf<String, IntSize>() }

    /** Границы поля qty внутри бейджа (эта зона не инициирует драг) */
    val orderQtyRects = remember { mutableStateMapOf<String, Rect>() }

    /** Размеры бейджей позиций (тоже поглощают клики по себе) */
    val positionBadgeSizes = remember { mutableStateMapOf<String, IntSize>() }

    /** Шкалы и серия — единая модель для свечей и footprint */
    val timeScale = remember { TimeScale(initialZoom = initialZoomLevel) }
    val priceScale = remember { PriceScale() }
    val designTokens = LocalTerminalDesignTokens.current

    val series: ChartSeries = remember(candles, footprintCandles, config) {
        if (footprintCandles != null) {
            FootprintSeries(skeleton = candles, footprintCandles = footprintCandles, config = config)
        } else {
            CandlestickSeries(skeleton = candles, config = config)
        }
    }

    /** Актуальные данные для обработчиков жестов (pointerInput не перезапускается) */
    val currentCandles by rememberUpdatedState(candles)
    val currentTradingOrders by rememberUpdatedState(tradingOrders)

    val currentOnMoveTradingOrder by rememberUpdatedState(onMoveTradingOrder)

    val zoomStep = 1.25f
    val minZoom = config.minZoom
    val maxZoom = if (footprintCandles != null) config.maxZoomFootprint else config.maxZoom

    /** Порог «клик» vs «drag» для выделения рисунков */
    val tapThresholdPx = 4f

    /** TextMeasurer для измерения текста */
    val textMeasurer = rememberTextMeasurer(cacheSize = 128)
    val density = LocalDensity.current
    val doubleClickSlopPx = with(density) { 6.dp.toPx() }

    val chartFocusRequester = remember { FocusRequester() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { chartOriginInRoot = it.positionInRoot() }
            .focusRequester(chartFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                when {
                    event.key == Key.CtrlLeft || event.key == Key.CtrlRight -> {
                        isCtrlPressed = event.type == KeyEventType.KeyDown
                        true
                    }
                    /** Undo/Redo рисунков: потребляем только когда есть что откатить,
                     * иначе событие уходит наверх (undo/redo сплитов workspace)
                     */
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

            /** Обработка жестов: move/resize рисунков, pan / Alt+вертикаль (footprint).
             * Модификаторы читаются из PointerEvent.keyboardModifiers —
             * не зависят от фокуса (раньше onKeyEvent их «терял» после кликов по тулбару).
             */
            .pointerInput(activeDrawingTool, drawingHistory) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)

                    // TP/SL мини-бейджи обрабатывают драг сами; не панорамируем чарт.
                    if (tpSlChipRects.values.any { down.position in it }) {
                        return@awaitEachGesture
                    }

                    /** Двойной клик сбрасывает всё вертикальное состояние, горизонталь не трогаем. */
                    val nowMs = down.uptimeMillis
                    val isDoubleClick = lastClickTimeMs != 0L &&
                        nowMs - lastClickTimeMs in 0..DOUBLE_CLICK_TIMEOUT_MS &&
                        (down.position - lastClickPos).getDistance() <= doubleClickSlopPx
                    if (isDoubleClick) {
                        lastClickTimeMs = 0L
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                            if (event.changes.none { it.pressed }) break
                        } while (true)
                        verticalScroll = 0f
                        verticalZoom = 1f
                        verticalAnchorPrice = null
                        chartFocusRequester.requestFocus()
                        return@awaitEachGesture
                    }

                    /** 1. Перемещение/изменение размера существующего рисунка */
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
                                chartFocusRequester.requestFocus()
                            }
                            return@awaitEachGesture
                        }
                    }
                    // Активный инструмент рисования — жесты обрабатывает DrawingOverlay
                    if (activeDrawingTool != DrawingToolType.NONE) return@awaitEachGesture

                    /** 1b. Перетаскивание торгового ордера: вся поверхность
                     * бейджа (центр чарта), кроме поля qty
                     */
                    if (currentOnMoveTradingOrder != null && currentTradingOrders.isNotEmpty()) {
                        val badgeH = with(density) { TRADING_BADGE_HEIGHT.toPx() }
                        val hitOrder = currentTradingOrders.firstOrNull { o ->
                            if (o.price <= 0.0 || o.isTpSl) return@firstOrNull false
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

                    /** 2. Панорамирование / Alt+вертикаль (и свечи, и footprint) */
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

                        if (alt) {
                            // Единая вертикальная панорама для свечей и footprint
                            verticalScroll = (verticalScroll + deltaY)
                                .coerceIn(-chartHeightPx * 2f, chartHeightPx * 2f)
                        } else {
                            timeScale.panBy(deltaX, currentCandles.size, chartWidthPx)
                            // Ушёл от правого края — открепляем follow; вернулся — прикрепляем
                            followLive = timeScale.isAtLatest(currentCandles.size, chartWidthPx)
                        }
                        change.consume()
                    } while (true)
                    // Клик по пустому месту — снимаем выделение рисунка.
                    if (!moved) selectedDrawingId = null
                    // Кандидат на двойной клик: допускаем небольшое дрожание мыши,
                    // иначе быстрый double-click иногда «съедается» микропаном.
                    if ((previous - down.position).getDistance() <= doubleClickSlopPx) {
                        lastClickTimeMs = down.uptimeMillis
                        lastClickPos = down.position
                    } else {
                        lastClickTimeMs = 0L
                    }
                }
            }
            // Зум: без Ctrl — от правого края, с Ctrl — от свечи под курсором.
            // Alt+scroll — вертикальный зум цены вокруг курсора (свечи и footprint).
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val sd = change.scrollDelta
                        if (event.type == PointerEventType.Scroll && sd != Offset.Zero) {
                            val factor = if (sd.y < 0) zoomStep else 1f / zoomStep
                            if (event.keyboardModifiers.isAltPressed) {
                                val yRatio = if (chartHeightPx > 0f) {
                                    (change.position.y / chartHeightPx).coerceIn(0f, 1f)
                                } else {
                                    0.5f
                                }
                                // Цена под курсором в текущем (уже масштабированном) виде
                                val currentRange = priceScale.range(
                                    verticalScroll = verticalScroll,
                                    chartHeight = chartHeightPx,
                                    verticalZoom = verticalZoom,
                                    anchorPrice = verticalAnchorPrice,
                                )
                                verticalAnchorPrice = currentRange.visibleMax - yRatio * currentRange.range
                                verticalZoom = (verticalZoom * factor)
                                    .coerceIn(MIN_VERTICAL_ZOOM, MAX_VERTICAL_ZOOM)
                            } else {
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
                            }
                            change.consume()
                        }
                    }
                }
            }
            /** Hover-трекинг: crosshair всегда включён (как в TradingView),
             * Alt+hover popup для footprint. Модификатор читаем из pointer-события,
             * чтобы не зависеть от фокуса.
             */
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

        val layout =
            remember(
                config.priceScaleWidth,
                canvasWidth,
                canvasHeight,
                indicatorRenderers.size,
                indicatorHeightsDp,
                indicatorTabsHeightDp,
                indicatorHeaderHeightDp,
            ) {
                val widthPx = with(density) { canvasWidth.toPx() }
                val heightPx = with(density) { canvasHeight.toPx() }
                val chartPadding = 8f
                val plotRight = widthPx - with(density) { config.priceScaleWidth.toPx() } - chartPadding

                val timeScaleHeight = (heightPx * 0.04f).coerceAtLeast(20f).coerceAtMost(40f)
                val priceScaleWidthPx = with(density) { config.priceScaleWidth.toPx() }

                val paneCount = indicatorRenderers.size
                val requestTabsH = if (paneCount == 0) 0f else with(density) { indicatorTabsHeightDp.toPx() }
                val requestHeaderH = if (paneCount == 0) 0f else with(density) { indicatorHeaderHeightDp.toPx() }
                val minPlotH = with(density) { 24.dp.toPx() }
                val requestedPlotHeights = List(paneCount) { index ->
                    with(density) { indicatorHeightsDp.getOrElse(index) { 80.dp }.toPx() }
                        .coerceAtLeast(minPlotH)
                }

                // Keep at least ~80dp for the main chart; if panes are too tall,
                // shrink their plot areas proportionally (headers/tabs stay fixed).
                val minChartMainH = with(density) { 80.dp.toPx() }
                val availableChromeH = (heightPx - timeScaleHeight - minChartMainH).coerceAtLeast(0f)
                val fixedChromeH = requestTabsH + requestHeaderH * paneCount
                val requestedPlotTotal = requestedPlotHeights.sum()
                val plotScale = if (requestedPlotTotal <= 0f) {
                    1f
                } else {
                    val availablePlotH = (availableChromeH - fixedChromeH).coerceAtLeast(0f)
                    (availablePlotH / requestedPlotTotal).coerceIn(0f, 1f)
                }
                val effectivePlotHeights = requestedPlotHeights.map { it * plotScale }
                val indicatorChromeH = fixedChromeH + effectivePlotHeights.sum()

                val chartMainArea = Rect(
                    left = 0f,
                    top = 0f,
                    right = plotRight,
                    bottom = (heightPx - timeScaleHeight - indicatorChromeH).coerceAtLeast(0f)
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
                    right = plotRight,
                    bottom = heightPx
                )

                val indicatorTabsArea = if (paneCount == 0) {
                    null
                } else {
                    Rect(
                        left = 0f,
                        top = chartMainArea.bottom,
                        right = plotRight,
                        bottom = chartMainArea.bottom + requestTabsH,
                    )
                }
                val headerAreas = ArrayList<Rect>(paneCount)
                val indicatorAreas = ArrayList<Rect>(paneCount)
                var cursorY = indicatorTabsArea?.bottom ?: chartMainArea.bottom
                for (index in 0 until paneCount) {
                    headerAreas += Rect(
                        left = 0f,
                        top = cursorY,
                        right = plotRight,
                        bottom = cursorY + requestHeaderH,
                    )
                    cursorY += requestHeaderH
                    indicatorAreas += Rect(
                        left = 0f,
                        top = cursorY,
                        right = plotRight,
                        bottom = cursorY + effectivePlotHeights[index],
                    )
                    cursorY += effectivePlotHeights[index]
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
                    indicatorTabsArea = indicatorTabsArea,
                    indicatorHeaderAreas = headerAreas,
                    indicatorAreas = indicatorAreas,
                )
            }

        val currentOnLayoutChanged by rememberUpdatedState(onLayoutChanged)
        SideEffect { currentOnLayoutChanged?.invoke(layout) }

        // Метрики свечей и скролл — из TimeScale
        chartWidthPx = layout.chartMainArea.width
        chartHeightPx = layout.chartMainArea.height
        val candleMetrics = timeScale.metrics()
        val totalW = candleMetrics.width + candleMetrics.spacing
        val maxScroll = timeScale.maxScroll(candles.size, chartWidthPx)

        /** Единый эффект на изменение списка свечей:
         *   prepend истории (первая свеча стала старше) — удерживаем позицию;
         *   иначе новая realtime-свеча — следуем за ней, если стоим у правого края.
         */
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

        /** При ресайзе панели/окна ширина вьюпорта меняется: если прижаты
         * к последней свече (followLive) — держимся за неё, иначе свеча
         * «отлипает» и новые данные уходят за правый край.
         */
        LaunchedEffect(chartWidthPx) {
            if (followLive) {
                timeScale.scrollToLatest(candles.size, chartWidthPx)
            }
        }

        /** Автозаполнение вьюпорта: если свечей меньше, чем помещается на экран
         * (например, восстановлен зум «вдаль»), догружаем недостающие
         */
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

        val currentOnVisibleTimeRangeChanged by rememberUpdatedState(onVisibleTimeRangeChanged)
        val currentOnVisibleCandleRangeChanged by rememberUpdatedState(onVisibleCandleRangeChanged)
        LaunchedEffect(candles, startIdx, endIdx) {
            if (candles.isNotEmpty() && endIdx > startIdx) {
                currentOnVisibleTimeRangeChanged?.invoke(
                    candles[startIdx].timestamp,
                    candles[endIdx - 1].timestamp,
                )
                currentOnVisibleCandleRangeChanged?.invoke(startIdx, endIdx)
            }
        }

        // Autoscale по видимым свечам (текущая цена НЕ влияет на диапазон).
        // Кэш на (series, startIdx, endIdx): тики/перерисовки без смены данных
        // не пересчитывают диапазон по всем уровням footprint.
        val visibleRange = remember(series, startIdx, endIdx) {
            series.priceRange(startIdx, endIdx)
        }
        priceScale.fit { visibleRange }
        val priceRange = priceScale.range(verticalScroll, chartHeightPx, verticalZoom, verticalAnchorPrice)
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
        val projectionTrends: List<Drawing.TrendLine> = buildList {
            drawingHistory?.drawings
                ?.filterIsInstance<Drawing.TrendLine>()
                ?.filter { it.isRuler }
                ?.let { addAll(it) }

            // Превью/выделенный — только если это НЕ ruler (иначе будет дубль)
            val active = (previewDrawing as? Drawing.TrendLine)
                ?: (selectedDrawing as? Drawing.TrendLine)
            if (active != null && !active.isRuler && none { it.id == active.id }) {
                add(active)
            }
        }
        // Основной Canvas для графика
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            /** 1. Серия (свечи или footprint) */
            series.draw(this, chartCanvas)

            /** 1b. Индикаторы поверх графика (SMA/EMA/Bollinger/VWAP/Keltner) */
            overlayRenderers.forEach { renderer ->
                renderer(this, layout.chartMainArea, candles, priceRange, clampedOffset, timeScale.zoomLevel)
            }

            /** 2. Пунктирная линия текущей цены в footprint-режиме */
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

            /** 2b. Линии открытых торговых ордеров (chart trading) */
            if (tradingOrders.isNotEmpty()) {
                drawTradingOrderLines(
                    orders = tradingOrders,
                    priceRange = priceRange,
                    chartArea = layout.chartMainArea,
                    draggingOrderId = draggingOrderId,
                    draggingPrice = draggingOrderPrice,
                    buyColor = config.candleStyle.bullishColor,
                    sellColor = config.candleStyle.bearishColor,
                )
            }

            /** 2c. Линии позиций (Show positions) */
            if (tradingPositions.isNotEmpty()) {
                drawTradingPositionLines(
                    positions = tradingPositions,
                    priceRange = priceRange,
                    chartArea = layout.chartMainArea,
                    buyColor = config.candleStyle.bullishColor,
                    sellColor = config.candleStyle.bearishColor,
                )
            }

            /** 2d. Превью TP/SL при драге мини-бейджа */
            tpSlPreview?.let { preview ->
                val y = priceToY(preview.price.toFloat(), priceRange, layout.chartMainArea.height)
                if (y in 0f..layout.chartMainArea.height) {
                    drawLine(
                        color = if (preview.isTakeProfit) config.candleStyle.bullishColor
                        else config.candleStyle.bearishColor,
                        start = Offset(layout.chartMainArea.left, layout.chartMainArea.top + y),
                        end = Offset(layout.chartMainArea.right, layout.chartMainArea.top + y),
                        strokeWidth = 1.5f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(6f, 4f),
                        ),
                    )
                }
            }

            // Постоянные dashed-линии установленных TP/SL
            fun drawTpSlLevelLine(price: Double, color: Color) {
                val y = priceToY(price.toFloat(), priceRange, layout.chartMainArea.height)
                if (y !in 0f..layout.chartMainArea.height) return
                drawLine(
                    color = color.copy(alpha = 0.45f),
                    start = Offset(layout.chartMainArea.left, layout.chartMainArea.top + y),
                    end = Offset(layout.chartMainArea.right, layout.chartMainArea.top + y),
                    strokeWidth = 1f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(4f, 4f),
                    ),
                )
            }
            tradingPositions.forEach { p ->
                val prefix = "p:${positionBadgeKey(p)}"
                p.takeProfitPrice?.let {
                    if (tpSlPreview?.key != "$prefix:true") {
                        drawTpSlLevelLine(it, config.candleStyle.bullishColor)
                    }
                }
                p.stopLossPrice?.let {
                    if (tpSlPreview?.key != "$prefix:false") {
                        drawTpSlLevelLine(it, designTokens.colors.warning)
                    }
                }
            }
            tradingOrders.forEach { o ->
                if (o.reduceOnly || o.isTpSl) return@forEach
                val prefix = "o:${o.orderId}"
                o.takeProfitPrice?.let {
                    if (tpSlPreview?.key != "$prefix:true") {
                        drawTpSlLevelLine(it, config.candleStyle.bullishColor)
                    }
                }
                o.stopLossPrice?.let {
                    if (tpSlPreview?.key != "$prefix:false") {
                        drawTpSlLevelLine(it, designTokens.colors.warning)
                    }
                }
            }

            /** 3. Шкала времени (по каркасным свечам — общая для обоих режимов) */
            drawTimeScale(
                candles = candles,
                config = config,
                timeScaleArea = layout.timeScaleArea,
                textMeasurer = textMeasurer,
                scrollOffset = clampedOffset,
                zoomLevel = timeScale.zoomLevel,
            )

            /** 5. Indicator panels (below main chart, above timescale) */
            layout.indicatorAreas.forEachIndexed { idx, area ->
                indicatorRenderers.getOrNull(idx)
                    ?.invoke(this, area, candles, priceRange, clampedOffset, timeScale.zoomLevel)
                // Separator line below each indicator
                drawLine(
                    color = config.gridColor.copy(alpha = 0.3f),
                    start = Offset(area.left, area.bottom),
                    end = Offset(area.right, area.bottom),
                    strokeWidth = 1f
                )
            }

            /** 5b. Right-side value scale for each indicator pane */
            if (config.showPriceScale) {
                layout.indicatorAreas.forEachIndexed { idx, area ->
                    val range = indicatorValueRanges.getOrNull(idx) ?: return@forEachIndexed
                    drawIndicatorValueScale(
                        scaleArea = Rect(
                            left = layout.priceScaleArea.left,
                            top = area.top,
                            right = layout.priceScaleArea.right,
                            bottom = area.bottom,
                        ),
                        rangeMin = range.first,
                        rangeMax = range.second,
                        config = config,
                        textMeasurer = textMeasurer,
                    )
                }
            }

            /** 5c. Проекции линейки/трендовой на шкалу цен (под тиками шкалы) */
            projectionTrends.forEach { trend ->
                drawDrawingProjections(
                    drawing = trend,
                    priceRange = priceRange,
                    layout = layout,
                    projectionColor = config.accentColor,
                )
            }

            /** 6. Шкала цен */
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
            /** 6b. Ценовые теги проекций (поверх шкалы) */
            if (config.showPriceScale && projectionTrends != null) {
                projectionTrends.forEach { trend ->
                    drawProjectionPriceTags(
                        drawing = trend,
                        priceRange = priceRange,
                        layout = layout,
                        textMeasurer = textMeasurer,
                        formatter = config.priceFormatter,
                        startTagColor = config.accentColor,
                    )
                }
            }
            /** 7. Alt+hover popup for footprint */
            if (footprintData != null && footprintHoverPos != null) {
                drawFootprintPopup(
                    mousePosition = footprintHoverPos!!,
                    candles = footprintData,
                    priceRange = priceRange,
                    config = config,
                    chartLayout = layout,
                    textMeasurer = textMeasurer,
                    tokens = designTokens,
                    scrollOffset = clampedOffset,
                    zoomLevel = timeScale.zoomLevel,
                )
            }
            /** 8. Drawings */
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
                    priceFormatter = config.priceFormatter,
                )
            }
            /** 8b. Превью рисуемой фигуры (линейка с актуальными вычислениями) */
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
                    priceFormatter = config.priceFormatter,
                )
            }
            /** 8c. Ручки выделенного рисунка */
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
                    handleColor = config.accentColor,
                )
            }
            /** 9. Crosshair: всегда включён (hover), единый для свечей и footprint.
             * Во время трейдинга горизонтальная линия — цветом будущей стороны.
             */
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
                    indicatorValueRanges = indicatorValueRanges,
                    tradingEnabled = false,
                    currentPrice = currentPrice ?: candles.lastOrNull()?.close,
                )
            }
        }
        val tpSlPricePerPx = if (layout.chartMainArea.height > 0f) {
            (priceRange.range / layout.chartMainArea.height).toDouble()
        } else {
            0.0
        }
        val tpSlLevelGapPx = with(density) { 6.dp.toPx() }
        /** Бейджи открытых ордеров поверх графика (TradingView-style):
         * по центру чарта на линии цены; драг — вся поверхность, кроме qty.
         */
        if (tradingOrders.isNotEmpty()) {
            tradingOrders.forEach { order ->
                if (order.isTpSl && order.orderType == OrderType.STOP) return@forEach
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
                        sideColor = if (order.side == OrderSide.BUY) config.candleStyle.bullishColor
                        else config.candleStyle.bearishColor,
                        takeProfitPrice = order.takeProfitPrice,
                        stopLossPrice = order.stopLossPrice,
                        pricePerPx = tpSlPricePerPx,
                        tpColor = config.candleStyle.bullishColor,
                        slColor = config.candleStyle.bearishColor,
                        surfaceColor = config.backgroundColor.copy(alpha = 0.92f),
                        gridColor = config.gridColor,
                        onTpSlPreview = { isTp, price ->
                            tpSlPreview = price?.let {
                                TpSlPreview("o:${order.orderId}:$isTp", isTp, it)
                            }
                        },
                        onTpSlCommit = { isTp, price ->
                            onSetTradingOrderTpSl?.invoke(
                                order,
                                if (isTp) price else null,
                                if (isTp) null else price,
                            )
                        },
                        onTpSlRect = { isTp, rect ->
                            val key = "o:${order.orderId}:$isTp:btn"
                            tpSlChipRects[key] = Rect(
                                left = rect.left - chartOriginInRoot.x,
                                top = rect.top - chartOriginInRoot.y,
                                right = rect.right - chartOriginInRoot.x,
                                bottom = rect.bottom - chartOriginInRoot.y,
                            )
                        },
                        onTpSlRectRemoved = { isTp -> tpSlChipRects.remove("o:${order.orderId}:$isTp:btn") },
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
                        sideColor = if (position.side == TradeSide.BUY) config.candleStyle.bullishColor
                        else config.candleStyle.bearishColor,
                        pnlUpColor = config.currentPriceColor,
                        pnlDownColor = config.candleStyle.bearishColor,
                        pnlPlateColor = config.pnlPlateColor,
                        takeProfitPrice = position.takeProfitPrice,
                        stopLossPrice = position.stopLossPrice,
                        pricePerPx = tpSlPricePerPx,
                        tpColor = config.candleStyle.bullishColor,
                        slColor = config.candleStyle.bearishColor,
                        surfaceColor = config.backgroundColor.copy(alpha = 0.92f),
                        onTpSlPreview = { isTp, price ->
                            tpSlPreview = price?.let {
                                TpSlPreview("p:${positionBadgeKey(position)}:$isTp", isTp, it)
                            }
                        },
                        onTpSlCommit = { isTp, price ->
                            onSetTradingPositionTpSl?.invoke(
                                position,
                                if (isTp) price else null,
                                if (isTp) null else price,
                            )
                        },
                        onTpSlRect = { isTp, rect ->
                            val key = "p:${positionBadgeKey(position)}:$isTp:btn"
                            tpSlChipRects[key] = Rect(
                                left = rect.left - chartOriginInRoot.x,
                                top = rect.top - chartOriginInRoot.y,
                                right = rect.right - chartOriginInRoot.x,
                                bottom = rect.bottom - chartOriginInRoot.y,
                            )
                        },
                        onTpSlRectRemoved = { isTp ->
                            tpSlChipRects.remove("p:${positionBadgeKey(position)}:$isTp:btn")
                        },
                        onClose = onCloseTradingPosition?.let { cb -> { cb(position) } },
                        modifier = Modifier
                            .offset(y = with(density) { y.toDp() } - TRADING_BADGE_HEIGHT / 2)
                            .height(TRADING_BADGE_HEIGHT)
                            .onSizeChanged { positionBadgeSizes[positionBadgeKey(position)] = it },
                    )
                }
            }
        }
        // Отдельные TP/SL бейджи на ценовых уровнях (тащатся вертикально)
        tradingPositions.forEach { position ->
            if (position.avgPrice <= 0.0) return@forEach
            val prefix = "p:${positionBadgeKey(position)}"
            val isLong = position.side == TradeSide.BUY
            val badgeWidth = positionBadgeSizes[positionBadgeKey(position)]?.width?.toFloat() ?: 0f
            val levelStartX = chartWidthPx / 2f + badgeWidth / 2f + tpSlLevelGapPx
            listOf(true to position.takeProfitPrice, false to position.stopLossPrice).forEach { (isTp, price) ->
                val key = "$prefix:$isTp"
                val preview = tpSlPreview?.takeIf { it.key == key }?.price
                val effectivePrice = preview ?: price ?: return@forEach
                val levelY = priceToY(effectivePrice.toFloat(), priceRange, layout.chartMainArea.height)
                if (levelY < 0f || levelY > layout.chartMainArea.height) return@forEach
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = with(density) { levelStartX.toDp() },
                            y = with(density) { levelY.toDp() } - 6.dp,
                        ),
                ) {
                    TpSlLevelBadge(
                        label = if (isTp) "TP" else "SL",
                        price = effectivePrice,
                        referencePrice = position.avgPrice,
                        isLong = isLong,
                        formatter = config.priceFormatter,
                        color = if (isTp) config.candleStyle.bullishColor else designTokens.colors.warning,
                        background = config.backgroundColor.copy(alpha = 0.92f),
                        enabled = onSetTradingPositionTpSl != null,
                        pricePerPx = tpSlPricePerPx,
                        onPreview = { kind, p ->
                            tpSlPreview = p?.let { TpSlPreview(key, kind, it) }
                        },
                        onCommit = { kind, p ->
                            onSetTradingPositionTpSl?.invoke(
                                position,
                                if (kind) p else null,
                                if (kind) null else p,
                            )
                        },
                        onBounds = { rect ->
                            tpSlChipRects["$key:level"] = Rect(
                                left = rect.left - chartOriginInRoot.x,
                                top = rect.top - chartOriginInRoot.y,
                                right = rect.right - chartOriginInRoot.x,
                                bottom = rect.bottom - chartOriginInRoot.y,
                            )
                        },
                        onBoundsRemoved = { tpSlChipRects.remove("$key:level") },
                    )
                }
            }
        }
        tradingOrders.forEach { order ->
            if (order.price <= 0.0 || order.reduceOnly || order.isTpSl) return@forEach
            val prefix = "o:${order.orderId}"
            val isLong = order.side == OrderSide.BUY
            val badgeWidth = orderBadgeSizes[order.orderId]?.width?.toFloat() ?: 0f
            val levelStartX = chartWidthPx / 2f + badgeWidth / 2f + tpSlLevelGapPx
            listOf(true to order.takeProfitPrice, false to order.stopLossPrice).forEach { (isTp, price) ->
                val key = "$prefix:$isTp"
                val preview = tpSlPreview?.takeIf { it.key == key }?.price
                val effectivePrice = preview ?: price ?: return@forEach
                val levelY = priceToY(effectivePrice.toFloat(), priceRange, layout.chartMainArea.height)
                if (levelY < 0f || levelY > layout.chartMainArea.height) return@forEach
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = with(density) { levelStartX.toDp() },
                            y = with(density) { levelY.toDp() } - 6.dp,
                        ),
                ) {
                    TpSlLevelBadge(
                        label = if (isTp) "TP" else "SL",
                        price = effectivePrice,
                        referencePrice = order.price,
                        isLong = isLong,
                        formatter = config.priceFormatter,
                        color = if (isTp) config.candleStyle.bullishColor else designTokens.colors.warning,
                        background = config.backgroundColor.copy(alpha = 0.92f),
                        enabled = onSetTradingOrderTpSl != null,
                        pricePerPx = tpSlPricePerPx,
                        onPreview = { kind, p ->
                            tpSlPreview = p?.let { TpSlPreview(key, kind, it) }
                        },
                        onCommit = { kind, p ->
                            onSetTradingOrderTpSl?.invoke(
                                order,
                                if (kind) p else null,
                                if (kind) null else p,
                            )
                        },
                        onBounds = { rect ->
                            tpSlChipRects["$key:level"] = Rect(
                                left = rect.left - chartOriginInRoot.x,
                                top = rect.top - chartOriginInRoot.y,
                                right = rect.right - chartOriginInRoot.x,
                                bottom = rect.bottom - chartOriginInRoot.y,
                            )
                        },
                        onBoundsRemoved = { tpSlChipRects.remove("$key:level") },
                    )
                }
            }
        }
        // Кнопка «+» на линии crosshair: тип ордера — из Trading-меню, сторона — по цене.
        val crosshairPrice = mousePosition
            ?.takeIf {
                isCrosshairVisible &&
                    it.x >= layout.chartMainArea.left && it.x <= layout.chartMainArea.right &&
                    it.y >= layout.chartMainArea.top && it.y <= layout.chartMainArea.bottom
            }
            ?.let { priceFromY(it.y - layout.chartMainArea.top, priceRange, layout.chartMainArea.height) }
        if (crosshairPrice != null && crosshairPrice > 0f && onPlaceTradingOrderAt != null) {
            val plusY = mousePosition?.y ?: layout.chartMainArea.top
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = with(density) { layout.chartMainArea.right.toDp() } - 22.dp,
                        y = with(density) { plusY.toDp() } - 9.dp,
                    )
                    .size(18.dp)
                    .background(config.backgroundColor.copy(alpha = 0.92f), RoundedCornerShape(3.dp))
                    .border(1.dp, config.gridColor, RoundedCornerShape(3.dp))
                    .pointerInput(chartOrderType) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Press) {
                                    if (!event.buttons.isSecondaryPressed) {
                                        val price = mousePosition?.let {
                                            priceFromY(
                                                it.y - layout.chartMainArea.top,
                                                priceRange,
                                                layout.chartMainArea.height,
                                            ).toDouble()
                                        }
                                        if (price != null && price > 0.0) {
                                            val lastPrice = currentPrice?.toDouble()
                                                ?: candles.lastOrNull()?.close?.toDouble()
                                                ?: price
                                            val side =
                                                if (lastPrice > 0.0 && price >= lastPrice) OrderSide.SELL
                                                else OrderSide.BUY
                                            val type =
                                                if (chartOrderType == OrderType.STOP) OrderType.STOP
                                                else OrderType.LIMIT
                                            onPlaceTradingOrderAt.invoke(price, side, type)
                                        }
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    color = config.gridColor,
                    fontSize = 14.sp,
                    lineHeight = 14.sp,
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Bold,
                )
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
                .background(config.backgroundColor.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                .clickable {
                    followLive = true
                    timeScale.scrollToLatest(candles.size, chartWidthPx)
                }
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = "\u21E5",
                color = if (isAtRightEdge) config.axisTextColor else config.accentColor,
                fontSize = 14.sp,
                fontFamily = TerminalFontFamily,
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
    buyColor: Color,
    sellColor: Color,
) {
    orders.forEach { order ->
        if (order.isTpSl) return@forEach
        val price = if (order.orderId == draggingOrderId && draggingPrice != null) {
            draggingPrice
        } else {
            order.price
        }
        if (price <= 0.0) return@forEach
        val y = priceToY(price.toFloat(), priceRange, chartArea.height)
        if (y < 0f || y > chartArea.height) return@forEach

        val isBuy = order.side == OrderSide.BUY
        val color = if (isBuy) buyColor else sellColor
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
    positions: List<Position>,
    priceRange: PriceRange,
    chartArea: Rect,
    buyColor: Color,
    sellColor: Color,
) {
    positions.forEach { position ->
        if (position.avgPrice <= 0.0) return@forEach
        val y = priceToY(position.avgPrice.toFloat(), priceRange, chartArea.height)
        if (y < 0f || y > chartArea.height) return@forEach
        val isBuy = position.side == TradeSide.BUY
        val color = if (isBuy) buyColor else sellColor
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
    sideColor: Color,
    takeProfitPrice: Double?,
    stopLossPrice: Double?,
    pricePerPx: Double,
    tpColor: Color,
    slColor: Color,
    surfaceColor: Color,
    gridColor: Color,
    onTpSlPreview: (Boolean, Double?) -> Unit,
    onTpSlCommit: (Boolean, Double?) -> Unit,
    onTpSlRect: (Boolean, Rect) -> Unit,
    onTpSlRectRemoved: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onResize: (Double) -> Unit,
    onQtyBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isBuy = order.side == OrderSide.BUY
    val color = sideColor
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
            .height(TRADING_BADGE_HEIGHT)
            .background(surfaceColor, RoundedCornerShape(2.dp))
            .border(1.dp, color.copy(alpha = 0.9f), RoundedCornerShape(2.dp))
            // Кликабельная область на бейдже, чтобы клики не разворачивали график
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GripDots(
            color = color.copy(alpha = 0.85f),
            modifier = Modifier.padding(start = 4.dp, end = 3.dp),
        )
        Text(
            text = title,
            color = color,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(end = 4.dp),
        )
        BasicTextField(
            value = qtyText,
            onValueChange = { qtyText = it },
            enabled = !order.isTpSl,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 10.sp,
                lineHeight = 11.sp,
                textAlign = TextAlign.Center,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(color),
            modifier = Modifier
                .width(44.dp)
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                .border(1.dp, gridColor.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
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
        if (takeProfitPrice == null && !order.reduceOnly) {
            TpSlActionButton(
                label = "TP",
                color = tpColor,
                background = Color.Black.copy(alpha = 0.25f),
                enabled = true,
                referencePrice = order.price,
                currentPrice = null,
                pricePerPx = pricePerPx,
                onPreview = onTpSlPreview,
                onCommit = onTpSlCommit,
                onBounds = { rect -> onTpSlRect(true, rect) },
                onBoundsRemoved = { onTpSlRectRemoved(true) },
                modifier = Modifier.padding(start = 3.dp),
            )
        }
        if (stopLossPrice == null && !order.reduceOnly) {
            TpSlActionButton(
                label = "SL",
                color = slColor,
                background = Color.Black.copy(alpha = 0.25f),
                enabled = true,
                referencePrice = order.price,
                currentPrice = null,
                pricePerPx = pricePerPx,
                onPreview = onTpSlPreview,
                onCommit = onTpSlCommit,
                onBounds = { rect -> onTpSlRect(false, rect) },
                onBoundsRemoved = { onTpSlRectRemoved(false) },
                modifier = Modifier.padding(start = 2.dp),
            )
        }
        Text(
            text = "✕",
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 11.sp,
            lineHeight = 12.sp,
            fontFamily = TerminalFontFamily,
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
private fun GripDots(
    color: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(width = 6.dp, height = 10.dp)) {
        val r = 0.9f * density
        val step = size.height / 3f
        val xs = listOf(size.width * 0.25f, size.width * 0.75f)
        val ys = listOf(step * 0.5f, step * 1.5f, step * 2.5f)
        xs.forEach { x ->
            ys.forEach { y ->
                drawCircle(color.copy(alpha = 0.9f), radius = r, center = Offset(x, y))
            }
        }
    }
}

/** Кнопка TP/SL в основном бейдже: drag «вытаскивает» уровень. */
@Composable
private fun TpSlActionButton(
    label: String,
    color: Color,
    background: Color,
    enabled: Boolean,
    referencePrice: Double,
    currentPrice: Double?,
    pricePerPx: Double,
    onPreview: (Boolean, Double?) -> Unit,
    onCommit: (Boolean, Double?) -> Unit,
    onBounds: (Rect) -> Unit,
    onBoundsRemoved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTakeProfit = label == "TP"
    var dragPrice by remember { mutableStateOf<Double?>(null) }
    Row(
        modifier = modifier
            .onGloballyPositioned { coords -> onBounds(coords.boundsInRoot()) }
            .height(TRADING_BADGE_HEIGHT)
            .pointerInput(label, currentPrice, pricePerPx, referencePrice, enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { dragPrice = currentPrice ?: referencePrice },
                    onDrag = { change, amount ->
                        change.consume()
                        val base = currentPrice ?: referencePrice
                        if (base > 0.0 && pricePerPx > 0.0) {
                            val next = ((dragPrice ?: base) - amount.y * pricePerPx)
                                .takeIf { it > 0.0 }
                            dragPrice = next
                            onPreview(isTakeProfit, next)
                        }
                    },
                    onDragEnd = {
                        val target = dragPrice
                        dragPrice = null
                        onPreview(isTakeProfit, null)
                        if (target != null) onCommit(isTakeProfit, target)
                    },
                    onDragCancel = {
                        dragPrice = null
                        onPreview(isTakeProfit, null)
                    },
                )
            }
            .pointerInput(label, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onDoubleTap = { onCommit(isTakeProfit, 0.0) })
            }
            .background(background, RoundedCornerShape(2.dp))
            .border(1.dp, color.copy(alpha = 0.9f), RoundedCornerShape(2.dp))
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
    DisposableEffect(label) {
        onDispose { onBoundsRemoved() }
    }
}

/** Отдельный бейдж TP/SL на ценовом уровне: цена, % изменения и ✕. */
@Composable
private fun TpSlLevelBadge(
    label: String,
    price: Double,
    referencePrice: Double,
    isLong: Boolean,
    formatter: SymbolFormatter,
    color: Color,
    background: Color,
    enabled: Boolean,
    pricePerPx: Double,
    onPreview: (Boolean, Double?) -> Unit,
    onCommit: (Boolean, Double?) -> Unit,
    onBounds: (Rect) -> Unit,
    onBoundsRemoved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTakeProfit = label == "TP"
    val pct = if (referencePrice > 0.0) {
        (price - referencePrice) / referencePrice * 100.0 * (if (isLong) 1.0 else -1.0)
    } else {
        0.0
    }
    val pctText = (if (pct >= 0.0) "+" else "") +
        plainDecimalString(kotlin.math.round(pct * 100.0) / 100.0)

    Row(
        modifier = modifier
            .onGloballyPositioned { coords -> onBounds(coords.boundsInRoot()) }
            .height(TRADING_BADGE_HEIGHT)
            .background(background, RoundedCornerShape(2.dp))
            .border(1.dp, color.copy(alpha = 0.9f), RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp)
            .pointerInput(label, price, pricePerPx, enabled) {
                if (!enabled) return@pointerInput
                var dragPrice: Double? = null
                detectDragGestures(
                    onDragStart = { dragPrice = price },
                    onDrag = { change, amount ->
                        change.consume()
                        if (pricePerPx > 0.0) {
                            val next = ((dragPrice ?: price) - amount.y * pricePerPx)
                                .takeIf { it > 0.0 }
                            dragPrice = next
                            onPreview(isTakeProfit, next)
                        }
                    },
                    onDragEnd = {
                        val target = dragPrice
                        dragPrice = null
                        onPreview(isTakeProfit, null)
                        if (target != null) onCommit(isTakeProfit, target)
                    },
                    onDragCancel = {
                        dragPrice = null
                        onPreview(isTakeProfit, null)
                    },
                )
            }
            .pointerInput(label, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onDoubleTap = { onCommit(isTakeProfit, 0.0) })
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = "${formatter.formatPrice(price)}  $pctText%",
            color = color,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontFamily = TerminalFontFamily,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp),
        )
        Text(
            text = "✕",
            color = color,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onCommit(isTakeProfit, 0.0) }
                .padding(start = 4.dp),
        )
    }
    DisposableEffect(label) {
        onDispose { onBoundsRemoved() }
    }
}

private fun trimQtyText(v: Double): String = plainDecimalString(v)

private fun positionBadgeKey(p: Position): String =
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
    sideColor: Color,
    pnlUpColor: Color,
    pnlDownColor: Color,
    pnlPlateColor: Color,
    takeProfitPrice: Double?,
    stopLossPrice: Double?,
    pricePerPx: Double,
    tpColor: Color,
    slColor: Color,
    surfaceColor: Color,
    onTpSlPreview: (Boolean, Double?) -> Unit,
    onTpSlCommit: (Boolean, Double?) -> Unit,
    onTpSlRect: (Boolean, Rect) -> Unit,
    onTpSlRectRemoved: (Boolean) -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val isBuy = position.side == TradeSide.BUY
    val pnlColor = if (pnlUp) pnlUpColor else pnlDownColor
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
            .height(TRADING_BADGE_HEIGHT)
            .background(surfaceColor, RoundedCornerShape(2.dp))
            .border(1.dp, sideColor.copy(alpha = 0.9f), RoundedCornerShape(2.dp))
            // Кликабельная область на бейдже, чтобы клики не разворачивали график
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = sideColor,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp, end = if (pnlLabel != null) 3.dp else 6.dp),
        )
        pnlLabel?.let { label ->
            Box(
                modifier = Modifier
                    .background(pnlPlateColor, RoundedCornerShape(2.dp))
                    .border(1.dp, pnlColor.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = label,
                    color = pnlColor,
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
        if (takeProfitPrice == null) {
            TpSlActionButton(
                label = "TP",
                color = tpColor,
                background = Color.Black.copy(alpha = 0.25f),
                enabled = true,
                referencePrice = position.avgPrice,
                currentPrice = null,
                pricePerPx = pricePerPx,
                onPreview = onTpSlPreview,
                onCommit = onTpSlCommit,
                onBounds = { rect -> onTpSlRect(true, rect) },
                onBoundsRemoved = { onTpSlRectRemoved(true) },
                modifier = Modifier.padding(start = 3.dp),
            )
        }
        if (stopLossPrice == null) {
            TpSlActionButton(
                label = "SL",
                color = slColor,
                background = Color.Black.copy(alpha = 0.25f),
                enabled = true,
                referencePrice = position.avgPrice,
                currentPrice = null,
                pricePerPx = pricePerPx,
                onPreview = onTpSlPreview,
                onCommit = onTpSlCommit,
                onBounds = { rect -> onTpSlRect(false, rect) },
                onBoundsRemoved = { onTpSlRectRemoved(false) },
                modifier = Modifier.padding(start = 2.dp),
            )
        }
        onClose?.let { close ->
            Row(
                modifier = Modifier
                    .padding(start = 2.dp)
                    .height(TRADING_BADGE_HEIGHT)
                    .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                    .border(1.dp, pnlDownColor.copy(alpha = 0.9f), RoundedCornerShape(2.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { close() }
                    .padding(horizontal = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Flatten",
                    color = pnlDownColor,
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
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
