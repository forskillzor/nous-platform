/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import nous_platform.composeapp.generated.resources.Res
import nous_platform.composeapp.generated.resources.close

@Composable
fun ToolDetailsPanel(
    type: ToolPanelType,
    width: Dp,
    selectedSymbol: String,
    onSymbolSelected: (String) -> Unit,
    selectedTimeframe: String,
    onTimeframeSelected: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier.Companion
) {
    Surface(
        modifier = modifier.width(width),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.Companion.fillMaxSize()
        ) {
            // Заголовок панели
            Row(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Companion.CenterVertically
            ) {
                Text(
                    text = when (type) {
                        ToolPanelType.SYMBOLS -> "Symbols"
                        ToolPanelType.INDICATORS -> "Indicators"
                        ToolPanelType.TIMEFRAMES -> "Timeframes"
                        ToolPanelType.DRAWINGS -> "Drawing Tools"
                        ToolPanelType.STRATEGIES -> "Strategies"
                        ToolPanelType.WORKSPACES -> "Workspaces"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.Companion.size(24.dp)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.close), // Замени на свою иконку
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.Companion.size(16.dp)
                    )
                }
            }

            HorizontalDivider()

            // Контент в зависимости от типа
            when (type) {
                ToolPanelType.SYMBOLS -> {
                    SymbolsPanel(
                        selectedSymbol = selectedSymbol,
                        onSymbolSelected = onSymbolSelected,
                        modifier = Modifier.Companion.weight(1f)
                    )
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
}