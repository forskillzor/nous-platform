/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalSemanticColors
import com.aandios.nous.core.workspace.PanelType
import com.aandios.nous.core.workspace.WorkspaceConfig

/**
 * Миниатюра раскладки workspace в навигаторе: та же отрисовка, что и
 * [WorkspaceLayoutThumbnail] на WelcomeScreen — прямоугольники панелей по
 * дереву сплитов [WorkspaceConfig.layout] в семантических цветах типов,
 * но без подписей и в маленьком размере.
 */
@Composable
fun NavigatorMiniMapGlyph(
    config: WorkspaceConfig,
    active: Boolean = false,
    preview: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val panels = remember(config.panels) { config.panels.associateBy { it.id } }
    val tokens = LocalTerminalDesignTokens.current
    val fillAlpha = when {
        active -> 0.28f
        preview -> 0.22f
        else -> 0.16f
    }
    val borderAlpha = when {
        active -> 0.9f
        preview -> 0.75f
        else -> 0.6f
    }
    Canvas(modifier = modifier.size(16.dp)) {
        val gap = 1.dp.toPx()
        val stroke = tokens.dimensions.borderWidth.toPx().coerceAtLeast(0.5f)
        collectRects(config.layout, 0f, 0f, size.width, size.height, gap).forEach { (rect, panelId) ->
            val color = panelPreviewColor(panels[panelId]?.type, tokens.colors)
            drawRect(
                color = color.copy(alpha = fillAlpha),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
            )
            drawRect(
                color = color.copy(alpha = borderAlpha),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                style = Stroke(width = stroke),
            )
        }
    }
}

private fun panelPreviewColor(type: PanelType?, colors: TerminalSemanticColors): Color = when (type) {
    PanelType.CHART -> colors.bullish
    PanelType.DOM -> colors.sma
    PanelType.TRADES -> colors.warning
    PanelType.TRADING -> colors.bollinger
    null -> colors.neutral
}
