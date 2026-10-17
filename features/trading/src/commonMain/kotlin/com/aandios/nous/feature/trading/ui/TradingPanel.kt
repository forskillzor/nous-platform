/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trading.ui
import com.aandios.nous.core.ui.theme.TerminalFontFamily

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.api.market.model.Balance
import com.aandios.nous.api.market.model.ContractType
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.OrderStatus
import com.aandios.nous.api.market.model.trading.Position
import com.aandios.nous.api.market.model.trading.TradeFill
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.core.ui.component.PanelTextWatermark
import com.aandios.nous.core.ui.component.TerminalDropdown
import com.aandios.nous.core.ui.component.TerminalPanelHeader
import com.aandios.nous.core.ui.component.TerminalSwitch
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.core.ui.format.plainDecimalFixed
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import org.koin.compose.koinInject

enum class TradingTab(val label: String) {
    POSITIONS("Positions"),
    ORDERS("Orders"),
    BALANCES("Balance"),
    HISTORY("History"),
}

// Веса колонок: заголовок и строки используют одни и те же —
// колонки выровнены.
private object PositionsWindow {
    const val SYMBOL = 1.3f
    const val SIDE = 0.7f
    const val QTY = 0.9f
    const val ENTRY = 1f
    const val LIQ = 1f
    const val PNL = 1f
    const val ACTION = 0.7f
}

private object OrdersWindow {
    const val SYMBOL = 1.2f
    const val SIDE_TYPE = 0.9f
    const val PRICE = 1f
    const val QTY = 0.9f
    const val TIME = 0.9f
    const val STATUS = 0.8f
    const val ACTION = 0.7f
}

private object BalancesWindow {
    const val ASSET = 0.8f
    const val AVAILABLE = 1.2f
    const val FROZEN = 1f
    const val MARGIN = 1f
    const val EQUITY = 1f
}

private object HistoryWindow {
    const val SYMBOL = 1.2f
    const val SIDE = 0.7f
    const val PRICE = 1f
    const val QTY = 0.8f
    const val FEE = 0.8f
    const val PNL = 0.9f
}

/**
 * Полная торговая панель: верхняя строка (exchange + one-way/hedge + paper
 * switch + шестерёнка настроек), строка действий (Cancel all / Close all),
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
    val lastMessage by viewModel.lastMessage.collectAsState()
    val activeTabRaw by viewModel.activeTab.collectAsState()
    val tab = TradingTab.entries.firstOrNull { it.name == activeTabRaw } ?: TradingTab.POSITIONS

    val paperEnabled by viewModel.paperEnabled.collectAsState()
    val contractType by viewModel.contractType.collectAsState()
    val symbolTypes by viewModel.symbolTypes.collectAsState()
    val dailyPnl by viewModel.dailyPnl.collectAsState()
    val paperSettings: PaperSettingsController = koinInject()

    // Фильтр по типу контрактов: позиции/ордера/история/балансы
    val visiblePositions = remember(positions, contractType, symbolTypes) {
        positions.filter { viewModel.matchesContractType(it.symbol, contractType) }
    }
    val visibleOrders = remember(orders, contractType, symbolTypes) {
        orders.filter { viewModel.matchesContractType(it.symbol, contractType) }
    }
    val visibleHistory = remember(history, contractType, symbolTypes) {
        history.filter { viewModel.matchesContractType(it.symbol, contractType) }
    }
    val visibleBalances = remember(balances, contractType) {
        balances.filter { viewModel.balanceMatchesContractType(it.currency, contractType) }
    }
    val primaryBalance = remember(balances, contractType) {
        viewModel.primaryBalance(balances, contractType)
    }

    val tabCounts = mapOf(
        TradingTab.POSITIONS to visiblePositions.size,
        TradingTab.ORDERS to visibleOrders.size,
        TradingTab.BALANCES to visibleBalances.size,
        TradingTab.HISTORY to visibleHistory.size,
    )

    val formatter = remember { SymbolFormatter.DEFAULT }
    val tokens = LocalTerminalDesignTokens.current

    Column(modifier = modifier.background(tokens.colors.panelSurface)) {
        TerminalPanelHeader {
        // ── Верхняя строка: exchange · USDT-M/COIN-M · режим позиций · Paper + шестерёнка ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
        ) {
            TerminalDropdown(
                currentValue = providerId,
                items = registry.providers.map { it.providerId },
                onValueChanged = { viewModel.selectProvider(it) },
                displayText = { id ->
                    registry.providers.firstOrNull { it.providerId == id }?.config?.displayName ?: id
                },
                menuWidth = 130.dp,
            )
            TerminalDropdown(
                currentValue = contractType,
                items = ContractType.entries.toList(),
                onValueChanged = { viewModel.setContractType(it) },
                displayText = { it.label },
                menuWidth = 120.dp,
            )
            PositionModeButton(
                hedge = positionMode == 1,
                onClick = { viewModel.switchPositionMode(if (positionMode == 1) 2 else 1) },
            )
            Spacer(Modifier.weight(1f))
            PaperToggle(
                enabled = paperEnabled,
                onToggle = { viewModel.setPaperEnabled(it) },
            )
            // Настройки paper trading (баланс/сбросы) — отдельное окно
            Text(
                text = "⚙",
                color = tokens.colors.accent,
                fontSize = tokens.typography.bodySize,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickableNoIndication { paperSettings.open() }
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }

        // ── Счёт: баланс/дневной PnL/маржа слева, Cancel All / Close All справа (стек) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = "Balance  ${primaryBalance?.let { "${fmtAmount(it.amount)} ${it.currency}" } ?: "-"}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = tokens.typography.compactSize,
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Text(
                    text = "Day PnL  ${dailyPnl?.let { "${fmtSigned(it)} USDT" } ?: "-"}",
                    color = when {
                        dailyPnl == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        dailyPnl!! >= 0 -> tokens.colors.profit
                        else -> tokens.colors.loss
                    },
                    fontSize = tokens.typography.compactSize,
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                primaryBalance?.let { b ->
                    Text(
                        text = "Equity ${fmtAmount(b.equity)} · Available ${fmtAmount(b.amount)}",
                        color = tokens.colors.label,
                        fontSize = tokens.typography.microSize,
                        fontFamily = TerminalFontFamily,
                        maxLines = 1,
                    )
                    Text(
                        text = "In Position ${fmtAmount(b.margin)} · Frozen ${fmtAmount(b.frozen)} · Unrealized ${fmtSigned(b.unrealizedPnl.toDoubleOrNull() ?: 0.0)}",
                        color = tokens.colors.label,
                        fontSize = tokens.typography.microSize,
                        fontFamily = TerminalFontFamily,
                        maxLines = 1,
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
                horizontalAlignment = Alignment.End,
            ) {
                ActionButton("Cancel All", tokens.colors.warning) { viewModel.cancelAllOrders() }
                ActionButton("Close All", tokens.colors.loss) { viewModel.closeAllPositions() }
            }
        }

        // ── Табы ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
        ) {
            TradingTab.entries.forEach { t ->
                Text(
                    text = "${t.label} (${tabCounts[t] ?: 0})",
                    color = if (t == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = tokens.typography.controlSize,
                    fontWeight = if (t == tab) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clickableNoIndication { viewModel.selectTab(t.name) }
                        .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.xxs),
                )
            }
        }
        }

        lastMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.primary,
                fontSize = tokens.typography.compactSize,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xxs),
            )
        }

        // ── Контент (списки отфильтрованы по типу контрактов) ──
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Водяной знак (нижний слой, центр-центр): биржа + тип контрактов
            PanelTextWatermark(
                bigText = registry.providers.firstOrNull { it.providerId == providerId }
                    ?.config?.displayName ?: providerId,
                subTexts = listOf(contractType.label),
                modifier = Modifier.align(Alignment.Center),
            )
            when (tab) {
                TradingTab.POSITIONS -> PositionsTable(
                    visiblePositions, formatter, onClose = { viewModel.closePosition(it) },
                    modifier = Modifier.fillMaxSize(),
                )
                TradingTab.ORDERS -> OrdersTable(
                    visibleOrders, formatter, onCancel = { viewModel.cancelOrder(it) },
                    modifier = Modifier.fillMaxSize(),
                )
                TradingTab.BALANCES -> BalancesTable(visibleBalances, modifier = Modifier.fillMaxSize())
                TradingTab.HISTORY -> {
                    LaunchedEffect(Unit) { viewModel.refreshTradeHistory() }
                    HistoryTable(visibleHistory, formatter, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

// ── Заголовочные контролы ──

@Composable
private fun PaperToggle(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val tokens = LocalTerminalDesignTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
    ) {
        Text(
            text = "Paper",
            color = if (enabled) tokens.colors.profit else tokens.colors.label,
            fontSize = tokens.typography.compactSize,
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

/** Кнопка режима позиций: One-way / Hedge со стрелками переключения. */
@Composable
private fun PositionModeButton(hedge: Boolean, onClick: () -> Unit) {
    val tokens = LocalTerminalDesignTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.cornerRadii.card))
            .background(tokens.colors.panelHeaderSurface)
            .border(
                tokens.dimensions.borderWidth,
                tokens.colors.panelBorder,
                RoundedCornerShape(tokens.cornerRadii.card),
            )
            .clickableNoIndication(onClick)
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
    ) {
        Text(
            text = if (hedge) "Hedge" else "One-way",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = tokens.typography.compactSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Spacer(Modifier.width(tokens.spacing.xs))
        Text(
            text = "⇄",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = tokens.typography.compactSize,
            maxLines = 1,
        )
    }
}

/** Кнопка действия панели (обводка в цвет, как Cancel All / Close All в DOM). */
@Composable
private fun ActionButton(label: String, color: Color, onClick: () -> Unit) {
    val tokens = LocalTerminalDesignTokens.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.cornerRadii.card))
            .border(tokens.dimensions.borderWidth, color.copy(alpha = 0.85f), RoundedCornerShape(tokens.cornerRadii.card))
            .clickableNoIndication(onClick)
            .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color,
            fontSize = tokens.typography.compactSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** Сумма с 4 знаками, как на MEXC: "75.8234", "0.0000". */
private fun fmtAmount(raw: String?): String {
    val v = raw?.toDoubleOrNull() ?: return raw ?: "-"
    return plainDecimalFixed(v, 4)
}

/** Знаковая сумма с 4 знаками ("+1.2345" / "-0.5000"). */
private fun fmtSigned(v: Double): String {
    val sign = if (v > 0.0) "+" else ""
    return sign + plainDecimalFixed(v, 4)
}

// ── Таблицы (колонки заголовка и строк выровнены) ──

@Composable
private fun PositionsTable(
    positions: List<Position>,
    formatter: SymbolFormatter,
    onClose: (Position) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalTerminalDesignTokens.current
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        TableHeader(
            listOf(
                "Symbol" to PositionsWindow.SYMBOL, "Side" to PositionsWindow.SIDE, "Qty" to PositionsWindow.QTY,
                "Entry" to PositionsWindow.ENTRY, "Liq" to PositionsWindow.LIQ, "PnL" to PositionsWindow.PNL, "" to PositionsWindow.ACTION,
            )
        )
        if (positions.isEmpty()) {
            EmptyHint("No open positions")
            return@Column
        }
        positions.forEach { p ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xxs + tokens.dimensions.borderWidth),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cell(p.symbol, PositionsWindow.SYMBOL, align = TextAlign.Start)
                Cell(p.side.name, PositionsWindow.SIDE, color = sideColor(p.side), align = TextAlign.Start)
                Cell(formatter.formatVolume(p.quantity), PositionsWindow.QTY)
                Cell(formatter.formatPrice(p.avgPrice), PositionsWindow.ENTRY)
                Cell(p.liquidatePrice?.let { formatter.formatPrice(it) } ?: "-", PositionsWindow.LIQ)
                Cell(formatSigned(p.pnl, formatter), PositionsWindow.PNL, color = pnlColor(p.pnl))
                ActionCell("Close", PositionsWindow.ACTION) { onClose(p) }
            }
            HorizontalDivider(
                color = tokens.colors.panelBorder,
                thickness = tokens.dimensions.dividerThickness,
                modifier = Modifier.padding(horizontal = tokens.spacing.md),
            )
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
    val tokens = LocalTerminalDesignTokens.current
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        TableHeader(
            listOf(
                "Symbol" to OrdersWindow.SYMBOL, "Side/Type" to OrdersWindow.SIDE_TYPE, "Price" to OrdersWindow.PRICE,
                "Qty/Filled" to OrdersWindow.QTY, "Time" to OrdersWindow.TIME, "Status" to OrdersWindow.STATUS, "" to OrdersWindow.ACTION,
            )
        )
        if (orders.isEmpty()) {
            EmptyHint("No open orders")
            return@Column
        }
        orders.forEach { o ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xxs + tokens.dimensions.borderWidth),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cell(o.symbol, OrdersWindow.SYMBOL, align = TextAlign.Start)
                Cell("${o.side.name}/${o.orderType.name}", OrdersWindow.SIDE_TYPE, color = sideColor(o.side), align = TextAlign.Start)
                Cell(
                    if (o.orderType == OrderType.MARKET) "market" else formatter.formatPrice(o.price),
                    OrdersWindow.PRICE,
                )
                Cell("${formatter.formatVolume(o.quantity)}/${formatter.formatVolume(o.filledQuantity)}", OrdersWindow.QTY)
                Cell(formatTime(o.timestamp), OrdersWindow.TIME)
                Cell(
                    o.status.name,
                    OrdersWindow.STATUS,
                    color = when (o.status) {
                        OrderStatus.OPEN -> tokens.colors.warning
                        OrderStatus.FILLED -> tokens.colors.profit
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (o.status == OrderStatus.OPEN) {
                    ActionCell("Cancel", OrdersWindow.ACTION) { onCancel(o.orderId) }
                } else {
                    Spacer(Modifier.weight(OrdersWindow.ACTION))
                }
            }
            HorizontalDivider(
                color = tokens.colors.panelBorder,
                thickness = tokens.dimensions.dividerThickness,
                modifier = Modifier.padding(horizontal = tokens.spacing.md),
            )
        }
    }
}

@Composable
private fun BalancesTable(balances: List<Balance>, modifier: Modifier = Modifier) {
    val tokens = LocalTerminalDesignTokens.current
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        TableHeader(
            listOf(
                "Asset" to BalancesWindow.ASSET, "Available" to BalancesWindow.AVAILABLE, "Frozen" to BalancesWindow.FROZEN,
                "Margin" to BalancesWindow.MARGIN, "Equity" to BalancesWindow.EQUITY,
            )
        )
        if (balances.isEmpty()) {
            EmptyHint("No balances")
            return@Column
        }
        balances.forEach { b ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xxs + tokens.dimensions.borderWidth),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cell(b.currency, BalancesWindow.ASSET, align = TextAlign.Start, fontWeight = FontWeight.Medium)
                Cell(fmtAmount(b.amount), BalancesWindow.AVAILABLE)
                Cell(fmtAmount(b.frozen), BalancesWindow.FROZEN)
                Cell(fmtAmount(b.margin), BalancesWindow.MARGIN)
                Cell(fmtAmount(b.equity), BalancesWindow.EQUITY, color = tokens.colors.profit)
            }
            HorizontalDivider(
                color = tokens.colors.panelBorder,
                thickness = tokens.dimensions.dividerThickness,
                modifier = Modifier.padding(horizontal = tokens.spacing.md),
            )
        }
    }
}

@Composable
private fun HistoryTable(history: List<TradeFill>, formatter: SymbolFormatter, modifier: Modifier = Modifier) {
    val tokens = LocalTerminalDesignTokens.current
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        TableHeader(
            listOf(
                "Symbol" to HistoryWindow.SYMBOL, "Side" to HistoryWindow.SIDE, "Price" to HistoryWindow.PRICE,
                "Qty" to HistoryWindow.QTY, "Fee" to HistoryWindow.FEE, "PnL" to HistoryWindow.PNL,
            )
        )
        if (history.isEmpty()) {
            EmptyHint("No trade history")
            return@Column
        }
        history.forEach { f ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xxs + tokens.dimensions.borderWidth),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cell(f.symbol, HistoryWindow.SYMBOL, align = TextAlign.Start)
                Cell(f.side.name, HistoryWindow.SIDE, color = sideColor(f.side), align = TextAlign.Start)
                Cell(formatter.formatPrice(f.price), HistoryWindow.PRICE)
                Cell(formatter.formatVolume(f.quantity), HistoryWindow.QTY)
                Cell(formatter.formatPrice(f.fee), HistoryWindow.FEE)
                Cell(formatSigned(f.pnl, formatter), HistoryWindow.PNL, color = pnlColor(f.pnl))
            }
            HorizontalDivider(
                color = tokens.colors.panelBorder,
                thickness = tokens.dimensions.dividerThickness,
                modifier = Modifier.padding(horizontal = tokens.spacing.md),
            )
        }
    }
}

// ── Вспомогательное ──

@Composable
private fun TableHeader(columns: List<Pair<String, Float>>) {
    val tokens = LocalTerminalDesignTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        columns.forEach { (label, weight) ->
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = tokens.typography.microSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(weight),
            )
        }
    }
    HorizontalDivider(color = tokens.colors.panelBorder, thickness = tokens.dimensions.dividerThickness)
}

@Composable
private fun RowScope.Cell(
    text: String,
    weight: Float,
    color: Color = MaterialTheme.colorScheme.onSurface,
    align: TextAlign = TextAlign.End,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    val tokens = LocalTerminalDesignTokens.current
    Text(
        text = text,
        color = color,
        fontSize = tokens.typography.compactSize,
        maxLines = 1,
        textAlign = align,
        fontWeight = fontWeight,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun RowScope.ActionCell(label: String, weight: Float, onClick: () -> Unit) {
    val tokens = LocalTerminalDesignTokens.current
    Text(
        text = label,
        color = tokens.colors.loss,
        fontSize = tokens.typography.compactSize,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier
            .weight(weight)
            .clickableNoIndication(onClick),
    )
}

@Composable
private fun EmptyHint(text: String) {
    val tokens = LocalTerminalDesignTokens.current
    Box(Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xxl), contentAlignment = Alignment.Center) {
        Text(text, color = tokens.colors.label, fontSize = tokens.typography.controlSize)
    }
}

@Composable
private fun sideColor(side: TradeSide): Color =
    if (side == TradeSide.BUY) LocalTerminalDesignTokens.current.colors.bullish
    else LocalTerminalDesignTokens.current.colors.bearish

@Composable
private fun sideColor(side: OrderSide): Color =
    if (side == OrderSide.BUY) LocalTerminalDesignTokens.current.colors.bullish
    else LocalTerminalDesignTokens.current.colors.bearish

@Composable
private fun pnlColor(pnl: Double): Color =
    if (pnl >= 0) LocalTerminalDesignTokens.current.colors.profit
    else LocalTerminalDesignTokens.current.colors.loss

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
