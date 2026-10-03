/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout
import com.aandios.nous.core.ui.format.SymbolFormatter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Список балансов
@Composable
fun BalanceList(
    balances: List<MockBalance>,
    modifier: Modifier = Modifier.Companion
) {
    // Общая стоимость портфеля
    val totalValue = balances.sumOf { it.usdValue }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Общая стоимость
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.Companion.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.Companion.padding(12.dp)
            ) {
                Text(
                    text = "Total Portfolio Value",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = String.format("$%,.2f", totalValue),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }

        HorizontalDivider()

        // Список активов
        LazyColumn(
            modifier = Modifier.Companion.weight(1f),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            // Заголовки
            item {
                Row(
                    modifier = Modifier.Companion
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Asset", Modifier.Companion.weight(1f), style = MaterialTheme.typography.labelSmall)
                    Text("Free", Modifier.Companion.weight(1f), style = MaterialTheme.typography.labelSmall)
                    Text("Locked", Modifier.Companion.weight(1f), style = MaterialTheme.typography.labelSmall)
                    Text("Total", Modifier.Companion.weight(1f), style = MaterialTheme.typography.labelSmall)
                    Text("USD Value", Modifier.Companion.weight(1f), style = MaterialTheme.typography.labelSmall)
                }
            }

            items(balances) { balance ->
                Row(
                    modifier = Modifier.Companion
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Companion.CenterVertically
                ) {
                    Text(
                        text = balance.asset,
                        Modifier.Companion.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = String.format("%.3f", balance.free),
                        Modifier.Companion.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = String.format("%.3f", balance.locked),
                        Modifier.Companion.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = String.format("%.3f", balance.total),
                        Modifier.Companion.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = String.format("$%,.2f", balance.usdValue),
                        Modifier.Companion.weight(1f),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                HorizontalDivider(modifier = Modifier.Companion.padding(horizontal = 8.dp))
            }
        }
    }
}