/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily

/**
 * Воздушный заголовок панелей левой колонки (WORKSPACES, MARKET WATCH и
 * остальные tool-панели): глиф, капс-тайтл с опциональным счётчиком и слот
 * действий справа. Единый стиль для всех левых панелей.
 */
@Composable
fun ToolPanelTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val tokens = LocalTerminalDesignTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .padding(horizontal = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(tokens.spacing.sm))
        }
        Text(
            text = title.uppercase(),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = tokens.typography.compactSize,
            lineHeight = tokens.typography.controlLineHeight,
            letterSpacing = tokens.typography.compactLetterSpacing,
            fontWeight = FontWeight.Bold,
            fontFamily = TerminalFontFamily,
            maxLines = 1,
        )
        if (count != null) {
            Spacer(Modifier.width(tokens.spacing.sm))
            Text(
                text = count.toString(),
                color = tokens.colors.label,
                fontSize = tokens.typography.compactSize,
                lineHeight = tokens.typography.controlLineHeight,
                fontFamily = TerminalFontFamily,
                maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}
