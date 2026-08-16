/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.core.ui.theme.TradingTerminalTheme
import com.aandios.nous.feature.dom.di.initKoinForPreview
import com.aandios.nous.feature.dom.ui.content.DomContent
import com.aandios.nous.feature.dom.ui.footer.OrderPlacementPanel
import com.aandios.nous.feature.dom.ui.header.DomHeader
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import org.koin.core.context.stopKoin

@Composable
fun DomWindow() {
    val domViewModel: DomViewModel = koinInject()
    DomWindow(domViewModel = domViewModel)
}

@Composable
fun DomWindow(
    domViewModel: DomViewModel,
    modifier: Modifier = Modifier,
) {
    val domOptions by domViewModel.domOptions.collectAsState()
    val loadedSymbols by domViewModel.loadedSymbols.collectAsState()
    val orderQuantity by domViewModel.orderQuantity.collectAsState()
    val isTradingEnabled by domViewModel.isTradingEnabled.collectAsState()
    val symbolTickSize by domViewModel.symbolTickSize.collectAsState()
    val symbolStepSize by domViewModel.symbolStepSize.collectAsState()
    val selectedPrice by domViewModel.selectedPrice.collectAsState()

    val incrementalBestBid by domViewModel.incrementalBestBid.collectAsState()
    val incrementalBestAsk by domViewModel.incrementalBestAsk.collectAsState()
    val incrementalBestBidQuantity by domViewModel.incrementalBestBidQuantity.collectAsState()
    val incrementalBestAskQuantity by domViewModel.incrementalBestAskQuantity.collectAsState()

    val displayLevels by domViewModel.displayLevels.collectAsState()
    val maxSteps by remember { derivedStateOf { displayLevels.maxOfOrNull { maxOf(it.bidSteps, it.askSteps) } ?: 0L } }
    val bestBidDisplayTicks by domViewModel.bestBidDisplayTicks.collectAsState()
    val bestAskDisplayTicks by domViewModel.bestAskDisplayTicks.collectAsState()

    val formatter = remember(symbolTickSize, symbolStepSize) {
        SymbolFormatter(
            tickSize = symbolTickSize ?: 0.01,
            minQty = symbolStepSize ?: 0.001
        )
    }

    val bestBidPrice = incrementalBestBid ?: 0.0
    val bestAskPrice = incrementalBestAsk ?: 0.0

    Column(modifier = modifier.fillMaxSize()) {
        DomHeader(
            domOptions = domOptions,
            symbolTickSize = symbolTickSize,
            loadedSymbols = loadedSymbols,
            onDomOptionsChanged = { newOptions -> domViewModel.updateDomOptions(newOptions) }
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface)
                .fillMaxWidth()
        ) {
            DomContent(
                levels = displayLevels,
                maxSteps = maxSteps,
                selectedPrice = selectedPrice,
                bestBidDisplayTicks = bestBidDisplayTicks,
                bestAskDisplayTicks = bestAskDisplayTicks,
                tickSize = symbolTickSize ?: 0.01,
                stepSize = symbolStepSize ?: 0.001,
                formatter = formatter,
                onPriceSelected = { price -> domViewModel.selectPrice(price) },
                modifier = Modifier.fillMaxSize()
            )
        }
        OrderPlacementPanel(
            symbol = domOptions.symbol.symbol,
            selectedPrice = selectedPrice,
            orderQuantity = orderQuantity,
            bestBidPrice = bestBidPrice,
            bestAskPrice = bestAskPrice,
            onQuantityChanged = { qty -> domViewModel.updateOrderQuantity(qty) },
            onOrderIntent = { intent -> domViewModel.handleOrderIntent(intent) },
            isTradingEnabled = isTradingEnabled,
            modifier = Modifier.fillMaxWidth().height(180.dp)
        )
    }
}

fun main() = application {
    stopKoin()
    initKoinForPreview()

    Window(
        onCloseRequest = ::exitApplication,
        title = "Nous Platform • DOM Preview",
        state = rememberWindowState(width = 300.dp, height = 800.dp)
    ) {
        KoinContext {
            TradingTerminalTheme {
                DomWindow()
            }
        }
    }
}
