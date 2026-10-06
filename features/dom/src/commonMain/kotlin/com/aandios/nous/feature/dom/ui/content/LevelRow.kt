/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
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

internal val longColor = Color(0xFF26A69A)
internal val shortColor = Color(0xFFEF5350)

/**
 * Строка ценовой лесенки. `level == null` — пустой уровень (только цена).
 *
 * Поверх объёмов:
 *  * позиция (Long/Short) — весь уровень сплошной плашкой цвета позиции
 *    (как бейдж позиции в chart trading), объёмы скрыты; слева «Long 10 SOL»
 *    белым, цена по центру, справа PnL на тёмной мини-плашке (изменение
 *    цены в базовых значениях, % от изменения цены);
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
    /** Цена одной строки лесенки (tickSize * шаг агрегации) — для драга. */
    priceStepPerRow: Double = 0.0,
    /** Общее состояние драга: чип «летит» за курсором поверх лесенки. */
    dragState: DomOrderDragState? = null,
    onCancelOrder: (String) -> Unit = {},
    onResizeOrder: (Order, Double) -> Unit = { _, _ -> },
    onMoveOrder: (Order, Double) -> Unit = { _, _ -> },
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
    val orderColor = if (order?.side == OrderSide.BUY) longColor else shortColor

    val backgroundColor = when {
        // Позиция — весь row сплошной плашкой как бейдж позиции в chart trading
        position != null -> positionColor
        // Весь уровень с лимитным ордером подсвечен цветом ордера
        order != null -> orderColor.copy(alpha = 0.35f)
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
            ) {
                // Уровень с ордером занят: клик по любой точке строки не
                // выбирает цену и не размещает второй ордер
                if (order == null) onPriceClick(priceTicks, price)
            }
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
                    text = buildString {
                        append(if (position.side == TradeSide.BUY) "Long " else "Short ")
                        append(trimQty(position.quantity))
                        if (!baseText.isNullOrBlank()) {
                            append(" ")
                            append(baseText)
                        }
                    },
                    color = Color.White,
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
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
                    priceStepPerRow = priceStepPerRow,
                    dragState = dragState,
                    onCancel = { onCancelOrder(order.orderId) },
                    onResize = { qty -> onResizeOrder(order, qty) },
                    onMove = { price -> onMoveOrder(order, price) },
                    // short: label слева, qty справа
                    qtyOnLeft = false,
                    modifier = Modifier.align(Alignment.CenterStart)
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
                fontWeight = if (isSelected || position != null || order != null) FontWeight.Bold else FontWeight.Normal
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
                // Тёмная мини-плашка под PnL — как у бейджа позиции в chart trading
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .background(Color(0xFF1B222B), RoundedCornerShape(2.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = pnlText,
                        color = pnlColor,
                        fontSize = 10.sp,
                        lineHeight = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
            if (order != null && order.side == OrderSide.BUY && position == null) {
                OrderChip(
                    order = order,
                    baseText = baseText,
                    priceStepPerRow = priceStepPerRow,
                    dragState = dragState,
                    onCancel = { onCancelOrder(order.orderId) },
                    onResize = { qty -> onResizeOrder(order, qty) },
                    onMove = { price -> onMoveOrder(order, price) },
                    // long: qty слева, label справа
                    qtyOnLeft = true,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}

/** Составляющие PnL позиции для верхней строки панели ордеров. */
data class DomPnlLines(
    val price: String,
    val percent: String,
    val usdt: String,
    val up: Boolean,
)

/**
 * PnL позиции в базовых значениях цены (SOL: 0.35, без «тиков») ·
 * % от изменения цены · USDT.
 */
fun domPositionPnl(position: Position, markPrice: Double, tickSize: Double): DomPnlLines {
    val mark = if (markPrice > 0.0) markPrice else position.markPrice
    val dir = if (position.side == TradeSide.BUY) 1.0 else -1.0
    val pnl = if (mark > 0.0) (mark - position.avgPrice) * position.quantity * dir else 0.0
    val delta = if (mark > 0.0) (mark - position.avgPrice) * dir else 0.0
    val pct = if (mark > 0.0 && position.avgPrice > 0.0) {
        delta / position.avgPrice * 100.0
    } else 0.0
    val sign = if (pnl > 0.0) "+" else ""
    return DomPnlLines(
        price = "$sign${fmtByTick(delta, tickSize)}",
        percent = "$sign${fmt2(pct)}%",
        usdt = "$sign${fmt2(pnl)} USDT",
        up = pnl >= 0,
    )
}

/** PnL позиции в лесенке: изменение цены в базовых значениях и % от изменения. */
private fun positionPnlText(position: Position, markPrice: Double, tickSize: Double): Pair<String, Color> {
    val mark = if (markPrice > 0.0) markPrice else position.markPrice
    val dir = if (position.side == TradeSide.BUY) 1.0 else -1.0
    val pnl = if (mark > 0.0) (mark - position.avgPrice) * position.quantity * dir else 0.0
    val delta = if (mark > 0.0) (mark - position.avgPrice) * dir else 0.0
    // % — от изменения цены, а не от маржи (как просили)
    val pct = if (mark > 0.0 && position.avgPrice > 0.0) {
        delta / position.avgPrice * 100.0
    } else 0.0
    val sign = if (pnl > 0.0) "+" else ""
    val text = "$sign${fmtByTick(delta, tickSize)} $sign${fmt2(pct)}%"
    return text to (if (pnl >= 0) longColor else shortColor)
}

/**
 * Состояние драга ордера в лесенке: чип «летит» за курсором поверх списка
 * (как перетаскивание линии ордера в chart trading).
 */
class DomOrderDragState {
    var order by mutableStateOf<Order?>(null)
    var startX by mutableStateOf(0f)
    var startY by mutableStateOf(0f)
    var widthPx by mutableStateOf(0f)
    var deltaY by mutableStateOf(0f)

    fun clear() {
        order = null
        deltaY = 0f
    }
}

/** Бейдж ордера как в chart trading: цветной рект, текст, инпут qty, драг. */
@Composable
private fun OrderChip(
    order: Order,
    baseText: String?,
    priceStepPerRow: Double,
    dragState: DomOrderDragState?,
    onCancel: () -> Unit,
    onResize: (Double) -> Unit,
    onMove: (Double) -> Unit,
    /** true — инпут слева от подписи (long), false — справа (short). */
    qtyOnLeft: Boolean,
    modifier: Modifier = Modifier,
) {
    val isBuy = order.side == OrderSide.BUY
    val color = if (isBuy) longColor else shortColor
    val kind = if (order.reduceOnly) "Close" else "Open"
    val sideName = if (isBuy) "Long" else "Short"
    val base = baseText.orEmpty()

    val full = listOf(kind, sideName, base).filter { it.isNotBlank() }.joinToString(" ")
    val short = (kind.first().toString() + sideName + " " + base).trim()
    val shortest = kind.first().toString() + sideName

    var qtyText by remember(order.orderId) { mutableStateOf(trimQty(order.quantity)) }
    var committed by remember(order.orderId) { mutableStateOf(false) }

    fun commit() {
        if (committed) return
        val q = qtyText.toDoubleOrNull()?.takeIf { it > 0 } ?: return
        if (q == order.quantity) return
        committed = true
        onResize(q)
    }

    val density = LocalDensity.current
    val rowHeightPx = with(density) { LadderRowHeight.toPx() }
    var dragPx by remember(order.orderId) { mutableStateOf(0f) }
    var chipRootPos by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var chipSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val beingDragged = dragState?.order?.orderId == order.orderId

    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val style = TextStyle(
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
        // Фиксированная часть: инпут qty
        val fixedPx = with(density) { 44.dp.toPx() }
        val textBudgetPx = with(density) { maxWidth.toPx() } - fixedPx
        val label = listOf(full, short, shortest).firstOrNull { candidate ->
            measurer.measure(AnnotatedString(candidate), style).size.width <= textBudgetPx
        } ?: shortest

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .background(color, RoundedCornerShape(2.dp))
                .padding(horizontal = 3.dp)
                .alpha(if (beingDragged) 0.3f else 1f)
                // Поглощаем одиночные клики, чтобы строка лесенки не реагировала
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { }
                .onGloballyPositioned { coords ->
                    chipRootPos = coords.positionInRoot()
                    chipSize = coords.size
                }
                .pointerInput(order.orderId, priceStepPerRow) {
                    detectDragGestures(
                        onDragStart = {
                            dragPx = 0f
                            dragState?.let { st ->
                                st.order = order
                                st.startX = chipRootPos.x
                                st.startY = chipRootPos.y
                                st.widthPx = chipSize.width.toFloat()
                                st.deltaY = 0f
                            }
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragPx += amount.y
                            dragState?.deltaY = dragPx
                        },
                        onDragEnd = {
                            val rows = dragPx / rowHeightPx
                            dragPx = 0f
                            dragState?.clear()
                            if (priceStepPerRow > 0.0 && kotlin.math.abs(rows) >= 0.5f) {
                                val newPrice = order.price - rows * priceStepPerRow
                                if (newPrice > 0.0) onMove(newPrice)
                            }
                        },
                        onDragCancel = {
                            dragPx = 0f
                            dragState?.clear()
                        },
                    )
                },
        ) {
            if (qtyOnLeft) {
                QtyField(qtyText, color, { qtyText = it }, ::commit)
            }
            // Двойной клик по подписи — отмена ордера; драг — перемещение
            Text(
                text = label,
                style = style,
                maxLines = 1,
                softWrap = false,
                textAlign = if (qtyOnLeft) TextAlign.End else TextAlign.Start,
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(order.orderId) {
                        detectTapGestures(
                            onTap = { /* consume: не отдаём клик строке лесенки */ },
                            onDoubleTap = { onCancel() },
                        )
                    },
            )
            if (!qtyOnLeft) {
                QtyField(qtyText, color, { qtyText = it }, ::commit)
            }
        }
    }
}

private val LadderRowHeight = 24.dp

/** Поле qty на бейдже (белое, тёмный текст по центру; commit — Enter/фокус). */
@Composable
private fun QtyField(
    value: String,
    cursorColor: Color,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = Color(0xFF1A1A1A),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(cursorColor),
        modifier = Modifier
            .width(40.dp)
            .background(Color.White, RoundedCornerShape(2.dp))
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onCommit()
                    true
                } else {
                    false
                }
            }
            .onFocusChanged { state -> if (!state.isFocused) onCommit() },
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center) { inner() }
        },
    )
}

private fun trimQty(v: Double): String =
    com.aandios.nous.core.ui.format.plainDecimalString(v)

/** Число с двумя знаками без String.format (commonMain). */
private fun fmt2(v: Double): String = fmtDecimals(v, 2)

/** Значение в базовых единицах цены с числом знаков шага (SOL 0.01 → 2 знака). */
private fun fmtByTick(v: Double, tickSize: Double): String {
    var decimals = 0
    var t = tickSize
    while (decimals < 8 && t > 0.0 && kotlin.math.abs(t - t.roundToLong()) > 1e-9) {
        t *= 10.0
        decimals++
    }
    return fmtDecimals(v, decimals)
}

/** Фиксированное число знаков без String.format (commonMain). */
private fun fmtDecimals(v: Double, decimals: Int): String {
    var factor = 1.0
    repeat(decimals) { factor *= 10.0 }
    val rounded = round(v * factor) / factor
    val s = rounded.toString()
    val neg = s.startsWith("-")
    val body = if (neg) s.substring(1) else s
    val parts = body.split(".")
    val intPart = parts[0]
    val decPart = if (decimals <= 0) "" else {
        val raw = if (parts.size > 1) parts[1] else ""
        "." + raw.padEnd(decimals, '0').take(decimals)
    }
    return (if (neg) "-" else "") + intPart + decPart
}
