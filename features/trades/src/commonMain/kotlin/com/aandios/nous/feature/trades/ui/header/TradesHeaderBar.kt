/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trades.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.core.ui.component.SymbolExplorerDropdown
import com.aandios.nous.core.ui.component.SymbolExplorerRow
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.feature.trades.ui.SizeFilter

/**
 * Заголовок Trades в стиле DOM-хедера: одна тонкая строка на едином с контентом
 * фоне, снизу — линия отделения:
 *  1) символ с биржей (explorer) · фильтр размера (воронка + режим)
 * Тип контрактов отдельной кнопкой не переключается — он однозначно известен
 * из символа и синхронизируется выбором строки в explorer'е. Настройки
 * (ширина панели) живут в ⚙ тайтла панели, не в хедере.
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
    explorerRows: List<SymbolExplorerRow> = emptyList(),
    onExplorerRowSelected: (SymbolExplorerRow) -> Unit = {},
    onFilterChanged: (SizeFilter) -> Unit,
    onPresetAdd: (Double) -> Unit,
    onPresetEdit: (Int, Double) -> Unit,
    onPresetDelete: (Int) -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current

    // Единый фон с контентом Trades (один цвет панели), снизу — линия
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // Строка 1: символ с биржей (сжимается) · фильтр размера
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
