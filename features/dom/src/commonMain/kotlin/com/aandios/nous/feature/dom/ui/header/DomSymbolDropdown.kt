/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui.header

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.aandios.nous.core.ui.component.SymbolSearchDropdown
import com.aandios.nous.feature.dom.domain.TradingProvider
import com.aandios.nous.feature.dom.domain.TradingSymbol

/**
 * Dropdown выбора символа для DOM через унифицированный SymbolSearchDropdown.
 */
@Composable
fun DomSymbolDropdown(
    currentSymbol: TradingSymbol,
    provider: TradingProvider,
    availableSymbols: List<TradingSymbol> = emptyList(),
    onSymbolChanged: (TradingSymbol) -> Unit,
    modifier: Modifier = Modifier
) {
    val symbolList = remember(provider, availableSymbols) {
        if (availableSymbols.isNotEmpty()) availableSymbols.map { it.symbol }
        else TradingSymbol.getSymbolsForProvider(provider).map { it.symbol }
    }

    val symbolMap = remember(symbolList) {
        (availableSymbols.ifEmpty { TradingSymbol.getSymbolsForProvider(provider) }).associateBy { it.symbol }
    }

    SymbolSearchDropdown(
        symbols = symbolList,
        currentSymbol = currentSymbol.symbol,
        onSymbolSelected = { sym ->
            symbolMap[sym]?.let { onSymbolChanged(it) }
        },
        modifier = modifier,
    )
}
