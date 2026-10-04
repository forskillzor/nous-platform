/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.workspace

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.workspace.LayoutEngine
import com.aandios.nous.core.workspace.LayoutNode
import com.aandios.nous.core.workspace.PanelConfig
import com.aandios.nous.core.workspace.PanelType

// Минимальные ширины панелей (тайл-менеджерская логика):
// charts — гибкие, но не уже CHART_MIN; dom/trades — фиксированные preferred,
// при нехватке места сжимаются через k-масштаб.
private val ChartMinWidthDp = 200.dp

/**
 * Состояние перетаскивания панели внутри workspace (IntelliJ-стиль).
 * Передаётся вниз по рекурсии рендера — единый источник для всех панелей.
 */
private class PanelDragState {
    var panelId by mutableStateOf<String?>(null)
    var targetId by mutableStateOf<String?>(null)
    var zone by mutableStateOf<LayoutEngine.DropZone?>(null)
    var rootZone by mutableStateOf<LayoutEngine.DropZone?>(null)
    var cancelled by mutableStateOf(false)

    fun cancel() {
        panelId = null
        targetId = null
        zone = null
        rootZone = null
        cancelled = true
    }

    fun clearTargets() {
        panelId = null
        targetId = null
        zone = null
        rootZone = null
    }
}

/**
 * Контекст анимации перелёта панелей (IntelliJ-стиль): при изменении дерева
 * панели плавно перемещаются и ресайзятся из старых позиций в новые.
 */
private class PanelFlyContext(
    val progress: Animatable<Float, AnimationVector1D>,
    val rects: MutableState<Map<String, Rect>>,
) {
    /** Позиции панелей ДО изменения дерева (координаты окна). */
    var oldRects: Map<String, Rect> = emptyMap()

    /** Дерево, для которого сняты oldRects. */
    var node: LayoutNode? = null

    /** Запускать анимацию по завершении текущей рекомпозиции. */
    var animateOnChange = false
}

@Composable
fun LayoutRenderer(
    node: LayoutNode,
    modifier: Modifier = Modifier,
    panels: Map<String, PanelConfig> = emptyMap(),
    onClosePanel: ((String) -> Unit)? = null,
    onSplitPanel: ((String, LayoutNode.Direction, PanelType) -> Unit)? = null,
    onRatioChange: (() -> Unit)? = null,
    onRatioChangeStart: (() -> Unit)? = null,
    onMovePanel: ((String, String, LayoutEngine.DropZone) -> Unit)? = null,
    onMovePanelToRoot: ((String, LayoutEngine.DropZone) -> Unit)? = null,
    fixedPanelWidths: Map<String, Dp> = emptyMap(),
    panelContent: @Composable (panelId: String) -> Unit
) {
    // Drag-состояние привязано к дереву: при любом split/close/move/undo или
    // смене воркспейса дерево — новый инстанс, и состояние начинается с чистого
    // листа. Иначе протухшие rect'ы давали битую цель дропа.
    val dragState = remember(node) { PanelDragState() }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val focusInteraction = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val rootBandPx = with(density) { 28.dp.toPx() }
    val rootStripPx = with(density) { 6.dp.toPx() }

    // Позиции панелей (координаты окна) — единый источник для hit-test'а
    // drag'а и анимации перелёта. Живёт дольше dragState.
    val panelRects = remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }

    // Фиксированные ширины панелей (например, DOM/Trades): приходят от
    // вызывающего кода (ширина хедера конкретного виджета). Панель из этой
    // карты получает фиксированную ширину, остальные делят остаток весами.
    val fixedPanelWidthsPx = remember(node, panels, fixedPanelWidths, density) {
        fixedPanelWidths.mapValues { (_, width) -> with(density) { width.toPx() } }
    }

    // Анимация перелёта панелей при изменении дерева (split/close/move/undo).
    val flyProgress = remember { Animatable(1f) }
    val fly = remember { PanelFlyContext(flyProgress, panelRects) }
    if (fly.node === null) {
        fly.node = node
    } else if (fly.node !== node) {
        // Фиксируем старые позиции ДО ре-лэйаута нового дерева (composition-time)
        fly.node = node
        fly.oldRects = panelRects.value
        fly.animateOnChange = true
    }
    LaunchedEffect(node) {
        if (fly.animateOnChange) {
            fly.animateOnChange = false
            flyProgress.snapTo(0f)
            flyProgress.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
        }
    }

    val wsRectWindow = Rect(
        left = rootOrigin.x,
        top = rootOrigin.y,
        right = rootOrigin.x + rootSize.width,
        bottom = rootOrigin.y + rootSize.height,
    )

    Box(
        modifier = modifier
            .onSizeChanged { rootSize = it }
            .onGloballyPositioned { rootOrigin = it.positionInWindow() }
            .clickable(interactionSource = focusInteraction, indication = null) { /* focusable for Esc */ }
            .onPreviewKeyEvent { event ->
                // Esc во время перетаскивания панели — отменить перенос
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown &&
                    dragState.panelId != null
                ) {
                    dragState.cancel()
                    true
                } else false
            }
    ) {
        RenderNode(
            node = node,
            modifier = Modifier.fillMaxSize(),
            panels = panels,
            onClosePanel = onClosePanel,
            onSplitPanel = onSplitPanel,
            onRatioChange = onRatioChange,
            onRatioChangeStart = onRatioChangeStart,
            onMovePanel = onMovePanel,
            onMovePanelToRoot = onMovePanelToRoot,
            wsRect = wsRectWindow,
            rootBandPx = rootBandPx,
            dragState = dragState,
            fly = fly,
            fixedPanelWidthsPx = fixedPanelWidthsPx,
            panelContent = panelContent,
        )

        // Анимированный «призрак» панели: показывает, куда и какого размера
        // она встанет при отпускании. Для зон панели — половина/цель панели,
        // для корневых зон — тонкая полоска на всю ширину/высоту воркспейса.
        val landingWindow = when {
            dragState.rootZone != null ->
                rootLandingRect(wsRectWindow, dragState.rootZone!!, rootStripPx)
            else -> dragState.targetId
                ?.let { id -> panelRects.value[id] }
                ?.let { r -> dragState.zone?.let { z -> landingRect(r, z) } }
        }
        val landing = landingWindow?.let {
            Rect(
                left = it.left - rootOrigin.x,
                top = it.top - rootOrigin.y,
                right = it.right - rootOrigin.x,
                bottom = it.bottom - rootOrigin.y,
            )
        }
        DropPreview(landing = landing)
    }
}

@Composable
private fun RenderNode(
    node: LayoutNode,
    modifier: Modifier,
    panels: Map<String, PanelConfig>,
    onClosePanel: ((String) -> Unit)?,
    onSplitPanel: ((String, LayoutNode.Direction, PanelType) -> Unit)?,
    onRatioChange: (() -> Unit)?,
    onRatioChangeStart: (() -> Unit)?,
    onMovePanel: ((String, String, LayoutEngine.DropZone) -> Unit)?,
    onMovePanelToRoot: ((String, LayoutEngine.DropZone) -> Unit)? = null,
    wsRect: Rect = Rect.Zero,
    rootBandPx: Float = 0f,
    dragState: PanelDragState,
    fly: PanelFlyContext,
    fixedPanelWidthsPx: Map<String, Float> = emptyMap(),
    panelContent: @Composable (panelId: String) -> Unit,
) {
    when (node) {
        is LayoutNode.Leaf -> {
            val config = panels[node.panelId]
            val panelId = node.panelId
            Column(
                modifier = modifier
                    .border(1.dp, Color(0xFF222222))
                    .onGloballyPositioned {
                        fly.rects.value = fly.rects.value + (panelId to it.boundsInWindow())
                    }
                    .graphicsLayer { applyFlyTransform(fly, panelId) }
            ) {
                if (config != null) {
                    PanelHeader(
                        config = config,
                        onClose = onClosePanel?.let { { it(panelId) } },
                        onSplitH = onSplitPanel?.let { fn -> { type -> fn(panelId, LayoutNode.Direction.HORIZONTAL, type) } },
                        onSplitV = onSplitPanel?.let { fn -> { type -> fn(panelId, LayoutNode.Direction.VERTICAL, type) } },
                        onDrag = if (onMovePanel != null || onMovePanelToRoot != null) {
                            PanelDragHandlers(
                                onDragStart = {
                                    dragState.panelId = panelId
                                    dragState.targetId = null
                                    dragState.zone = null
                                    dragState.rootZone = null
                                    dragState.cancelled = false
                                },
                                onDrag = { globalPos ->
                                    if (!dragState.cancelled) {
                                        resolveDropTarget(
                                            dragState = dragState,
                                            globalPos = globalPos,
                                            excludeId = panelId,
                                            validPanelIds = panels.keys,
                                            rects = fly.rects.value,
                                            wsRect = wsRect,
                                            rootBandPx = rootBandPx,
                                        )
                                    }
                                },
                                onDragEnd = {
                                    val target = dragState.targetId
                                    val zone = dragState.zone
                                    val rootZone = dragState.rootZone
                                    val wasCancelled = dragState.cancelled
                                    dragState.clearTargets()
                                    dragState.cancelled = false
                                    if (!wasCancelled) {
                                        when {
                                            rootZone != null && onMovePanelToRoot != null ->
                                                onMovePanelToRoot(panelId, rootZone)
                                            target != null && zone != null && onMovePanel != null ->
                                                onMovePanel(panelId, target, zone)
                                        }
                                    }
                                },
                                onDragCancel = {
                                    // Прерванный жест (фокус ушёл и т.п.) — только чистим
                                    // состояние, перенос НЕ коммитим.
                                    dragState.clearTargets()
                                    dragState.cancelled = false
                                },
                            )
                        } else null,
                    )
                }
                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    panelContent(panelId)
                }
            }
        }
        is LayoutNode.Split -> {
            // Без AnimatedContent: перелёт панелей делается на уровне листьев
            // (graphicsLayer + PanelFlyContext) — анимируются движение и ресайз
            // панелей, а не fade всего сплита.
            var ratio by remember(node) { mutableFloatStateOf(node.ratio) }
            val numChildren = node.children.size
            var parentSizePx by remember { mutableFloatStateOf(800f) }
            val density = LocalDensity.current

            // Sync mutable ratio to node for persistence.
            // Ключ node: при пересборке дерева эффект пишет в АКТУАЛЬНЫЙ инстанс
            // (иначе с equals-true remember эффект продолжал писать в старый узел).
            LaunchedEffect(ratio, node) { node.ratio = ratio }

            fun fixedWidthOf(child: LayoutNode): Float? =
                (child as? LayoutNode.Leaf)?.let { fixedPanelWidthsPx[it.panelId] }

            when (node.direction) {
                LayoutNode.Direction.HORIZONTAL -> {
                    // Тайл-менеджерская логика:
                    //  * поддерево С chart-листьями — FLEX: делит остаток через
                    //    ratio, но не уже своих минимумов;
                    //  * поддерево БЕЗ chart — FIXED (ширина = сумма preferred,
                    //    k-масштаб при тесноте): никогда не ресайзится,
                    //    весь остаток автоматически достаётся chart'ам;
                    //  * ручка рендерится ТОЛЬКО между двумя FLEX-детьми.
                    val chartMinPx = with(density) { ChartMinWidthDp.toPx() }

                    fun containsChart(child: LayoutNode): Boolean = when (child) {
                        is LayoutNode.Leaf -> fixedWidthOf(child) == null
                        is LayoutNode.Split -> child.children.any { containsChart(it) }
                    }

                    // Минимальная ширина поддерева: fixed-leaf → preferred,
                    // chart-leaf → CHART_MIN, H-сплит → сумма детей,
                    // V-сплит → максимум детей (ширина = широчайшей строки).
                    fun minWidthPxOf(child: LayoutNode): Float = when (child) {
                        is LayoutNode.Leaf -> fixedWidthOf(child) ?: chartMinPx
                        is LayoutNode.Split -> when (child.direction) {
                            LayoutNode.Direction.HORIZONTAL ->
                                child.children.fold(0f) { acc, c -> acc + minWidthPxOf(c) }
                            LayoutNode.Direction.VERTICAL ->
                                child.children.maxOfOrNull { minWidthPxOf(it) } ?: chartMinPx
                        }
                    }

                    val isFlex = node.children.map { containsChart(it) }
                    val flexIndices = node.children.indices.filter { isFlex[it] }
                    val flexCount = flexIndices.size
                    val fixedWidthsPx = node.children.mapIndexed { index, child ->
                        if (isFlex[index]) null else fixedWidthOf(child) ?: minWidthPxOf(child)
                    }
                    val fixedSumPx = fixedWidthsPx.fold(0f) { acc, p -> acc + (p ?: 0f) }

                    val flexMins = flexIndices.map { minWidthPxOf(node.children[it]) }
                    val flexMinsTotal = flexMins.sum()
                    val firstFlexMin = flexMins.firstOrNull() ?: 0f
                    val restMinsSum = flexMins.drop(1).sum()

                    // Глобальный масштаб при нехватке места: пропорционально
                    // сжимаются и фикс-панели, и flex-минимумы — никто не
                    // схлопывается в ноль. Когда места хватает — scaleK = 1,
                    // фикс стоят по preferred, chart забирает остаток.
                    val totalMinPx = fixedSumPx + flexMinsTotal
                    val scaleK = if (totalMinPx > 0f) {
                        (parentSizePx / totalMinPx).coerceAtMost(1f)
                    } else 1f
                    val effectiveFixedSumPx = fixedSumPx * scaleK

                    // Границы ratio (пиксельные минимумы flex-детей)
                    val spanPx = if (flexCount > 0) {
                        (parentSizePx - effectiveFixedSumPx).coerceAtLeast(1f)
                    } else {
                        parentSizePx.coerceAtLeast(1f)
                    }
                    val (minRatio, maxRatio) = if (flexCount >= 2) {
                        firstFlexMin * scaleK / spanPx to
                            1f - restMinsSum * scaleK / spanPx
                    } else 0.15f to 0.85f
                    val clampMin = if (minRatio > maxRatio) 0.15f else minRatio
                    val clampMax = if (minRatio > maxRatio) 0.85f else maxRatio
                    val effectiveRatio = ratio.coerceIn(clampMin, clampMax)

                    Row(modifier.onSizeChanged { parentSizePx = it.width.toFloat() }) {
                        node.children.forEachIndexed { index, child ->
                            val fixedPx = fixedWidthsPx[index]
                            val childModifier = when {
                                // Фиксированное поддерево (масштаб при нехватке)
                                fixedPx != null -> Modifier.width(
                                    with(density) { (fixedPx * scaleK).toDp() }
                                )
                                flexCount == 1 -> Modifier.weight(1f)
                                else -> {
                                    val flexIndex = flexIndices.indexOf(index)
                                    val weight = when {
                                        flexIndex == 0 -> effectiveRatio
                                        restMinsSum > 0f ->
                                            (1f - effectiveRatio) * flexMins[flexIndex] / restMinsSum
                                        else -> (1f - effectiveRatio) / (flexCount - 1).coerceAtLeast(1)
                                    }
                                    Modifier.weight(weight)
                                }
                            }
                            key(layoutSignature(child)) {
                                RenderNode(node = child, modifier = childModifier, panels = panels, onClosePanel = onClosePanel, onSplitPanel = onSplitPanel, onRatioChange = onRatioChange, onRatioChangeStart = onRatioChangeStart, onMovePanel = onMovePanel, onMovePanelToRoot = onMovePanelToRoot, wsRect = wsRect, rootBandPx = rootBandPx, dragState = dragState, fly = fly, fixedPanelWidthsPx = fixedPanelWidthsPx, panelContent = panelContent)
                            }
                            // Ручка только между двумя FLEX-поддеревьями:
                            // dom/trades не ресайзятся никогда.
                            if (index < node.children.lastIndex && isFlex[index] && isFlex[index + 1]) {
                                SplitHandle(
                                    direction = LayoutNode.Direction.HORIZONTAL,
                                    parentSize = spanPx,
                                    onResizeStart = onRatioChangeStart,
                                    onResize = { delta ->
                                        val newRatio = (ratio + delta).coerceIn(clampMin, clampMax)
                                        if (newRatio != ratio) {
                                            ratio = newRatio; onRatioChange?.invoke()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                LayoutNode.Direction.VERTICAL -> {
                    Column(modifier.onSizeChanged { parentSizePx = it.height.toFloat() }) {
                        node.children.forEachIndexed { index, child ->
                            val weight =
                                if (index == 0) ratio else (1f - ratio) / (numChildren - 1).coerceAtLeast(1)
                            key(layoutSignature(child)) {
                                RenderNode(node = child, modifier = Modifier.weight(weight), panels = panels, onClosePanel = onClosePanel, onSplitPanel = onSplitPanel, onRatioChange = onRatioChange, onRatioChangeStart = onRatioChangeStart, onMovePanel = onMovePanel, onMovePanelToRoot = onMovePanelToRoot, wsRect = wsRect, rootBandPx = rootBandPx, dragState = dragState, fly = fly, fixedPanelWidthsPx = fixedPanelWidthsPx, panelContent = panelContent)
                            }
                            if (index < node.children.lastIndex) {
                                SplitHandle(
                                    direction = LayoutNode.Direction.VERTICAL,
                                    parentSize = parentSizePx,
                                    onResizeStart = onRatioChangeStart,
                                    onResize = { delta ->
                                        val newRatio = ratio + delta
                                        if (newRatio in 0.15f..0.85f) {
                                            ratio = newRatio; onRatioChange?.invoke()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Сигнатура структуры поддерева: direction + идентификаторы панелей,
 * БЕЗ ratio — стабильный ключ identity панели в композиции.
 */
private fun layoutSignature(node: LayoutNode): String = when (node) {
    is LayoutNode.Leaf -> "L:${node.panelId}"
    is LayoutNode.Split ->
        "S:${node.direction}(" + node.children.joinToString(",") { layoutSignature(it) } + ")"
}

/**
 * Перелёт панели из старой позиции/размера в новую (IntelliJ-стиль):
 * translation + scale от oldRects к текущему rect. Новые панели — плавное
 * появление. Вне анимации (progress = 1) — no-op.
 */
private fun GraphicsLayerScope.applyFlyTransform(fly: PanelFlyContext, panelId: String) {
    val p = fly.progress.value
    if (p >= 1f) return
    val cur = fly.rects.value[panelId]
    val old = fly.oldRects[panelId]
    if (old != null && cur != null && cur.width > 0f && cur.height > 0f) {
        transformOrigin = TransformOrigin(0f, 0f)
        translationX = (old.left - cur.left) * (1f - p)
        translationY = (old.top - cur.top) * (1f - p)
        val sx = old.width / cur.width
        val sy = old.height / cur.height
        scaleX = sx + (1f - sx) * p
        scaleY = sy + (1f - sy) * p
    } else if (cur != null) {
        alpha = p
        val s = 0.96f + 0.04f * p
        scaleX = s
        scaleY = s
    }
}

/** Hit-test во время drag: корневые зоны (края воркспейса) в приоритете, затем панели. */
private fun resolveDropTarget(
    dragState: PanelDragState,
    globalPos: Offset,
    excludeId: String,
    validPanelIds: Set<String>,
    rects: Map<String, Rect>,
    wsRect: Rect,
    rootBandPx: Float,
) {
    // 1) Корневые зоны: курсор в полосе у внешнего края воркспейса
    if (wsRect.width > 0f && wsRect.height > 0f && rootBandPx > 0f) {
        val dLeft = globalPos.x - wsRect.left
        val dRight = wsRect.right - globalPos.x
        val dTop = globalPos.y - wsRect.top
        val dBottom = wsRect.bottom - globalPos.y
        val min = minOf(dLeft, dRight, dTop, dBottom)
        if (min < rootBandPx) {
            dragState.rootZone = when {
                min == dLeft -> LayoutEngine.DropZone.LEFT
                min == dRight -> LayoutEngine.DropZone.RIGHT
                min == dTop -> LayoutEngine.DropZone.TOP
                else -> LayoutEngine.DropZone.BOTTOM
            }
            dragState.targetId = null
            dragState.zone = null
            return
        }
    }
    dragState.rootZone = null

    // 2) Зоны внутри панелей. Протухшие rect'ы (удалённые панели, другие
    // воркспейсы) отфильтровываются по текущему набору панелей.
    val entry = rects.entries.firstOrNull { (id, r) ->
        id != excludeId && id in validPanelIds && r.contains(globalPos)
    }
    if (entry == null) {
        dragState.targetId = null
        dragState.zone = null
        return
    }
    val r = entry.value
    val dx = (globalPos.x - r.left) / r.width
    val dy = (globalPos.y - r.top) / r.height
    val zone = when {
        dx < 0.3f && dy >= 0.3f && dy <= 0.7f -> LayoutEngine.DropZone.LEFT
        dx > 0.7f && dy >= 0.3f && dy <= 0.7f -> LayoutEngine.DropZone.RIGHT
        dy < 0.3f -> LayoutEngine.DropZone.TOP
        dy > 0.7f -> LayoutEngine.DropZone.BOTTOM
        else -> LayoutEngine.DropZone.CENTER
    }
    dragState.targetId = entry.key
    dragState.zone = zone
}

/** Зона дропа → прямоугольник, который займёт панель после переселения. */
private fun landingRect(target: Rect, zone: LayoutEngine.DropZone): Rect = when (zone) {
    LayoutEngine.DropZone.LEFT -> Rect(target.left, target.top, target.left + target.width / 2f, target.bottom)
    LayoutEngine.DropZone.RIGHT -> Rect(target.left + target.width / 2f, target.top, target.right, target.bottom)
    LayoutEngine.DropZone.TOP -> Rect(target.left, target.top, target.right, target.top + target.height / 2f)
    LayoutEngine.DropZone.BOTTOM -> Rect(target.left, target.top + target.height / 2f, target.right, target.bottom)
    LayoutEngine.DropZone.CENTER -> target
}

/** Корневая зона → тонкая полоска на всю ширину/высоту воркспейса. */
private fun rootLandingRect(ws: Rect, zone: LayoutEngine.DropZone, thickness: Float): Rect = when (zone) {
    LayoutEngine.DropZone.TOP -> Rect(ws.left, ws.top, ws.right, ws.top + thickness)
    LayoutEngine.DropZone.BOTTOM -> Rect(ws.left, ws.bottom - thickness, ws.right, ws.bottom)
    LayoutEngine.DropZone.LEFT -> Rect(ws.left, ws.top, ws.left + thickness, ws.bottom)
    LayoutEngine.DropZone.RIGHT -> Rect(ws.right - thickness, ws.top, ws.right, ws.bottom)
    LayoutEngine.DropZone.CENTER -> ws
}

/**
 * Анимированный призрак панели: плавно (tween 160ms) перетекает и ресайзится
 * под целевую зону. При уходе с цели — остаётся на месте и растворяется.
 */
@Composable
private fun DropPreview(landing: Rect?) {
    val lastLanding = remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(landing) {
        if (landing != null) lastLanding.value = landing
    }
    val animated by animateValueAsState(
        targetValue = landing ?: lastLanding.value,
        typeConverter = Rect.VectorConverter,
        animationSpec = tween(durationMillis = 160),
    )
    val alpha by animateFloatAsState(
        targetValue = if (landing != null) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
    )
    if (alpha > 0.01f) {
        val accent = Color(0xFF00C853)
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = accent.copy(alpha = 0.22f * alpha),
                topLeft = animated.topLeft,
                size = animated.size,
                cornerRadius = CornerRadius(4f)
            )
            drawRoundRect(
                color = accent.copy(alpha = 0.90f * alpha),
                topLeft = animated.topLeft,
                size = animated.size,
                cornerRadius = CornerRadius(4f),
                style = Stroke(width = 2f)
            )
        }
    }
}

@Composable
private fun SplitHandle(
    direction: LayoutNode.Direction,
    parentSize: Float,
    onResizeStart: (() -> Unit)? = null,
    onResize: (Float) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val bgColor = if (isHovered) Color(0xFF00C853).copy(alpha = 0.4f) else Color(0xFF333333)

    // Свежие ссылки: pointerInput(Unit) создаётся один раз, и без
    // rememberUpdatedState он навсегда держал бы СТАРЫЕ onResize/onResizeStart
    // и старый parentSize. После move/split (remember(node) пересоздаёт ratio-
    // state) жест писал бы в откреплённый state — resize «умирал».
    val currentOnResizeStart by rememberUpdatedState(onResizeStart)
    val currentOnResize by rememberUpdatedState(onResize)
    val currentParentSize by rememberUpdatedState(parentSize)

    Box(
        modifier = Modifier
            .then(
                if (direction == LayoutNode.Direction.HORIZONTAL)
                    Modifier.width(4.dp).fillMaxHeight()
                else Modifier.height(4.dp).fillMaxWidth()
            )
            .background(bgColor)
            .hoverable(interactionSource)
            .testTag("split-handle-${if (direction == LayoutNode.Direction.HORIZONTAL) "h" else "v"}")
            .pointerInput(Unit) {
                val size = if (currentParentSize > 0f) currentParentSize else 500f
                if (direction == LayoutNode.Direction.HORIZONTAL) {
                    detectHorizontalDragGestures(
                        onDragStart = { currentOnResizeStart?.invoke() },
                        onHorizontalDrag = { _, dragAmount ->
                            currentOnResize(dragAmount / size)
                        }
                    )
                } else {
                    detectVerticalDragGestures(
                        onDragStart = { currentOnResizeStart?.invoke() },
                        onVerticalDrag = { _, dragAmount ->
                            currentOnResize(dragAmount / size)
                        }
                    )
                }
            }
    )
}

