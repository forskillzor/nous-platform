/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aandios.nous.api.market.model.trading.TradeSide
//import com.aandios.nous_platform.data.api.binance.models.TradeSide

// Панель портфеля с табами
@Composable
fun PortfolioPanel(
    modifier: Modifier = Modifier.Companion
) {
    var selectedTab by remember { mutableStateOf(PortfolioTab.POSITIONS) }

    // Мок данные
    val positions = remember {
        listOf(
            MockPosition("BTCUSDT", TradeSide.BUY, 0.5, 45000.0, 46500.0, 750.0, 1.67),
            MockPosition("ETHUSDT", TradeSide.BUY, 5.0, 3200.0, 3150.0, -250.0, -1.56),
            MockPosition("SOLUSDT", TradeSide.SELL, 20.0, 110.0, 108.5, 30.0, 1.36)
        )
    }

    val orders = remember {
        listOf(
            MockOrder(
                "1",
                "BTCUSDT",
                TradeSide.BUY,
                "LIMIT",
                44000.0,
                0.1,
                0.0,
                System.currentTimeMillis() - 3600000,
                "OPEN"
            ),
            MockOrder(
                "2",
                "ETHUSDT",
                TradeSide.SELL,
                "MARKET",
                3180.0,
                2.0,
                2.0,
                System.currentTimeMillis() - 1800000,
                "FILLED"
            ),
            MockOrder(
                "3",
                "SOLUSDT",
                TradeSide.BUY,
                "LIMIT",
                105.0,
                10.0,
                0.0,
                System.currentTimeMillis() - 300000,
                "OPEN"
            )
        )
    }

    val balances = remember {
        listOf(
            MockBalance("USDT", 15000.0, 5000.0, 20000.0, 20000.0),
            MockBalance("BTC", 0.5, 0.1, 0.6, 27900.0),
            MockBalance("ETH", 2.0, 1.0, 3.0, 9450.0),
            MockBalance("SOL", 50.0, 20.0, 70.0, 7595.0)
        )
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Табы
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            modifier = Modifier.Companion.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            edgePadding = 0.dp
        ) {
            PortfolioTab.values().forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            text = tab.name,
                            color = if (selectedTab == tab)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }
        }

        Divider()

        // Контент табов
        when (selectedTab) {
            PortfolioTab.POSITIONS -> PositionsList(positions)
            PortfolioTab.ORDERS -> OrdersList(orders)
            PortfolioTab.BALANCE -> BalanceList(balances)
            PortfolioTab.STATS -> TradingStats()
        }
    }
}