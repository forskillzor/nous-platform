/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.theme.ChartColors
import com.aandios.nous.feature.chart.tools.DrawingToolType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val panelBg = Color.Black.copy(alpha = 0.35f)
private val accentColor = Color(0xFF5B9BD5)
private val iconColor = Color(0xFF8A97A5)

private data class ToolEntry(
    val tool: DrawingToolType,
    val icon: DrawScope.(Color, Float) -> Unit,
)

private val drawingTools = listOf(
    ToolEntry(DrawingToolType.TREND_LINE) { c, s -> iconTrendLine(c, s) },
    ToolEntry(DrawingToolType.HORIZONTAL) { c, s -> iconHorizontal(c, s) },
    ToolEntry(DrawingToolType.RECTANGLE) { c, s -> iconRectangle(c, s) },
    ToolEntry(DrawingToolType.VERTICAL) { c, s -> iconVertical(c, s) },
    ToolEntry(DrawingToolType.RULER) { c, s -> iconRuler(c, s) },
)

/**
 * Левая панель инструментов рисования в стиле TradingView:
 * вертикальный ряд иконок (линии, фигуры, линейка) + undo/redo.
 */
@Composable
fun DrawingToolPanel(
    activeTool: DrawingToolType,
    onToolChange: (DrawingToolType) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .border(1.dp, ChartColors.gridLine, RoundedCornerShape(8.dp))
            .background(panelBg, RoundedCornerShape(8.dp))
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        drawingTools.forEach { entry ->
            PanelIconButton(
                active = activeTool == entry.tool,
                onClick = { onToolChange(if (activeTool == entry.tool) DrawingToolType.NONE else entry.tool) },
                icon = entry.icon,
            )
        }

        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(26.dp)
                .height(1.dp)
                .background(Color(0xFF3A4550))
        )
        Spacer(Modifier.height(6.dp))

        PanelIconButton(
            active = false,
            enabled = canUndo,
            onClick = onUndo,
            icon = { c, s -> iconUndo(c, s) },
        )
        PanelIconButton(
            active = false,
            enabled = canRedo,
            onClick = onRedo,
            icon = { c, s -> iconRedo(c, s) },
        )
    }
}

@Composable
private fun PanelIconButton(
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    icon: DrawScope.(Color, Float) -> Unit,
) {
    val tint = if (active) Color.White else iconColor
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) accentColor.copy(alpha = 0.35f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(5.dp)
            .size(16.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            icon(tint.copy(alpha = if (enabled) 1f else 0.35f), size.minDimension)
        }
    }
}

// ─── Иконки (стиль TradingView) ─────────────────────────────────────────────

private fun DrawScope.iconTrendLine(c: Color, s: Float) {
    drawLine(c, Offset(s * 0.15f, s * 0.85f), Offset(s * 0.85f, s * 0.15f), strokeWidth = 1.5f)
    drawCircle(c, radius = s * 0.09f, center = Offset(s * 0.15f, s * 0.85f))
    drawCircle(c, radius = s * 0.09f, center = Offset(s * 0.85f, s * 0.15f))
}

private fun DrawScope.iconHorizontal(c: Color, s: Float) {
    drawLine(c, Offset(s * 0.12f, s * 0.5f), Offset(s * 0.88f, s * 0.5f), strokeWidth = 1.5f)
    drawCircle(c, radius = s * 0.09f, center = Offset(s * 0.12f, s * 0.5f))
    drawCircle(c, radius = s * 0.09f, center = Offset(s * 0.88f, s * 0.5f))
}

private fun DrawScope.iconRectangle(c: Color, s: Float) {
    drawRect(
        color = c,
        topLeft = Offset(s * 0.2f, s * 0.3f),
        size = Size(s * 0.6f, s * 0.45f),
        style = Stroke(width = 1.5f)
    )
}

private fun DrawScope.iconVertical(c: Color, s: Float) {
    drawLine(c, Offset(s * 0.5f, s * 0.12f), Offset(s * 0.5f, s * 0.88f), strokeWidth = 1.5f)
    drawCircle(c, radius = s * 0.09f, center = Offset(s * 0.5f, s * 0.12f))
    drawCircle(c, radius = s * 0.09f, center = Offset(s * 0.5f, s * 0.88f))
}

private fun DrawScope.iconRuler(c: Color, s: Float) {
    drawRect(
        color = c,
        topLeft = Offset(s * 0.08f, s * 0.28f),
        size = Size(s * 0.84f, s * 0.32f),
        style = Stroke(width = 1.5f)
    )
    for (i in 0..4) {
        val x = s * (0.08f + 0.84f * i / 4f)
        drawLine(c, Offset(x, s * 0.28f), Offset(x, s * 0.44f), strokeWidth = 1f)
    }
}

private fun DrawScope.iconUndo(c: Color, s: Float) {
    val center = Offset(s / 2f, s / 2f)
    val r = s * 0.32f
    drawArc(
        color = c,
        startAngle = 90f,
        sweepAngle = -300f,
        useCenter = false,
        topLeft = Offset(center.x - r, center.y - r),
        size = Size(r * 2, r * 2),
        style = Stroke(width = 1.5f)
    )
    val endAngle = 90f - 300f
    val rad = endAngle * PI.toFloat() / 180f
    val tip = Offset(center.x + r * cos(rad), center.y + r * sin(rad))
    val dirRad = (endAngle + 90f) * PI.toFloat() / 180f
    drawArrowHead(c, tip, Offset(cos(dirRad), sin(dirRad)), s * 0.22f)
}

private fun DrawScope.iconRedo(c: Color, s: Float) {
    val center = Offset(s / 2f, s / 2f)
    val r = s * 0.32f
    drawArc(
        color = c,
        startAngle = 90f,
        sweepAngle = 300f,
        useCenter = false,
        topLeft = Offset(center.x - r, center.y - r),
        size = Size(r * 2, r * 2),
        style = Stroke(width = 1.5f)
    )
    val endAngle = 90f + 300f
    val rad = endAngle * PI.toFloat() / 180f
    val tip = Offset(center.x + r * cos(rad), center.y + r * sin(rad))
    val dirRad = (endAngle - 90f) * PI.toFloat() / 180f
    drawArrowHead(c, tip, Offset(cos(dirRad), sin(dirRad)), s * 0.22f)
}

private fun DrawScope.drawArrowHead(c: Color, tip: Offset, dir: Offset, size: Float) {
    val perp = Offset(-dir.y, dir.x)
    val base = Offset(tip.x - dir.x * size, tip.y - dir.y * size)
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base.x + perp.x * size * 0.55f, base.y + perp.y * size * 0.55f)
        lineTo(base.x - perp.x * size * 0.55f, base.y - perp.y * size * 0.55f)
        close()
    }
    drawPath(path, c)
}
