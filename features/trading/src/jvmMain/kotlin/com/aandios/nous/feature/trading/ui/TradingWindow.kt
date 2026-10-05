/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trading.ui

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
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.core.ui.theme.TradingTerminalTheme
import com.aandios.nous.feature.trading.di.initKoinForPreview
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import org.koin.core.context.stopKoin

/** Торговая панель для использования в preview (или изолированного теста). */
@Composable
fun TradingWindow() {
    val viewModel: TradingViewModel = koinInject()
    TradingWindow(viewModel = viewModel)
}

/** Рекомендуемая ширина торговой панели в workspace (flex — ширину задаёт layout). */
val TradingRecommendedWidth: Dp = 320.dp

/**
 * Торговая панель для использования внутри workspace-панелей.
 */
@Composable
fun TradingWindow(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier,
) {
    val registry: ProviderRegistry = koinInject()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TradingPanel(viewModel = viewModel, registry = registry, modifier = Modifier.fillMaxSize())
    }
}

fun main() = application {
    stopKoin()
    initKoinForPreview()

    Window(
        onCloseRequest = ::exitApplication,
        title = "Nous Platform • Trading Preview",
        state = rememberWindowState(width = 520.dp, height = 700.dp)
    ) {
        KoinContext {
            TradingTerminalTheme {
                TradingWindow()
            }
        }
    }
}
