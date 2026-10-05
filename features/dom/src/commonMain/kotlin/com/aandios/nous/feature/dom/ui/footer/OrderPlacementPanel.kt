/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui.footer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.commands.CommandResult
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.core.ui.component.TerminalButton
import com.aandios.nous.core.ui.component.TerminalDropdown
import com.aandios.nous.feature.dom.domain.model.OrderIntent

/**
 * Компактная панель ордеров DOM: все кнопки помещаются по высоте.
 * Ряды: Market Buy/Sell, Limit Buy/Sell, Best Bid/Ask, Close All/Cancel All,
 * TRADE OFF + строка qty/тип/reduce-only/статус/результат.
 */
@Composable
fun OrderPlacementPanel(
    symbol: String,
    selectedPrice: Double?,
    orderQuantity: String,
    bestBidPrice: Double?,
    bestAskPrice: Double?,
    onQuantityChanged: (String) -> Unit,
    onOrderIntent: (OrderIntent) -> Unit,
    onCloseAll: () -> Unit,
    onCancelAll: () -> Unit,
    isTradingEnabled: Boolean,
    reduceOnly: Boolean,
    limitOrderType: OrderType,
    lastCommandResult: CommandResult?,
    onReduceOnlyChanged: (Boolean) -> Unit,
    onLimitOrderTypeChanged: (OrderType) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Ряд 1: Market
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                OrderButton("Market Buy", MaterialTheme.colorScheme.primary, Modifier.weight(1f)) {
                    onOrderIntent(OrderIntent.MarketBuy(symbol, orderQuantity.toDoubleOrNull() ?: 0.0))
                }
                OrderButton("Market Sell", MaterialTheme.colorScheme.secondary, Modifier.weight(1f)) {
                    onOrderIntent(OrderIntent.MarketSell(symbol, orderQuantity.toDoubleOrNull() ?: 0.0))
                }
            }

            // Ряд 2: Limit
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                val priceReady = selectedPrice != null
                OrderButton(
                    text = if (priceReady) "Limit Buy" else "Limit Buy (click price)",
                    color = if (priceReady) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f),
                ) {
                    selectedPrice?.let {
                        onOrderIntent(OrderIntent.LimitBuy(symbol, it, orderQuantity.toDoubleOrNull() ?: 0.0))
                    }
                }
                OrderButton(
                    text = if (priceReady) "Limit Sell" else "Limit Sell (click price)",
                    color = if (priceReady) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f),
                ) {
                    selectedPrice?.let {
                        onOrderIntent(OrderIntent.LimitSell(symbol, it, orderQuantity.toDoubleOrNull() ?: 0.0))
                    }
                }
            }

            // Ряд 3: Best Bid / Best Ask
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                val bidReady = bestBidPrice != null && bestBidPrice > 0
                val askReady = bestAskPrice != null && bestAskPrice > 0
                OrderButton(
                    text = if (bidReady) "Best Bid" else "Best Bid ...",
                    color = if (bidReady) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f),
                ) {
                    if (bidReady) onOrderIntent(OrderIntent.BestBidBuy(symbol, bestBidPrice, orderQuantity.toDoubleOrNull() ?: 0.0))
                }
                OrderButton(
                    text = if (askReady) "Best Ask" else "Best Ask ...",
                    color = if (askReady) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f),
                ) {
                    if (askReady) onOrderIntent(OrderIntent.BestAskSell(symbol, bestAskPrice, orderQuantity.toDoubleOrNull() ?: 0.0))
                }
            }

            // Ряд 4: Close All / Cancel All
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                OrderButton("Close All", Color(0xFFE05B5B), Modifier.weight(1f)) { onCloseAll() }
                OrderButton("Cancel All", Color(0xFFE0A95B), Modifier.weight(1f)) { onCancelAll() }
            }

            // Ряд 5: настройки (qty / тип лимитки / reduce-only)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Qty:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                BasicTextField(
                    value = orderQuantity,
                    onValueChange = onQuantityChanged,
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
                TerminalDropdown(
                    currentValue = limitOrderType,
                    items = listOf(OrderType.LIMIT, OrderType.POST_ONLY, OrderType.IOC, OrderType.FOK),
                    onValueChanged = onLimitOrderTypeChanged,
                    displayText = { it.name },
                    menuWidth = 100.dp,
                )
                Text(
                    text = if (reduceOnly) "RO" else "RO",
                    color = if (reduceOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .background(
                            if (reduceOnly) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                            MaterialTheme.shapes.small
                        )
                        .clickable { onReduceOnlyChanged(!reduceOnly) }
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }

            // Ряд 6: статус + TRADE OFF + результат
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isTradingEnabled) "Trading: ON" else "Trading: OFF",
                    color = if (isTradingEnabled) Color.Green else Color.Red,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = statusText(lastCommandResult),
                    color = statusColor(lastCommandResult),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                TerminalButton(
                    onClick = { onOrderIntent(OrderIntent.ToggleTrading) },
                    isActive = !isTradingEnabled,
                    height = 22.dp,
                ) {
                    Text(
                        text = if (isTradingEnabled) "⚠ OFF" else "✅ ON",
                        color = if (isTradingEnabled) Color.Red else Color.Green,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TerminalButton(onClick = onClick, modifier = modifier, height = 24.dp) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun statusText(result: CommandResult?): String = when (result) {
    null -> ""
    is CommandResult.Success -> {
        val d = result.orderData
        when {
            d.symbol == "SYSTEM" && d.quantity > 0 -> "Closed ${d.quantity.toInt()} positions"
            d.symbol == "SYSTEM" -> "OK"
            d.type == OrderType.MARKET -> "Market ${d.side} ${d.quantity}"
            else -> "${d.type} ${d.side} ${d.quantity}"
        }
    }
    is CommandResult.Error -> result.message
    CommandResult.TradingDisabled -> "Trading disabled"
}

private fun statusColor(result: CommandResult?): Color = when (result) {
    is CommandResult.Success -> Color.Green
    is CommandResult.Error -> Color.Red
    CommandResult.TradingDisabled -> Color.Red
    null -> Color.Transparent
}

private fun formatPrice(price: Double): String = com.aandios.nous.core.ui.format.SymbolFormatter.DEFAULT.formatPrice(price)
