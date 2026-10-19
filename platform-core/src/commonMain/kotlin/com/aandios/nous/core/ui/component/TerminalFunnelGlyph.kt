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

/** Обводка-воронка (фильтр/сортировка) — общая для explorer и trades-фильтра. */
@Composable
fun TerminalFunnelGlyph(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = 1.2.dp.toPx()
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.08f, h * 0.12f)
            lineTo(w * 0.92f, h * 0.12f)
            lineTo(w * 0.58f, h * 0.55f)
            lineTo(w * 0.58f, h * 0.9f)
            lineTo(w * 0.42f, h * 0.9f)
            lineTo(w * 0.42f, h * 0.55f)
            close()
        }
        drawPath(path, color = color, style = Stroke(stroke))
    }
}
