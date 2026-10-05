/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.core.ui.component.SymbolSearchDropdown
import com.aandios.nous.core.ui.component.TerminalDropdownWithLabel
import com.aandios.nous.feature.dom.domain.model.AggregationLevel

private val timeframes = listOf("1m", "5m", "15m", "30m", "1h", "4h", "1d", "1w")
private val toolbarBg = Color.Black.copy(alpha = 0.35f)
private val accentColor = Color(0xFF5B9BD5)

private val chartModes = listOf(
    ChartMode.CANDLESTICK to "Candles",
    ChartMode.FOOTPRINT to "Footprint",
)

/**
 * Верхняя панель графика (над областью графика, не оверлей):
 * биржа, символ, режим графика, агрегация footprint, таймфреймы.
 * Инструменты рисования вынесены в отдельную левую панель [DrawingToolPanel].
 */
@Composable
fun ChartToolbar(
    currentSymbol: String,
    currentTimeframe: String,
    availableSymbols: List<String>,
    onSymbolChange: (String) -> Unit,
    onTimeframeChange: (String) -> Unit,
    exchanges: List<String> = emptyList(),
    currentExchange: String = "",
    onExchangeChange: (String) -> Unit = {},
    chartMode: ChartMode = ChartMode.CANDLESTICK,
    onChartModeChange: (ChartMode) -> Unit = {},
    symbolsWithFootprint: Set<String> = emptySet(),
    fpAggregation: AggregationLevel = AggregationLevel.BaseTick,
    onFpAggregationChange: (AggregationLevel) -> Unit = {},
    tradingEnabled: Boolean = false,
    onTradingToggle: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(toolbarBg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (currentExchange.isNotEmpty()) {
            ExchangeDropdown(
                current = currentExchange,
                exchanges = exchanges,
                onChange = onExchangeChange,
            )
//            Spacer(Modifier.width(12.dp))
        }

        SymbolSearchDropdown(
            symbols = availableSymbols,
            currentSymbol = currentSymbol,
            onSymbolSelected = onSymbolChange,
            symbolsWithFootprint = symbolsWithFootprint,
            showLabel = false,
        )

//        Spacer(Modifier.width(12.dp))

        ChartModeDropdown(mode = chartMode, onModeChange = onChartModeChange)

        if (chartMode == ChartMode.FOOTPRINT) {
            Spacer(Modifier.width(8.dp))
            FpAggregationSelector(level = fpAggregation, onChange = onFpAggregationChange)
        }

        Spacer(Modifier.width(8.dp))
        TimeframeDropdown(currentTimeframe = currentTimeframe, onTimeframeChange = onTimeframeChange)

        // Компактный тумблер trading — после таймфрейма
        Spacer(Modifier.width(8.dp))
        TradingToggle(enabled = tradingEnabled, onToggle = onTradingToggle)
    }
}

@Composable
private fun TradingToggle(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Text(
        text = if (enabled) "Trading" else "Trading",
        color = if (enabled) Color(0xFF00C853) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .clickable { onToggle(!enabled) }
            .padding(horizontal = 4.dp, vertical = 3.dp),
    )
}

@Composable
private fun ExchangeDropdown(current: String, exchanges: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    TerminalDropdownWithLabel(label = "") {
        Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { expanded = true }
        ) {
            Text(
                text = current,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "\u25BE",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 130.dp)
        ) {
            exchanges.forEach { ex ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = ex,
                            color = if (ex == current) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                    onClick = {
                        onChange(ex)
                        expanded = false
                    },
                    modifier = Modifier.background(
                        if (ex == current) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else Color.Transparent
                    )
                )
            }
        }
        }
    }
}

@Composable
private fun ChartModeDropdown(mode: ChartMode, onModeChange: (ChartMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = chartModes.firstOrNull { it.first == mode }?.second ?: "Candles"

    TerminalDropdownWithLabel(label = "") {
        Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { expanded = true }
        ) {
            Text(
                text = currentLabel,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "\u25BE",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 130.dp)
        ) {
            chartModes.forEach { (m, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            color = if (m == mode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                    onClick = {
                        onModeChange(m)
                        expanded = false
                    },
                    modifier = Modifier.background(
                        if (m == mode) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else Color.Transparent
                    )
                )
            }
        }
        }
    }
}

@Composable
private fun TimeframeDropdown(currentTimeframe: String, onTimeframeChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    TerminalDropdownWithLabel(label = "") {
        Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { expanded = true }
        ) {
            Text(
                text = currentTimeframe,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "\u25BE",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 90.dp)
        ) {
            timeframes.forEach { tf ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = tf,
                            color = if (tf == currentTimeframe) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                    onClick = {
                        onTimeframeChange(tf)
                        expanded = false
                    },
                    modifier = Modifier.background(
                        if (tf == currentTimeframe) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else Color.Transparent
                    )
                )
            }
        }
        }
    }
}

@Composable
private fun FpAggregationSelector(level: AggregationLevel, onChange: (AggregationLevel) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AggregationLevel.all().forEach { ag ->
            val isActive = ag == level
            val label = when (ag) {
                AggregationLevel.BaseTick -> "1x"
                AggregationLevel.TenTick -> "10x"
                AggregationLevel.HundredTick -> "100x"
            }
            Text(
                text = label,
                color = if (isActive) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { onChange(ag) }.background(
                    if (isActive) accentColor.copy(alpha = 0.25f) else Color.Transparent, RoundedCornerShape(3.dp)
                ).padding(horizontal = 5.dp, vertical = 3.dp),
            )
            if (ag != AggregationLevel.all().last()) Spacer(Modifier.width(2.dp))
        }
    }
}
