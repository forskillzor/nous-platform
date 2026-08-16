/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui.header

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.component.TerminalDropdown
import com.aandios.nous.core.ui.component.TerminalDropdownWithLabel
import com.aandios.nous.feature.dom.domain.TradingProvider

/**
 * Вспомогательная функция для создания dropdown с label для TradingProvider.
 */
@Composable
fun TradingProviderDropdown(
    currentProvider: TradingProvider,
    onProviderChanged: (TradingProvider) -> Unit,
    modifier: Modifier = Modifier.Companion
) {
    TerminalDropdownWithLabel(
        label = "Ex",
        modifier = modifier
    ) {
        TerminalDropdown(
            currentValue = currentProvider,
            items = TradingProvider.all(),
            onValueChanged = onProviderChanged,
            displayText = { it.displayName },
            menuWidth = 180.dp
        )
    }
}