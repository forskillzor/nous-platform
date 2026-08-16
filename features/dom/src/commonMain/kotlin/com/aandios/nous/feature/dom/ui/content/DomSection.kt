/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.feature.dom.ui.model.DomLevel
import kotlin.math.roundToLong

@Composable
fun DomSection(
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
    val lazyListState = rememberLazyListState()

    val scrollTargetTicks = bestBidDisplayTicks ?: bestAskDisplayTicks

    LaunchedEffect(scrollTargetTicks) {
        val target = scrollTargetTicks ?: return@LaunchedEffect
        if (lazyListState.isScrollInProgress) return@LaunchedEffect
        val idx = levels.indexOfFirst { it.priceTicks == target }
        if (idx < 0) return@LaunchedEffect
        val visible = lazyListState.layoutInfo.visibleItemsInfo
        if (visible.any { it.index == idx }) return@LaunchedEffect
        val visibleCount = visible.size.coerceAtLeast(1)
        lazyListState.animateScrollToItem((idx - visibleCount / 2).coerceAtLeast(0), 0)
    }

    val selectedDisplayTicks = remember(selectedPrice, tickSize) {
        selectedPrice?.let { sp ->
            if (tickSize <= 0.0) null
            else (sp / tickSize).roundToLong()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
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
                items = levels,
                key = { it.priceTicks }
            ) { level ->
                LevelRow(
                    level = level,
                    maxSteps = maxSteps,
                    selectedDisplayTicks = selectedDisplayTicks,
                    bestBidDisplayTicks = bestBidDisplayTicks,
                    bestAskDisplayTicks = bestAskDisplayTicks,
                    tickSize = tickSize,
                    stepSize = stepSize,
                    formatter = formatter,
                    onPriceClick = { _, dPrice -> onPriceSelected(dPrice) }
                )
            }
        }
    }
}
