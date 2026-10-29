/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui.header

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
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.component.TerminalInlineSelect
import com.aandios.nous.core.ui.format.plainDecimalString
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.feature.dom.domain.DomOptions
import com.aandios.nous.feature.dom.domain.model.AggregationLevel
import com.aandios.nous.feature.dom.domain.model.DepthLimit

/**
 * Заголовок DOM: одна компактная строка без «двойных» рамок —
 * глубина · агрегация · частота обновления уровней.
 *
 * Символ и биржа выбираются explorer'ом в тайтле панели (как в chart),
 * поэтому здесь их нет. Тип контрактов тоже не переключается отдельно:
 * он однозначно известен из символа и синхронизируется выбором строки
 * в explorer'е.
 */
@Composable
fun DomHeader(
    domOptions: DomOptions,
    onDomOptionsChanged: (DomOptions) -> Unit,
    symbolTickSize: Double? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalTerminalDesignTokens.current

    // Единый фон с контентом DOM (один цвет панели) + полоска снизу
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TerminalInlineSelect(
                label = "Depth",
                current = domOptions.depth,
                items = DepthLimit.standardValues.map { DepthLimit.create(it) },
                display = { it.value.toString() },
                onSelect = { onDomOptionsChanged(domOptions.copy(depth = it)) },
                menuWidth = 90.dp,
            )
            AggTerminalInlineSelect(
                current = domOptions.aggregation,
                symbolTickSize = symbolTickSize,
                onSelect = { onDomOptionsChanged(domOptions.copy(aggregation = it)) },
            )
            TerminalInlineSelect(
                label = "Freq",
                current = domOptions.freqMs,
                items = DomOptions.frequencyOptions,
                display = { it.toString() },
                onSelect = { onDomOptionsChanged(domOptions.copy(freqMs = it)) },
                menuWidth = 80.dp,
            )
        }
        HorizontalDivider(
            color = tokens.colors.panelBorder,
            thickness = tokens.dimensions.dividerThickness,
        )
    }
}

/**
 * Селект агрегации: базовый уровень показываем просто тиком («0.01») —
 * и так ясно, что минимальный шаг это базовый; остальные — «10× (0.1)».
 */
@Composable
private fun AggTerminalInlineSelect(
    current: AggregationLevel,
    symbolTickSize: Double?,
    onSelect: (AggregationLevel) -> Unit,
) {
    TerminalInlineSelect(
        label = "Agg",
        current = current,
        items = AggregationLevel.all(),
        display = { aggregationLabel(it, symbolTickSize) },
        onSelect = onSelect,
        menuWidth = 140.dp,
    )
}

private fun aggregationLabel(level: AggregationLevel, baseTickSize: Double?): String {
    val tick = baseTickSize
        ?.let { level.effectiveTickSize(it) }
        ?.takeIf { it > 0.0 }
        ?.let { plainDecimalString(it) }
    return when (level) {
        AggregationLevel.BaseTick -> tick ?: "1x"
        AggregationLevel.TenTick -> "10×" + (tick?.let { " ($it)" } ?: "")
        AggregationLevel.HundredTick -> "100×" + (tick?.let { " ($it)" } ?: "")
    }
}
