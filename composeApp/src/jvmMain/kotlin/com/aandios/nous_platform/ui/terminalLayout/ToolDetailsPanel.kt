/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.component.TerminalPanelHeader
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.workspace.WorkspaceConfig
import org.jetbrains.compose.resources.painterResource
import nous_platform.composeapp.generated.resources.Res
import nous_platform.composeapp.generated.resources.close

@Composable
fun ToolDetailsPanel(
    type: ToolPanelType,
    width: Dp,
    selectedSymbol: String,
    selectedProviderId: String? = null,
    onSymbolSelected: (String, String) -> Unit,
    selectedTimeframe: String,
    onTimeframeSelected: (String) -> Unit,
    onClose: () -> Unit,
    marketWatchTemplates: List<Pair<String, WorkspaceConfig>>,
    onOpenMarketWatchTemplate: ((WorkspaceConfig, String, String, String, String) -> Unit)?,
    onApplyToCurrentWorkspace: ((String, String, String) -> Unit)? = null,
    onWidthDelta: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier.Companion
) {
    val tokens = LocalTerminalDesignTokens.current
    val shape = RoundedCornerShape(tokens.cornerRadii.workspacePanel)
    Surface(
        modifier = modifier
            .padding(
                start = tokens.dimensions.workspacePanelGap,
                top = tokens.dimensions.workspacePanelGap,
                bottom = tokens.dimensions.workspacePanelGap,
            )
            .width(width)
            .clip(shape)
            .border(tokens.dimensions.borderWidth, tokens.colors.panelBorder, shape),
        color = tokens.colors.panelSurface,
    ) {
        Box(modifier = Modifier.Companion.fillMaxSize()) {
            if (type == ToolPanelType.SYMBOLS) {
                // Market Watch рисует собственный заголовок по прототипу
                SymbolsPanel(
                    selectedSymbol = selectedSymbol,
                    selectedProviderId = selectedProviderId,
                    onSymbolSelected = onSymbolSelected,
                    workspaceTemplates = marketWatchTemplates,
                    onOpenWorkspaceTemplate = onOpenMarketWatchTemplate,
                    onApplyToCurrentWorkspace = onApplyToCurrentWorkspace,
                    onClose = onClose,
                    modifier = Modifier.Companion.fillMaxSize(),
                )
            } else {
                Column(
                    modifier = Modifier.Companion.fillMaxSize()
                ) {
                    // Заголовок панели
                    TerminalPanelHeader {
                        Row(
                            modifier = Modifier.Companion.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Companion.CenterVertically
                        ) {
                            Text(
                                text = when (type) {
                                    ToolPanelType.SYMBOLS -> "Market Watch"
                                    ToolPanelType.INDICATORS -> "Indicators"
                                    ToolPanelType.TIMEFRAMES -> "Timeframes"
                                    ToolPanelType.DRAWINGS -> "Drawing Tools"
                                    ToolPanelType.STRATEGIES -> "Strategies"
                                    ToolPanelType.WORKSPACES -> "Workspaces"
                                },
                                fontSize = tokens.typography.controlSize,
                                fontFamily = com.aandios.nous.core.ui.theme.TerminalFontFamily,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )

                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.Companion.size(tokens.dimensions.compactControlHeight)
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.close),
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.Companion.size(16.dp)
                                )
                            }
                        }
                    }

                    // Контент в зависимости от типа
                    when (type) {
                        ToolPanelType.SYMBOLS -> {
                            // Content rendered by SymbolsPanel above
                        }

                        ToolPanelType.INDICATORS -> {
                            IndicatorsPanel(modifier = Modifier.Companion.weight(1f))
                        }

                        ToolPanelType.TIMEFRAMES -> {
                            TimeframesPanel(
                                selectedTimeframe = selectedTimeframe,
                                onTimeframeSelected = onTimeframeSelected,
                                modifier = Modifier.Companion.weight(1f)
                            )
                        }

                        ToolPanelType.DRAWINGS -> {
                            DrawingsPanel(modifier = Modifier.Companion.weight(1f))
                        }

                        ToolPanelType.STRATEGIES -> {
                            StrategiesPanel(modifier = Modifier.Companion.weight(1f))
                        }

                        ToolPanelType.WORKSPACES -> {
                            // Content rendered directly in TerminalLayout
                        }
                    }
                }
            }

            if (onWidthDelta != null) {
                CornerResizeGrip(
                    onDrag = onWidthDelta,
                    modifier = Modifier.Companion
                        .align(Alignment.BottomEnd)
                        .padding(tokens.spacing.sm),
                )
            }
        }
    }
}

/**
 * Угловая ручка ширины панели: диагональная стрелка в правом нижнем углу
 * островка (как в прототипе). Тянется горизонтально.
 */
@Composable
private fun CornerResizeGrip(
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier.Companion,
) {
    val tokens = LocalTerminalDesignTokens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color = if (hovered) tokens.colors.accent else tokens.colors.label.copy(alpha = 0.55f)
    // pointerInput(Unit) не перезапускается: обработчик читаем через
    // rememberUpdatedState, иначе жест навсегда замыкается на первый колбэк,
    // который считает ширину от устаревшего topPanelState (дрожание/«не тянется»).
    val currentOnDrag by rememberUpdatedState(onDrag)

    Box(
        modifier = modifier
            .size(16.dp)
            .hoverable(interaction)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount -> currentOnDrag(dragAmount) }
            },
        contentAlignment = Alignment.Companion.Center,
    ) {
        Canvas(modifier = Modifier.Companion.size(12.dp)) {
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