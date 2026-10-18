/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily

/**
 * Стиль «коробки»-дропдауна:
 * - [NORMAL] — приглушённая заливка, белый текст (символ, списки MW);
 * - [ACCENT] — заливка surfaceVariant, акцентный текст (биржа, режим графика);
 * - [SELECTED] — сплошная акцентная заливка, тёмный текст, без шеврона
 *   (выбранный таймфрейм — «кнопкой», но всё ещё дропдаун).
 */
enum class TerminalBoxStyle { NORMAL, ACCENT, SELECTED }

/**
 * Общий дропдаун-«коробка» в стиле LIST-дропдауна Market Watch:
 * скруглённая рамка, компактные размеры (11sp, 20dp высота),
 * моно-текст + шеврон. Переиспользуется в тайтлах панелей и хедерах.
 */
@Composable
fun <T> TerminalBoxDropdown(
    current: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    style: TerminalBoxStyle = TerminalBoxStyle.NORMAL,
    menuWidth: Dp = 140.dp,
) {
    TerminalBoxDropdown(
        text = optionLabel(current),
        modifier = modifier,
        style = style,
        menuWidth = menuWidth,
    ) { dismiss ->
        options.forEach { option ->
            val isSelected = option == current
            DropdownMenuItem(
                text = {
                    Text(
                        text = optionLabel(option),
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontFamily = TerminalFontFamily,
                        maxLines = 1,
                    )
                },
                onClick = {
                    onSelect(option)
                    dismiss()
                },
                modifier = Modifier.background(
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else Color.Transparent,
                ),
            )
        }
    }
}

/**
 * Базовая форма [TerminalBoxDropdown] с произвольным содержимым меню
 * (для дропдаунов с поиском/табами — ту же «коробку» использует
 * symbol explorer).
 */
@Composable
fun TerminalBoxDropdown(
    text: String,
    modifier: Modifier = Modifier,
    style: TerminalBoxStyle = TerminalBoxStyle.NORMAL,
    menuWidth: Dp = 140.dp,
    leading: (@Composable () -> Unit)? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    menuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    var expanded by remember { mutableStateOf(false) }
    fun setExpanded(value: Boolean) {
        expanded = value
        onExpandedChange?.invoke(value)
    }
    val shape = RoundedCornerShape(tokens.cornerRadii.smallCard)
    val fill = when (style) {
        TerminalBoxStyle.NORMAL -> tokens.colors.panelHeaderSurface.copy(alpha = 0.45f)
        TerminalBoxStyle.ACCENT -> MaterialTheme.colorScheme.surfaceVariant
        TerminalBoxStyle.SELECTED -> MaterialTheme.colorScheme.primary
    }
    val borderColor = when (style) {
        TerminalBoxStyle.NORMAL, TerminalBoxStyle.ACCENT -> tokens.colors.panelBorder
        TerminalBoxStyle.SELECTED -> MaterialTheme.colorScheme.primary
    }
    val textColor = when (style) {
        TerminalBoxStyle.NORMAL -> MaterialTheme.colorScheme.onSurface
        TerminalBoxStyle.ACCENT -> MaterialTheme.colorScheme.primary
        TerminalBoxStyle.SELECTED -> MaterialTheme.colorScheme.surface
    }
    val chevronTint = when (style) {
        TerminalBoxStyle.NORMAL -> tokens.colors.label
        TerminalBoxStyle.ACCENT -> MaterialTheme.colorScheme.primary
        TerminalBoxStyle.SELECTED -> MaterialTheme.colorScheme.surface
    }

    Box(modifier = modifier) {
        Surface(
            shape = shape,
            color = fill,
            modifier = Modifier
                .border(tokens.dimensions.borderWidth, borderColor, shape)
                .clickable { setExpanded(true) },
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = tokens.spacing.sm,
                    vertical = tokens.spacing.xxs,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading?.let {
                    it()
                    Spacer(Modifier.width(tokens.spacing.xs))
                }
                Text(
                    text = text,
                    color = textColor,
                    fontSize = tokens.typography.compactSize,
                    lineHeight = tokens.typography.controlLineHeight,
                    fontWeight = if (style == TerminalBoxStyle.SELECTED) FontWeight.Bold
                    else FontWeight.Medium,
                    fontFamily = TerminalFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (style != TerminalBoxStyle.SELECTED) {
                    Spacer(Modifier.width(tokens.spacing.xs))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = chevronTint,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { setExpanded(false) },
            offset = DpOffset(0.dp, 2.dp),
            modifier = Modifier.width(menuWidth),
            shape = RoundedCornerShape(tokens.cornerRadii.workspacePanel),
            containerColor = tokens.colors.panelSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(tokens.dimensions.borderWidth, tokens.colors.panelBorder),
        ) {
            menuContent { setExpanded(false) }
        }
    }
}
