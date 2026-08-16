/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.aandios.nous.feature.dom.domain.model.AggregationLevel

private val timeframes = listOf("1m", "5m", "15m", "30m", "1h", "4h", "1d", "1w")
private val toolbarBg = Color.Black.copy(alpha = 0.35f)
private val accentColor = Color(0xFF5B9BD5)

@Composable
fun ChartToolbar(
    currentSymbol: String,
    currentTimeframe: String,
    availableSymbols: List<String>,
    onSymbolChange: (String) -> Unit,
    onTimeframeChange: (String) -> Unit,
    crosshairEnabled: Boolean = false,
    onCrosshairToggle: () -> Unit = {},
    chartMode: ChartMode = ChartMode.CANDLESTICK,
    onChartModeToggle: () -> Unit = {},
    symbolsWithFootprint: Set<String> = emptySet(),
    fpAggregation: AggregationLevel = AggregationLevel.BaseTick,
    onFpAggregationChange: (AggregationLevel) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(toolbarBg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolSearchDropdown(
            symbols = availableSymbols,
            currentSymbol = currentSymbol,
            onSymbolSelected = onSymbolChange,
            symbolsWithFootprint = symbolsWithFootprint,
        )

        Spacer(Modifier.width(12.dp))

        ChartModeToggleButton(mode = chartMode, onToggle = onChartModeToggle)

        if (chartMode == ChartMode.FOOTPRINT) {
            Spacer(Modifier.width(8.dp))
            FpAggregationSelector(level = fpAggregation, onChange = onFpAggregationChange)
        }

        Spacer(Modifier.width(8.dp))
        CrosshairToggleButton(enabled = crosshairEnabled, onToggle = onCrosshairToggle)
        Spacer(Modifier.width(8.dp))
        TimeframeSelector(currentTimeframe = currentTimeframe, onTimeframeChange = onTimeframeChange)
    }
}

@Composable
private fun ChartModeToggleButton(mode: ChartMode, onToggle: () -> Unit) {
    val label = when (mode) { ChartMode.CANDLESTICK -> "C"; ChartMode.FOOTPRINT -> "FP" }
    Text(
        text = label,
        color = if (mode == ChartMode.FOOTPRINT) accentColor else MaterialTheme.colorScheme.surfaceVariant,
        fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
        modifier = Modifier.clickable { onToggle() }.background(
            if (mode == ChartMode.FOOTPRINT) accentColor.copy(alpha = 0.25f) else Color.Transparent, RoundedCornerShape(3.dp)
        ).padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun CrosshairToggleButton(enabled: Boolean, onToggle: () -> Unit) {
    Text(
        text = "\u29C9",
        color = if (enabled) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.surfaceVariant,
        fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
        modifier = Modifier.clickable { onToggle() }.background(
            if (enabled) accentColor.copy(alpha = 0.25f) else Color.Transparent, RoundedCornerShape(3.dp)
        ).padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun TimeframeSelector(currentTimeframe: String, onTimeframeChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        timeframes.forEach { tf ->
            val isActive = tf == currentTimeframe
            Text(
                text = tf,
                color = if (isActive) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { onTimeframeChange(tf) }.background(
                    if (isActive) accentColor.copy(alpha = 0.25f) else Color.Transparent, RoundedCornerShape(3.dp)
                ).padding(horizontal = 5.dp, vertical = 3.dp),
            )
            if (tf != timeframes.last()) Spacer(Modifier.width(2.dp))
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
