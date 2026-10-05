/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.Position
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.feature.dom.ui.model.DomLevel

@Composable
fun DomContent(
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
    orders: List<Order> = emptyList(),
    positions: List<Position> = emptyList(),
    markPrice: Double = 0.0,
    baseText: String? = null,
    onCancelOrder: (String) -> Unit = {},
    onResizeOrder: (Order, Double) -> Unit = { _, _ -> },
    onMoveOrder: (Order, Double) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        DomSection(
            levelsMap = levelsMap,
            ladderStepTicks = ladderStepTicks,
            selectedPrice = selectedPrice,
            bestBidDisplayTicks = bestBidDisplayTicks,
            bestAskDisplayTicks = bestAskDisplayTicks,
            lastPriceDisplayTicks = lastPriceDisplayTicks,
            tickSize = tickSize,
            stepSize = stepSize,
            formatter = formatter,
            onPriceSelected = onPriceSelected,
            orders = orders,
            positions = positions,
            markPrice = markPrice,
            baseText = baseText,
            onCancelOrder = onCancelOrder,
            onResizeOrder = onResizeOrder,
            onMoveOrder = onMoveOrder,
            modifier = Modifier.weight(1f)
        )
    }
}
