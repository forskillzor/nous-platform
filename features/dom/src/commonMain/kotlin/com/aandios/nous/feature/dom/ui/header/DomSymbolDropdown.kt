/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui.header

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.aandios.nous.core.ui.component.SymbolSearchDropdown
import com.aandios.nous.feature.dom.domain.TradingSymbol

/**
 * Dropdown выбора символа для DOM через унифицированный SymbolSearchDropdown.
 * Список символов приходит от symbolInfoAdapter выбранного провайдера.
 */
@Composable
fun DomSymbolDropdown(
    currentSymbol: TradingSymbol,
    availableSymbols: List<TradingSymbol> = emptyList(),
    onSymbolChanged: (TradingSymbol) -> Unit,
    modifier: Modifier = Modifier
) {
    val symbolMap = remember(availableSymbols) {
        availableSymbols.associateBy { it.symbol }
    }

    SymbolSearchDropdown(
        symbols = availableSymbols.map { it.symbol },
        currentSymbol = currentSymbol.symbol,
        onSymbolSelected = { sym ->
            symbolMap[sym]?.let { onSymbolChanged(it) }
        },
        // Компактный триггер «Sym SOLUSDT ▾» вместо тяжёлой обёртки с label
        showLabel = false,
        labelPrefix = "Sym",
        // Тикер чуть крупнее остальных контролов хедера
        valueFontSize = 12.sp,
        modifier = modifier,
    )
}
