/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Стрелки вверх/вниз — триггер сортировки explorer и Market Watch (не фильтр). */
@Composable
fun TerminalSortGlyph(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = 1.2.dp.toPx()
        val w = this.size.width
        val h = this.size.height
        val up = Path().apply {
            moveTo(w * 0.33f, h * 0.82f)
            lineTo(w * 0.33f, h * 0.2f)
            moveTo(w * 0.2f, h * 0.34f)
            lineTo(w * 0.33f, h * 0.2f)
            lineTo(w * 0.46f, h * 0.34f)
        }
        val down = Path().apply {
            moveTo(w * 0.67f, h * 0.18f)
            lineTo(w * 0.67f, h * 0.8f)
            moveTo(w * 0.54f, h * 0.66f)
            lineTo(w * 0.67f, h * 0.8f)
            lineTo(w * 0.8f, h * 0.66f)
        }
        drawPath(up, color = color, style = Stroke(stroke))
        drawPath(down, color = color, style = Stroke(stroke))
    }
}
