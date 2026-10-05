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

/**
 * Дропдаун выбора провайдера данных для DOM.
 * Список — только реально зарегистрированные провайдеры (из ProviderRegistry),
 * значение — [Provider.providerId].
 */
@Composable
fun TradingProviderDropdown(
    currentProviderId: String,
    providers: List<com.aandios.nous.api.market.Provider>,
    onProviderChanged: (String) -> Unit,
    modifier: Modifier = Modifier.Companion
) {
    TerminalDropdownWithLabel(
        label = "Ex",
        modifier = modifier
    ) {
        TerminalDropdown(
            currentValue = currentProviderId,
            items = providers.map { it.providerId },
            onValueChanged = onProviderChanged,
            displayText = { id ->
                providers.firstOrNull { it.providerId == id }?.config?.displayName ?: id
            },
            menuWidth = 180.dp
        )
    }
}
