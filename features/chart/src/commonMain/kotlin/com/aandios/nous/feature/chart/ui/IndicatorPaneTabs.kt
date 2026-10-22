/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily
import com.aandios.nous.research.indicator.IndicatorState

@Composable
fun IndicatorPaneTabs(
    state: IndicatorState,
    onSelectPane: (String) -> Unit,
    onMovePane: (String, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    if (state.panes.isEmpty()) return
    val tokens = LocalTerminalDesignTokens.current
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    val scrollState = rememberScrollState()
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragTargetIndex by remember { mutableStateOf<Int?>(null) }
    val panes by rememberUpdatedState(state.panes)
    val currentOnMovePane by rememberUpdatedState(onMovePane)

    fun insertionIndex(x: Float, draggedId: String): Int {
        val others = panes.filterNot { it.id == draggedId }
        val count = others.count { pane ->
            val center = bounds[pane.id]?.center?.x ?: return@count false
            x > center
        }
        return count.coerceIn(0, others.size)
    }

    val targetPaneId = dragTargetIndex?.let { target ->
        panes.filterNot { it.id == draggingId }.getOrNull(target)?.id
            ?: panes.lastOrNull { it.id != draggingId }?.id
    }

    Row(
        modifier = modifier.horizontalScroll(scrollState, enabled = draggingId == null),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.panes.forEachIndexed { index, pane ->
            val active = pane.id == state.activePaneId
            val dragging = pane.id == draggingId
            val target = pane.id == targetPaneId
            val shape = RoundedCornerShape(tokens.cornerRadii.smallCard)
            Text(
                text = indicatorPaneTitle(pane),
                color = if (active || dragging) tokens.colors.accent else tokens.colors.label,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                fontFamily = TerminalFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 170.dp)
                    .clip(shape)
                    .background(
                        when {
                            dragging -> tokens.colors.selection
                            active -> tokens.colors.selection
                            else -> tokens.colors.panelSurface.copy(alpha = 0.88f)
                        },
                    )
                    .then(
                        if (target && draggingId != null) {
                            Modifier.border(1.dp, tokens.colors.accent, shape)
                        } else {
                            Modifier
                        },
                    )
                    .onGloballyPositioned { bounds[pane.id] = it.boundsInRoot() }
                    .pointerInput(pane.id) {
                        var dragTotalX = 0f
                        detectDragGestures(
                            onDragStart = {
                                dragTotalX = 0f
                                draggingId = pane.id
                                dragTargetIndex = index
                            },
                            onDragCancel = {
                                draggingId = null
                                dragTargetIndex = null
                            },
                            onDragEnd = {
                                val id = draggingId
                                val targetIndex = dragTargetIndex
                                if (id != null && targetIndex != null) {
                                    currentOnMovePane(id, targetIndex)
                                }
                                draggingId = null
                                dragTargetIndex = null
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragTotalX += dragAmount.x
                                val centerX = bounds[pane.id]?.center?.x ?: return@detectDragGestures
                                dragTargetIndex = insertionIndex(centerX + dragTotalX, pane.id)
                            },
                        )
                    }
                    .clickable { onSelectPane(pane.id) }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}
