/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trades.ui.header

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.model.ContractType
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.core.ui.component.TerminalInlineSelect
import com.aandios.nous.feature.trades.ui.SizeFilter

/**
 * Заголовок Trades в компактном стиле DOM-хедера: две тонкие строки
 *  1) биржа · тип контрактов
 *  2) символ (с поиском) · фильтр размера
 * Без live-индикатора и без «двойных» рамок.
 */
@Composable
fun TradesHeaderBar(
    currentSymbol: String,
    availableSymbols: List<SymbolInfo>,
    currentSymbolInfo: SymbolInfo?,
    selectedSizeFilter: SizeFilter,
    customPresets: List<Double>,
    providers: List<Provider> = emptyList(),
    currentProviderId: String = "",
    contractType: ContractType = ContractType.USDT_M,
    onProviderChanged: (String) -> Unit = {},
    onContractTypeChanged: (ContractType) -> Unit = {},
    onSymbolChanged: (String) -> Unit,
    onFilterChanged: (SizeFilter) -> Unit,
    onPresetAdd: (Double) -> Unit,
    onPresetEdit: (Int, Double) -> Unit,
    onPresetDelete: (Int) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Строка 1: биржа · USDT-M/COIN-M
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TerminalInlineSelect(
                    label = "Ex",
                    current = currentProviderId,
                    items = providers.map { it.providerId },
                    display = { id ->
                        providers.firstOrNull { it.providerId == id }?.config?.displayName ?: id
                    },
                    onSelect = onProviderChanged,
                    menuWidth = 150.dp,
                )
                TerminalInlineSelect(
                    label = "Type",
                    current = contractType,
                    items = ContractType.entries.toList(),
                    display = { it.label },
                    onSelect = onContractTypeChanged,
                    menuWidth = 120.dp,
                )
                Spacer(Modifier.weight(1f))
            }

            // Строка 2: символ (сжимается) · фильтр размера
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f).clipToBounds()) {
                    TradesSymbolDropdown(
                        currentSymbol = currentSymbol,
                        availableSymbols = availableSymbols,
                        onSymbolChanged = onSymbolChanged,
                    )
                }
                SizeFilterDropdown(
                    currentFilter = selectedSizeFilter,
                    minQty = currentSymbolInfo?.minQty,
                    customPresets = customPresets,
                    onFilterChanged = onFilterChanged,
                    onPresetAdd = onPresetAdd,
                    onPresetEdit = onPresetEdit,
                    onPresetDelete = onPresetDelete,
                )
            }
        }
    }
}
