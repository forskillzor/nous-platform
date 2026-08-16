/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.aandios.nous.core.workspace.viewmodel.WorkspaceViewModel
import kotlin.math.roundToInt

@Composable
fun TabBar(
    workspaces: List<WorkspaceViewModel>,
    activeIndex: Int,
    onTabClick: (Int) -> Unit,
    onTabClose: (String) -> Unit,
    onTabReorder: ((Int, Int) -> Unit)? = null,
) {
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    var targetIndex by remember { mutableStateOf<Int?>(null) }
    var pendingReorder by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val density = LocalDensity.current
    val tabWidthEstimate = with(density) { 120.dp.toPx() }

    // Defer reorder to next frame — avoids recomposition during active drag
    if (onTabReorder != null) {
        LaunchedEffect(pendingReorder) {
            pendingReorder?.let { (from, to) ->
                onTabReorder(from, to)
                pendingReorder = null
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(Color(0xFF0A0A0A))
            .padding(start = 4.dp),
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
                        .width(2.dp)
                        .height(24.dp)
                        .background(Color(0xFF00C853))
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
                        if (onTabReorder != null) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    draggedIndex = index
                                    dragAccumulator = 0f
                                    targetIndex = null
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
                                },
                                onDragCancel = {
                                    draggedIndex = null
                                    dragAccumulator = 0f
                                    targetIndex = null
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
                    onClick = { if (!isDragged) onTabClick(index) },
                    onClose = { if (!isDragged) onTabClose(ws.config.id) }
                )
            }
        }

        // Insertion indicator after last tab
        if (targetIndex == workspaces.size && draggedIndex != null) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(24.dp)
                    .background(Color(0xFF00C853))
            )
        }
    }
}

@Composable
private fun TabItem(
    name: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(end = 1.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .background(if (isActive) Color(0xFF1E1E1E) else Color(0xFF111111))
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = if (isActive) Color(0xFF00C853) else Color(0xFF888888),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "×",
            color = if (isActive) Color(0xFF666666) else Color(0xFF444444),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onClose() }
        )
    }
}
