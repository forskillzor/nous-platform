/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens

enum class TerminalGripOrientation {
    VERTICAL,
    HORIZONTAL,
}

@Composable
fun TerminalGripDots(
    orientation: TerminalGripOrientation,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val dimensions = LocalTerminalDesignTokens.current.dimensions
    Canvas(modifier.fillMaxSize()) {
        val radius = with(density) { dimensions.resizeGripDotRadius.toPx() }
        val spacing = with(density) { dimensions.resizeGripDotSpacing.toPx() }
        val horizontalPositions = when (orientation) {
            TerminalGripOrientation.VERTICAL -> listOf(-0.5f, 0.5f)
            TerminalGripOrientation.HORIZONTAL -> listOf(-1f, 0f, 1f)
        }
        val verticalPositions = when (orientation) {
            TerminalGripOrientation.VERTICAL -> listOf(-1f, 0f, 1f)
            TerminalGripOrientation.HORIZONTAL -> listOf(-0.5f, 0.5f)
        }

        horizontalPositions.forEach { x ->
            verticalPositions.forEach { y ->
                drawCircle(
                    color = color,
                    radius = radius,
                    center = Offset(
                        x = size.width / 2f + x * spacing,
                        y = size.height / 2f + y * spacing,
                    ),
                )
            }
        }
    }
}
