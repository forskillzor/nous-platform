/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.workspace

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.workspace.LayoutEngine
import com.aandios.nous.core.workspace.LayoutNode
import com.aandios.nous.core.workspace.PanelConfig
import com.aandios.nous.core.workspace.PanelType

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
    var panelRects by mutableStateOf<Map<String, Rect>>(emptyMap())

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
    panelContent: @Composable (panelId: String) -> Unit
) {
    // Drag-состояние привязано к дереву: при любом split/close/move/undo или
    // смене воркспейса дерево — новый инстанс, и состояние (в т.ч. rect'ы панелей)
    // начинается с чистого листа. Иначе протухшие rect'ы давали битую цель
    // дропа (панель «закрывалась») и плейсхолдер неправильного размера.
    val dragState = remember(node) { PanelDragState() }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val focusInteraction = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val rootBandPx = with(density) { 28.dp.toPx() }
    val rootStripPx = with(density) { 6.dp.toPx() }

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
            panelContent = panelContent,
        )

        // Анимированный «призрак» панели: показывает, куда и какого размера
        // она встанет при отпускании. Для зон панели — половина/цель панели,
        // для корневых зон — тонкая полоска на всю ширину/высоту воркспейса.
        val landingWindow = when {
            dragState.rootZone != null ->
                rootLandingRect(wsRectWindow, dragState.rootZone!!, rootStripPx)
            else -> dragState.targetId
                ?.let { id -> dragState.panelRects[id] }
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
                        dragState.panelRects = dragState.panelRects + (panelId to it.boundsInWindow())
                    }
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
            // Анимация структурных изменений (split/remove/move/undo/redo):
            // targetState — сигнатура структуры БЕЗ ratio (ресайз сплит-ручкой
            // остаётся мгновенным). Контент рендерится из АКТУАЛЬНОГО дерева
            // (rememberUpdatedState) — иначе правки глубже «замерзали»: keyed-
            // контент AnimatedContent не обновляется при том же ключе.
            val signature = layoutSignature(node)
            AnimatedContent(
                targetState = signature,
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(initialScale = 0.97f, animationSpec = tween(200)))
                        .togetherWith(fadeOut(tween(150)) + scaleOut(targetScale = 0.97f, animationSpec = tween(150)))
                        .using(SizeTransform(clip = true))
                },
                label = "split-content",
            ) {
                val currentNode by rememberUpdatedState(node)
                var ratio by remember(currentNode) { mutableFloatStateOf(currentNode.ratio) }
                val numChildren = currentNode.children.size
                var parentSizePx by remember(currentNode) { mutableFloatStateOf(800f) }

                // Sync mutable ratio to node for persistence
                LaunchedEffect(ratio) { currentNode.ratio = ratio }

                when (currentNode.direction) {
                    LayoutNode.Direction.HORIZONTAL -> {
                        Row(modifier.onSizeChanged { parentSizePx = it.width.toFloat() }) {
                            currentNode.children.forEachIndexed { index, child ->
                                val weight =
                                    if (index == 0) ratio else (1f - ratio) / (numChildren - 1).coerceAtLeast(1)
                                key(layoutSignature(child)) {
                                    RenderNode(node = child, modifier = Modifier.weight(weight), panels = panels, onClosePanel = onClosePanel, onSplitPanel = onSplitPanel, onRatioChange = onRatioChange, onRatioChangeStart = onRatioChangeStart, onMovePanel = onMovePanel, onMovePanelToRoot = onMovePanelToRoot, wsRect = wsRect, rootBandPx = rootBandPx, dragState = dragState, panelContent = panelContent)
                                }
                                if (index < currentNode.children.lastIndex) {
                                    SplitHandle(
                                        direction = LayoutNode.Direction.HORIZONTAL,
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

                    LayoutNode.Direction.VERTICAL -> {
                        Column(modifier.onSizeChanged { parentSizePx = it.height.toFloat() }) {
                            currentNode.children.forEachIndexed { index, child ->
                                val weight =
                                    if (index == 0) ratio else (1f - ratio) / (numChildren - 1).coerceAtLeast(1)
                                key(layoutSignature(child)) {
                                    RenderNode(node = child, modifier = Modifier.weight(weight), panels = panels, onClosePanel = onClosePanel, onSplitPanel = onSplitPanel, onRatioChange = onRatioChange, onRatioChangeStart = onRatioChangeStart, onMovePanel = onMovePanel, onMovePanelToRoot = onMovePanelToRoot, wsRect = wsRect, rootBandPx = rootBandPx, dragState = dragState, panelContent = panelContent)
                                }
                                if (index < currentNode.children.lastIndex) {
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
}

/**
 * Сигнатура структуры поддерева для анимации: direction + идентификаторы
 * панелей, БЕЗ ratio (ресайз не должен триггерить переход).
 */
private fun layoutSignature(node: LayoutNode): String = when (node) {
    is LayoutNode.Leaf -> "L:${node.panelId}"
    is LayoutNode.Split ->
        "S:${node.direction}(" + node.children.joinToString(",") { layoutSignature(it) } + ")"
}

/** Hit-test во время drag: корневые зоны (края воркспейса) в приоритете, затем панели. */
private fun resolveDropTarget(
    dragState: PanelDragState,
    globalPos: Offset,
    excludeId: String,
    validPanelIds: Set<String>,
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
    val entry = dragState.panelRects.entries.firstOrNull { (id, r) ->
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

    Box(
        modifier = Modifier
            .then(
                if (direction == LayoutNode.Direction.HORIZONTAL)
                    Modifier.width(4.dp).fillMaxHeight()
                else Modifier.height(4.dp).fillMaxWidth()
            )
            .background(bgColor)
            .hoverable(interactionSource)
            .pointerInput(Unit) {
                val size = if (parentSize > 0f) parentSize else 500f
                if (direction == LayoutNode.Direction.HORIZONTAL) {
                    detectHorizontalDragGestures(
                        onDragStart = { onResizeStart?.invoke() },
                        onHorizontalDrag = { _, dragAmount ->
                            onResize(dragAmount / size)
                        }
                    )
                } else {
                    detectVerticalDragGestures(
                        onDragStart = { onResizeStart?.invoke() },
                        onVerticalDrag = { _, dragAmount ->
                            onResize(dragAmount / size)
                        }
                    )
                }
            }
    )
}

