/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui.header

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.aandios.nous.api.market.Provider
import com.aandios.nous.feature.dom.domain.*

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
        // Компактный режим: только provider и symbol
        DomHeaderCompact(
            providerDisplayName = providers.firstOrNull { it.providerId == domOptions.provider }
                ?.config?.displayName ?: domOptions.provider,
            tradingSymbol = domOptions.symbol,
            isExpanded = false,
            onToggleExpand = { onDomOptionsChanged(domOptions.copy(collapsed = false)) },
            modifier = modifier
        )
    } else {
        // Полный режим: все dropdown с labels
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

/**
 * Развернутая версия DomHeader со всеми dropdown и кнопкой сворачивания.
 */
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Первая строка: provider + тип контрактов + кнопка сворачивания
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Provider dropdown с label — только реально реализованные провайдеры
                TradingProviderDropdown(
                    currentProviderId = domOptions.provider,
                    providers = providers,
                    onProviderChanged = { newProvider ->
                        onDomOptionsChanged(domOptions.copy(provider = newProvider))
                    },
                    modifier = Modifier.weight(1.2f)
                )

                // USDT-M / COIN-M — фильтр списка символов (как Ex — тот же dropdown)
                ContractTypeDropdown(
                    current = domOptions.contractType,
                    onContractTypeChanged = { newType ->
                        onDomOptionsChanged(domOptions.copy(contractType = newType))
                    },
                    modifier = Modifier.weight(1f)
                )

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

            // Вторая строка: symbol dropdown + depth limit selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Symbol dropdown с label
                DomSymbolDropdown(
                    currentSymbol = domOptions.symbol,
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
