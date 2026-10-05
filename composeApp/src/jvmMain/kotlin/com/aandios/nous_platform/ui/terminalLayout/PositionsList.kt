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
import androidx.compose.ui.unit.dp
import com.aandios.nous.api.market.model.trading.Position
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.core.ui.format.SymbolFormatter

// Список позиций (реальные данные из TradingAdapter)
@Composable
fun PositionsList(
    positions: List<Position>,
    onClose: ((Position) -> Unit)? = null,
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
                Text("Side", Modifier.weight(0.7f), style = MaterialTheme.typography.labelSmall)
                Text("Size", Modifier.weight(0.9f), style = MaterialTheme.typography.labelSmall)
                Text("Entry", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("Liq", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("PnL", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                if (onClose != null) Text("", Modifier.weight(0.5f), style = MaterialTheme.typography.labelSmall)
            }
        }

        items(positions) { position ->
            val pnlColor = if (position.pnl >= 0)
                MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary
            val sideColor = if (position.side == TradeSide.BUY)
                MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(position.symbol, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                Text(position.side.name, Modifier.weight(0.7f), color = sideColor, style = MaterialTheme.typography.bodySmall)
                Text(formatter.formatVolume(position.quantity), Modifier.weight(0.9f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text(formatter.formatPrice(position.avgPrice), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text(position.liquidatePrice?.let { formatter.formatPrice(it) } ?: "-", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text(formatSigned(position.pnl, formatter), Modifier.weight(1f), color = pnlColor, style = MaterialTheme.typography.bodySmall)
                if (onClose != null) {
                    Text(
                        text = "Close",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .weight(0.5f)
                            .padding(start = 4.dp),
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

private fun formatSigned(v: Double, f: SymbolFormatter): String =
    (if (v >= 0) "+" else "") + f.formatPrice(v)
