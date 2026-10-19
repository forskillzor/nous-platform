/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.workspace
import com.aandios.nous.core.ui.theme.TerminalFontFamily

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.aandios.nous.core.ui.component.TerminalDropdownMenu
import com.aandios.nous.core.ui.component.TerminalMenuItem
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalDesignTokens
import com.aandios.nous.core.workspace.currentTime
import com.aandios.nous.core.workspace.viewmodel.WorkspaceViewModel
import kotlin.math.roundToInt

/**
 * Панель вкладок окна. Поддерживает:
 *  - reorder перетаскиванием внутри окна;
 *  - начало межоконного переноса ([onDragStart]/[onDragEndResolve] — резолвится
 *    в окне-источнике по глобальной позиции курсора);
 *  - подсветку приёма при активном переносе из другого окна ([externalDragActive]);
 *  - меню вкладки «Open in new window»;
 *  - [trailing]-слот для действий после вкладок (кнопка «+»).
 */
@Composable
fun TabBar(
    workspaces: List<WorkspaceViewModel>,
    activeIndex: Int,
    onTabClick: (Int) -> Unit,
    onTabClose: (String) -> Unit,
    onTabReorder: ((Int, Int) -> Unit)? = null,
    onDragStart: ((String) -> Unit)? = null,
    onDragEndResolve: (() -> Unit)? = null,
    onOpenInNewWindow: ((String) -> Unit)? = null,
    previewWorkspaceId: String? = null,
    onTabDoubleClick: ((String) -> Unit)? = null,
    externalDragActive: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    var targetIndex by remember { mutableStateOf<Int?>(null) }
    var pendingReorder by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val density = LocalDensity.current
    val tokens = LocalTerminalDesignTokens.current
    val tabWidthEstimate = with(density) { tokens.dimensions.workspaceTabDragEstimate.toPx() }

    // Defer reorder to next frame — avoids recomposition during active drag
    if (onTabReorder != null) {
        LaunchedEffect(pendingReorder) {
            pendingReorder?.let { (from, to) ->
                onTabReorder(from, to)
                pendingReorder = null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(tokens.dimensions.workspaceTabBarHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = tokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            workspaces.forEachIndexed { index, ws ->
                val isActive = index == activeIndex
                val isDragged = draggedIndex == index
                val isTarget = targetIndex == index

                // Insertion indicator before this tab
                if (isTarget && draggedIndex != null) {
                    Box(
                        modifier = Modifier
                            .width(tokens.dimensions.workspaceDropIndicatorWidth)
                            .height(tokens.dimensions.workspaceDropIndicatorHeight)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }

                Box(
                    modifier = Modifier
                        .then(if (isDragged) Modifier.zIndex(1f) else Modifier)
                        .offset {
                            if (isDragged) IntOffset(dragAccumulator.roundToInt(), 0) else IntOffset.Zero
                        }
                        .graphicsLayer {
                            scaleX = if (isDragged) 1.05f else 1f
                            scaleY = if (isDragged) 1.05f else 1f
                            alpha = if (isDragged) 0.85f else 1f
                        }
                        .pointerInput(Unit) {
                            if (onTabReorder != null || onDragStart != null) {
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        draggedIndex = index
                                        dragAccumulator = 0f
                                        targetIndex = null
                                        onDragStart?.invoke(ws.config.id)
                                    },
                                    onDragEnd = {
                                        val from = draggedIndex
                                        val to = targetIndex
                                        draggedIndex = null
                                        dragAccumulator = 0f
                                        targetIndex = null
                                        if (from != null && to != null && from != to) {
                                            pendingReorder = Pair(from, to)
                                        }
                                        onDragEndResolve?.invoke()
                                    },
                                    onDragCancel = {
                                        draggedIndex = null
                                        dragAccumulator = 0f
                                        targetIndex = null
                                        onDragEndResolve?.invoke()
                                    },
                                    onHorizontalDrag = { _, dragAmount ->
                                        dragAccumulator += dragAmount
                                        val newPos = index * tabWidthEstimate + dragAccumulator
                                        val est = tabWidthEstimate
                                        val raw = ((newPos + est / 2) / est).toInt().coerceIn(0, workspaces.lastIndex)
                                        targetIndex = if (raw != index) raw else null
                                    }
                                )
                            }
                        }
                ) {
                    TabItem(
                        name = ws.config.name,
                        isActive = isActive && !isDragged,
                        isPreview = ws.config.id == previewWorkspaceId,
                        tokens = tokens,
                        onClick = { if (!isDragged) onTabClick(index) },
                        onClose = { if (!isDragged) onTabClose(ws.config.id) },
                        onDoubleClick = onTabDoubleClick?.let { fn -> { fn(ws.config.id) } },
                        onOpenInNewWindow = onOpenInNewWindow?.let { fn -> { fn(ws.config.id) } },
                    )
                }
            }

            // Insertion indicator after last tab
            if (targetIndex == workspaces.size && draggedIndex != null) {
                Box(
                    modifier = Modifier
                        .width(tokens.dimensions.workspaceDropIndicatorWidth)
                        .height(tokens.dimensions.workspaceDropIndicatorHeight)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            // Действия после вкладок (кнопка «+» с меню шаблонов)
            trailing()
        }

        // Highlight приёма при переносе вкладки из другого окна
        if (externalDragActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(tokens.dimensions.borderWidth * 2)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

/** Окно распознавания двойного клика (мс): одиночный клик срабатывает мгновенно. */
private const val TAB_DOUBLE_TAP_WINDOW_MS = 400L

@Composable
private fun TabItem(
    name: String,
    isActive: Boolean,
    isPreview: Boolean,
    tokens: TerminalDesignTokens,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    onOpenInNewWindow: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var lastTapAt by remember { mutableStateOf(0L) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(tokens.cornerRadii.workspaceTab)

    Row(
        modifier = Modifier
            .padding(end = tokens.spacing.xs)
            .height(tokens.dimensions.workspaceTabHeight)
            .clip(shape)
            .background(
                when {
                    isActive -> tokens.colors.selection
                    hovered -> tokens.colors.panelHeaderSurface
                    else -> Color.Transparent
                }
            )
            .border(
                tokens.dimensions.borderWidth,
                if (isActive) tokens.colors.panelBorder else Color.Transparent,
                shape,
            )
            .hoverable(interaction)
            .clickable {
                val now = currentTime()
                val doubleTap = now - lastTapAt <= TAB_DOUBLE_TAP_WINDOW_MS
                lastTapAt = now
                onClick()
                if (doubleTap) onDoubleClick?.invoke()
            }
            .padding(horizontal = tokens.spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = if (isActive) MaterialTheme.colorScheme.primary
            else tokens.colors.label,
            fontSize = tokens.typography.compactSize,
            fontFamily = TerminalFontFamily,
            fontStyle = if (isPreview) FontStyle.Italic else FontStyle.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = tokens.dimensions.workspaceTabMaxWidth)
        )
        Spacer(Modifier.width(tokens.spacing.sm))
        if (onOpenInNewWindow != null) {
            Box {
                Text(
                    text = "\u22EE",
                    color = tokens.colors.label,
                    fontSize = tokens.typography.controlSize,
                    fontFamily = TerminalFontFamily,
                    modifier = Modifier
                        .clickable { menuExpanded = true }
                        .padding(horizontal = tokens.spacing.xxs)
                )
                TerminalDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    TerminalMenuItem(
                        text = "Open in new window",
                        onClick = {
                            menuExpanded = false
                            onOpenInNewWindow()
                        }
                    )
                }
            }
            Spacer(Modifier.width(tokens.spacing.xs))
        }
        Text(
            text = "×",
            color = tokens.colors.label,
            fontSize = tokens.typography.bodySize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onClose() }
        )
    }
}
