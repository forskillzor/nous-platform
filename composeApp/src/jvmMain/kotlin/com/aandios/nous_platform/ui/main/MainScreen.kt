/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.main


import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aandios.nous.feature.chart.ui.ChartViewModel
import com.aandios.nous.feature.chart.ui.ChartWindow
import com.aandios.nous.feature.dom.domain.TradingSymbol
import com.aandios.nous.feature.dom.ui.DomViewModel
import com.aandios.nous.feature.dom.ui.DomWindow
import com.aandios.nous.feature.trades.ui.TradesViewModel
import com.aandios.nous.feature.trades.ui.TradesWindow
import com.aandios.nous.feature.localstorage.LocalStorage
import com.aandios.nous_platform.ui.components.*
import com.aandios.nous.feature.settings.SettingsWindow
import org.koin.compose.koinInject

@Composable
fun MainScreen(
    chartViewModel: ChartViewModel,
    domViewModel: DomViewModel,
    tradesViewModel: TradesViewModel,
    modifier: Modifier = Modifier,
) {
    var selectedSymbol by remember { mutableStateOf("BTCUSDT") }
    var selectedTimeframe by remember { mutableStateOf("1h") }

    // Sync from ViewModel
    LaunchedEffect(Unit) {
        chartViewModel.currentSymbol.collect { selectedSymbol = it }
    }
    LaunchedEffect(Unit) {
        chartViewModel.currentTimeframe.collect { selectedTimeframe = it }
    }
    var showSettings by remember { mutableStateOf(false) }
    val storage: LocalStorage = koinInject()

    // Restore saved state on first launch
    LaunchedEffect(Unit) {
        chartViewModel.restoreState()
    }

    LaunchedEffect(selectedSymbol, selectedTimeframe) {
        chartViewModel.loadChart(selectedSymbol, selectedTimeframe)
        domViewModel.updateDomOptions(
            domViewModel.domOptions.value.copy(
                symbol = TradingSymbol.findSymbol(selectedSymbol, domViewModel.domOptions.value.provider)
                    ?: TradingSymbol(selectedSymbol, selectedSymbol, domViewModel.domOptions.value.provider)
            )
        )
        tradesViewModel.subscribeToTrades(selectedSymbol)
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {

        TerminalToolbar {
            SymbolSelector(selectedSymbol) { symbol ->
                selectedSymbol = symbol
            }

            Spacer(modifier = Modifier.weight(1f))

            TimeframeSelector(selectedTimeframe) { timeframe ->
                selectedTimeframe = timeframe
            }

            TerminalBadge(
                text = "BINANCE", modifier = Modifier.padding(start = 8.dp)
            )
            Spacer(Modifier.width(4.dp))
            TerminalButton(
                onClick = { showSettings = true },
                isActive = false,
                modifier = Modifier.height(32.dp)
            ) {
                Text("⚙", style = MaterialTheme.typography.labelMedium)
            }
        }

        TerminalCard(
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                ChartWindow(
                    chartViewModel = chartViewModel,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                )

                // DOM
                DomWindow(
                    domViewModel = domViewModel,
                    modifier = Modifier.width(300.dp)
                )

                // Trades
                TradesWindow(
                    tradesViewModel = tradesViewModel,
                    modifier = Modifier
                        .width(250.dp)
                )
            }
        }
        TerminalStatusBar(
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showSettings) {
        SettingsWindow(storage = storage, onClose = { showSettings = false })
    }
}

@Composable
private fun SymbolSelector(
    selectedSymbol: String, onSymbolSelected: (String) -> Unit
) {
    val symbols = listOf("BTCUSDT", "ETHUSDT", "LTCUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT")

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        symbols.forEach { symbol ->
            TerminalButton(
                onClick = { onSymbolSelected(symbol) },
                isActive = symbol == selectedSymbol,
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = symbol.replace("USDT", ""), style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun TimeframeSelector(
    selectedTimeframe: String, onTimeframeSelected: (String) -> Unit
) {
    val timeframes = listOf("1m", "5m", "15m", "30m", "1h", "4h", "1d", "1w")

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        timeframes.forEach { tf ->
            TerminalButton(
                onClick = { onTimeframeSelected(tf) },
                isActive = tf == selectedTimeframe,
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = tf, style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
