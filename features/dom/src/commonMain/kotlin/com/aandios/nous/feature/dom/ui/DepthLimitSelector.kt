/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.feature.dom.domain.model.DepthLimit

/**
 * Компактный выпадающий список для выбора глубины стакана.
 * Оптимизирован для десктопного интерфейса с минимальным использованием пространства.
 *
 * @param currentLimit текущая выбранная глубина
 * @param onLimitChanged callback при изменении глубины
 * @param modifier Modifier для контейнера
 */
@Composable
fun DepthLimitSelector(
    currentLimit: DepthLimit,
    onLimitChanged: (DepthLimit) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val standardValues = DepthLimit.standardValues

    Box(
        modifier = modifier.wrapContentSize(Alignment.TopStart)
    ) {
        // Кнопка-триггер с текущей глубиной и стрелкой
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
                    text = "${currentLimit.value}",
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Раскрыть список глубин",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Выпадающее меню
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(100.dp)
        ) {
            standardValues.forEach { value ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "$value",
                            fontSize = 12.sp,
                            fontWeight = if (currentLimit.value == value) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onLimitChanged(DepthLimit.create(value))
                        expanded = false
                    }
                )
            }
            
            // Кастомное значение
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Custom...",
                        fontSize = 12.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                },
                onClick = {
                    // TODO: Реализовать диалог для ввода кастомного значения
                    expanded = false
                }
            )
        }
    }
}