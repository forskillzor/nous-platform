/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trades.ui.header
import com.aandios.nous.core.ui.theme.TerminalFontFamily

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.model.ContractType
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.core.ui.component.SymbolExplorerDropdown
import com.aandios.nous.core.ui.component.SymbolExplorerRow
import com.aandios.nous.core.ui.component.TerminalInlineSelect
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.feature.trades.ui.SizeFilter
import kotlin.math.roundToInt

/**
 * Заголовок Trades в стиле DOM-хедера: две тонкие строки на едином с контентом
 * фоне, разделённые полосками-спейсерами:
 *  1) тип контрактов · шестерёнка настроек
 *  2) символ с биржей (explorer) · фильтр размера
 * Биржа переключается выбором строки другой биржи в explorer'е (как в тайтле
 * графика). Без live-индикатора и без «двойных» рамок.
 */
@Composable
fun TradesHeaderBar(
    currentSymbol: String,
    currentSymbolInfo: SymbolInfo?,
    selectedSizeFilter: SizeFilter,
    customPresets: List<Double>,
    currentProviderId: String = "",
    contractType: ContractType = ContractType.USDT_M,
    explorerRows: List<SymbolExplorerRow> = emptyList(),
    onExplorerRowSelected: (SymbolExplorerRow) -> Unit = {},
    /** Ширина панели (dp) для шестерёнки настроек. */
    panelWidthDp: Float = 240f,
    minPanelWidthDp: Float = 120f,
    maxPanelWidthDp: Float = 240f,
    onPanelWidthDpChanged: (Float) -> Unit = {},
    onPanelWidthDpChangeFinished: () -> Unit = {},
    onContractTypeChanged: (ContractType) -> Unit = {},
    onFilterChanged: (SizeFilter) -> Unit,
    onPresetAdd: (Double) -> Unit,
    onPresetEdit: (Int, Double) -> Unit,
    onPresetDelete: (Int) -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current

    // Единый фон с контентом Trades (один цвет панели) + полоски между строками,
    // как в переделанном DOM-хедере
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // Строка 1: USDT-M/COIN-M · шестерёнка
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

        HorizontalDivider(
            color = tokens.colors.panelBorder,
            thickness = tokens.dimensions.dividerThickness,
        )

        // Строка 2: символ с биржей (сжимается) · фильтр размера
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f).clipToBounds()) {
                SymbolExplorerDropdown(
                    currentSymbol = currentSymbol,
                    currentExchangeId = currentProviderId,
                    rows = explorerRows,
                    onSelect = onExplorerRowSelected,
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

        HorizontalDivider(
            color = tokens.colors.panelBorder,
            thickness = tokens.dimensions.dividerThickness,
        )
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
    val tokens = LocalTerminalDesignTokens.current
    var expanded by remember { mutableStateOf(false) }
    val lo = minPanelWidthDp.coerceAtMost((maxPanelWidthDp - 8f).coerceAtLeast(40f))
    val hi = maxPanelWidthDp.coerceAtLeast(lo + 8f)
    val current = panelWidthDp.coerceIn(lo, hi)

    Box {
        Text(
            text = "⚙",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = tokens.typography.controlSize,
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
                    fontSize = tokens.typography.compactSize,
                    fontFamily = TerminalFontFamily,
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
