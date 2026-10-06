/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trades.ui.header

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.model.ContractType
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.core.ui.component.TerminalInlineSelect
import com.aandios.nous.feature.trades.ui.SizeFilter
import kotlin.math.roundToInt

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
    /** Ширина панели (dp) для шестерёнки настроек. */
    panelWidthDp: Float = 240f,
    minPanelWidthDp: Float = 120f,
    maxPanelWidthDp: Float = 240f,
    onPanelWidthDpChanged: (Float) -> Unit = {},
    onPanelWidthDpChangeFinished: () -> Unit = {},
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
                // Шестерёнка настроек: пока только ширина панели (ползунок)
                TradesSettingsGear(
                    panelWidthDp = panelWidthDp,
                    minPanelWidthDp = minPanelWidthDp,
                    maxPanelWidthDp = maxPanelWidthDp,
                    onPanelWidthDpChanged = onPanelWidthDpChanged,
                    onPanelWidthDpChangeFinished = onPanelWidthDpChangeFinished,
                )
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

/**
 * Шестерёнка настроек Trades: пока единственная настройка — ширина панели
 * ползунком от измеренного минимума (строка сделки) до максимальной (240dp).
 */
@Composable
private fun TradesSettingsGear(
    panelWidthDp: Float,
    minPanelWidthDp: Float,
    maxPanelWidthDp: Float,
    onPanelWidthDpChanged: (Float) -> Unit,
    onPanelWidthDpChangeFinished: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val lo = minPanelWidthDp.coerceAtMost((maxPanelWidthDp - 8f).coerceAtLeast(40f))
    val hi = maxPanelWidthDp.coerceAtLeast(lo + 8f)
    val current = panelWidthDp.coerceIn(lo, hi)

    Box {
        Text(
            text = "⚙",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier
                .clickable { expanded = true }
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Column(
                modifier = Modifier
                    .width(200.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Width  ${current.roundToInt()} dp",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(2.dp))
                Slider(
                    value = current,
                    onValueChange = onPanelWidthDpChanged,
                    onValueChangeFinished = onPanelWidthDpChangeFinished,
                    valueRange = lo..hi,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
