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

/**
 * Панель chart trading (поверх графика, слева снизу): ордер ставится по
 * активному символу/бирже графика (ex/sym берутся с chart).
 *
 * Настройки: количество (minQty / своё), тип ордера, плечо, режим маржи,
 * reduce-only, TP/SL, подтверждение ордеров. Размещение: клик по графику
 * (выбранный тип по цене клика) или кнопки Buy/Sell (market по последней цене).
 */
@OptIn(ExperimentalLayoutApi::class)
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
    lastMessage: String?,
    pendingOrder: OrderRequest?,
    paperEnabled: Boolean,
    onQuantityChanged: (Double?) -> Unit,
    onOrderTypeChanged: (OrderType) -> Unit,
    onReduceOnlyChanged: (Boolean) -> Unit,
    onLeverageChanged: (Int?) -> Unit,
    onMarginModeChanged: (Int) -> Unit,
    onTakeProfitChanged: (Double?) -> Unit,
    onStopLossChanged: (Double?) -> Unit,
    onBuy: () -> Unit,
    onSell: () -> Unit,
    onConfirmPending: () -> Unit,
    onCancelPending: () -> Unit,
    onConfirmChanged: (Boolean) -> Unit,
    onPaperChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Локальный текст поля: ввод «0.05» не затирается валидацией
    var qtyText by remember(quantity) {
        mutableStateOf(quantity?.let { trimZeros(it) } ?: "")
    }

    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        // Шапка: ex/sym из графика + подтверждение ордеров.
        // FlowRow — в узких тайлах строки переносятся, а не режутся.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = caption,
                color = Color(0xFF6B7A88),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (confirmOrders) "Confirm: ON" else "Confirm: OFF",
                color = if (confirmOrders) Color(0xFF5B9BD5) else Color(0xFF6B7A88),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(
                        if (confirmOrders) Color(0xFF5B9BD5).copy(alpha = 0.2f) else Color.Transparent,
                        RoundedCornerShape(3.dp)
                    )
                    .clickableNoIndication { onConfirmChanged(!confirmOrders) }
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
            Text(
                text = if (paperEnabled) "Paper ✔" else "Paper",
                color = if (paperEnabled) Color(0xFF00C853) else Color(0xFF6B7A88),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(
                        if (paperEnabled) Color(0xFF00C853).copy(alpha = 0.2f) else Color.Transparent,
                        RoundedCornerShape(3.dp)
                    )
                    .clickableNoIndication { onPaperChanged(!paperEnabled) }
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }

        // Количество: minQty инструмента — быстрый выбор + своё значение
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Qty:", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontFamily = FontFamily.Monospace)

            minQty?.takeIf { it > 0 }?.let { mq ->
                Text(
                    text = "min ${trimZeros(mq)}",
                    color = if (quantity == null) Color(0xFF00C853) else Color(0xFF6B7A88),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier
                        .background(
                            if (quantity == null) Color(0xFF00C853).copy(alpha = 0.2f) else Color.Transparent,
                            RoundedCornerShape(3.dp)
                        )
                        .clickableNoIndication {
                            qtyText = ""
                            onQuantityChanged(null)
                        }
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }

            BasicTextField(
                value = qtyText,
                onValueChange = { text: String ->
                    qtyText = text
                    // В VM кладём только корректное положительное число;
                    // незавершённый ввод ("0.") не стирает поле и не падает
                    onQuantityChanged(text.toDoubleOrNull()?.takeIf { it > 0 })
                },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(Color(0xFF5B9BD5)),
                modifier = Modifier
                    .width(70.dp)
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }

        // Настройки ордера: тип / маржа / плечо / reduce-only
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
            TerminalDropdown(
                currentValue = leverage ?: 0,
                items = listOf(0, 1, 2, 3, 5, 10, 20, 50, 100, 125),
                onValueChanged = { onLeverageChanged(it.takeIf { l -> l > 0 }) },
                displayText = { if (it <= 0) "Lev" else "${it}x" },
                menuWidth = 90.dp,
            )
            Text(
                text = "RO",
                color = if (reduceOnly) Color(0xFF5B9BD5) else Color(0xFF6B7A88),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .background(
                        if (reduceOnly) Color(0xFF5B9BD5).copy(alpha = 0.2f) else Color.Transparent,
                        RoundedCornerShape(3.dp)
                    )
                    .clickableNoIndication { onReduceOnlyChanged(!reduceOnly) }
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }

        // TP/SL — необязательные цены тейка/стопа
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            OptionalPriceField("TP:", takeProfit, onTakeProfitChanged)
            OptionalPriceField("SL:", stopLoss, onStopLossChanged)
        }

        // Размещение: market по кнопкам (сторона явная), либо подтверждение
        // отложенного ордера (Confirm: ON) — без попапов, прямо в панели.
        val pending = pendingOrder
        if (pending != null) {
            val sideColor = if (pending.side.name == "BUY") Color(0xFF26A69A) else Color(0xFFEF5350)
            val px = if (pending.orderType == OrderType.MARKET) "market" else "@ ${pending.price}"
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${pending.orderType.name} ${pending.side.name} ${trimZeros(pending.quantity)} $px",
                    color = sideColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SideButton("Confirm", sideColor, onConfirmPending)
                SideButton("Cancel", Color(0xFF888888), onCancelPending)
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                SideButton("Buy", Color(0xFF26A69A), onBuy)
                SideButton("Sell", Color(0xFFEF5350), onSell)
            }
        }

        // Подсказка: как размещать ордера (тип — из настроек выше)
        Text(
            text = "Click chart → ${orderType.name} order",
            color = Color(0xFF6B7A88),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        lastMessage?.let { msg ->
            Text(
                text = msg,
                color = Color(0xFF00C853),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Компактная кнопка стороны ордера (Buy/Sell) без M3-паддингов. */
@Composable
private fun SideButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
            .clickableNoIndication(onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
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
) {
    var text by remember(value) {
        mutableStateOf(value?.let { trimZeros(it) } ?: "")
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = Color(0xFFAAAAAA), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        BasicTextField(
            value = text,
            onValueChange = { t: String ->
                text = t
                onChanged(t.toDoubleOrNull()?.takeIf { it > 0 })
            },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(Color(0xFF5B9BD5)),
            modifier = Modifier
                .width(64.dp)
                .background(Color(0xFF1A1A1A), RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text("—", color = Color(0xFF555555), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                inner()
            },
        )
    }
}

private fun trimZeros(v: Double): String {
    var s = v.toString()
    if ('.' in s) {
        s = s.trimEnd('0').trimEnd('.')
    }
    return s
}

@Composable
private fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier {
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
    )
}
