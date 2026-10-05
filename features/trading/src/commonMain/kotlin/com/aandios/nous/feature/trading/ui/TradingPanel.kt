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
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.api.market.model.Balance
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.OrderStatus
import com.aandios.nous.api.market.model.trading.Position
import com.aandios.nous.api.market.model.trading.TradeFill
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.api.market.paper.PaperTrading
import com.aandios.nous.core.ui.component.TerminalDropdown
import com.aandios.nous.core.ui.component.TerminalSwitch
import com.aandios.nous.core.ui.format.SymbolFormatter
import kotlinx.coroutines.launch

enum class TradingTab(val label: String) {
    POSITIONS("Positions"),
    ORDERS("Orders"),
    BALANCES("Balance"),
    HISTORY("History"),
}

// Веса колонок: заголовок и строки используют одни и те же —
// колонки выровнены.
private object PW {
    const val SYMBOL = 1.3f
    const val SIDE = 0.7f
    const val QTY = 0.9f
    const val ENTRY = 1f
    const val LIQ = 1f
    const val PNL = 1f
    const val ACTION = 0.7f
}

private object OW {
    const val SYMBOL = 1.2f
    const val SIDE_TYPE = 0.9f
    const val PRICE = 1f
    const val QTY = 0.9f
    const val TIME = 0.9f
    const val STATUS = 0.8f
    const val ACTION = 0.7f
}

private object BW {
    const val ASSET = 0.8f
    const val AVAILABLE = 1.2f
    const val FROZEN = 1f
    const val MARGIN = 1f
    const val EQUITY = 1f
}

private object HW {
    const val SYMBOL = 1.2f
    const val SIDE = 0.7f
    const val PRICE = 1f
    const val QTY = 0.8f
    const val FEE = 0.8f
    const val PNL = 0.9f
}

/**
 * Полная торговая панель: заголовок «Trading {exchange}» + провайдер +
 * тумблер Paper, строка настроек (маржа/плечо/режим позиций/cancel all),
 * табы позиции/ордера/балансы/история.
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

    val paperEnabled by PaperTrading.enabledFlow.collectAsState()
    val paperSettings: PaperSettingsController = org.koin.compose.koinInject()
    val scope = rememberCoroutineScope()

    val tabCounts = mapOf(
        TradingTab.POSITIONS to positions.size,
        TradingTab.ORDERS to orders.size,
        TradingTab.BALANCES to balances.size,
        TradingTab.HISTORY to history.size,
    )

    val exchangeName = registry.get(providerId)?.config?.displayName ?: providerId
    val formatter = remember { SymbolFormatter.DEFAULT }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        // ── Заголовок: Trading {exchange} + провайдер + Paper ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Trading $exchangeName",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TerminalDropdown(
                currentValue = providerId,
                items = registry.providers.map { it.providerId },
                onValueChanged = { viewModel.selectProvider(it) },
                displayText = { id ->
                    registry.providers.firstOrNull { it.providerId == id }?.config?.displayName ?: id
                },
                menuWidth = 130.dp,
            )
            PaperToggle(
                enabled = paperEnabled,
                onToggle = { viewModel.setPaperEnabled(it) },
            )
            if (paperEnabled) {
                // Настройки paper trading (баланс/сбросы) — отдельное окно
                Text(
                    text = "⚙",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickableNoIndication { paperSettings.open() }
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }

        // ── Настройки: маржа / плечо / режим позиций | Cancel all ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                modifier = Modifier
                    .clickableNoIndication { viewModel.cancelAllOrders() }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }

        // ── Табы ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TradingTab.values().forEach { t ->
                Text(
                    text = "${t.label} (${tabCounts[t] ?: 0})",
                    color = if (t == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = if (t == tab) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clickableNoIndication { viewModel.selectTab(t.name) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

        lastMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        // ── Контент ──
        when (tab) {
            TradingTab.POSITIONS -> PositionsTable(
                positions, formatter, onClose = { viewModel.closePosition(it) },
                modifier = Modifier.weight(1f),
            )
            TradingTab.ORDERS -> OrdersTable(
                orders, formatter, onCancel = { viewModel.cancelOrder(it) },
                modifier = Modifier.weight(1f),
            )
            TradingTab.BALANCES -> Column(modifier = Modifier.weight(1f)) {
                if (paperEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PaperAction("Top up USDT +1000") {
                            scope.launch {
                                PaperTrading.adapter.topUp("USDT", 1000.0)
                                viewModel.reload()
                            }
                        }
                        PaperAction("Top up BTC +0.1") {
                            scope.launch {
                                PaperTrading.adapter.topUp("BTC", 0.1)
                                viewModel.reload()
                            }
                        }
                    }
                }
                BalancesTable(balances, modifier = Modifier.weight(1f))
            }
            TradingTab.HISTORY -> {
                LaunchedEffect(Unit) { viewModel.refreshTradeHistory() }
                HistoryTable(history, formatter, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ── Заголовочные контролы ──

@Composable
private fun PaperToggle(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Paper",
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        // Отключаем M3 min-touch-target (48dp) — компактная строка заголовка
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            TerminalSwitch(
                checked = enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.scale(0.7f),
            )
        }
    }
}

@Composable
private fun PaperAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clickableNoIndication(onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp),
    )
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
        modifier = Modifier
            .clickableNoIndication { onSwitch(if (hedge) 2 else 1) }
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

// ── Таблицы (колонки заголовка и строк выровнены) ──

@Composable
private fun PositionsTable(
    positions: List<Position>,
    formatter: SymbolFormatter,
    onClose: (Position) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        TableHeader(
            listOf(
                "Symbol" to PW.SYMBOL, "Side" to PW.SIDE, "Qty" to PW.QTY,
                "Entry" to PW.ENTRY, "Liq" to PW.LIQ, "PnL" to PW.PNL, "" to PW.ACTION,
            )
        )
        if (positions.isEmpty()) {
            EmptyHint("No open positions")
            return@Column
        }
        positions.forEach { p ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cell(p.symbol, PW.SYMBOL, align = TextAlign.Start)
                Cell(p.side.name, PW.SIDE, color = sideColor(p.side), align = TextAlign.Start)
                Cell(formatter.formatVolume(p.quantity), PW.QTY)
                Cell(formatter.formatPrice(p.avgPrice), PW.ENTRY)
                Cell(p.liquidatePrice?.let { formatter.formatPrice(it) } ?: "-", PW.LIQ)
                Cell(formatSigned(p.pnl, formatter), PW.PNL, color = pnlColor(p.pnl))
                ActionCell("Close", PW.ACTION) { onClose(p) }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

@Composable
private fun OrdersTable(
    orders: List<Order>,
    formatter: SymbolFormatter,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        TableHeader(
            listOf(
                "Symbol" to OW.SYMBOL, "Side/Type" to OW.SIDE_TYPE, "Price" to OW.PRICE,
                "Qty/Filled" to OW.QTY, "Time" to OW.TIME, "Status" to OW.STATUS, "" to OW.ACTION,
            )
        )
        if (orders.isEmpty()) {
            EmptyHint("No open orders")
            return@Column
        }
        orders.forEach { o ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cell(o.symbol, OW.SYMBOL, align = TextAlign.Start)
                Cell("${o.side.name}/${o.orderType.name}", OW.SIDE_TYPE, color = sideColor(o.side), align = TextAlign.Start)
                Cell(
                    if (o.orderType == OrderType.MARKET) "market" else formatter.formatPrice(o.price),
                    OW.PRICE,
                )
                Cell("${formatter.formatVolume(o.quantity)}/${formatter.formatVolume(o.filledQuantity)}", OW.QTY)
                Cell(formatTime(o.timestamp), OW.TIME)
                Cell(
                    o.status.name,
                    OW.STATUS,
                    color = when (o.status) {
                        OrderStatus.OPEN -> Color.Yellow
                        OrderStatus.FILLED -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (o.status == OrderStatus.OPEN) {
                    ActionCell("Cancel", OW.ACTION) { onCancel(o.orderId) }
                } else {
                    Spacer(Modifier.weight(OW.ACTION))
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

@Composable
private fun BalancesTable(balances: List<Balance>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        TableHeader(
            listOf(
                "Asset" to BW.ASSET, "Available" to BW.AVAILABLE, "Frozen" to BW.FROZEN,
                "Margin" to BW.MARGIN, "Equity" to BW.EQUITY,
            )
        )
        if (balances.isEmpty()) {
            EmptyHint("No balances")
            return@Column
        }
        balances.forEach { b ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cell(b.currency, BW.ASSET, align = TextAlign.Start, fontWeight = FontWeight.Medium)
                Cell(b.amount, BW.AVAILABLE)
                Cell(b.frozen, BW.FROZEN)
                Cell(b.margin, BW.MARGIN)
                Cell(b.equity, BW.EQUITY, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

@Composable
private fun HistoryTable(history: List<TradeFill>, formatter: SymbolFormatter, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        TableHeader(
            listOf(
                "Symbol" to HW.SYMBOL, "Side" to HW.SIDE, "Price" to HW.PRICE,
                "Qty" to HW.QTY, "Fee" to HW.FEE, "PnL" to HW.PNL,
            )
        )
        if (history.isEmpty()) {
            EmptyHint("No trade history")
            return@Column
        }
        history.forEach { f ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cell(f.symbol, HW.SYMBOL, align = TextAlign.Start)
                Cell(f.side.name, HW.SIDE, color = sideColor(f.side), align = TextAlign.Start)
                Cell(formatter.formatPrice(f.price), HW.PRICE)
                Cell(formatter.formatVolume(f.quantity), HW.QTY)
                Cell(formatter.formatPrice(f.fee), HW.FEE)
                Cell(formatSigned(f.pnl, formatter), HW.PNL, color = pnlColor(f.pnl))
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

// ── Вспомогательное ──

@Composable
private fun TableHeader(columns: List<Pair<String, Float>>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        columns.forEach { (label, weight) ->
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(weight),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
}

@Composable
private fun RowScope.Cell(
    text: String,
    weight: Float,
    color: Color = MaterialTheme.colorScheme.onSurface,
    align: TextAlign = TextAlign.End,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        maxLines = 1,
        textAlign = align,
        fontWeight = fontWeight,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun RowScope.ActionCell(label: String, weight: Float, onClick: () -> Unit) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.error,
        fontSize = 11.sp,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier
            .weight(weight)
            .clickableNoIndication(onClick),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

private fun sideColor(side: TradeSide): Color =
    if (side == TradeSide.BUY) Color(0xFF26A69A) else Color(0xFFEF5350)

private fun sideColor(side: OrderSide): Color =
    if (side == OrderSide.BUY) Color(0xFF26A69A) else Color(0xFFEF5350)

private fun pnlColor(pnl: Double): Color =
    if (pnl >= 0) Color(0xFF26A69A) else Color(0xFFEF5350)

private fun formatSigned(v: Double, formatter: SymbolFormatter): String =
    (if (v >= 0) "+" else "") + formatter.formatPrice(v)

private fun formatTime(timestampMs: Long): String {
    if (timestampMs <= 0) return "-"
    val s = timestampMs / 1000
    val h = (s / 3600) % 24
    val m = (s / 60) % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}

@Composable
private fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
    )
}
