/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.trading.OrderRequest
import com.aandios.nous.core.ui.component.TerminalDropdown
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.core.ui.theme.ChartColors

// Стиль как у DrawingToolPanel — единый вид вертикальных панелей графика
private val panelBg = Color.Black.copy(alpha = 0.9f)
private val labelColor = Color(0xFF8A97A5)
private val fieldBg = Color(0xFF14181F)
private val fieldBorder = Color(0xFF3A4550)
private val accent = Color(0xFF5B9BD5)
private val buyColor = Color(0xFF26A69A)
private val sellColor = Color(0xFFEF5350)
private val warnColor = Color(0xFFE0A95B)

/**
 * Вертикальная панель chart trading в стиле DrawingToolPanel: строка настроек
 * с label (Order / Margin / Leverage / Qty), затем действия.
 *
 * Qty — edittext с placeholder = minQty инструмента (пусто → берётся minQty).
 * Buy/Sell — market; Buy Limit/Sell Limit — лимитки по лучшим bid/ask.
 * Cancel All — отмена всех ордеров, Close All — закрытие позиций symbol.
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
    bestBid: Double?,
    bestAsk: Double?,
    formatter: SymbolFormatter = SymbolFormatter.DEFAULT,
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
    onBuyLimit: () -> Unit,
    onSellLimit: () -> Unit,
    onCancelAll: () -> Unit,
    onCloseAll: () -> Unit,
    onConfirmPending: () -> Unit,
    onCancelPending: () -> Unit,
    onConfirmChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Локальный текст поля: ввод «0.05» не затирается валидацией
    var qtyText by remember(quantity) {
        mutableStateOf(quantity?.let { trimZeros(it) } ?: "")
    }

    Column(
        modifier = modifier
            .border(1.dp, ChartColors.gridLine, RoundedCornerShape(8.dp))
            .background(panelBg, RoundedCornerShape(8.dp))
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
            )
            return@Column
        }

        // PnL позиции (в самом верху панели): %, тики, USDT
        pnlText?.let { text ->
            Text(
                text = text,
                color = if (pnlUp) buyColor else sellColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
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
                color = labelColor,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "▼",
                color = labelColor,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
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
        SettingRow("Qty") {
            QtyInput(
                qtyText = qtyText,
                minQty = minQty,
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
                color = labelColor,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
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
                .background(fieldBorder)
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
                activeColor = accent,
            ) { onConfirmChanged(!confirmOrders) }
        }

        val pending = pendingOrder
        if (pending != null) {
            // Подтверждение отложенного ордера (Confirm: ON) — прямо в панели
            val sideColor = if (pending.side.name == "BUY") buyColor else sellColor
            val px = if (pending.orderType == OrderType.MARKET) "market"
            else "@ ${formatter.formatPrice(pending.price)}"
            Text(
                text = "${pending.orderType.name} ${pending.side.name} ${trimZeros(pending.quantity)} $px",
                color = sideColor,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                SideButton("Confirm", sideColor, onConfirmPending, Modifier.weight(1f))
                SideButton("Cancel", Color(0xFF888888), onCancelPending, Modifier.weight(1f))
            }
        } else {
            // Market
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                SideButton("Buy", buyColor, onBuy, Modifier.weight(1f))
                SideButton("Sell", sellColor, onSell, Modifier.weight(1f))
            }
            // Лимитки по лучшим ценам (bid/ask)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                LimitButton("Buy Limit", buyColor, bestBid, onBuyLimit, Modifier.weight(1f))
                LimitButton("Sell Limit", sellColor, bestAsk, onSellLimit, Modifier.weight(1f))
            }
        }

        // Управление ордерами/позициями по exchange+symbol
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            SideButton("Cancel All", warnColor, onCancelAll, Modifier.weight(1f), outlined = true)
            SideButton("Close All", sellColor, onCloseAll, Modifier.weight(1f), outlined = true)
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
) {
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
            Text("Lvg", color = labelColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
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
            color = if (reduceOnly) accent else labelColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier
                .background(
                    if (reduceOnly) accent.copy(alpha = 0.2f) else Color.Transparent,
                    RoundedCornerShape(3.dp),
                )
                .border(
                    1.dp,
                    if (reduceOnly) accent.copy(alpha = 0.5f) else fieldBorder,
                    RoundedCornerShape(3.dp),
                )
                .clickableNoIndication { onReduceOnlyChanged(!reduceOnly) }
                .padding(horizontal = 5.dp, vertical = 2.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Qty", color = labelColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            QtyInput(
                qtyText = qtyText,
                minQty = minQty,
                onTextChanged = onQtyTextChanged,
                modifier = Modifier.width(66.dp),
            )
        }
        // PnL позиции — в конце компактной строки
        pnlText?.let { text ->
            Text(
                text = text,
                color = if (pnlUp) buyColor else sellColor,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Text(
            text = "▲",
            color = accent,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier
                .clickableNoIndication(onExpand)
                .padding(horizontal = 2.dp, vertical = 1.dp),
        )
    }
}
/** Поле qty с placeholder = minQty инструмента (пусто → minQty). */
@Composable
private fun QtyInput(
    qtyText: String,
    minQty: Double?,
    onTextChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = qtyText,
        onValueChange = onTextChanged,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
        cursorBrush = SolidColor(accent),
        modifier = modifier
            .height(24.dp)
            .background(fieldBg, RoundedCornerShape(3.dp))
            .border(1.dp, fieldBorder, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (qtyText.isEmpty() && minQty != null && minQty > 0) {
                    Text(
                        text = trimZeros(minQty),
                        color = Color(0xFF5A6674),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                inner()
            }
        },
    )
}

/** Строка настройки: label фиксированной ширины + контрол. */
@Composable
private fun SettingRow(label: String, content: @Composable RowScope.() -> Unit) {    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
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
    activeColor: Color = accent,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (active) activeColor else labelColor,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .background(
                if (active) activeColor.copy(alpha = 0.2f) else Color.Transparent,
                RoundedCornerShape(3.dp),
            )
            .border(1.dp, if (active) activeColor.copy(alpha = 0.5f) else fieldBorder, RoundedCornerShape(3.dp))
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
            fontFamily = FontFamily.Monospace,
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
            fontFamily = FontFamily.Monospace,
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
    var text by remember(value) {
        mutableStateOf(value?.let { trimZeros(it) } ?: "")
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Text(label, color = labelColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        BasicTextField(
            value = text,
            onValueChange = { t: String ->
                text = t
                onChanged(t.toDoubleOrNull()?.takeIf { it > 0 })
            },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(accent),
            modifier = Modifier
                .weight(1f)
                .height(22.dp)
                .background(fieldBg, RoundedCornerShape(3.dp))
                .border(1.dp, fieldBorder, RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 3.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text("—", color = Color(0xFF555555), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
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
