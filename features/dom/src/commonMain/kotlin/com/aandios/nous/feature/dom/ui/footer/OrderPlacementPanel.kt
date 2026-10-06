/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui.footer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

// Стили — как у chart trading panel (SideButton/Chip/LimitButton)
private val buyColor = Color(0xFF26A69A)
private val sellColor = Color(0xFFEF5350)
private val okColor = Color(0xFF00C853)
private val warnColor = Color(0xFFE0A95B)
private val accent = Color(0xFF5B9BD5)
private val labelColor = Color(0xFF8A97A5)

/**
 * Компактная панель ордеров DOM.
 *
 * Верхняя строка: PnL позиции (без позиции — «-/- -/- -/-»).
 * Строка 1: Trading · Confirm · RO · Cross/Isol · Paper (в одну строку).
 * Строка 2: Qty (узкий input) · тип лимитки · Lvg (плечо).
 * Далее: строка подтверждения (Confirm: ON), Close All/Cancel All,
 * Buy/Sell Limit (цена кликом по уровню), market Buy/Sell, Best Bid/Ask.
 * Результаты ордеров — snackbar'ами под заголовком панели.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrderPlacementPanel(
    symbol: String,
    selectedPrice: Double?,
    orderQuantity: String,
    /** Минимальный размер символа — серый placeholder пустого поля qty. */
    qtyPlaceholder: String? = null,
    bestBidPrice: Double?,
    bestAskPrice: Double?,
    onQuantityChanged: (String) -> Unit,
    onOrderIntent: (OrderIntent) -> Unit,
    onCloseAll: () -> Unit,
    onCancelAll: () -> Unit,
    isTradingEnabled: Boolean,
    reduceOnly: Boolean,
    limitOrderType: OrderType,
    leverage: Int?,
    marginMode: Int,
    paperEnabled: Boolean,
    confirmOrders: Boolean,
    pendingText: String?,
    /** PnL текущей позиции: изменение цены · % · USDT (null — нет позиции). */
    pnlPrice: String?,
    pnlPercent: String?,
    pnlUsdt: String?,
    pnlUp: Boolean,
    /** Маржа под ордер: «Margin ≈ X · Free Y · Max Z (Nx)» (null — нет данных). */
    marginText: String? = null,
    /** Есть открытая позиция Long/Short — для лейблов reduce-only лимиток. */
    hasLongPosition: Boolean = false,
    hasShortPosition: Boolean = false,
    onReduceOnlyChanged: (Boolean) -> Unit,
    onLimitOrderTypeChanged: (OrderType) -> Unit,
    onLeverageChanged: (Int?) -> Unit,
    onMarginModeChanged: (Int) -> Unit,
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
            // PnL позиции — самая верхняя строка, видна всегда (без позиций: -/-),
            // три значения равномерно по ширине панели
            val pnlColor = when {
                pnlPrice == null -> labelColor
                pnlUp -> buyColor
                else -> sellColor
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                PnlCell(
                    text = pnlPrice ?: "-/-",
                    color = pnlColor,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f),
                )
                PnlCell(
                    text = pnlPercent ?: "-/-",
                    color = pnlColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                PnlCell(
                    text = pnlUsdt ?: "-/-",
                    color = pnlColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }

            // Строка 1: Trading · Confirm · RO · маржа · Paper (всё в одну строку)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ToggleChip(
                    label = "Trading",
                    active = isTradingEnabled,
                    activeColor = okColor,
                    inactiveColor = sellColor,
                ) { onOrderIntent(OrderIntent.ToggleTrading) }
                ToggleChip(label = "Confirm", active = confirmOrders) { onConfirmChanged(!confirmOrders) }
                ToggleChip(label = "RO", active = reduceOnly) { onReduceOnlyChanged(!reduceOnly) }
                // Маржа: Cross / Isol (компактный тумблер)
                ToggleChip(
                    label = if (marginMode == 1) "Isol" else "Cross",
                    active = marginMode == 1,
                    activeColor = accent,
                ) { onMarginModeChanged(if (marginMode == 1) 2 else 1) }
                ToggleChip(label = "Paper", active = paperEnabled) { onPaperChanged(!paperEnabled) }
            }

            // Строка 2: qty (узкий) / тип лимитки / плечо
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Qty:", color = labelColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                BasicTextField(
                    value = orderQuantity,
                    onValueChange = onQuantityChanged,
                    modifier = Modifier
                        .width(70.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(3.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (orderQuantity.isEmpty() && !qtyPlaceholder.isNullOrBlank()) {
                                Text(
                                    text = qtyPlaceholder,
                                    color = labelColor,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                )
                            }
                            inner()
                        }
                    },
                )
                TerminalDropdown(
                    currentValue = limitOrderType,
                    items = listOf(OrderType.LIMIT, OrderType.POST_ONLY, OrderType.IOC, OrderType.FOK),
                    onValueChanged = onLimitOrderTypeChanged,
                    displayText = { it.name },
                    menuWidth = 100.dp,
                )
                TerminalDropdown(
                    currentValue = leverage ?: 0,
                    items = listOf(0, 1, 2, 3, 5, 10, 20, 50, 100, 125),
                    onValueChanged = { onLeverageChanged(it.takeIf { l -> l > 0 }) },
                    displayText = { if (it <= 0) "Lvg" else "${it}x" },
                    menuWidth = 90.dp,
                )
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
                        color = accent,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    SideButton("OK", buyColor, { onConfirmPending() }, Modifier.width(48.dp))
                    SideButton("X", sellColor, { onCancelPending() }, Modifier.width(32.dp))
                }
            }

            // Cancel All / Close All (порядок: cancel слева, close справа)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                SideButton("Cancel All", warnColor, onCancelAll, Modifier.weight(1f), outlined = true)
                SideButton("Close All", Color(0xFFE05B5B), onCloseAll, Modifier.weight(1f), outlined = true)
            }

            // Лимитки по выбранной цене (выбор — Confirm: ON + клик по уровню)
            // Reduce-only называет, что закрывает: CShort / CLong (как в chart trading)
            val buyLimitLabel = (if (reduceOnly && hasShortPosition) "CShort" else "Buy Limit") +
                if (selectedPrice == null) " (click price)" else ""
            val sellLimitLabel = (if (reduceOnly && hasLongPosition) "CLong" else "Sell Limit") +
                if (selectedPrice == null) " (click price)" else ""
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                LimitButton(
                    label = buyLimitLabel,
                    color = buyColor,
                    ready = selectedPrice != null,
                    modifier = Modifier.weight(1f),
                ) {
                    selectedPrice?.let {
                        onOrderIntent(OrderIntent.LimitBuy(symbol, it, orderQuantity.toDoubleOrNull() ?: 0.0))
                    }
                }
                LimitButton(
                    label = sellLimitLabel,
                    color = sellColor,
                    ready = selectedPrice != null,
                    modifier = Modifier.weight(1f),
                ) {
                    selectedPrice?.let {
                        onOrderIntent(OrderIntent.LimitSell(symbol, it, orderQuantity.toDoubleOrNull() ?: 0.0))
                    }
                }
            }

            // Market
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                SideButton("Buy", buyColor, {
                    onOrderIntent(OrderIntent.MarketBuy(symbol, orderQuantity.toDoubleOrNull() ?: 0.0))
                }, Modifier.weight(1f))
                SideButton("Sell", sellColor, {
                    onOrderIntent(OrderIntent.MarketSell(symbol, orderQuantity.toDoubleOrNull() ?: 0.0))
                }, Modifier.weight(1f))
            }

            // Best Bid / Best Ask
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                val bidReady = bestBidPrice != null && bestBidPrice > 0
                val askReady = bestAskPrice != null && bestAskPrice > 0
                LimitButton(
                    label = if (bidReady) "Best Bid" else "Best Bid ...",
                    color = buyColor,
                    ready = bidReady,
                    modifier = Modifier.weight(1f),
                ) {
                    if (bidReady) onOrderIntent(OrderIntent.BestBidBuy(symbol, bestBidPrice, orderQuantity.toDoubleOrNull() ?: 0.0))
                }
                LimitButton(
                    label = if (askReady) "Best Ask" else "Best Ask ...",
                    color = sellColor,
                    ready = askReady,
                    modifier = Modifier.weight(1f),
                ) {
                    if (askReady) onOrderIntent(OrderIntent.BestAskSell(symbol, bestAskPrice, orderQuantity.toDoubleOrNull() ?: 0.0))
                }
            }

            // Маржа под ордер: занятая (выбранная qty/плечо) и свободная
            marginText?.let { text ->
                Text(
                    text = text,
                    color = labelColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Компактный чип-тумблер (стиль Chip из chart trading panel). */
/** Ячейка строки PnL: моно-шрифт, цвет по знаку, выравнивание в своей трети. */
@Composable
private fun PnlCell(
    text: String,
    color: Color,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        textAlign = textAlign,
        modifier = modifier,
    )
}

@Composable
private fun ToggleChip(
    label: String,
    active: Boolean,
    activeColor: Color = accent,
    inactiveColor: Color = labelColor,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (active) activeColor else inactiveColor,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .background(
                if (active) activeColor.copy(alpha = 0.2f) else Color.Transparent,
                RoundedCornerShape(3.dp),
            )
            .border(
                1.dp,
                if (active) activeColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(3.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/** Кнопка стороны (стиль SideButton из chart trading panel). */
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
            .clickable(onClick = onClick),
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

/** Кнопка с состоянием готовности (стиль LimitButton из chart trading panel). */
@Composable
private fun LimitButton(
    label: String,
    color: Color,
    ready: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
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
