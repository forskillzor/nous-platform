/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aandios.nous.feature.dom.domain.*

@Composable
fun DomHeader(
    domOptions: DomOptions,
    onDomOptionsChanged: (DomOptions) -> Unit,
    loadedSymbols: List<TradingSymbol> = emptyList(),
    isLive: Boolean = true,
    symbolTickSize: Double? = null,
    modifier: Modifier = Modifier
) {
    if (domOptions.collapsed) {
        // Компактный режим: только provider и symbol
        DomHeaderCompact(
            tradingProvider = domOptions.provider,
            tradingSymbol = domOptions.symbol,
            isLive = isLive,
            isExpanded = false,
            onToggleExpand = { onDomOptionsChanged(domOptions.copy(collapsed = false)) },
            modifier = modifier
        )
    } else {
        // Полный режим: все dropdown с labels
        ExpandedDomHeader(
            domOptions = domOptions,
            onDomOptionsChanged = onDomOptionsChanged,
            loadedSymbols = loadedSymbols,
            isLive = isLive,
            symbolTickSize = symbolTickSize,
            modifier = modifier
        )
    }
}

/**
 * Развернутая версия DomHeader со всеми dropdown и кнопкой сворачивания.
 */
@Composable
private fun ExpandedDomHeader(
    domOptions: DomOptions,
    onDomOptionsChanged: (DomOptions) -> Unit,
    loadedSymbols: List<TradingSymbol> = emptyList(),
    isLive: Boolean = true,
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Первая строка: provider + live индикатор + кнопка сворачивания
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Provider dropdown с label
                TradingProviderDropdown(
                    currentProvider = domOptions.provider,
                    onProviderChanged = { newProvider ->
                        onDomOptionsChanged(domOptions.copy(provider = newProvider))
                    },
                    modifier = Modifier.weight(1f)
                )
                
                // Правая часть: live индикатор + кнопка сворачивания
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Live индикатор
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (isLive) Color.Green else Color.Red,
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                        Text(
                            text = if (isLive) "LIVE" else "OFFLINE",
                            color = if (isLive) Color.Green else Color.Red,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // Кнопка сворачивания
                    IconButton(
                        onClick = { onDomOptionsChanged(domOptions.copy(collapsed = true)) },
                        modifier = Modifier.size(24.dp)
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

            // Вторая строка: symbol dropdown + depth limit selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Symbol dropdown с label
                DomSymbolDropdown(
                    currentSymbol = domOptions.symbol,
                    provider = domOptions.provider,
                    availableSymbols = loadedSymbols,
                    onSymbolChanged = { newSymbol ->
                        onDomOptionsChanged(domOptions.copy(symbol = newSymbol))
                    },
                    modifier = Modifier.weight(1.4f)
                )
                
                // Depth limit selector с label
                DepthLimitDropdown(
                    currentLimit = domOptions.depth,
                    onLimitChanged = { newDepth ->
                        onDomOptionsChanged(domOptions.copy(depth = newDepth))
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Третья строка: aggregation level + DOM mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Aggregation level dropdown с label
                AggregationLevelDropdown(
                    currentLevel = domOptions.aggregation,
                    symbolTickSize = symbolTickSize,
                    onLevelChanged = { newAggregation ->
                        onDomOptionsChanged(domOptions.copy(aggregation = newAggregation))
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}