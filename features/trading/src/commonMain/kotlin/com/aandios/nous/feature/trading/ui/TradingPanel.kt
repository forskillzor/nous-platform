/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trading.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.api.market.model.Balance
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.Position
import com.aandios.nous.api.market.model.trading.TradeFill
import com.aandios.nous.core.ui.component.TerminalDropdown
import com.aandios.nous.core.ui.format.SymbolFormatter

enum class TradingTab(val label: String) {
    POSITIONS("Positions"),
    ORDERS("Orders"),
    BALANCES("Balance"),
    HISTORY("History"),
}

/**
 * Полная торговая панель: заголовок «Trading {exchange}» + настройки
 * (режим маржи, плечо, hedge/one-way) + табы позиции/ордера/балансы/история.
 */
@Composable
fun TradingPanel(
    viewModel: TradingViewModel,
    registry: ProviderRegistry,
    modifier: Modifier = Modifier,
) {
    val providerId by viewModel.providerId.collectAsState()
    val positions by viewModel.positions.collectAsState()
    val orders by viewModel.openOrders.collectAsState()
    val balances by viewModel.balances.collectAsState()
    val history by viewModel.tradeHistory.collectAsState()
    val positionMode by viewModel.positionMode.collectAsState()
    val marginMode by viewModel.marginMode.collectAsState()
    val leverage by viewModel.leverage.collectAsState()
    val lastMessage by viewModel.lastMessage.collectAsState()
    val activeTabRaw by viewModel.activeTab.collectAsState()
    val tab = TradingTab.values().firstOrNull { it.name == activeTabRaw } ?: TradingTab.POSITIONS

    val provider: Provider? = registry.get(providerId)
    val exchangeName = provider?.config?.displayName ?: providerId
    val formatter = remember { SymbolFormatter.DEFAULT }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        // ── Заголовок: Trading {exchange} + provider ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Trading $exchangeName",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            TerminalDropdown(
                currentValue = providerId,
                items = registry.providers.map { it.providerId },
                onValueChanged = { viewModel.selectProvider(it) },
                displayText = { id ->
                    registry.providers.firstOrNull { it.providerId == id }?.config?.displayName ?: id
                },
                menuWidth = 130.dp,
            )
        }

        // ── Настройки: маржа / плечо / режим позиций ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MarginModeSelector(
                current = marginMode,
                onChange = { viewModel.setMarginMode(it) },
            )
            LeverageSelector(
                current = leverage,
                onChange = { viewModel.setLeverage(it) },
            )
            PositionModeToggle(
                current = positionMode,
                onSwitch = { mode -> viewModel.switchPositionMode(mode) },
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Cancel all",
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickableNoIndication { viewModel.cancelAllOrders() }
            )
        }

        lastMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        // ── Табы ──
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TradingTab.values().forEach { t ->
                Text(
                    text = t.label,
                    color = if (t == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = if (t == tab) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clickableNoIndication { viewModel.selectTab(t.name) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

        // ── Контент ──
        when (tab) {
            TradingTab.POSITIONS -> PositionsList(positions, formatter, onClose = { viewModel.closePosition(it) })
            TradingTab.ORDERS -> OrdersList(orders, formatter, onCancel = { viewModel.cancelOrder(it) })
            TradingTab.BALANCES -> BalanceList(balances)
            TradingTab.HISTORY -> {
                LaunchedEffect(Unit) { viewModel.refreshTradeHistory() }
                HistoryList(history, formatter)
            }
        }
    }
}

@Composable
private fun MarginModeSelector(current: Int, onChange: (Int) -> Unit) {
    TerminalDropdown(
        currentValue = current,
        items = listOf(1, 2),
        onValueChanged = onChange,
        displayText = { if (it == 1) "Isolated" else "Cross" },
        menuWidth = 120.dp,
    )
}

@Composable
private fun LeverageSelector(current: Int?, onChange: (Int) -> Unit) {
    TerminalDropdown(
        currentValue = current ?: 0,
        items = listOf(1, 2, 3, 5, 10, 20, 50, 100, 125),
        onValueChanged = { if (it > 0) onChange(it) },
        displayText = { if (it <= 0) "Lev" else "${it}x" },
        menuWidth = 90.dp,
    )
}

@Composable
private fun PositionModeToggle(current: Int?, onSwitch: (Int) -> Unit) {
    val hedge = current == 1
    Text(
        text = if (hedge) "Hedge" else "One-way",
        color = MaterialTheme.colorScheme.primary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clickableNoIndication { onSwitch(if (hedge) 2 else 1) }
    )
}

// ── Списки ──

@Composable
private fun PositionsList(
    positions: List<Position>,
    formatter: SymbolFormatter,
    onClose: (Position) -> Unit,
) {
    if (positions.isEmpty()) {
        EmptyHint("No open positions")
        return
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeaderRow(listOf("Symbol", "Side", "Qty", "Entry", "Liq", "PnL", ""))
        positions.forEach { p ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cell(p.symbol, 1.2f)
                Cell(p.side.name, 0.8f, color = if (p.side == com.aandios.nous.api.market.model.trading.TradeSide.BUY)
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                Cell(formatter.formatVolume(p.quantity), 0.8f)
                Cell(formatter.formatPrice(p.avgPrice), 1f)
                Cell(p.liquidatePrice?.let { formatter.formatPrice(it) } ?: "-", 1f)
                Cell(
                    text = formatSigned(p.pnl),
                    weight = 0.9f,
                    color = if (p.pnl >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "Close",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    modifier = Modifier.clickableNoIndication { onClose(p) },
                )
            }
        }
    }
}

@Composable
private fun OrdersList(
    orders: List<Order>,
    formatter: SymbolFormatter,
    onCancel: (String) -> Unit,
) {
    if (orders.isEmpty()) {
        EmptyHint("No open orders")
        return
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeaderRow(listOf("Symbol", "Side", "Type", "Price", "Qty", "Filled", ""))
        orders.forEach { o ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cell(o.symbol, 1.2f)
                Cell(o.side.name, 0.7f, color = if (o.side == com.aandios.nous.api.market.model.orderbook.OrderSide.BUY)
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                Cell(o.orderType.name, 0.9f)
                Cell(if (o.orderType == com.aandios.nous.api.market.model.orderbook.OrderType.MARKET) "market" else formatter.formatPrice(o.price), 1f)
                Cell(formatter.formatVolume(o.quantity), 0.8f)
                Cell(formatter.formatVolume(o.filledQuantity), 0.8f)
                Text(
                    text = "Cancel",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    modifier = Modifier.clickableNoIndication { onCancel(o.orderId) },
                )
            }
        }
    }
}

@Composable
private fun BalanceList(balances: List<Balance>) {
    if (balances.isEmpty()) {
        EmptyHint("No balances")
        return
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeaderRow(listOf("Asset", "Available", "Frozen", "Margin"))
        balances.forEach { b ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cell(b.currency, 0.8f)
                Cell(b.amount, 1.2f)
                Cell(b.frozen, 1f)
                Cell(b.margin, 1f)
            }
        }
    }
}

@Composable
private fun HistoryList(history: List<TradeFill>, formatter: SymbolFormatter) {
    if (history.isEmpty()) {
        EmptyHint("No trade history")
        return
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeaderRow(listOf("Symbol", "Side", "Price", "Qty", "Fee", "PnL"))
        history.forEach { f ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cell(f.symbol, 1.2f)
                Cell(f.side.name, 0.7f, color = if (f.side == com.aandios.nous.api.market.model.orderbook.OrderSide.BUY)
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                Cell(formatter.formatPrice(f.price), 1f)
                Cell(formatter.formatVolume(f.quantity), 0.8f)
                Cell(formatter.formatPrice(f.fee), 0.8f)
                Cell(
                    text = formatSigned(f.pnl),
                    weight = 0.8f,
                    color = if (f.pnl >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ── Вспомогательное ──

@Composable
private fun HeaderRow(labels: List<String>) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        labels.forEach { label ->
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RowScope.Cell(text: String, weight: Float, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        maxLines = 1,
        textAlign = TextAlign.End,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

private fun formatSigned(v: Double): String =
    (if (v >= 0) "+" else "") + SymbolFormatter.DEFAULT.formatPrice(v)

@Composable
private fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
    )
}
