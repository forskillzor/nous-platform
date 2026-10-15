/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily

/**
 * Компактный inline-селект хедеров панелей: `label value ▾` одной строкой,
 * mono-шрифт, без внешней рамки и вложенных Surface — высота ~18dp.
 * Используется в DOM и Trades хедерах (единый стиль).
 */
@Composable
fun <T> TerminalInlineSelect(
    label: String,
    current: T,
    items: List<T>,
    display: (T) -> String,
    onSelect: (T) -> Unit,
    menuWidth: Dp? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalTerminalDesignTokens.current
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { expanded = true }
                .padding(horizontal = tokens.spacing.xs, vertical = tokens.spacing.xxs),
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = tokens.typography.microSize,
                maxLines = 1,
            )
            Spacer(Modifier.width(tokens.spacing.xs))
            Text(
                text = display(current),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = tokens.typography.inlineSelectValueSize,
                fontWeight = FontWeight.Medium,
                fontFamily = TerminalFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(tokens.spacing.xxs))
            Text(
                text = "▾",
                color = tokens.colors.label,
                fontSize = tokens.typography.microSize,
                fontFamily = TerminalFontFamily,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(menuWidth ?: tokens.dimensions.inlineSelectMenuWidth),
        ) {
            items.forEach { item ->
                val selected = item == current
                DropdownMenuItem(
                    text = {
                        Text(
                            text = display(item),
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                            fontSize = tokens.typography.controlSize,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    },
                )
            }
        }
    }
}
