/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui.header

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aandios.nous.api.market.model.ContractType
import com.aandios.nous.core.ui.component.TerminalDropdown
import com.aandios.nous.core.ui.component.TerminalDropdownWithLabel

/**
 * Дропдаун типа контрактов DOM-панели: USDT-M / COIN-M.
 * Фильтрует список символов и переключает единицы/PnL-математику.
 */
@Composable
fun ContractTypeDropdown(
    current: ContractType,
    onContractTypeChanged: (ContractType) -> Unit,
    modifier: Modifier = Modifier.Companion
) {
    TerminalDropdownWithLabel(
        label = "Type",
        modifier = modifier
    ) {
        TerminalDropdown(
            currentValue = current,
            items = ContractType.entries.toList(),
            onValueChanged = onContractTypeChanged,
            displayText = { it.label },
            menuWidth = 120.dp
        )
    }
}
