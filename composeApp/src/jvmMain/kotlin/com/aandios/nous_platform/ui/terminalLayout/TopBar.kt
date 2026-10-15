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

/**
 * Верхняя строка окна терминала: логотип, вкладки workspace, статус подключения,
 * время, Paper toggle, иконки темы и настроек.
 *
 * @param tabsSlot слот для TabBar (рендерится после логотипа, растягивается).
 * @param paperEnabled текущее состояние paper trading (глобально на окно).
 * @param onPaperToggle переключение Paper.
 * @param onThemeClick открыть выбор темы.
 * @param onSettingsClick открыть настройки.
 */
@Composable
fun TerminalTopBar(
    tabsSlot: @Composable RowScope.() -> Unit,
    paperEnabled: Boolean,
    onPaperToggle: () -> Unit,
    onThemeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    connected: Boolean = true,
) {
    val tokens = LocalTerminalDesignTokens.current
    val shape = RoundedCornerShape(tokens.cornerRadii.workspacePanel)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
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
                .padding(horizontal = tokens.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(tokens.colors.workspaceBackground)
                    .padding(horizontal = tokens.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            ) {
                // ── Логотип ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
                ) {
                    Text(
                        text = "◆",
                        color = tokens.colors.accent,
                        fontSize = tokens.typography.compactActionSize,
                        fontFamily = TerminalFontFamily,
                    )
                    Text(
                        text = "NOUS PLATFORM",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = tokens.typography.controlSize,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = tokens.typography.controlLetterSpacing,
                    )
                }

                Spacer(Modifier.width(tokens.spacing.md))

                // ── Вкладки workspace ──
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    content = tabsSlot,
                )

                // ── Правый блок: LIVE · время · Paper · theme · settings ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
                ) {
                    // LIVE индикатор
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (connected) tokens.colors.profit else tokens.colors.loss,
                                    RoundedCornerShape(50),
                                )
                        )
                        Text(
                            text = if (connected) "LIVE" else "OFFLINE",
                            color = if (connected) tokens.colors.profit else tokens.colors.loss,
                            fontSize = tokens.typography.microSize,
                            fontFamily = TerminalFontFamily,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    // Время (заглушка, подключается отдельно)
                    Text(
                        text = currentUtcTimeLabel(),
                        color = tokens.colors.label,
                        fontSize = tokens.typography.microSize,
                        fontFamily = TerminalFontFamily,
                    )

                    // Paper toggle
                    Text(
                        text = "Paper",
                        color = if (paperEnabled) tokens.colors.accent else tokens.colors.label,
                        fontSize = tokens.typography.microSize,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .background(
                                if (paperEnabled) tokens.colors.accent.copy(alpha = 0.15f) else Color.Transparent,
                                RoundedCornerShape(tokens.cornerRadii.smallCard),
                            )
                            .border(
                                tokens.dimensions.borderWidth,
                                if (paperEnabled) tokens.colors.accent.copy(alpha = 0.55f) else tokens.colors.panelBorder,
                                RoundedCornerShape(tokens.cornerRadii.smallCard),
                            )
                            .clickable(onClick = onPaperToggle)
                            .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.xxs),
                    )

                    // Иконка темы
                    Text(
                        text = "◐",
                        color = tokens.colors.label,
                        fontSize = tokens.typography.controlSize,
                        fontFamily = TerminalFontFamily,
                        modifier = Modifier
                            .clickable(onClick = onThemeClick)
                            .padding(tokens.spacing.xs),
                    )

                    // Иконка настроек
                    Text(
                        text = "⚙",
                        color = tokens.colors.label,
                        fontSize = tokens.typography.controlSize,
                        fontFamily = TerminalFontFamily,
                        modifier = Modifier
                            .clickable(onClick = onSettingsClick)
                            .padding(tokens.spacing.xs),
                    )
                }
            }
        }
    }
}

/** Время UTC "HH:mm:ss UTC" — простая заглушка без реактивности (обновится позже). */
private fun currentUtcTimeLabel(): String {
    // TODO: заменить на реактивное обновление каждую секунду
    return "14:32:08 UTC"
}