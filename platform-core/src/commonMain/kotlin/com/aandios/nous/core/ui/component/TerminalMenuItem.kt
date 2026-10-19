/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily

/** Высота строки меню — как у строк списка workspaces (26dp). */
val TerminalMenuItemHeight: Dp = 26.dp

/**
 * Единая компактная строка выпадающего меню: высота 26dp, левая полоска 2dp
 * (акцентная у выбранного пункта — стиль explorer/Market Watch), фон
 * selection у выбранного и panelHeaderSurface при наведении.
 */
@Composable
fun TerminalMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val tokens = LocalTerminalDesignTokens.current
    TerminalMenuItem(
        onClick = onClick,
        modifier = modifier,
        selected = selected,
        enabled = enabled,
    ) {
        Text(
            text = text,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else tokens.colors.label.copy(alpha = 0.5f),
            fontSize = tokens.typography.compactSize,
            lineHeight = tokens.typography.controlLineHeight,
            fontFamily = TerminalFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TerminalMenuItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TerminalMenuItemHeight)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .hoverable(interaction, enabled = enabled)
            .background(
                when {
                    selected -> tokens.colors.selection
                    hovered && enabled -> tokens.colors.panelHeaderSurface
                    else -> Color.Transparent
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(if (selected) tokens.colors.accent else Color.Transparent),
        )
        Spacer(Modifier.width(tokens.spacing.md))
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
