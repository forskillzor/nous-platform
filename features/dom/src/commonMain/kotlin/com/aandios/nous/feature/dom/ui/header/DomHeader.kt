/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.Provider
import com.aandios.nous.core.ui.component.SymbolExplorerDropdown
import com.aandios.nous.core.ui.component.SymbolExplorerRow
import com.aandios.nous.core.ui.component.TerminalInlineSelect
import com.aandios.nous.core.ui.format.plainDecimalString
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.feature.dom.domain.*
import com.aandios.nous.feature.dom.domain.model.AggregationLevel
import com.aandios.nous.feature.dom.domain.model.DepthLimit

/**
 * Заголовок DOM: две компактные строки без «двойных» рамок —
 *  1) символ с биржей (explorer) · свернуть
 *  2) глубина · агрегация
 * Тип контрактов отдельной кнопкой не переключается: он однозначно известен
 * из символа (perpetual/инверс) и синхронизируется выбором строки в explorer'е.
 * Биржа переключается выбором строки другой биржи в explorer'е (как в тайтле
 * графика). Ширина не превышает панель: символ сжимается, у остальных —
 * короткие mono-контролы «label value ▾» в стиле терминала.
 */
@Composable
fun DomHeader(
    domOptions: DomOptions,
    onDomOptionsChanged: (DomOptions) -> Unit,
    providers: List<Provider> = emptyList(),
    explorerRows: List<SymbolExplorerRow> = emptyList(),
    onExplorerRowSelected: (SymbolExplorerRow) -> Unit = {},
    symbolTickSize: Double? = null,
    modifier: Modifier = Modifier
) {
    if (domOptions.collapsed) {
        DomHeaderCompact(
            providerDisplayName = providers.firstOrNull { it.providerId == domOptions.provider }
                ?.config?.displayName ?: domOptions.provider,
            tradingSymbol = domOptions.symbol,
            isExpanded = false,
            onToggleExpand = { onDomOptionsChanged(domOptions.copy(collapsed = false)) },
            modifier = modifier
        )
    } else {
        ExpandedDomHeader(
            domOptions = domOptions,
            onDomOptionsChanged = onDomOptionsChanged,
            explorerRows = explorerRows,
            onExplorerRowSelected = onExplorerRowSelected,
            symbolTickSize = symbolTickSize,
            modifier = modifier
        )
    }
}

@Composable
private fun ExpandedDomHeader(
    domOptions: DomOptions,
    onDomOptionsChanged: (DomOptions) -> Unit,
    explorerRows: List<SymbolExplorerRow> = emptyList(),
    onExplorerRowSelected: (SymbolExplorerRow) -> Unit = {},
    symbolTickSize: Double? = null,
    modifier: Modifier = Modifier
) {
    val tokens = LocalTerminalDesignTokens.current

    // Единый фон с контентом DOM (один цвет панели) + полоски между строками
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Узкие панели: агрегация переезжает в третью тонкую строку
            val narrow = maxWidth < 360.dp
            Column(modifier = Modifier.fillMaxWidth()) {
                // Строка 1: символ (сжимается, клип) · сворачивание
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f).clipToBounds()) {
                        SymbolExplorerDropdown(
                            currentSymbol = domOptions.symbol.symbol,
                            currentExchangeId = domOptions.provider,
                            rows = explorerRows,
                            onSelect = onExplorerRowSelected,
                        )
                    }
                    // Отключаем M3 min-touch-target (48dp) — компактная строка
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                        IconButton(
                            onClick = { onDomOptionsChanged(domOptions.copy(collapsed = true)) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Свернуть",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.rotate(180f)
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = tokens.colors.panelBorder,
                    thickness = tokens.dimensions.dividerThickness,
                )

                // Строка 2: глубина · агрегация
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
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
                }
                HorizontalDivider(
                    color = tokens.colors.panelBorder,
                    thickness = tokens.dimensions.dividerThickness,
                )
            }
        }
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
