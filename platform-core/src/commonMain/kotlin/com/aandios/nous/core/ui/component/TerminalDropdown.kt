/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens

/**
 * Универсальный выпадающий список для терминального интерфейса.
 * Заменяет все специализированные dropdown-компоненты (DomModeDropdown, SymbolDropdown и т.д.).
 *
 * @param T тип значения в dropdown
 * @param currentValue текущее выбранное значение
 * @param items список доступных значений
 * @param onValueChanged callback при изменении значения
 * @param displayText функция для преобразования значения в отображаемый текст
 * @param menuWidth ширина выпадающего меню (по умолчанию из темы)
 * @param modifier Modifier для контейнера
 */
@Composable
fun <T> TerminalDropdown(
    currentValue: T,
    items: List<T>,
    onValueChanged: (T) -> Unit,
    displayText: (T) -> String,
    menuWidth: Dp? = null,
    modifier: Modifier = Modifier
) {
    val tokens = LocalTerminalDesignTokens.current
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.wrapContentSize(Alignment.TopStart)
    ) {
        // Кнопка-триггер с текущим значением и стрелкой
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = tokens.spacing.xxs,
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
                modifier = Modifier.padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.xxs)
            ) {
                Text(
                    text = displayText(currentValue),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = tokens.typography.controlLetterSpacing,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Раскрыть список",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(tokens.dimensions.dropdownIconSize)
                )
            }
        }

        // Выпадающее меню
        TerminalDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(menuWidth ?: tokens.dimensions.dropdownWidth)
        ) {
            items.forEach { item ->
                TerminalMenuItem(
                    text = displayText(item),
                    selected = currentValue == item,
                    onClick = {
                        onValueChanged(item)
                        expanded = false
                    },
                )
            }
        }
    }
}