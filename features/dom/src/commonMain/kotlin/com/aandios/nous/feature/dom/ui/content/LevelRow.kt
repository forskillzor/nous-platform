/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.Position
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.feature.dom.ui.model.DomLevel
import kotlin.math.round
import kotlin.math.roundToLong

private val longColor = Color(0xFF26A69A)
private val shortColor = Color(0xFFEF5350)

/**
 * Строка ценовой лесенки. `level == null` — пустой уровень (только цена).
 *
 * Поверх объёмов:
 *  * позиция (Long/Short) — весь уровень выделен цветом позиции, объёмы
 *    скрыты; слева от цены «Long 10», справа PnL (тики, % от маржи, USDT);
 *  * ордер — покупка (Open Long) в пустой ask-колонке (крестик слева),
 *    продажа (Open Short) в пустой bid-колонке (крестик справа); подпись
 *    сокращается по месту: «Open Long 10 SOL» → «OLong 10 SOL» → «OLong 10».
 */
@Composable
fun LevelRow(
    priceTicks: Long,
    level: DomLevel?,
    maxSteps: Long,
    selectedDisplayTicks: Long?,
    lastPriceDisplayTicks: Long?,
    tickSize: Double,
    stepSize: Double,
    formatter: SymbolFormatter,
    order: Order? = null,
    position: Position? = null,
    markPrice: Double = 0.0,
    baseText: String? = null,
    onCancelOrder: (String) -> Unit = {},
    onPriceClick: (Long, Double) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val isSelected = selectedDisplayTicks?.let { it == priceTicks } ?: false
    val isLastPrice = lastPriceDisplayTicks?.let { it == priceTicks } ?: false

    val price = priceTicks * tickSize
    val bidQty = level?.bidSteps?.let { it * stepSize }
    val askQty = level?.askSteps?.let { it * stepSize }

    val positionColor = if (position?.side == TradeSide.BUY) longColor else shortColor

    val backgroundColor = when {
        position != null -> positionColor.copy(alpha = 0.22f)
        isSelected -> Color.Yellow.copy(alpha = 0.3f)
        isLastPrice -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
        isHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    val priceColor = when {
        position != null -> Color.White
        isSelected -> MaterialTheme.colorScheme.onSurface
        isLastPrice -> MaterialTheme.colorScheme.tertiary
        level == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        else -> Color.White
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onPriceClick(priceTicks, price) }
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bid-колонка: объёмы, позиция (Long) и SELL-ордер
        Box(
            modifier = Modifier.weight(0.8f).height(20.dp)
        ) {
            if (position == null) {
                val bidSteps = level?.bidSteps ?: 0L
                if (bidSteps > 0) {
                    val volumeWidth = if (maxSteps > 0) (bidSteps.toFloat() / maxSteps.toFloat()).coerceIn(0f, 1f) else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(volumeWidth)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    )
                }
                if (bidSteps > 0 && bidQty != null) {
                    Text(
                        text = formatter.formatVolumeFull(bidQty),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }
            } else {
                Text(
                    text = "${if (position.side == TradeSide.BUY) "Long" else "Short"} ${trimQty(position.quantity)}",
                    color = positionColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
            if (order != null && order.side == OrderSide.SELL && position == null) {
                OrderChip(
                    order = order,
                    baseText = baseText,
                    onCancel = { onCancelOrder(order.orderId) },
                    crossOnLeft = false,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }

        // Price
        Text(
            text = formatter.formatPrice(price),
            color = priceColor,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = if (isSelected || position != null) FontWeight.Bold else FontWeight.Normal
            ),
            modifier = Modifier.weight(0.6f)
        )

        // Ask-колонка: объёмы, PnL позиции и BUY-ордер
        Box(
            modifier = Modifier.weight(0.8f).height(20.dp)
        ) {
            if (position == null) {
                val askSteps = level?.askSteps ?: 0L
                if (askSteps > 0) {
                    val volumeWidth = if (maxSteps > 0) (askSteps.toFloat() / maxSteps.toFloat()).coerceIn(0f, 1f) else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(volumeWidth)
                            .align(Alignment.CenterEnd)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                    )
                }
                if (askSteps > 0 && askQty != null) {
                    Text(
                        text = formatter.formatVolumeFull(askQty),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            } else {
                val (pnlText, pnlColor) = positionPnlText(position, markPrice, tickSize)
                Text(
                    text = pnlText,
                    color = pnlColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
            if (order != null && order.side == OrderSide.BUY && position == null) {
                OrderChip(
                    order = order,
                    baseText = baseText,
                    onCancel = { onCancelOrder(order.orderId) },
                    crossOnLeft = true,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
        }
    }
}

/** PnL позиции: тики · % от маржи (как chart panel) · USDT. */
private fun positionPnlText(position: Position, markPrice: Double, tickSize: Double): Pair<String, Color> {
    val mark = if (markPrice > 0.0) markPrice else position.markPrice
    val dir = if (position.side == TradeSide.BUY) 1.0 else -1.0
    val pnl = if (mark > 0.0) (mark - position.avgPrice) * position.quantity * dir else 0.0
    val leverage = (position.leverage ?: 1).coerceAtLeast(1)
    val margin = position.avgPrice * position.quantity / leverage
    val pct = if (margin > 0.0) pnl / margin * 100.0 else 0.0
    val ticks = if (tickSize > 0.0 && mark > 0.0) (mark - position.avgPrice) * dir / tickSize else 0.0
    val sign = if (pnl >= 0) "+" else ""
    val text = "$sign${ticks.roundToLong()}t $sign${fmt2(pct)}% $sign${fmt2(pnl)}"
    return text to (if (pnl >= 0) longColor else shortColor)
}

/** Бейдж ордера в лесенке с сокращением подписи по доступной ширине. */
@Composable
private fun OrderChip(
    order: Order,
    baseText: String?,
    onCancel: () -> Unit,
    crossOnLeft: Boolean,
    modifier: Modifier = Modifier,
) {
    val isBuy = order.side == OrderSide.BUY
    val color = if (isBuy) longColor else shortColor
    val kind = if (order.reduceOnly) "Close" else "Open"
    val sideName = if (isBuy) "Long" else "Short"
    val qty = trimQty(order.quantity)
    val base = baseText.orEmpty()

    val full = listOf(kind, sideName, qty, base).filter { it.isNotBlank() }.joinToString(" ")
    val short = listOf(kind.first().toString() + sideName, qty, base).filter { it.isNotBlank() }.joinToString(" ")
    val shortest = kind.first().toString() + sideName + " " + qty

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val style = TextStyle(
            color = color,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
        val crossPx = with(density) { 14.dp.toPx() }
        val budget = with(density) { maxWidth.toPx() } - crossPx
        val label = listOf(full, short, shortest).firstOrNull { candidate ->
            measurer.measure(AnnotatedString(candidate), style).size.width <= budget
        } ?: shortest

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (crossOnLeft) CancelCross(color, onCancel)
            Text(
                text = label,
                style = style,
                maxLines = 1,
                softWrap = false,
            )
            if (!crossOnLeft) CancelCross(color, onCancel)
        }
    }
}

@Composable
private fun CancelCross(color: Color, onCancel: () -> Unit) {
    Text(
        text = "✕",
        color = color.copy(alpha = 0.85f),
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCancel() }
            .padding(horizontal = 2.dp),
    )
}

private fun trimQty(v: Double): String {
    var s = v.toString()
    if ('.' in s) s = s.trimEnd('0').trimEnd('.')
    return s
}

/** Число с двумя знаками без String.format (commonMain). */
private fun fmt2(v: Double): String {
    val rounded = round(v * 100.0) / 100.0
    val s = rounded.toString()
    val neg = s.startsWith("-")
    val body = if (neg) s.substring(1) else s
    val parts = body.split(".")
    val intPart = parts[0]
    val decPart = if (parts.size > 1) parts[1].padEnd(2, '0').take(2) else "00"
    return (if (neg) "-" else "") + intPart + "." + decPart
}
