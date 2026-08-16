/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Статистика торговли
@Composable
fun TradingStats(
    modifier: Modifier = Modifier.Companion
) {
    // Мок статистика
    val stats = remember {
        mapOf(
            "Total Trades" to "156",
            "Winning Trades" to "89",
            "Losing Trades" to "67",
            "Win Rate" to "57.1%",
            "Total PnL" to "+$2,450.50",
            "Avg Win" to "$85.20",
            "Avg Loss" to "-$42.30",
            "Largest Win" to "$450.00",
            "Largest Loss" to "-$180.00",
            "Profit Factor" to "2.15",
            "Sharpe Ratio" to "1.82",
            "Max Drawdown" to "-12.5%"
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp)
    ) {
        items(stats.toList()) { (key, value) ->
            Row(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = key,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )

                val valueColor = when {
                    value.startsWith("+") -> MaterialTheme.colorScheme.primary
                    value.startsWith("-") -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurface
                }

                Text(
                    text = value,
                    color = valueColor,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Divider(modifier = Modifier.Companion.padding(vertical = 4.dp))
        }
    }
}