/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily

/** Режимы работы терминала (визуальные табы). */
enum class TerminalMode(val label: String) {
    ACTIVE_FLOW("Active Flow"),
    BTC_SUITE("BTC Suite"),
    EXECUTION("Execution"),
    RESEARCH("Research"),
}

/**
 * Вторая строка окна: табы режима, подсказка, индикаторы DRAG/RESIZE.
 */
@Composable
fun TerminalModeBar(
    currentMode: TerminalMode,
    onModeChange: (TerminalMode) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "Drag panel headers to rearrange your workspace",
) {
    val tokens = LocalTerminalDesignTokens.current
    val shape = RoundedCornerShape(tokens.cornerRadii.workspacePanel)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(
                horizontal = tokens.dimensions.workspacePanelGap / 2,
                vertical = tokens.dimensions.workspacePanelGap / 2,
            )
            .clip(shape)
            .border(tokens.dimensions.borderWidth, tokens.colors.panelBorder, shape),
        color = tokens.colors.panelSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(tokens.colors.workspaceBackground)
                    .padding(horizontal = tokens.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ── Табы режима ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
                ) {
                    TerminalMode.entries.forEach { mode ->
                        val active = mode == currentMode
                        Text(
                            text = mode.label,
                            color = if (active) MaterialTheme.colorScheme.onSurface else tokens.colors.label,
                            fontSize = tokens.typography.compactSize,
                            fontFamily = TerminalFontFamily,
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                            modifier = Modifier
                                .background(
                                    if (active) tokens.colors.selection else Color.Transparent,
                                    RoundedCornerShape(tokens.cornerRadii.smallCard),
                                )
                                .clickable { onModeChange(mode) }
                                .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.xxs),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // ── Подсказка ──
                Text(
                    text = hint,
                    color = tokens.colors.label.copy(alpha = 0.55f),
                    fontSize = tokens.typography.microSize,
                    fontFamily = TerminalFontFamily,
                )

                Spacer(Modifier.weight(1f))

                // ── DRAG | RESIZE ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
                ) {
                    DragResizeIndicator(label = "DRAG", symbol = "⣿")
                    DragResizeIndicator(label = "RESIZE", symbol = "⣿")
                }
            }
        }
    }
}

@Composable
private fun DragResizeIndicator(label: String, symbol: String) {
    val tokens = LocalTerminalDesignTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xxs),
    ) {
        Text(
            text = symbol,
            color = tokens.colors.label.copy(alpha = 0.7f),
            fontSize = tokens.typography.microSize,
            fontFamily = TerminalFontFamily,
        )
        Text(
            text = label,
            color = tokens.colors.label.copy(alpha = 0.7f),
            fontSize = tokens.typography.microSize,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.Medium,
        )
    }
}