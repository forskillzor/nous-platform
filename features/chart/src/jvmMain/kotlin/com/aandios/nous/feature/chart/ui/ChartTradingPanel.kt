/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui
import com.aandios.nous.core.ui.theme.TerminalFontFamily

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.adapters.BookTickerAdapter
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.trading.OrderRequest
import com.aandios.nous.core.ui.component.TerminalDropdown
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens

/**
 * Поглощает pointer-события: клики/драги по панели (в т.ч. по пустым местам
 * между кнопками) не проваливаются в чарт и не размещают ордер.
 */
internal fun Modifier.blockChartPointerInput(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent()
        }
    }
}

/**
 * Вертикальная панель chart trading в стиле DrawingToolPanel: строки настроек
 * (Order / Margin / Leverage / Price / Qty), TP/SL, RO + Confirm, затем CTA
 * «BUY / LONG» / «SELL / SHORT», лимитки по лучшей цене и Cancel/Close All.
 *
 * Order — тип ордера одним дропдауном (LIMIT/POST_ONLY/IOC/FOK/MARKET —
 * аналог сегментов LIMIT/MARKET/STOP из макета). Price виден для не-market
 * типов: задаёт цену лимиток, пусто → лучшие bid/ask (поведение как раньше).
 * Qty — edittext с placeholder = minQty инструмента (пусто → minQty);
 * поля Qty/Price показывают валютный суффикс (SOL / USDT).
 * Ордер ставится по активному символу/бирже графика (ex/sym берутся с chart).
 */
@Composable
fun ChartTradingPanel(
    caption: String,
    minQty: Double?,
    quantity: Double?,
    orderType: OrderType,
    reduceOnly: Boolean,
    leverage: Int?,
    marginMode: Int,
    takeProfit: Double?,
    stopLoss: Double?,
    confirmOrders: Boolean,
    pendingOrder: OrderRequest?,
    bookTicker: BookTickerAdapter? = null,
    symbol: String = "",
    formatter: SymbolFormatter = SymbolFormatter.DEFAULT,
    /** Базовая валюта символа — валютный суффикс поля Qty («SOL»). */
    baseAsset: String? = null,
    /** Котируемая валюта символа — валютный суффикс поля Price («USDT»). */
    quoteAsset: String? = null,
    marginInfo: String? = null,
    pnlText: String? = null,
    pnlUp: Boolean = true,
    collapsed: Boolean = false,
    onCollapsedChange: (Boolean) -> Unit,
    onQuantityChanged: (Double?) -> Unit,
    onOrderTypeChanged: (OrderType) -> Unit,
    onReduceOnlyChanged: (Boolean) -> Unit,
    onLeverageChanged: (Int?) -> Unit,
    onMarginModeChanged: (Int) -> Unit,
    onTakeProfitChanged: (Double?) -> Unit,
    onStopLossChanged: (Double?) -> Unit,
    onBuy: () -> Unit,
    onSell: () -> Unit,
    /** Лимитка BUY: цена из поля Price, пусто → лучший bid. */
    onBuyLimit: (Double) -> Unit,
    /** Лимитка SELL: цена из поля Price, пусто → лучший ask. */
    onSellLimit: (Double) -> Unit,
    onCancelAll: () -> Unit,
    onCloseAll: () -> Unit,
    onConfirmPending: () -> Unit,
    onCancelPending: () -> Unit,
    onConfirmChanged: (Boolean) -> Unit,
    /** Быстрый lock трейдинга (замок в панели): Trading выключается. */
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalTerminalDesignTokens.current
    // Локальный текст поля: ввод «0.05» не затирается валидацией
    var qtyText by remember(quantity) {
        mutableStateOf(quantity?.let { trimZeros(it) } ?: "")
    }
    // Цена лимитки из поля Price (пусто → лучшая bid/ask); сброс при смене символа
    var priceText by remember(caption) { mutableStateOf("") }

    // Лучшие bid/ask живут в панели, чтобы тики стакана не перерисовывали чарт
    var bestBid by remember { mutableStateOf<Double?>(null) }
    var bestAsk by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(bookTicker, symbol, collapsed) {
        bestBid = null
        bestAsk = null
        if (collapsed || bookTicker == null || symbol.isBlank()) return@LaunchedEffect
        bookTicker.subscribeToBookTicker(symbol).collect { tick ->
            bestBid = tick.bestBid.takeIf { it > 0 }
            bestAsk = tick.bestAsk.takeIf { it > 0 }
        }
    }

    Column(
        modifier = modifier
            // Клики по панели (в т.ч. мимо кнопок) не проваливаются в чарт
            .blockChartPointerInput()
            .border(
                tokens.dimensions.borderWidth,
                tokens.colors.panelBorder,
                RoundedCornerShape(tokens.cornerRadii.workspacePanel),
            )
            .background(tokens.colors.panelSurface, RoundedCornerShape(tokens.cornerRadii.workspacePanel))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        if (collapsed) {
            // Компактный вариант: те же контролы в строку, без symbol и кнопок
            CompactHeader(
                orderType = orderType,
                marginMode = marginMode,
                leverage = leverage,
                reduceOnly = reduceOnly,
                qtyText = qtyText,
                minQty = minQty,
                pnlText = pnlText,
                pnlUp = pnlUp,
                onOrderTypeChanged = onOrderTypeChanged,
                onMarginModeChanged = onMarginModeChanged,
                onLeverageChanged = onLeverageChanged,
                onReduceOnlyChanged = onReduceOnlyChanged,
                onQtyTextChanged = { text ->
                    qtyText = text
                    onQuantityChanged(text.toDoubleOrNull()?.takeIf { it > 0 })
                },
                onExpand = { onCollapsedChange(false) },
                onLock = onLock,
            )
            return@Column
        }

        // PnL позиции (в самом верху панели): %, тики, USDT
        pnlText?.let { text ->
            Text(
                text = text,
                color = if (pnlUp) tokens.colors.profit else tokens.colors.loss,
                fontSize = 11.sp,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // ex/sym берутся с графика; стрелка сворачивает панель в компактную строку
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = caption,
                color = tokens.colors.label,
                fontSize = 10.sp,
                fontFamily = TerminalFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Быстрый lock: выключает Trading (замок закрыт — торговля включена)
            Text(
                text = "🔒",
                fontSize = 10.sp,
                maxLines = 1,
                modifier = Modifier
                    .clickableNoIndication { onLock() }
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
            Text(
                text = "▼",
                color = tokens.colors.label,
                fontSize = 10.sp,
                fontFamily = TerminalFontFamily,
                maxLines = 1,
                modifier = Modifier
                    .clickableNoIndication { onCollapsedChange(true) }
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }

        SettingRow("Order") {
            TerminalDropdown(
                currentValue = orderType,
                items = listOf(OrderType.LIMIT, OrderType.POST_ONLY, OrderType.IOC, OrderType.FOK, OrderType.MARKET),
                onValueChanged = onOrderTypeChanged,
                displayText = { it.name },
                menuWidth = 110.dp,
            )
        }
        SettingRow("Margin") {
            TerminalDropdown(
                currentValue = marginMode,
                items = listOf(2, 1),
                onValueChanged = onMarginModeChanged,
                displayText = { if (it == 1) "Isolated" else "Cross" },
                menuWidth = 110.dp,
            )
        }
        SettingRow("Leverage") {
            TerminalDropdown(
                currentValue = leverage ?: 0,
                items = listOf(0, 1, 2, 3, 5, 10, 20, 50, 100, 125),
                onValueChanged = { onLeverageChanged(it.takeIf { l -> l > 0 }) },
                displayText = { if (it <= 0) "—" else "${it}x" },
                menuWidth = 90.dp,
            )
        }
        // Price — цена лимитки (аналог макета): пусто → лучшие bid/ask.
        // Для MARKET цена не используется и строка скрыта.
        if (orderType != OrderType.MARKET) {
            SettingRow("Price") {
                NumberInput(
                    text = priceText,
                    placeholder = "—",
                    suffix = quoteAsset?.uppercase(),
                    onTextChanged = { priceText = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        SettingRow("Qty") {
            NumberInput(
                text = qtyText,
                placeholder = minQty?.takeIf { it > 0 }?.let { trimZeros(it) } ?: "",
                suffix = baseAsset?.uppercase(),
                onTextChanged = { text ->
                    qtyText = text
                    // В VM кладём только корректное положительное число;
                    // незавершённый ввод ("0.") не стирает поле и не падает
                    onQuantityChanged(text.toDoubleOrNull()?.takeIf { it > 0 })
                },
                modifier = Modifier.weight(1f),
            )
        }

        // Paper: ориентир по марже — видно до клика, что qty/плечо не влезают
        marginInfo?.let { info ->
            Text(
                text = info,
                color = tokens.colors.label,
                fontSize = 10.sp,
                fontFamily = TerminalFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // TP/SL — необязательные цены тейка/стопа (компактно, одной строкой)
        SettingRow("TP/SL") {
            OptionalPriceField("TP", takeProfit, onTakeProfitChanged, Modifier.weight(1f))
            OptionalPriceField("SL", stopLoss, onStopLossChanged, Modifier.weight(1f))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(tokens.colors.panelBorder)
        )

        // Флаги: reduce-only + подтверждение ордеров
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip("RO", active = reduceOnly) { onReduceOnlyChanged(!reduceOnly) }
            Chip(
                if (confirmOrders) "Confirm: ON" else "Confirm: OFF",
                active = confirmOrders,
                activeColor = tokens.colors.accent,
            ) { onConfirmChanged(!confirmOrders) }
        }

        val pending = pendingOrder
        if (pending != null) {
            // Подтверждение отложенного ордера (Confirm: ON) — прямо в панели
            val sideColor = if (pending.side.name == "BUY") tokens.colors.buyOrder else tokens.colors.sellOrder
            val px = if (pending.orderType == OrderType.MARKET) "market"
            else "@ ${formatter.formatPrice(pending.price)}"
            Text(
                text = "${pending.orderType.name} ${pending.side.name} ${trimZeros(pending.quantity)} $px",
                color = sideColor,
                fontSize = 10.sp,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                SideButton("Confirm", sideColor, onConfirmPending, Modifier.weight(1f))
                SideButton("Cancel", tokens.colors.label, onCancelPending, Modifier.weight(1f))
            }
        } else {
            val typedPrice = priceText.toDoubleOrNull()?.takeIf { it > 0 }
            // Market: основные CTA сторон (без хоткей-подписей Ctrl+B/S)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                PrimarySideButton("BUY / LONG", tokens.colors.buyOrder, onBuy, Modifier.weight(1f))
                PrimarySideButton("SELL / SHORT", tokens.colors.sellOrder, onSell, Modifier.weight(1f))
            }
            // Лимитки: цена из поля Price, пусто — лучшие bid/ask
            val buyLimitPrice = typedPrice ?: bestBid
            val sellLimitPrice = typedPrice ?: bestAsk
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                LimitButton("Buy Limit", tokens.colors.buyOrder, buyLimitPrice, { buyLimitPrice?.let(onBuyLimit) }, Modifier.weight(1f))
                LimitButton("Sell Limit", tokens.colors.sellOrder, sellLimitPrice, { sellLimitPrice?.let(onSellLimit) }, Modifier.weight(1f))
            }
        }

        // Управление ордерами/позициями по exchange+symbol
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            SideButton("Cancel All", tokens.colors.warning, onCancelAll, Modifier.weight(1f), outlined = true)
            SideButton("Close All", tokens.colors.loss, onCloseAll, Modifier.weight(1f), outlined = true)
        }
    }
}

/**
 * Компактный (свёрнутый) вид: те же контролы, что в большой панели —
 * дропдауны Order/Margin/Leverage и поле Qty, в одну строку (FlowRow
 * переносит в узких тайлах), без symbol и без лейблов, кроме Lvg и Qty.
 * Стрелка ▲ разворачивает панель обратно.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactHeader(
    orderType: OrderType,
    marginMode: Int,
    leverage: Int?,
    reduceOnly: Boolean,
    qtyText: String,
    minQty: Double?,
    pnlText: String?,
    pnlUp: Boolean,
    onOrderTypeChanged: (OrderType) -> Unit,
    onMarginModeChanged: (Int) -> Unit,
    onLeverageChanged: (Int?) -> Unit,
    onReduceOnlyChanged: (Boolean) -> Unit,
    onQtyTextChanged: (String) -> Unit,
    onExpand: () -> Unit,
    onLock: () -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        TerminalDropdown(
            currentValue = orderType,
            items = listOf(OrderType.LIMIT, OrderType.POST_ONLY, OrderType.IOC, OrderType.FOK, OrderType.MARKET),
            onValueChanged = onOrderTypeChanged,
            displayText = { it.name },
            menuWidth = 110.dp,
        )
        TerminalDropdown(
            currentValue = marginMode,
            items = listOf(2, 1),
            onValueChanged = onMarginModeChanged,
            displayText = { if (it == 1) "Isolated" else "Cross" },
            menuWidth = 110.dp,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Lvg", color = tokens.colors.label, fontSize = 10.sp, fontFamily = TerminalFontFamily)
            TerminalDropdown(
                currentValue = leverage ?: 0,
                items = listOf(0, 1, 2, 3, 5, 10, 20, 50, 100, 125),
                onValueChanged = { onLeverageChanged(it.takeIf { l -> l > 0 }) },
                displayText = { if (it <= 0) "—" else "${it}x" },
                menuWidth = 90.dp,
            )
        }
        // Reduce-only
        Text(
            text = "RO",
            color = if (reduceOnly) tokens.colors.accent else tokens.colors.label,
            fontSize = 10.sp,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier
                .background(
                    if (reduceOnly) tokens.colors.accent.copy(alpha = 0.2f) else Color.Transparent,
                    RoundedCornerShape(3.dp),
                )
                .border(
                    1.dp,
                    if (reduceOnly) tokens.colors.accent.copy(alpha = 0.5f) else tokens.colors.panelBorder,
                    RoundedCornerShape(3.dp),
                )
                .clickableNoIndication { onReduceOnlyChanged(!reduceOnly) }
                .padding(horizontal = 5.dp, vertical = 2.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Qty", color = tokens.colors.label, fontSize = 10.sp, fontFamily = TerminalFontFamily)
            NumberInput(
                text = qtyText,
                placeholder = minQty?.takeIf { it > 0 }?.let { trimZeros(it) } ?: "",
                onTextChanged = onQtyTextChanged,
                modifier = Modifier.width(66.dp),
            )
        }
        // PnL позиции — в конце компактной строки
        pnlText?.let { text ->
            Text(
                text = text,
                color = if (pnlUp) tokens.colors.profit else tokens.colors.loss,
                fontSize = 10.sp,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        // Быстрый lock трейдинга (компактный вид)
        Text(
            text = "🔒",
            fontSize = 10.sp,
            maxLines = 1,
            modifier = Modifier
                .clickableNoIndication { onLock() }
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
        Text(
            text = "▲",
            color = tokens.colors.accent,
            fontSize = 10.sp,
            fontFamily = TerminalFontFamily,
            maxLines = 1,
            modifier = Modifier
                .clickableNoIndication(onExpand)
                .padding(horizontal = 2.dp, vertical = 1.dp),
        )
    }
}
/**
 * Числовое поле панели (Qty / Price): placeholder при пустом значении и
 * валютный суффикс внутри коробки справа («SOL» / «USDT», как в макете).
 */
@Composable
private fun NumberInput(
    text: String,
    placeholder: String,
    onTextChanged: (String) -> Unit,
    suffix: String? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalTerminalDesignTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(24.dp)
            .background(tokens.colors.panelHeaderSurface, RoundedCornerShape(3.dp))
            .border(tokens.dimensions.borderWidth, tokens.colors.panelBorder, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChanged,
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                lineHeight = tokens.typography.controlLineHeight,
                fontFamily = TerminalFontFamily,
            ),
            cursorBrush = SolidColor(tokens.colors.accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            color = tokens.colors.label.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                            lineHeight = tokens.typography.controlLineHeight,
                            fontFamily = TerminalFontFamily,
                            maxLines = 1,
                        )
                    }
                    inner()
                }
            },
        )
        if (suffix != null) {
            Spacer(Modifier.width(tokens.spacing.xs))
            Text(
                text = suffix,
                color = tokens.colors.label.copy(alpha = 0.75f),
                fontSize = 9.sp,
                lineHeight = tokens.typography.compactLineHeight,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/** Строка настройки: label фиксированной ширины + контрол. */
@Composable
private fun SettingRow(label: String, content: @Composable RowScope.() -> Unit) {
    val tokens = LocalTerminalDesignTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            color = tokens.colors.label,
            fontSize = 10.sp,
            fontFamily = TerminalFontFamily,
            maxLines = 1,
            modifier = Modifier.width(52.dp),
        )
        content()
    }
}

/** Компактный чип-флаг (RO / Confirm). */
@Composable
private fun Chip(
    label: String,
    active: Boolean,
    activeColor: Color? = null,
    onClick: () -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    val resolvedActiveColor = activeColor ?: tokens.colors.accent
    Text(
        text = label,
        color = if (active) resolvedActiveColor else tokens.colors.label,
        fontSize = 10.sp,
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .background(
                if (active) resolvedActiveColor.copy(alpha = 0.2f) else Color.Transparent,
                RoundedCornerShape(3.dp),
            )
            .border(
                tokens.dimensions.borderWidth,
                if (active) resolvedActiveColor.copy(alpha = 0.5f) else tokens.colors.panelBorder,
                RoundedCornerShape(3.dp),
            )
            .clickableNoIndication(onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Кнопка market-стороны. */
@Composable
private fun SideButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
) {
    Box(
        modifier = modifier
            .height(24.dp)
            .background(
                if (outlined) Color.Transparent else color.copy(alpha = 0.15f),
                RoundedCornerShape(3.dp),
            )
            .border(1.dp, color.copy(alpha = if (outlined) 0.45f else 0.6f), RoundedCornerShape(3.dp))
            .clickableNoIndication(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * Основная CTA-кнопка стороны: «BUY / LONG» / «SELL / SHORT» — крупнее
 * обычных SideButton, одна строка без хоткей-подписей (Ctrl+B / Ctrl+S).
 */
@Composable
private fun PrimarySideButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(26.dp)
            .background(color.copy(alpha = 0.22f), RoundedCornerShape(3.dp))
            .border(1.dp, color.copy(alpha = 0.75f), RoundedCornerShape(3.dp))
            .clickableNoIndication(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** Лимитка по лучшей цене: неактивна, пока bid/ask не пришёл. */
@Composable
private fun LimitButton(
    label: String,
    color: Color,
    price: Double?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = price != null && price > 0
    Box(
        modifier = modifier
            .height(24.dp)
            .background(color.copy(alpha = if (ready) 0.15f else 0.05f), RoundedCornerShape(3.dp))
            .border(1.dp, color.copy(alpha = if (ready) 0.6f else 0.25f), RoundedCornerShape(3.dp))
            .clickable(enabled = ready, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color.copy(alpha = if (ready) 1f else 0.4f),
            fontSize = 10.sp,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** Необязательное ценовое поле (TP/SL): пусто = не задано. */
@Composable
private fun OptionalPriceField(
    label: String,
    value: Double?,
    onChanged: (Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalTerminalDesignTokens.current
    var text by remember(value) {
        mutableStateOf(value?.let { trimZeros(it) } ?: "")
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Text(label, color = tokens.colors.label, fontSize = 10.sp, fontFamily = TerminalFontFamily)
        BasicTextField(
            value = text,
            onValueChange = { t: String ->
                text = t
                onChanged(t.toDoubleOrNull()?.takeIf { it > 0 })
            },
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 10.sp,
                fontFamily = TerminalFontFamily,
            ),
            cursorBrush = SolidColor(tokens.colors.accent),
            modifier = Modifier
                .weight(1f)
                .height(22.dp)
                .background(tokens.colors.panelHeaderSurface, RoundedCornerShape(3.dp))
                .border(tokens.dimensions.borderWidth, tokens.colors.panelBorder, RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 3.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text(
                            "—",
                            color = tokens.colors.label.copy(alpha = 0.65f),
                            fontSize = 10.sp,
                            fontFamily = TerminalFontFamily,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

private fun trimZeros(v: Double): String =
    com.aandios.nous.core.ui.format.plainDecimalString(v)

@Composable
private fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier {
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
    )
}
