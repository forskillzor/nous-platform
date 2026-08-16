/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.bidasker.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.feature.chart.footprint.FootprintApiClient
import com.aandios.nous.feature.chart.ui.chart.FootprintChart
import com.aandios.nous.feature.chart.ui.ChartConfig
import com.aandios.nous.feature.chart.ui.DefaultChartConfig
import com.aandios.nous.feature.chart.ui.FootprintConfig
import com.aandios.nous.feature.dom.domain.model.AggregationLevel
import io.ktor.client.*
import kotlinx.coroutines.launch

@Composable
fun BidaskerApp(
    httpClient: HttpClient,
    config: BidaskerConfig
) {
    val scope = rememberCoroutineScope()

    var symbol by remember { mutableStateOf(config.symbol) }
    var timeframe by remember { mutableStateOf(config.timeframe) }
    val tariff by remember { mutableStateOf(config.tariff) }
    var candles by remember { mutableStateOf<List<FootprintCandle>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var availableSymbols by remember { mutableStateOf<List<String>>(emptyList()) }

    val apiClient = remember { FootprintApiClient(httpClient, config.baseUrl) }

    val chartConfig = remember {
        DefaultChartConfig.copy(
            footprintConfig = FootprintConfig(
                aggregationLevel = AggregationLevel.TenTick,
                tickSize = 0.01
            )
        )
    }

    fun refresh() {
        loading = true
        scope.launch {
            loadCandles(apiClient, symbol, timeframe, tariff) { candles = it; loading = false }
        }
    }

    LaunchedEffect(Unit) {
        scope.launch { loadAvailableSymbols(apiClient) { availableSymbols = it } }
        refresh()
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        FootprintToolbar(
            symbol = symbol, timeframe = timeframe,
            availableSymbols = availableSymbols,
            onSymbolChange = { symbol = it; refresh() },
            onTimeframeChange = { timeframe = it; refresh() },
            tariffName = tariff.name
        )

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF4CAF50)
                )
                else -> FootprintChart(
                    completedCandles = candles,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    config = chartConfig
                )
            }
        }

        StatusBar(symbol = symbol, timeframe = timeframe, candleCount = candles.size)
    }
}
