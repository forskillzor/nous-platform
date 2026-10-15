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
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily
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
    val tokens = LocalTerminalDesignTokens.current
    var expanded by remember { mutableStateOf(false) }
    val standardValues = DepthLimit.standardValues

    Box(
        modifier = modifier.wrapContentSize(Alignment.TopStart)
    ) {
        // Кнопка-триггер с текущей глубиной и стрелкой
        Surface(
            shape = MaterialTheme.shapes.small,
            color = tokens.colors.panelHeaderSurface,
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
                modifier = Modifier.padding(horizontal = tokens.spacing.xs, vertical = tokens.spacing.xxs)
            ) {
                Text(
                    text = "${currentLimit.value}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = tokens.typography.inlineSelectValueSize,
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = tokens.typography.controlLetterSpacing,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Раскрыть список глубин",
                    tint = tokens.colors.label,
                    modifier = Modifier.size(tokens.dimensions.dropdownIconSize)
                )
            }
        }

        // Выпадающее меню
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(tokens.dimensions.dropdownWidth)
        ) {
            standardValues.forEach { value ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "$value",
                            fontSize = tokens.typography.controlSize,
                            fontFamily = TerminalFontFamily,
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
                        fontSize = tokens.typography.controlSize,
                        fontFamily = TerminalFontFamily,
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