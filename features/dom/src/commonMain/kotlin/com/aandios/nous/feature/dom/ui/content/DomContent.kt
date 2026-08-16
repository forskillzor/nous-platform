/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.feature.dom.ui.model.DomLevel

@Composable
fun DomContent(
    levels: List<DomLevel>,
    maxSteps: Long,
    selectedPrice: Double?,
    bestBidDisplayTicks: Long?,
    bestAskDisplayTicks: Long?,
    tickSize: Double,
    stepSize: Double,
    formatter: SymbolFormatter,
    onPriceSelected: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        DomSection(
            levels = levels,
            maxSteps = maxSteps,
            selectedPrice = selectedPrice,
            bestBidDisplayTicks = bestBidDisplayTicks,
            bestAskDisplayTicks = bestAskDisplayTicks,
            tickSize = tickSize,
            stepSize = stepSize,
            formatter = formatter,
            onPriceSelected = onPriceSelected,
            modifier = Modifier.weight(1f)
        )
    }
}
