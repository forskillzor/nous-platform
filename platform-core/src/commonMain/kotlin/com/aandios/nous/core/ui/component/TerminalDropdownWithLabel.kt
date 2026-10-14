/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens

/**
 * Обертка для dropdown-компонента с label слева и бордером.
 *
 * @param label Текст label, отображаемый слева от dropdown
 * @param content Composable-функция, содержащая сам dropdown
 * @param modifier Modifier для контейнера
 */
@Composable
fun TerminalDropdownWithLabel(
    label: String,
    modifier: Modifier = Modifier.Companion,
    content: @Composable () -> Unit
) {
    val tokens = LocalTerminalDesignTokens.current
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(
            width = tokens.dimensions.borderWidth,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)
        ),
        color = Color.Companion.Transparent,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.Companion.CenterVertically,
            modifier = Modifier.Companion.padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.none)
        ) {
            if (label.isNotEmpty()) {
                // Label слева
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    fontWeight = FontWeight.Companion.Medium,
                    letterSpacing = tokens.typography.compactLetterSpacing,
                    modifier = Modifier.Companion.padding(end = tokens.spacing.md)
                )

                // Вертикальный разделитель
                Spacer(
                    modifier = Modifier.Companion
                        .width(tokens.dimensions.dropdownLabelSeparatorWidth)
                        .height(tokens.dimensions.dropdownLabelSeparatorHeight)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.8f))
                )
            }

            // Dropdown справа
            Box(
                modifier = Modifier.Companion.padding(
                    start = if (label.isNotEmpty()) tokens.spacing.md else tokens.spacing.none
                )
            ) {
                content()
            }
        }
    }
}