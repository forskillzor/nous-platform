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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.OrderStatus
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous_platform.utils.formatTime

// Список ордеров (реальные данные из TradingAdapter)
@Composable
fun OrdersList(
    orders: List<Order>,
    onCancel: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier.Companion
) {
    val formatter = SymbolFormatter.DEFAULT
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Symbol", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("Side/Type", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("Price", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("Qty/Filled", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("Time", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("Status", Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall)
                if (onCancel != null) Text("", Modifier.weight(0.5f), style = MaterialTheme.typography.labelSmall)
            }
        }

        items(orders) { order ->
            val sideColor = if (order.side == OrderSide.BUY)
                MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(order.symbol, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)

                Column(modifier = Modifier.weight(1f)) {
                    Text(order.side.name, color = sideColor, style = MaterialTheme.typography.bodySmall)
                    Text(order.orderType.name, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }

                Text(
                    text = if (order.orderType == com.aandios.nous.api.market.model.orderbook.OrderType.MARKET) "market"
                    else formatter.formatPrice(order.price),
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(formatter.formatVolume(order.quantity), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                    Text("Filled: ${formatter.formatVolume(order.filledQuantity)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }

                Text(formatTime(order.timestamp), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)

                Text(
                    text = order.status.name,
                    Modifier.weight(0.8f),
                    color = when (order.status) {
                        OrderStatus.OPEN -> Color.Yellow
                        OrderStatus.FILLED -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall,
                )

                if (onCancel != null) {
                    if (order.status == OrderStatus.OPEN) {
                        Text(
                            text = "Cancel",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(0.5f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(0.5f))
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}
