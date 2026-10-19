/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens

/** Ось, вдоль которой тянется [TerminalResizeGrip]. */
enum class TerminalResizeOrientation {
    HORIZONTAL,
    VERTICAL,
}

/**
 * Угловая ручка ресайза: диагональная двухсторонняя стрелка (как в прототипе),
 * тянется по одной оси — горизонтально (ширина панели) или вертикально
 * (высота списка). Общая для тул-панели и футера explorer'а.
 */
@Composable
fun TerminalResizeGrip(
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier,
    orientation: TerminalResizeOrientation = TerminalResizeOrientation.HORIZONTAL,
) {
    val tokens = LocalTerminalDesignTokens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color = if (hovered) tokens.colors.accent else tokens.colors.label.copy(alpha = 0.55f)
    // pointerInput не перезапускается: обработчик читаем через
    // rememberUpdatedState, иначе жест навсегда замыкается на первый колбэк,
    // который считает размер от устаревшего state (дрожание/«не тянется»).
    val currentOnDrag by rememberUpdatedState(onDrag)

    Box(
        modifier = modifier
            .size(16.dp)
            .hoverable(interaction)
            .pointerInput(orientation) {
                when (orientation) {
                    TerminalResizeOrientation.HORIZONTAL ->
                        detectHorizontalDragGestures { _, dragAmount -> currentOnDrag(dragAmount) }

                    TerminalResizeOrientation.VERTICAL ->
                        detectVerticalDragGestures { _, dragAmount -> currentOnDrag(dragAmount) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(12.dp)) {
            val stroke = 1.4.dp.toPx()
            val inset = size.minDimension * 0.12f
            val topLeft = Offset(inset, inset)
            val bottomRight = Offset(size.width - inset, size.height - inset)
            drawLine(color, topLeft, bottomRight, strokeWidth = stroke)
            val head = size.minDimension * 0.26f
            drawLine(
                color,
                topLeft,
                Offset(topLeft.x + head, topLeft.y),
                strokeWidth = stroke,
            )
            drawLine(
                color,
                topLeft,
                Offset(topLeft.x, topLeft.y + head),
                strokeWidth = stroke,
            )
            drawLine(
                color,
                bottomRight,
                Offset(bottomRight.x - head, bottomRight.y),
                strokeWidth = stroke,
            )
            drawLine(
                color,
                bottomRight,
                Offset(bottomRight.x, bottomRight.y - head),
                strokeWidth = stroke,
            )
        }
    }
}
