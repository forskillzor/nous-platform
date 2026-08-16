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
import com.aandios.nous.feature.dom.domain.model.AggregationLevel

/**
 * Вспомогательная функция для создания dropdown с label для Aggregation Level.
 */
@Composable
fun AggregationLevelDropdown(
    currentLevel: AggregationLevel,
    symbolTickSize: Double? = null,
    onLevelChanged: (AggregationLevel) -> Unit,
    modifier: Modifier = Modifier.Companion
) {
    TerminalDropdownWithLabel(
        label = "Agg",
        modifier = modifier
    ) {
        TerminalDropdown(
            currentValue = currentLevel,
            items = AggregationLevel.all(),
            onValueChanged = onLevelChanged,
            displayText = { it.displayName(symbolTickSize) },
            menuWidth = 100.dp
        )
    }
}