/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui.footer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.aandios.nous.core.ui.component.TerminalButton
import com.aandios.nous.feature.dom.domain.model.OrderIntent

@Composable
fun OrderPlacementPanel(
    symbol: String,
    selectedPrice: Double?,
    orderQuantity: String,
    bestBidPrice: Double?,
    bestAskPrice: Double?,
    onQuantityChanged: (String) -> Unit,
    onOrderIntent: (OrderIntent) -> Unit,
    isTradingEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Статус торговли
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Trading:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = if (isTradingEnabled) "ON" else "OFF",
                    color = if (isTradingEnabled) Color.Green else Color.Red,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // Информация о позиции
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "PnL:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = selectedPrice?.let { formatPrice(it) } ?: "--",
                    color = if (selectedPrice != null) Color.Yellow
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace
                    )
                )
            }

            // Поле ввода количества
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Qty:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                BasicTextField(
                    value = orderQuantity,
                    onValueChange = onQuantityChanged,
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.small
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
            }

            // Market ордера
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TerminalButton(
                    onClick = {
                        val quantity = orderQuantity.toDoubleOrNull() ?: 0.0
                        onOrderIntent(OrderIntent.MarketBuy(symbol, quantity))
                    },
                    isActive = false,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Market Buy",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                TerminalButton(
                    onClick = {
                        val quantity = orderQuantity.toDoubleOrNull() ?: 0.0
                        onOrderIntent(OrderIntent.MarketSell(symbol, quantity))
                    },
                    isActive = false,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Market Sell",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Limit ордера (по выбранной цене)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TerminalButton(
                    onClick = {
                        if (selectedPrice != null) {
                            val quantity = orderQuantity.toDoubleOrNull() ?: 0.0
                            onOrderIntent(OrderIntent.LimitBuy(symbol, selectedPrice, quantity))
                        }
                    },
                    isActive = false,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (selectedPrice != null) "Buy Limit" else "Buy Limit (select price)",
                        color = if (selectedPrice != null)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                TerminalButton(
                    onClick = {
                        if (selectedPrice != null) {
                            val quantity = orderQuantity.toDoubleOrNull() ?: 0.0
                            onOrderIntent(OrderIntent.LimitSell(symbol, selectedPrice, quantity))
                        }
                    },
                    isActive = false,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (selectedPrice != null) "Sell Limit" else "Sell Limit (select price)",
                        color = if (selectedPrice != null)
                            MaterialTheme.colorScheme.secondary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Best Bid/Ask ордера
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TerminalButton(
                    onClick = {
                        if (bestBidPrice != null && bestBidPrice > 0) {
                            val quantity = orderQuantity.toDoubleOrNull() ?: 0.0
                            onOrderIntent(OrderIntent.BestBidBuy(symbol, bestBidPrice, quantity))
                        }
                    },
                    isActive = false,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (bestBidPrice != null && bestBidPrice > 0) "Best Bid" else "Best Bid (waiting...)",
                        color = if (bestBidPrice != null && bestBidPrice > 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                TerminalButton(
                    onClick = {
                        if (bestAskPrice != null && bestAskPrice > 0) {
                            val quantity = orderQuantity.toDoubleOrNull() ?: 0.0
                            onOrderIntent(OrderIntent.BestAskSell(symbol, bestAskPrice, quantity))
                        }
                    },
                    isActive = false,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (bestAskPrice != null && bestAskPrice > 0) "Best Ask" else "Best Ask (waiting...)",
                        color = if (bestAskPrice != null && bestAskPrice > 0)
                            MaterialTheme.colorScheme.secondary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Trade Off кнопка
            TerminalButton(
                onClick = {
                    onOrderIntent(OrderIntent.ToggleTrading)
                },
                isActive = !isTradingEnabled,  // Активна когда торговля ВЫКЛЮЧЕНА
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isTradingEnabled) "⚠️ TRADE OFF" else "✅ TRADE ON",
                    color = if (isTradingEnabled) Color.Red else Color.Green,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun formatPrice(price: Double): String = com.aandios.nous.core.ui.format.SymbolFormatter.DEFAULT.formatPrice(price)
