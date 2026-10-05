/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.aandios.nous.feature.trading.ui.TradingViewModel

/**
 * Контент портфеля в нижнем drawer — реальные данные из [TradingViewModel]
 * активного провайдера (моки удалены). Табы рисуются в общей строке
 * заголовка нижней панели ([BottomToolPanel]).
 */
@Composable
fun PortfolioPanel(
    tradingViewModel: TradingViewModel,
    selectedTab: PortfolioTab,
    modifier: Modifier = Modifier.Companion
) {
    val positions by tradingViewModel.positions.collectAsState()
    val orders by tradingViewModel.openOrders.collectAsState()
    val balances by tradingViewModel.balances.collectAsState()
    val history by tradingViewModel.tradeHistory.collectAsState()

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        when (selectedTab) {
            PortfolioTab.POSITIONS -> {
                if (positions.isEmpty()) {
                    EmptyPortfolio("No open positions")
                } else {
                    PositionsList(positions)
                }
            }
            PortfolioTab.ORDERS -> {
                if (orders.isEmpty()) {
                    EmptyPortfolio("No open orders")
                } else {
                    OrdersList(orders)
                }
            }
            PortfolioTab.BALANCE -> {
                if (balances.isEmpty()) {
                    EmptyPortfolio("No balances")
                } else {
                    BalanceList(balances)
                }
            }
            PortfolioTab.STATS -> TradingStats()
        }
    }
}

@Composable
private fun EmptyPortfolio(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}
