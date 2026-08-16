/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trades.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.feature.trades.ui.SizeFilter

@Composable
fun TradesHeaderBar(
    currentSymbol: String,
    availableSymbols: List<SymbolInfo>,
    currentSymbolInfo: SymbolInfo?,
    selectedSizeFilter: SizeFilter,
    customPresets: List<Double>,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TradesSymbolDropdown(
                currentSymbol = currentSymbol,
                availableSymbols = availableSymbols,
                onSymbolChanged = onSymbolChanged,
                modifier = Modifier.weight(1.4f)
            )

            SizeFilterDropdown(
                currentFilter = selectedSizeFilter,
                minQty = currentSymbolInfo?.minQty,
                customPresets = customPresets,
                onFilterChanged = onFilterChanged,
                onPresetAdd = onPresetAdd,
                onPresetEdit = onPresetEdit,
                onPresetDelete = onPresetDelete,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = Color.Green,
                        shape = MaterialTheme.shapes.small
                    )
            )
        }
    }
}
