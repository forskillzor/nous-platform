/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
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
    var panelRects by mutableStateOf<Map<String, Rect>>(emptyMap())
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
    panelContent: @Composable (panelId: String) -> Unit
) {
    val dragState = remember { PanelDragState() }
    RenderNode(
        node = node,
        modifier = modifier,
        panels = panels,
        onClosePanel = onClosePanel,
        onSplitPanel = onSplitPanel,
        onRatioChange = onRatioChange,
        onRatioChangeStart = onRatioChangeStart,
        onMovePanel = onMovePanel,
        dragState = dragState,
        panelContent = panelContent,
    )
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
                        onDrag = if (onMovePanel != null) {
                            PanelDragHandlers(
                                onDragStart = {
                                    dragState.panelId = panelId
                                    dragState.targetId = null
                                    dragState.zone = null
                                },
                                onDrag = { globalPos ->
                                    resolveDropTarget(dragState, globalPos, excludeId = panelId)
                                },
                                onDragEnd = {
                                    val target = dragState.targetId
                                    val zone = dragState.zone
                                    dragState.panelId = null
                                    dragState.targetId = null
                                    dragState.zone = null
                                    if (target != null && zone != null) {
                                        onMovePanel(panelId, target, zone)
                                    }
                                },
                            )
                        } else null,
                    )
                }
                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    panelContent(panelId)
                    if (onMovePanel != null && dragState.panelId != null && dragState.panelId != panelId) {
                        DropZoneOverlay(
                            activeZone = if (dragState.targetId == panelId) dragState.zone else null
                        )
                    }
                }
            }
        }
        is LayoutNode.Split -> {
            // key() ensures recomposition when children list changes (new split/close)
            val splitKey = node.children.map { it.hashCode() }.hashCode()
            key(splitKey) {
                var ratio by remember { mutableFloatStateOf(node.ratio) }
                val numChildren = node.children.size
                var parentSizePx by remember { mutableFloatStateOf(800f) }

                // Sync mutable ratio to node for persistence
                LaunchedEffect(ratio) { node.ratio = ratio }

                when (node.direction) {
                    LayoutNode.Direction.HORIZONTAL -> {
                        Row(modifier.onSizeChanged { parentSizePx = it.width.toFloat() }) {
                            node.children.forEachIndexed { index, child ->
                                val weight =
                                    if (index == 0) ratio else (1f - ratio) / (numChildren - 1).coerceAtLeast(1)
                                RenderNode(node = child, modifier = Modifier.weight(weight), panels = panels, onClosePanel = onClosePanel, onSplitPanel = onSplitPanel, onRatioChange = onRatioChange, onRatioChangeStart = onRatioChangeStart, onMovePanel = onMovePanel, dragState = dragState, panelContent = panelContent)
                                if (index < node.children.lastIndex) {
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
                            node.children.forEachIndexed { index, child ->
                                val weight =
                                    if (index == 0) ratio else (1f - ratio) / (numChildren - 1).coerceAtLeast(1)
                                RenderNode(node = child, modifier = Modifier.weight(weight), panels = panels, onClosePanel = onClosePanel, onSplitPanel = onSplitPanel, onRatioChange = onRatioChange, onRatioChangeStart = onRatioChangeStart, onMovePanel = onMovePanel, dragState = dragState, panelContent = panelContent)
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
}

/** Hit-test по панелям во время drag: определяет целевой panel и зону. */
private fun resolveDropTarget(dragState: PanelDragState, globalPos: Offset, excludeId: String) {
    val entry = dragState.panelRects.entries.firstOrNull { (id, r) ->
        id != excludeId && r.contains(globalPos)
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

/**
 * Визуальный оверлей зон дропа (как в IntelliJ IDEA): 4 края + центр.
 */
@Composable
private fun BoxScope.DropZoneOverlay(activeZone: LayoutEngine.DropZone?) {
    ZoneBox(activeZone == LayoutEngine.DropZone.LEFT, Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(0.3f))
    ZoneBox(activeZone == LayoutEngine.DropZone.RIGHT, Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.3f))
    ZoneBox(activeZone == LayoutEngine.DropZone.TOP, Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight(0.3f))
    ZoneBox(activeZone == LayoutEngine.DropZone.BOTTOM, Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.3f))
    ZoneBox(activeZone == LayoutEngine.DropZone.CENTER, Modifier.align(Alignment.Center).fillMaxWidth(0.4f).fillMaxHeight(0.4f))
}

@Composable
private fun ZoneBox(active: Boolean, modifier: Modifier) {
    Box(
        modifier = modifier.background(
            when {
                active -> Color(0xFF00C853).copy(alpha = 0.30f)
                else -> Color.White.copy(alpha = 0.04f)
            }
        )
    )
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
