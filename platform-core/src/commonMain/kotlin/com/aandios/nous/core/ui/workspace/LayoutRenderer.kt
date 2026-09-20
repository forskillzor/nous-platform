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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.workspace.LayoutNode
import com.aandios.nous.core.workspace.PanelConfig
import com.aandios.nous.core.workspace.PanelType

@Composable
fun LayoutRenderer(
    node: LayoutNode,
    modifier: Modifier = Modifier,
    panels: Map<String, PanelConfig> = emptyMap(),
    onClosePanel: ((String) -> Unit)? = null,
    onSplitPanel: ((String, LayoutNode.Direction, PanelType) -> Unit)? = null,
    onRatioChange: (() -> Unit)? = null,
    panelContent: @Composable (panelId: String) -> Unit
) {
    when (node) {
        is LayoutNode.Leaf -> {
            val config = panels[node.panelId]
            Column(modifier = modifier.border(1.dp, Color(0xFF222222))) {
                if (config != null) {
                    PanelHeader(
                        config = config,
                        onClose = onClosePanel?.let { { it(node.panelId) } },
                        onSplitH = onSplitPanel?.let { fn -> { type -> fn(node.panelId, LayoutNode.Direction.HORIZONTAL, type) } },
                        onSplitV = onSplitPanel?.let { fn -> { type -> fn(node.panelId, LayoutNode.Direction.VERTICAL, type) } },
                    )
                }
                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    panelContent(node.panelId)
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

                when (node.direction ) {
                    LayoutNode.Direction.HORIZONTAL -> {
                        Row(modifier.onSizeChanged { parentSizePx = it.width.toFloat() }) {
                            node.children.forEachIndexed { index, child ->
                                val weight =
                                    if (index == 0) ratio else (1f - ratio) / (numChildren - 1).coerceAtLeast(1)
                                LayoutRenderer(
                                    node = child, modifier = Modifier.weight(weight),
                                    panels = panels, onClosePanel = onClosePanel, onSplitPanel = onSplitPanel,
                                    onRatioChange = onRatioChange, panelContent = panelContent
                                )
                                if (index < node.children.lastIndex) {
                                    SplitHandle(
                                        direction = LayoutNode.Direction.HORIZONTAL,
                                        parentSize = parentSizePx,
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
                                LayoutRenderer(
                                    node = child, modifier = Modifier.weight(weight),
                                    panels = panels, onClosePanel = onClosePanel, onSplitPanel = onSplitPanel,
                                    onRatioChange = onRatioChange, panelContent = panelContent
                                )
                                if (index < node.children.lastIndex) {
                                    SplitHandle(
                                        direction = LayoutNode.Direction.VERTICAL,
                                        parentSize = parentSizePx,
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

@Composable
private fun SplitHandle(
    direction: LayoutNode.Direction,
    parentSize: Float,
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
                    detectHorizontalDragGestures { _, dragAmount ->
                        onResize(dragAmount / size)
                    }
                } else {
                    detectVerticalDragGestures { _, dragAmount ->
                        onResize(dragAmount / size)
                    }
                }
            }
    )
}
