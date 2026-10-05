/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.layout.*
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
import com.aandios.nous.api.market.model.Balance

// Список балансов (реальные данные из TradingAdapter)
@Composable
fun BalanceList(
    balances: List<Balance>,
    modifier: Modifier = Modifier.Companion
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Futures account",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = balances.joinToString("  ") { "${it.currency}: ${it.amount}" },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Asset", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    Text("Available", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    Text("Frozen", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    Text("Margin", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    Text("Equity", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                }
            }

            items(balances) { balance ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(balance.currency, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                    Text(balance.amount, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text(balance.frozen, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text(balance.margin, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text(balance.equity, Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}
