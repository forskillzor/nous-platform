/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily

/** Режимы терминала во второй полосе (визуальные табы). */
enum class TerminalMode(val label: String) {
    WORKSPACES("Workspaces"),
    RESEARCH("Research"),
    BACKTESTING("Backtesting"),
    EXECUTION("Execution"),
}

/**
 * Вторая полоса окна: режимы терминала и подсказка о перетаскивании панелей.
 * Режимы пока визуальные — выбор ни на что не влияет, состояние локальное.
 */
@Composable
fun TerminalModeBar(
    currentMode: TerminalMode,
    onModeChange: (TerminalMode) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "Drag panel headers to rearrange your workspace",
) {
    val tokens = LocalTerminalDesignTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(tokens.dimensions.workspacePanelHeaderHeight)
            .background(tokens.colors.panelHeaderSurface)
            .padding(horizontal = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TerminalMode.entries.forEach { mode ->
            val active = mode == currentMode
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(end = tokens.spacing.xs)
                    .clip(RoundedCornerShape(tokens.cornerRadii.smallCard))
                    .background(if (active) tokens.colors.selection else Color.Transparent)
                    .clickable { onModeChange(mode) }
                    .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.xxs),
            ) {
                Text(
                    text = mode.label,
                    color = if (active) MaterialTheme.colorScheme.onSurface else tokens.colors.label,
                    fontSize = tokens.typography.compactSize,
                    fontFamily = TerminalFontFamily,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                )
                if (active) {
                    Spacer(Modifier.width(tokens.spacing.xxs))
                    Text(
                        text = "▾",
                        color = tokens.colors.label,
                        fontSize = tokens.typography.microSize,
                        fontFamily = TerminalFontFamily,
                    )
                }
            }
        }
        Spacer(Modifier.width(tokens.spacing.md))
        Text(
            text = hint,
            color = tokens.colors.label.copy(alpha = 0.55f),
            fontSize = tokens.typography.microSize,
            fontFamily = TerminalFontFamily,
        )
    }
}
