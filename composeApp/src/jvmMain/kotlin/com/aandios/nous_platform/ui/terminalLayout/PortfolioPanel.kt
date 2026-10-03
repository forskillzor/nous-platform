/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.aandios.nous.api.market.model.trading.TradeSide

/**
 * Контент портфеля. Табы (POSITIONS/ORDERS/BALANCE/STATS) рисуются
 * в общей строке заголовка нижней панели ([BottomToolPanel]) — экономия высоты.
 */
@Composable
fun PortfolioPanel(
    selectedTab: PortfolioTab,
    modifier: Modifier = Modifier.Companion
) {
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
        when (selectedTab) {
            PortfolioTab.POSITIONS -> PositionsList(positions)
            PortfolioTab.ORDERS -> OrdersList(orders)
            PortfolioTab.BALANCE -> BalanceList(balances)
            PortfolioTab.STATS -> TradingStats()
        }
    }
}
