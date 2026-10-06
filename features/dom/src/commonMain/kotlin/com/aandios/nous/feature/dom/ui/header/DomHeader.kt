/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui.header

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.model.ContractType
import com.aandios.nous.feature.dom.domain.*
import com.aandios.nous.feature.dom.domain.model.AggregationLevel
import com.aandios.nous.feature.dom.domain.model.DepthLimit

/**
 * Заголовок DOM: две компактные строки без «двойных» рамок —
 *  1) биржа · тип контрактов · свернуть
 *  2) символ (с поиском) · глубина · агрегация
 * Ширина не превышает панель: символ сжимается, у остальных — короткие
 * mono-контролы «label value ▾» в стиле терминала.
 */
@Composable
fun DomHeader(
    domOptions: DomOptions,
    onDomOptionsChanged: (DomOptions) -> Unit,
    providers: List<Provider> = emptyList(),
    loadedSymbols: List<TradingSymbol> = emptyList(),
    symbolTickSize: Double? = null,
    modifier: Modifier = Modifier
) {
    if (domOptions.collapsed) {
        DomHeaderCompact(
            providerDisplayName = providers.firstOrNull { it.providerId == domOptions.provider }
                ?.config?.displayName ?: domOptions.provider,
            tradingSymbol = domOptions.symbol,
            isExpanded = false,
            onToggleExpand = { onDomOptionsChanged(domOptions.copy(collapsed = false)) },
            modifier = modifier
        )
    } else {
        ExpandedDomHeader(
            domOptions = domOptions,
            onDomOptionsChanged = onDomOptionsChanged,
            providers = providers,
            loadedSymbols = loadedSymbols,
            symbolTickSize = symbolTickSize,
            modifier = modifier
        )
    }
}

@Composable
private fun ExpandedDomHeader(
    domOptions: DomOptions,
    onDomOptionsChanged: (DomOptions) -> Unit,
    providers: List<Provider> = emptyList(),
    loadedSymbols: List<TradingSymbol> = emptyList(),
    symbolTickSize: Double? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Строка 1: биржа · тип контрактов · сворачивание
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderSelect(
                    label = "Ex",
                    current = domOptions.provider,
                    items = providers.map { it.providerId },
                    display = { id ->
                        providers.firstOrNull { it.providerId == id }?.config?.displayName ?: id
                    },
                    onSelect = { onDomOptionsChanged(domOptions.copy(provider = it)) },
                    menuWidth = 150.dp,
                )
                HeaderSelect(
                    label = "Type",
                    current = domOptions.contractType,
                    items = ContractType.entries.toList(),
                    display = { it.label },
                    onSelect = { onDomOptionsChanged(domOptions.copy(contractType = it)) },
                    menuWidth = 120.dp,
                )
                Spacer(Modifier.weight(1f))
                // Отключаем M3 min-touch-target (48dp) — компактная строка
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    IconButton(
                        onClick = { onDomOptionsChanged(domOptions.copy(collapsed = true)) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Свернуть",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(180f)
                        )
                    }
                }
            }

            // Строка 2: символ (сжимается, клип) · глубина · агрегация
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f).clipToBounds()) {
                    DomSymbolDropdown(
                        currentSymbol = domOptions.symbol,
                        availableSymbols = loadedSymbols,
                        onSymbolChanged = { newSymbol ->
                            onDomOptionsChanged(domOptions.copy(symbol = newSymbol))
                        },
                    )
                }
                HeaderSelect(
                    label = "Depth",
                    current = domOptions.depth,
                    items = DepthLimit.standardValues.map { DepthLimit.create(it) },
                    display = { it.value.toString() },
                    onSelect = { onDomOptionsChanged(domOptions.copy(depth = it)) },
                    menuWidth = 90.dp,
                )
                HeaderSelect(
                    label = "Agg",
                    current = domOptions.aggregation,
                    items = AggregationLevel.all(),
                    display = { it.displayName(symbolTickSize) },
                    onSelect = { onDomOptionsChanged(domOptions.copy(aggregation = it)) },
                    menuWidth = 140.dp,
                )
            }
        }
    }
}

/**
 * Компактный селект заголовка: `label value ▾` одной строкой, mono-шрифт,
 * без внешней рамки и без вложенных Surface — высота ~18dp.
 */
@Composable
private fun <T> HeaderSelect(
    label: String,
    current: T,
    items: List<T>,
    display: (T) -> String,
    onSelect: (T) -> Unit,
    menuWidth: Dp = 140.dp,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { expanded = true }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = display(current),
                color = MaterialTheme.colorScheme.inverseOnSurface,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = "▾",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(menuWidth),
        ) {
            items.forEach { item ->
                val selected = item == current
                DropdownMenuItem(
                    text = {
                        Text(
                            text = display(item),
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
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