/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun IndicatorsPanel(
    modifier: Modifier = Modifier.Companion
) {
    val indicators = listOf(
        "Moving Average" to listOf("SMA", "EMA", "WMA"),
        "Oscillators" to listOf("RSI", "MACD", "Stochastic", "CCI"),
        "Volatility" to listOf("Bollinger Bands", "ATR", "Keltner Channels"),
        "Volume" to listOf("Volume", "OBV", "MFI", "VWAP")
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        indicators.forEach { (category, indicatorList) ->
            item {
                Text(
                    text = category,
                    modifier = Modifier.Companion.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            items(indicatorList) { indicator ->
                Surface(
                    modifier = Modifier.Companion
                        .fillMaxWidth()
                        .clickable { /* Добавить индикатор */ }
                ) {
                    Text(
                        text = indicator,
                        modifier = Modifier.Companion
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}