/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trades.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.aandios.nous.core.ui.theme.TradingTerminalTheme
import com.aandios.nous.feature.trades.di.initKoinForPreview
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import org.koin.core.context.stopKoin

/**
 * Trades-панель для использования в preview (или изолированного теста).
 * Инжектит TradesViewModel через Koin и делегирует полной сигнатуре.
 */
@Composable
fun TradesWindow() {
    val tradesViewModel: TradesViewModel = koinInject()
    TradesWindow(tradesViewModel = tradesViewModel)
}

/** Рекомендуемая ширина Trades-панели в workspace (как у DOM). */
val TradesRecommendedWidth: Dp = 240.dp

/**
 * Trades-панель для использования внутри workspace-панелей (и др. композитов).
 * Принимает TradesViewModel напрямую (чтобы не плодить лишних экземпляров при factory-scope).
 */
@Composable
fun TradesWindow(
    tradesViewModel: TradesViewModel,
    modifier: Modifier = Modifier,
    currentSymbol: String = "",
    onSymbolChanged: (String) -> Unit = {},
    onMinContentWidth: ((Dp) -> Unit)? = null,
) {
    val registry: com.aandios.nous.api.market.ProviderRegistry = koinInject()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TradesWidget(
            viewModel = tradesViewModel,
            currentSymbol = currentSymbol,
            onSymbolChanged = onSymbolChanged,
            providers = registry.providers,
            onProviderChanged = { providerId -> tradesViewModel.selectProvider(providerId) },
            onMinContentWidth = onMinContentWidth,
            modifier = Modifier.fillMaxSize()
        )
    }
}

fun main() = application {
    stopKoin()
    initKoinForPreview()

    Window(
        onCloseRequest = ::exitApplication,
        title = "Nous Platform • Trades Preview",
        state = rememberWindowState(width = 400.dp, height = 600.dp)
    ) {
        KoinContext {
            TradingTerminalTheme {
                TradesWindow()
            }
        }
    }
}
