/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trades.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.feature.trades.ui.SizeFilter

/**
 * Заголовок Trades в стиле DOM-хедера: одна тонкая строка на едином с контентом
 * фоне, снизу — линия отделения. Символ и биржа выбираются explorer'ом
 * в тайтле панели (как в chart), поэтому в хедере остаётся только фильтр
 * размера (воронка + режим). Настройки (ширина панели) живут в ⚙ тайтла.
 */
@Composable
fun TradesHeaderBar(
    currentSymbolInfo: SymbolInfo?,
    selectedSizeFilter: SizeFilter,
    customPresets: List<Double>,
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
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.sm),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
