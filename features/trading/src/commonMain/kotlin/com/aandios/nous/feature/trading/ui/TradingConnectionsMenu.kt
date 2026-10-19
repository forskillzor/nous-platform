/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trading.ui
import com.aandios.nous.core.ui.theme.TerminalFontFamily

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.component.TerminalDropdownMenu
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens

/**
 * Кнопка Manage connections в заголовке trading panel. Глиф (сигнал)
 * окрашен по худшему статусу: error — красный, connecting — жёлтый,
 * connected — акцент.
 */
@Composable
fun ConnectionsButton(
    connections: List<TradingConnection>,
    onClick: () -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color = when {
        connections.any { it.status == TradingConnectionStatus.ERROR } -> tokens.colors.loss
        connections.any { it.status == TradingConnectionStatus.CONNECTING } -> tokens.colors.warning
        else -> tokens.colors.accent
    }
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(tokens.cornerRadii.smallCard))
            .background(if (hovered) tokens.colors.panelHeaderSurface else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ConnectionsGlyph(color = color)
    }
}

/**
 * Контекстное окно Manage connections: строки REST API / приватного стрима
 * со статусом CONNECTED / CONNECTING / ERROR. Клик по строке с ошибкой
 * открывает окно с текстом ошибки (обрабатывает вызывающая сторона).
 */
@Composable
fun ConnectionsMenu(
    expanded: Boolean,
    connections: List<TradingConnection>,
    onDismiss: () -> Unit,
    onConnectionClick: (TradingConnection) -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    TerminalDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Column(modifier = Modifier.width(240.dp).padding(vertical = tokens.spacing.xs)) {
            Text(
                text = "MANAGE CONNECTIONS",
                color = tokens.colors.label,
                fontSize = tokens.typography.smallSize,
                lineHeight = tokens.typography.compactLineHeight,
                letterSpacing = tokens.typography.compactLetterSpacing,
                fontWeight = FontWeight.Medium,
                fontFamily = TerminalFontFamily,
                modifier = Modifier.padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
            )
            connections.forEach { connection ->
                ConnectionRow(
                    connection = connection,
                    onClick = if (connection.status == TradingConnectionStatus.ERROR) {
                        { onConnectionClick(connection) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun ConnectionRow(
    connection: TradingConnection,
    onClick: (() -> Unit)?,
) {
    val tokens = LocalTerminalDesignTokens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val statusColor = when (connection.status) {
        TradingConnectionStatus.CONNECTED -> tokens.colors.profit
        TradingConnectionStatus.CONNECTING -> tokens.colors.warning
        TradingConnectionStatus.ERROR -> tokens.colors.loss
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.xs)
            .clip(RoundedCornerShape(tokens.cornerRadii.smallCard))
            .background(if (hovered && onClick != null) tokens.colors.panelHeaderSurface else Color.Transparent)
            .hoverable(interaction)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = connection.label,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            fontSize = tokens.typography.compactSize,
            lineHeight = tokens.typography.controlLineHeight,
            fontFamily = TerminalFontFamily,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = connection.status.name,
            color = statusColor,
            fontSize = tokens.typography.smallSize,
            lineHeight = tokens.typography.compactLineHeight,
            fontWeight = FontWeight.Medium,
            fontFamily = TerminalFontFamily,
            maxLines = 1,
        )
    }
}

/** Глиф соединения: точка + три дуги вверх (terminal-стиль, как другие глифы). */
@Composable
private fun ConnectionsGlyph(color: Color) {
    Canvas(modifier = Modifier.size(14.dp)) {
        val stroke = 1.2.dp.toPx()
        val cx = size.width / 2f
        val cy = size.height * 0.8f
        drawCircle(color = color, radius = stroke, center = Offset(cx, cy))
        listOf(3.2.dp, 4.8.dp, 6.4.dp).forEach { radiusDp ->
            val radius = radiusDp.toPx()
            drawArc(
                color = color,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}
