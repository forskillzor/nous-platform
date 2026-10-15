/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.component.TerminalSwitch
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val utcTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * Верхняя полоса окна терминала: логотип, вкладки workspace, статус подключения,
 * часы UTC, Paper, иконки темы и настроек.
 *
 * @param tabsSlot слот для TabBar (рендерится после логотипа, растягивается).
 * @param paperEnabled текущее состояние paper trading (глобальный флаг панели).
 * @param onPaperToggle переключение Paper.
 * @param onThemeClick открыть выбор темы.
 * @param onSettingsClick открыть настройки.
 */
@Composable
fun TerminalTopBar(
    tabsSlot: @Composable RowScope.() -> Unit,
    paperEnabled: Boolean,
    onPaperToggle: (Boolean) -> Unit,
    onThemeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    connected: Boolean = true,
) {
    val tokens = LocalTerminalDesignTokens.current
    val utcTime by produceState(initialValue = utcTimeLabel()) {
        while (true) {
            delay(1_000)
            value = utcTimeLabel()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(tokens.colors.workspaceBackground)
            .padding(horizontal = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Логотип ──
        Text(
            text = "◆",
            color = tokens.colors.accent,
            fontSize = tokens.typography.compactActionSize,
            fontFamily = TerminalFontFamily,
        )
        Spacer(Modifier.width(tokens.spacing.xs))
        Text(
            text = "NOUS PLATFORM",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = tokens.typography.controlSize,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.Bold,
            letterSpacing = tokens.typography.controlLetterSpacing,
        )
        Spacer(Modifier.width(tokens.spacing.md))

        // ── Вкладки workspace ──
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            content = tabsSlot,
        )

        // ── Правый блок: LIVE · время · Paper · тема · настройки ──
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

            // Время UTC (реактивное, обновляется раз в секунду)
            Text(
                text = utcTime,
                color = tokens.colors.label,
                fontSize = tokens.typography.microSize,
                fontFamily = TerminalFontFamily,
            )

            // Paper: подпись + компактный свич
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
            ) {
                Text(
                    text = "Paper",
                    color = if (paperEnabled) tokens.colors.accent else tokens.colors.label,
                    fontSize = tokens.typography.microSize,
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Medium,
                )
                // Отключаем M3 min-touch-target (48dp) — компактная полоса
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    TerminalSwitch(
                        checked = paperEnabled,
                        onCheckedChange = onPaperToggle,
                        modifier = Modifier.scale(0.7f),
                    )
                }
            }

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

private fun utcTimeLabel(): String =
    LocalTime.now(ZoneOffset.UTC).format(utcTimeFormatter) + " UTC"
