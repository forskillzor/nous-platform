/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui
import com.aandios.nous.core.ui.theme.TerminalFontFamily

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.api.market.model.ContractType
import com.aandios.nous.core.ui.component.SymbolExplorerDropdown
import com.aandios.nous.core.ui.component.SymbolExplorerRow
import com.aandios.nous.core.ui.component.TerminalBoxDropdown
import com.aandios.nous.core.ui.component.TerminalBoxStyle
import com.aandios.nous.core.ui.component.TerminalSwitch
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.feature.dom.domain.model.AggregationLevel

private val timeframes = listOf("1m", "5m", "15m", "30m", "1h", "4h", "1d", "1w")

private val chartModes = listOf(
    ChartMode.CANDLESTICK to "Candles",
    ChartMode.FOOTPRINT to "Footprint",
)

/**
 * Компактные контролы графика для верхнего тайтла панели: биржа, символ
 * (расширенный explorer-список), режим графика, таймфрейм (акцентная
 * «кнопка»), Trading и мок-кнопка AI. Настройки (тип контрактов, агрегация,
 * paper, отображение ордеров/позиций) живут в меню шестерёнки тайтла.
 */
@Composable
fun ChartToolbar(
    currentSymbol: String,
    currentExchangeId: String?,
    explorerRows: List<SymbolExplorerRow>,
    onExplorerRowSelected: (SymbolExplorerRow) -> Unit,
    currentTimeframe: String,
    onTimeframeChange: (String) -> Unit,
    exchanges: List<String> = emptyList(),
    currentExchange: String = "",
    onExchangeChange: (String) -> Unit = {},
    chartMode: ChartMode = ChartMode.CANDLESTICK,
    onChartModeChange: (ChartMode) -> Unit = {},
    tradingEnabled: Boolean = false,
    onTradingToggle: (Boolean) -> Unit = {},
    onRequestExplorerRefresh: () -> Unit = {},
    onManageWatchlist: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    settingsMenu: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    val tokens = LocalTerminalDesignTokens.current

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (currentExchange.isNotEmpty()) {
            TerminalBoxDropdown(
                current = currentExchange,
                options = exchanges,
                optionLabel = { it },
                onSelect = onExchangeChange,
                style = TerminalBoxStyle.ACCENT,
                menuWidth = 150.dp,
            )
            Spacer(Modifier.width(tokens.spacing.sm))
        }

        SymbolExplorerDropdown(
            currentSymbol = currentSymbol,
            currentExchangeId = currentExchangeId,
            rows = explorerRows,
            onSelect = onExplorerRowSelected,
            onRequestRefresh = onRequestExplorerRefresh,
            onManageWatchlist = onManageWatchlist,
        )

        Box(
            modifier = Modifier
                .padding(horizontal = tokens.spacing.sm)
                .width(1.dp)
                .height(12.dp)
                .background(tokens.colors.panelBorder),
        )

        TerminalBoxDropdown(
            current = chartMode,
            options = chartModes.map { it.first },
            optionLabel = { mode -> chartModes.firstOrNull { it.first == mode }?.second ?: "Candles" },
            onSelect = onChartModeChange,
            style = TerminalBoxStyle.ACCENT,
            menuWidth = 130.dp,
        )

        Spacer(Modifier.width(tokens.spacing.md))

        TerminalBoxDropdown(
            current = currentTimeframe,
            options = timeframes,
            optionLabel = { it },
            onSelect = onTimeframeChange,
            style = TerminalBoxStyle.SELECTED,
            menuWidth = 90.dp,
        )

        Spacer(Modifier.width(tokens.spacing.lg))

        Text(
            text = "Trading",
            color = if (tradingEnabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            fontSize = tokens.typography.compactSize,
            fontWeight = if (tradingEnabled) FontWeight.Bold else FontWeight.Medium,
            fontFamily = TerminalFontFamily,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(tokens.cornerRadii.smallCard))
                .background(
                    if (tradingEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else Color.Transparent
                )
                .clickable { onTradingToggle(!tradingEnabled) }
                .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.xs),
        )

        Spacer(Modifier.width(tokens.spacing.lg))

        Text(
            text = "AI",
            color = tokens.colors.label,
            fontSize = tokens.typography.compactSize,
            fontWeight = FontWeight.Medium,
            fontFamily = TerminalFontFamily,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(tokens.cornerRadii.smallCard))
                .clickable { }
                .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.xs),
        )

        if (settingsMenu != null) {
            Spacer(Modifier.width(tokens.spacing.sm))
            ChartSettingsGear(settingsMenu)
        }
    }
}

/**
 * Тайтл-контролы графика для конкретной [ChartViewModel]: состояния и
 * действия берутся из вью-модели, список бирж — из [registry],
 * строки explorer-дропдауна — из [ChartViewModel.explorerRows].
 */
@Composable
fun ChartTitleToolbar(
    chartViewModel: ChartViewModel,
    registry: ProviderRegistry,
    modifier: Modifier = Modifier,
    onManageWatchlist: (() -> Unit)? = null,
    settingsMenu: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    val uiState by chartViewModel.state.collectAsState()
    val tradingEnabled by chartViewModel.tradingEnabled.collectAsState()
    val explorerRows by chartViewModel.explorerRows.collectAsState()

    val exchanges = registry.providers.map { it.config.displayName }
    val currentExchange = registry.displayName(uiState.currentProviderId)
        .ifEmpty { registry.first()?.config?.displayName.orEmpty() }

    ChartToolbar(
        currentSymbol = uiState.currentSymbol,
        currentExchangeId = uiState.currentProviderId,
        explorerRows = explorerRows,
        onExplorerRowSelected = { chartViewModel.selectExplorerRow(it) },
        currentTimeframe = uiState.currentTimeframe,
        onTimeframeChange = { chartViewModel.dispatch(ChartIntent.SelectTimeframe(it)) },
        exchanges = exchanges,
        currentExchange = currentExchange,
        onExchangeChange = { name ->
            registry.idByDisplayName(name)?.let { id ->
                chartViewModel.dispatch(ChartIntent.SelectProvider(id))
            }
        },
        chartMode = uiState.chartMode,
        onChartModeChange = { chartViewModel.dispatch(ChartIntent.SelectChartMode(it)) },
        tradingEnabled = tradingEnabled,
        onTradingToggle = { chartViewModel.setTradingEnabled(it) },
        onRequestExplorerRefresh = { chartViewModel.refreshExplorerRows() },
        onManageWatchlist = onManageWatchlist,
        modifier = modifier,
        settingsMenu = settingsMenu,
    )
}

/**
 * Содержимое меню настроек тайтла графика: тип контрактов, агрегация
 * footprint, paper-режим и отображение ордеров/позиций.
 */
@Composable
fun ChartSettingsMenu(chartViewModel: ChartViewModel, dismiss: () -> Unit) {
    val uiState by chartViewModel.state.collectAsState()
    val paperEnabled by chartViewModel.paperEnabled.collectAsState()
    val showOrders by chartViewModel.showOrders.collectAsState()
    val showPositions by chartViewModel.showPositions.collectAsState()

    OptionChoiceRow(
        label = "Type",
        options = ContractType.entries.map { it to it.label },
        selected = uiState.contractType,
        onSelect = { chartViewModel.dispatch(ChartIntent.SelectContractType(it)) },
    )
    if (uiState.chartMode == ChartMode.FOOTPRINT) {
        OptionChoiceRow(
            label = "Aggregation",
            options = AggregationLevel.all().map { level ->
                level to when (level) {
                    AggregationLevel.BaseTick -> "1x"
                    AggregationLevel.TenTick -> "10x"
                    AggregationLevel.HundredTick -> "100x"
                }
            },
            selected = uiState.fpAggregation,
            onSelect = { chartViewModel.dispatch(ChartIntent.SetFpAggregation(it)) },
        )
    }
    OptionSwitchRow("Paper", paperEnabled) { chartViewModel.setPaperEnabled(it) }
    OptionSwitchRow("Show orders", showOrders) { chartViewModel.setShowOrders(it) }
    OptionSwitchRow("Show positions", showPositions) { chartViewModel.setShowPositions(it) }
}

/** Шестерёнка настроек (только для standalone-графика без тайтла панели). */
@Composable
private fun ChartSettingsGear(settingsMenu: @Composable (dismiss: () -> Unit) -> Unit) {
    val tokens = LocalTerminalDesignTokens.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(tokens.cornerRadii.smallCard))
                .clickable { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(14.dp)) {
                val stroke = 1.2.dp.toPx()
                val knobRadius = size.minDimension * 0.155f
                val topY = size.height * 0.27f
                val bottomY = size.height * 0.73f
                val topKnobX = size.width * 0.36f
                val bottomKnobX = size.width * 0.64f
                val edge = size.width * 0.04f
                val color = tokens.colors.label

                drawLine(color, Offset(edge, topY), Offset(topKnobX - knobRadius, topY), strokeWidth = stroke)
                drawLine(color, Offset(topKnobX + knobRadius, topY), Offset(size.width - edge, topY), strokeWidth = stroke)
                drawCircle(color, radius = knobRadius, center = Offset(topKnobX, topY), style = Stroke(stroke))

                drawLine(color, Offset(edge, bottomY), Offset(bottomKnobX - knobRadius, bottomY), strokeWidth = stroke)
                drawLine(color, Offset(bottomKnobX + knobRadius, bottomY), Offset(size.width - edge, bottomY), strokeWidth = stroke)
                drawCircle(color, radius = knobRadius, center = Offset(bottomKnobX, bottomY), style = Stroke(stroke))
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 200.dp),
        ) {
            settingsMenu { expanded = false }
        }
    }
}

/** Строка Options с выбором из нескольких значений (label + опции справа). */
@Composable
private fun <T> OptionChoiceRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                options.forEachIndexed { index, (value, text) ->
                    Text(
                        text = text,
                        color = if (value == selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = TerminalFontFamily,
                        maxLines = 1,
                        modifier = Modifier
                            .clickable { onSelect(value) }
                            .background(
                                if (value == selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else Color.Transparent,
                                RoundedCornerShape(3.dp),
                            )
                            .padding(horizontal = 5.dp, vertical = 3.dp),
                    )
                    if (index != options.lastIndex) Spacer(Modifier.width(4.dp))
                }
            }
        },
        onClick = {},
    )
}

/** Строка Options: label + компактный свич (клик по строке тоже переключает). */
@Composable
private fun OptionSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                // Отключаем M3 min-touch-target (48dp) — компактные строки меню
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    TerminalSwitch(
                        checked = checked,
                        onCheckedChange = onCheckedChange,
                        modifier = Modifier.scale(0.7f),
                    )
                }
            }
        },
        onClick = { onCheckedChange(!checked) },
    )
}
