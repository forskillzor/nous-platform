/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.liquidation.LiquidationOrder
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.utils.calculateCandleMetrics


fun DrawScope.drawLiquidationHistogram(
    area: Rect,
    candles: List<Candle>,
    orders: List<LiquidationOrder>,
    scrollOffset: Float,
    zoomLevel: Float
) {
    if (candles.isEmpty()) return

    val metrics = calculateCandleMetrics(zoomLevel)
    val totalW = metrics.width + metrics.spacing
    val candleMs = if (candles.size >= 2) candles[1].timestamp - candles[0].timestamp else 3_600_000L
    val minTs = candles.minOfOrNull { it.timestamp } ?: return

    // Group liquidation orders by candle start time
    data class Bar(var longVol: Float = 0f, var shortVol: Float = 0f)
    val bars = linkedMapOf<Long, Bar>()

    for (order in orders) {
        val candleStart = order.timestamp / candleMs * candleMs
        if (candleStart < minTs) continue
        val bar = bars.getOrPut(candleStart) { Bar() }
        when (order.side) {
            TradeSide.SELL -> bar.longVol += order.quantity.toFloat()
            TradeSide.BUY -> bar.shortVol += order.quantity.toFloat()
        }
    }

    val maxVol = bars.values.maxOfOrNull { maxOf(it.longVol, it.shortVol) } ?: 1f
    val midY = area.top + area.height / 2

    // Background
    drawRect(Color.Black.copy(alpha = 0.3f), area.topLeft, Size(area.width, area.height))

    for ((candleStart, bar) in bars) {
        val slot = ((candleStart - minTs).toFloat() / candleMs.toFloat())
        val x = slot * totalW - scrollOffset + metrics.width / 2

        if (x < -metrics.width || x > area.width + metrics.width) continue

        val halfW = (metrics.width * 0.4f).coerceAtLeast(1f)
        val left = x - halfW
        val barW = halfW * 2

        // Long liquidations (SELL = green column above midline)
        if (bar.longVol > 0f) {
            val h = (bar.longVol / maxVol * (area.height / 2 - 4f)).coerceAtLeast(1f)
            drawRect(
                color = Color(0xFF22AA22),
                topLeft = Offset(left, midY - h),
                size = Size(barW, h)
            )
        }

        // Short liquidations (BUY = red column below midline)
        if (bar.shortVol > 0f) {
            val h = (bar.shortVol / maxVol * (area.height / 2 - 4f)).coerceAtLeast(1f)
            drawRect(
                color = Color(0xFFCC2222),
                topLeft = Offset(left, midY),
                size = Size(barW, h)
            )
        }
    }
}
