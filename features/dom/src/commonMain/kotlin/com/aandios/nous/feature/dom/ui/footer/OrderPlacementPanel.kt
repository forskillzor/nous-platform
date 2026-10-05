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
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.core.ui.component.TerminalDropdown
import com.aandios.nous.feature.dom.domain.model.OrderIntent

private val buyColor = Color(0xFF26A69A)
private val sellColor = Color(0xFFEF5350)
private val okColor = Color(0xFF00C853)

/**
 * Компактная панель ордеров DOM.
 *
 * Строки: Trading ON/OFF toggle + Paper + Confirm · Qty/тип/RO ·
 * строка подтверждения (Confirm: ON) · Close All/Cancel All ·
 * Buy/Sell Limit (цена кликом) · market Buy/Sell · Best Bid/Ask.
 * Результаты ордеров показываются snackbar'ами под заголовком панели.
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
    paperEnabled: Boolean,
    confirmOrders: Boolean,
    pendingText: String?,
    onReduceOnlyChanged: (Boolean) -> Unit,
    onLimitOrderTypeChanged: (OrderType) -> Unit,
    onPaperChanged: (Boolean) -> Unit,
    onConfirmChanged: (Boolean) -> Unit,
    onConfirmPending: () -> Unit,
    onCancelPending: () -> Unit,
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
            // Ряд 1: Trading ON/OFF toggle + Paper + Confirm
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ToggleChip(
                    label = if (isTradingEnabled) "Trading ON" else "Trading OFF",
                    active = isTradingEnabled,
                    activeColor = okColor,
                    inactiveColor = sellColor,
                ) { onOrderIntent(OrderIntent.ToggleTrading) }
                ToggleChip(label = "Paper", active = paperEnabled) { onPaperChanged(!paperEnabled) }
                ToggleChip(label = "Confirm", active = confirmOrders) { onConfirmChanged(!confirmOrders) }
            }

            // Ряд 2: qty / тип лимитки / reduce-only
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
                ToggleChip(label = "RO", active = reduceOnly) { onReduceOnlyChanged(!reduceOnly) }
            }

            // Строка подтверждения (Confirm: ON) — вместо немедленного исполнения
            pendingText?.let { text ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Confirm $text",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    OrderButton("OK", buyColor, Modifier.width(52.dp)) { onConfirmPending() }
                    OrderButton("X", sellColor, Modifier.width(36.dp)) { onCancelPending() }
                }
            }

            // Close All / Cancel All
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                OrderButton("Close All", Color(0xFFE05B5B), Modifier.weight(1f)) { onCloseAll() }
                OrderButton("Cancel All", Color(0xFFE0A95B), Modifier.weight(1f)) { onCancelAll() }
            }

            // Лимитки по выбранной цене (выбор — Confirm: ON + клик по уровню)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                val priceReady = selectedPrice != null
                OrderButton(
                    text = if (priceReady) "Buy Limit" else "Buy Limit (click price)",
                    color = if (priceReady) buyColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f),
                ) {
                    selectedPrice?.let {
                        onOrderIntent(OrderIntent.LimitBuy(symbol, it, orderQuantity.toDoubleOrNull() ?: 0.0))
                    }
                }
                OrderButton(
                    text = if (priceReady) "Sell Limit" else "Sell Limit (click price)",
                    color = if (priceReady) sellColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f),
                ) {
                    selectedPrice?.let {
                        onOrderIntent(OrderIntent.LimitSell(symbol, it, orderQuantity.toDoubleOrNull() ?: 0.0))
                    }
                }
            }

            // Market
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                OrderButton("Buy", buyColor, Modifier.weight(1f)) {
                    onOrderIntent(OrderIntent.MarketBuy(symbol, orderQuantity.toDoubleOrNull() ?: 0.0))
                }
                OrderButton("Sell", sellColor, Modifier.weight(1f)) {
                    onOrderIntent(OrderIntent.MarketSell(symbol, orderQuantity.toDoubleOrNull() ?: 0.0))
                }
            }

            // Best Bid / Best Ask
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                val bidReady = bestBidPrice != null && bestBidPrice > 0
                val askReady = bestAskPrice != null && bestAskPrice > 0
                OrderButton(
                    text = if (bidReady) "Best Bid" else "Best Bid ...",
                    color = if (bidReady) buyColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f),
                ) {
                    if (bidReady) onOrderIntent(OrderIntent.BestBidBuy(symbol, bestBidPrice, orderQuantity.toDoubleOrNull() ?: 0.0))
                }
                OrderButton(
                    text = if (askReady) "Best Ask" else "Best Ask ...",
                    color = if (askReady) sellColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f),
                ) {
                    if (askReady) onOrderIntent(OrderIntent.BestAskSell(symbol, bestAskPrice, orderQuantity.toDoubleOrNull() ?: 0.0))
                }
            }
        }
    }
}

/** Компактный чип-тумблер (стиль RO/Paper): активен — подсвечен. */
@Composable
private fun ToggleChip(
    label: String,
    active: Boolean,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (active) activeColor else inactiveColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .background(
                if (active) activeColor.copy(alpha = 0.18f) else Color.Transparent,
                MaterialTheme.shapes.small,
            )
            .border(
                1.dp,
                if (active) activeColor.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.shapes.small,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/** Кнопка без M3-паддингов: фиксированная высота не режет текст. */
@Composable
private fun OrderButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(26.dp)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
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
