/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trading.history

import com.aandios.nous.api.market.model.trading.TradeFill
import com.aandios.nous.api.market.model.trading.TradeSide
import kotlin.math.min

/**
 * One fully closed position assembled from provider fills: entry/exit VWAPs,
 * total fees and the provider realized PnL of the closing fills.
 */
data class PositionHistoryEntry(
    val symbol: String,
    val side: TradeSide,
    val quantity: Double,
    val avgEntryPrice: Double,
    val avgExitPrice: Double,
    val fee: Double,
    val feeCurrency: String,
    val pnl: Double,
    val openedAt: Long,
    val closedAt: Long,
)

/**
 * Reconstructs closed positions from fills. Open positions and closing fills
 * whose entries are outside the fetched history window are omitted.
 */
fun buildPositionHistory(fills: List<TradeFill>): List<PositionHistoryEntry> {
    val ordered = fills
        .filter { it.positionSide != null && it.quantity > EPS }
        .sortedWith(compareBy<TradeFill>({ it.timestamp }, { it.id }))
    val open = LinkedHashMap<PositionKey, OpenPosition>()
    val closed = mutableListOf<PositionHistoryEntry>()

    ordered.forEach { fill ->
        val side = fill.positionSide ?: return@forEach
        val closedQty = fill.closedQuantity.coerceIn(0.0, fill.quantity)
        val openQty = fill.quantity - closedQty
        if (closedQty > EPS) {
            val closeRatio = closedQty / fill.quantity
            applyClose(
                closed = closed,
                open = open,
                key = PositionKey(fill.symbol.lowercase(), side),
                fill = fill,
                quantity = closedQty,
                fee = fill.fee * closeRatio,
                pnl = fill.pnl,
            )
        }
        if (openQty > EPS) {
            val openSide = if (closedQty > EPS) side.opposite() else side
            val openRatio = openQty / fill.quantity
            applyOpen(
                open = open,
                key = PositionKey(fill.symbol.lowercase(), openSide),
                fill = fill,
                quantity = openQty,
                fee = fill.fee * openRatio,
            )
        }
    }

    return closed.sortedByDescending { it.closedAt }
}

private fun applyOpen(
    open: MutableMap<PositionKey, OpenPosition>,
    key: PositionKey,
    fill: TradeFill,
    quantity: Double,
    fee: Double,
) {
    val position = open.getOrPut(key) {
        OpenPosition(
            symbol = fill.symbol,
            side = key.side,
            feeCurrency = fill.feeCurrency,
            openedAt = fill.timestamp,
        )
    }
    position.entryQty += quantity
    position.entryNotional += fill.price * quantity
    position.entryFee += fee
}

private fun applyClose(
    closed: MutableList<PositionHistoryEntry>,
    open: MutableMap<PositionKey, OpenPosition>,
    key: PositionKey,
    fill: TradeFill,
    quantity: Double,
    fee: Double,
    pnl: Double,
) {
    val position = open[key] ?: return
    if (position.entryQty <= EPS) return
    val matched = min(quantity, position.entryQty)
    if (matched <= EPS) return
    val matchedRatio = matched / quantity

    position.entryQty -= matched
    position.exitQty += matched
    position.exitNotional += fill.price * matched
    position.exitFee += fee * matchedRatio
    position.pnl += pnl * matchedRatio
    position.closedAt = fill.timestamp

    if (position.entryQty <= EPS) {
        open.remove(key)
        closed += position.toEntry()
    }
}

private data class PositionKey(
    val symbol: String,
    val side: TradeSide,
)

private class OpenPosition(
    val symbol: String,
    val side: TradeSide,
    val feeCurrency: String,
    val openedAt: Long,
) {
    var entryQty = 0.0
    var entryNotional = 0.0
    var entryFee = 0.0
    var exitQty = 0.0
    var exitNotional = 0.0
    var exitFee = 0.0
    var pnl = 0.0
    var closedAt = openedAt

    fun toEntry(): PositionHistoryEntry {
        val entryQtyForAvg = entryQty + exitQty
        return PositionHistoryEntry(
            symbol = symbol,
            side = side,
            quantity = exitQty,
            avgEntryPrice = if (entryQtyForAvg > EPS) entryNotional / entryQtyForAvg else 0.0,
            avgExitPrice = if (exitQty > EPS) exitNotional / exitQty else 0.0,
            fee = entryFee + exitFee,
            feeCurrency = feeCurrency,
            pnl = pnl,
            openedAt = openedAt,
            closedAt = closedAt,
        )
    }
}

private fun TradeSide.opposite(): TradeSide =
    if (this == TradeSide.BUY) TradeSide.SELL else TradeSide.BUY

private const val EPS = 1e-9
