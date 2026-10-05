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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.api.market.ProviderRegistry
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

/** Рекомендуемая ширина DOM-панели в workspace. */
val DomRecommendedWidth: Dp = 240.dp

@Composable
fun DomWindow(
    domViewModel: DomViewModel,
    modifier: Modifier = Modifier,
) {
    val registry: ProviderRegistry = koinInject()
    val domOptions by domViewModel.domOptions.collectAsState()
    val loadedSymbols by domViewModel.loadedSymbols.collectAsState()
    val orderQuantity by domViewModel.orderQuantity.collectAsState()
    val isTradingEnabled by domViewModel.isTradingEnabled.collectAsState()
    val symbolTickSize by domViewModel.symbolTickSize.collectAsState()
    val symbolStepSize by domViewModel.symbolStepSize.collectAsState()
    val selectedPrice by domViewModel.selectedPrice.collectAsState()

    // Одно состояние лучших цен
    val bestPrices by domViewModel.bestPrices.collectAsState()

    // Таблица уровней для лесенки (SnapshotStateMap — чтение по ключу реактивно)
    val levelsMap = domViewModel.levelsMap
    val ladderStepTicks = domViewModel.ladderStepTicks

    val formatter = remember(symbolTickSize, symbolStepSize) {
        SymbolFormatter(
            tickSize = symbolTickSize ?: 0.01,
            minQty = symbolStepSize ?: 0.001
        )
    }

    val bestBidPrice = bestPrices.bestBid ?: 0.0
    val bestAskPrice = bestPrices.bestAsk ?: 0.0

    Column(modifier = modifier.fillMaxSize()) {
        DomHeader(
            domOptions = domOptions,
            symbolTickSize = symbolTickSize,
            loadedSymbols = loadedSymbols,
            providers = registry.providers,
            onDomOptionsChanged = { newOptions -> domViewModel.updateDomOptions(newOptions) }
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface)
                .fillMaxWidth()
        ) {
            DomContent(
                levelsMap = levelsMap,
                ladderStepTicks = ladderStepTicks,
                selectedPrice = selectedPrice,
                bestBidDisplayTicks = bestPrices.bestBidDisplayTicks,
                bestAskDisplayTicks = bestPrices.bestAskDisplayTicks,
                lastPriceDisplayTicks = bestPrices.lastPriceDisplayTicks,
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
