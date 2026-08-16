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
import androidx.compose.ui.unit.sp

/**
 * Универсальный выпадающий список для терминального интерфейса.
 * Заменяет все специализированные dropdown-компоненты (DomModeDropdown, SymbolDropdown и т.д.).
 *
 * @param T тип значения в dropdown
 * @param currentValue текущее выбранное значение
 * @param items список доступных значений
 * @param onValueChanged callback при изменении значения
 * @param displayText функция для преобразования значения в отображаемый текст
 * @param menuWidth ширина выпадающего меню (по умолчанию 120.dp)
 * @param modifier Modifier для контейнера
 */
@Composable
fun <T> TerminalDropdown(
    currentValue: T,
    items: List<T>,
    onValueChanged: (T) -> Unit,
    displayText: (T) -> String,
    menuWidth: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.wrapContentSize(Alignment.TopStart)
    ) {
        // Кнопка-триггер с текущим значением и стрелкой
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp)
            ) {
                Text(
                    text = displayText(currentValue),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Раскрыть список",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Выпадающее меню
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(menuWidth)
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = displayText(item),
                            fontSize = 12.sp,
                            fontWeight = if (currentValue == item) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onValueChanged(item)
                        expanded = false
                    }
                )
            }
        }
    }
}