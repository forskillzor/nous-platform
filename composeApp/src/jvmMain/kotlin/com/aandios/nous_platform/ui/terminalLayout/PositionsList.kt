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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nous_platform.composeapp.generated.resources.Res
import nous_platform.composeapp.generated.resources.close
import org.jetbrains.compose.resources.painterResource

// Список позиций
@Composable
fun PositionsList(
    positions: List<MockPosition>,
    modifier: Modifier = Modifier.Companion
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
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
                Text("Symbol", Modifier.Companion.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("Size", Modifier.Companion.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("Entry", Modifier.Companion.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("Mark", Modifier.Companion.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("PnL", Modifier.Companion.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("Action", Modifier.Companion.weight(0.5f), style = MaterialTheme.typography.labelSmall)
            }
        }

        items(positions) { position ->
            val pnlColor = if (position.pnl >= 0)
                MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary

            Row(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Companion.CenterVertically
            ) {
                Text(
                    text = position.symbol,
                    Modifier.Companion.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = String.format("%.3f", position.quantity),
                    Modifier.Companion.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = String.format("%.1f", position.entryPrice),
                    Modifier.Companion.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = String.format("%.1f", position.currentPrice),
                    Modifier.Companion.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )

                Column(
                    modifier = Modifier.Companion.weight(1f)
                ) {
                    Text(
                        text = String.format("%.2f", position.pnl),
                        color = pnlColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = String.format("(%.2f%%)", position.pnlPercent),
                        color = pnlColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                // Кнопка закрытия
                IconButton(
                    onClick = { /* Close position */ },
                    modifier = Modifier.Companion
                        .weight(0.5f)
                        .size(24.dp)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.close),
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.Companion.size(16.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.Companion.padding(horizontal = 8.dp))
        }
    }
}