/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.feature.dom.ui.model.DomLevel
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val ROWS_ABOVE = 120
private const val ROWS_BELOW = 120
private const val ROW_COUNT = ROWS_ABOVE + 1 + ROWS_BELOW
private const val MARGIN_ROWS = 3

private val RowHeight = 24.dp

/**
 * Классическая ценовая лесенка: одна строка = одна цена (корзина агрегации),
 * включая пустые уровни. Объёмы привязаны к своим ценовым строкам и остаются
 * на месте при движении рынка — движение видно по подсветке последней сделки
 * и перетеканию объёмов между строками.
 *
 * Строки генерируются вокруг последней сделки, а лесенка автоматически
 * подтягивается, чтобы маркер последней цены не уходил за край видимого
 * списка (запас — 3 строки).
 */
@Composable
fun DomSection(
    levelsMap: Map<Long, DomLevel>,
    ladderStepTicks: Long,
    selectedPrice: Double?,
    bestBidDisplayTicks: Long?,
    bestAskDisplayTicks: Long?,
    lastPriceDisplayTicks: Long?,
    tickSize: Double,
    stepSize: Double,
    formatter: SymbolFormatter,
    onPriceSelected: (Double) -> Unit,
    orders: List<com.aandios.nous.api.market.model.trading.Order> = emptyList(),
    positions: List<com.aandios.nous.api.market.model.trading.Position> = emptyList(),
    markPrice: Double = 0.0,
    baseText: String? = null,
    /** Inverse-контракт: номинал (USD) и признак COIN-M — для PnL позиции. */
    contractSize: Double = 1.0,
    inverse: Boolean = false,
    onCancelOrder: (String) -> Unit = {},
    onResizeOrder: (com.aandios.nous.api.market.model.trading.Order, Double) -> Unit = { _, _ -> },
    onMoveOrder: (com.aandios.nous.api.market.model.trading.Order, Double) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()

    // Ордер/позиция по ценовым строкам (в тиках лесенки)
    val orderByTicks = remember(orders, tickSize) {
        if (tickSize <= 0.0) emptyMap()
        else orders.filter { it.price > 0.0 }.associateBy { (it.price / tickSize).roundToLong() }
    }
    val positionByTicks = remember(positions, tickSize) {
        if (tickSize <= 0.0) emptyMap()
        else positions.filter { it.avgPrice > 0.0 }.associateBy { (it.avgPrice / tickSize).roundToLong() }
    }

    val maxSteps by derivedStateOf {
        levelsMap.values.maxOfOrNull { maxOf(it.bidSteps ?: 0L, it.askSteps ?: 0L) } ?: 0L
    }

    // Якорь: последняя сделка → best ask → best bid → верхний уровень книги
    val anchorTicks = lastPriceDisplayTicks ?: bestAskDisplayTicks ?: bestBidDisplayTicks
        ?: levelsMap.keys.maxOrNull() ?: 0L

    val step = ladderStepTicks.coerceAtLeast(1L)

    // Строка якоря всегда имеет индекс ROWS_ABOVE. Если она уходит за край
    // видимой зоны (запас MARGIN_ROWS) — минимально подтягиваем список обратно
    LaunchedEffect(anchorTicks, step) {
        if (lazyListState.isScrollInProgress) return@LaunchedEffect
        val visible = lazyListState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return@LaunchedEffect

        val first = visible.first().index
        val last = visible.last().index
        val allowedTop = first + MARGIN_ROWS
        val allowedBottom = last - MARGIN_ROWS
        val anchorIndex = ROWS_ABOVE

        val distance = when {
            anchorIndex < allowedTop -> allowedTop - anchorIndex
            anchorIndex > allowedBottom -> anchorIndex - allowedBottom
            else -> return@LaunchedEffect
        }

        // Минимальная коррекция: ставим якорь на строку MARGIN_ROWS от края
        val targetIndex = if (anchorIndex < allowedTop) {
            anchorIndex - MARGIN_ROWS
        } else {
            anchorIndex - (visible.size - 1) + MARGIN_ROWS
        }.coerceAtLeast(0)

        if (distance > 30) {
            lazyListState.scrollToItem(targetIndex, 0)
        } else {
            lazyListState.animateScrollToItem(targetIndex, 0)
        }
    }

    val selectedDisplayTicks = remember(selectedPrice, tickSize) {
        selectedPrice?.let { sp ->
            if (tickSize <= 0.0) null
            else (sp / tickSize).roundToLong()
        }
    }

    val dragState = remember { DomOrderDragState() }
    var boxRoot by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { RowHeight.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { boxRoot = it.positionInRoot() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Bid Vol",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(0.8f)
            )
            Text("Price",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(0.6f)
            )
            Text("Ask Vol",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(0.8f)
            )
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.weight(1f)
        ) {
            items(
                count = ROW_COUNT,
                key = { index -> anchorTicks + (ROWS_ABOVE - index) * step }
            ) { index ->
                val key = anchorTicks + (ROWS_ABOVE - index) * step
                Box(modifier = Modifier.height(RowHeight)) {
                    LevelRow(
                        priceTicks = key,
                        level = levelsMap[key],
                        maxSteps = maxSteps,
                        selectedDisplayTicks = selectedDisplayTicks,
                        lastPriceDisplayTicks = lastPriceDisplayTicks,
                        tickSize = tickSize,
                        stepSize = stepSize,
                        formatter = formatter,
                        order = orderByTicks[key],
                        position = positionByTicks[key],
                        markPrice = markPrice,
                        baseText = baseText,
                        contractSize = contractSize,
                        inverse = inverse,
                        priceStepPerRow = tickSize * step.toDouble(),
                        dragState = dragState,
                        onCancelOrder = onCancelOrder,
                        onResizeOrder = onResizeOrder,
                        onMoveOrder = onMoveOrder,
                        onPriceClick = { _, dPrice -> onPriceSelected(dPrice) }
                    )
                }
            }
        }
        }

        // «Летящий» чип при драге ордера — как перетаскивание в chart trading
        val draggingOrder = dragState.order
        if (draggingOrder != null) {
            val color = if (draggingOrder.side == OrderSide.BUY) longColor else shortColor
            val rows = dragState.deltaY / rowHeightPx
            val targetPrice = (draggingOrder.price - rows * tickSize * step).coerceAtLeast(0.0)
            val kind = if (draggingOrder.reduceOnly) "Close" else "Open"
            val sideName = if (draggingOrder.side == OrderSide.BUY) "Long" else "Short"
            val label = (listOf(kind, sideName, baseText.orEmpty()).filter { it.isNotBlank() }.joinToString(" ") +
                " → " + formatter.formatPrice(targetPrice)).trim()

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (dragState.startX - boxRoot.x).roundToInt(),
                            (dragState.startY - boxRoot.y + dragState.deltaY).roundToInt(),
                        )
                    }
                    .width(with(density) { dragState.widthPx.toDp() })
                    .zIndex(10f)
                    .shadow(4.dp, RoundedCornerShape(2.dp))
                    .background(color, RoundedCornerShape(2.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
