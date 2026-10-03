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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous_platform.utils.formatTime
import org.jetbrains.compose.resources.painterResource
import nous_platform.composeapp.generated.resources.Res
import nous_platform.composeapp.generated.resources.close

// Список ордеров
@Composable
fun OrdersList(
    orders: List<MockOrder>,
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
                Text("Side/Type", Modifier.Companion.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("Price", Modifier.Companion.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("Qty/Filled", Modifier.Companion.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("Time", Modifier.Companion.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("Status", Modifier.Companion.weight(0.8f), style = MaterialTheme.typography.labelSmall)
                Text("", Modifier.Companion.weight(0.5f), style = MaterialTheme.typography.labelSmall)
            }
        }

        items(orders) { order ->
            val sideColor = if (order.side == TradeSide.BUY)
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
                    text = order.symbol,
                    Modifier.Companion.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall
                )

                Column(
                    modifier = Modifier.Companion.weight(1f)
                ) {
                    Text(
                        text = order.side.name,
                        color = sideColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = order.type,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Text(
                    text = String.format("%.1f", order.price),
                    Modifier.Companion.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )

                Column(
                    modifier = Modifier.Companion.weight(1f)
                ) {
                    Text(
                        text = String.format("%.3f", order.quantity),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Filled: ${String.format("%.3f", order.filled)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Text(
                    text = formatTime(order.timestamp),
                    Modifier.Companion.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = order.status,
                    Modifier.Companion.weight(0.8f),
                    color = when (order.status) {
                        "OPEN" -> Color.Companion.Yellow
                        "FILLED" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall
                )

                // Кнопка отмены (только для OPEN ордеров)
                if (order.status == "OPEN") {
                    IconButton(
                        onClick = { /* Cancel order */ },
                        modifier = Modifier.Companion
                            .weight(0.5f)
                            .size(24.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.close),
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.Companion.size(16.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.Companion.weight(0.5f))
                }
            }

            HorizontalDivider(modifier = Modifier.Companion.padding(horizontal = 8.dp))
        }
    }
}