/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trades.ui.header

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.core.ui.component.SymbolSearchDropdown

/**
 * Dropdown выбора символа для Trades через унифицированный SymbolSearchDropdown.
 */
@Composable
fun TradesSymbolDropdown(
    currentSymbol: String,
    availableSymbols: List<SymbolInfo> = emptyList(),
    onSymbolChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val symbolList = remember(availableSymbols) {
        if (availableSymbols.isNotEmpty()) availableSymbols.map { it.symbol }
        else listOf("BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT", "ADAUSDT", "DOGEUSDT", "DOTUSDT", "AVAXUSDT", "LINKUSDT")
    }

    SymbolSearchDropdown(
        symbols = symbolList,
        currentSymbol = currentSymbol,
        onSymbolSelected = onSymbolChanged,
        // Компактный триггер «Sym SOLUSDT ▾» в стиле DOM-хедера
        showLabel = false,
        labelPrefix = "Sym",
        modifier = modifier,
    )
}
