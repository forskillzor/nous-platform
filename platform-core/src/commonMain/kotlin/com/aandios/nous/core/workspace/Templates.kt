/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

/**
 * Предопределённые шаблоны workspace'ов для быстрого старта.
 */
object Templates {

    fun scalping(symbol: String = "BTCUSDT", provider: String = "Binance"): WorkspaceConfig {
        return WorkspaceConfig(
            id = generateId(),
            name = "${symbol} Scalp 1m",
            group = "Scalping",
            providers = listOf(ProviderRef(id = "main", name = provider, symbols = listOf(symbol))),
            layout = LayoutNode.Split(
                direction = LayoutNode.Direction.HORIZONTAL,
                ratio = 0.7f,
                children = listOf(
                    LayoutNode.Split(
                        direction = LayoutNode.Direction.VERTICAL,
                        ratio = 0.75f,
                        children = listOf(
                            LayoutNode.Leaf("chart"),
                            LayoutNode.Leaf("trades")
                        )
                    ),
                    LayoutNode.Leaf("dom")
                )
            ),
            panels = listOf(
                PanelConfig("chart",  PanelType.CHART,  "main", symbol, PanelState.Chart(timeframe = "1m")),
                PanelConfig("dom",    PanelType.DOM,    "main", symbol, PanelState.Dom(depth = 20, aggregation = "10x")),
                PanelConfig("trades", PanelType.TRADES, "main", symbol, PanelState.Trades(minSize = "0.05"))
            )
        )
    }

    fun domGrid(symbols: List<String>, provider: String = "Binance"): WorkspaceConfig {
        val panels = symbols.mapIndexed { i, s ->
            PanelConfig("dom-$i", PanelType.DOM, "main", s, PanelState.Dom(depth = 10))
        }
        val cols = 4
        val rows = (symbols.size + cols - 1) / cols
        val rowNodes = (0 until rows).map { row ->
            val rowPanels = (0 until cols).mapNotNull { col ->
                val idx = row * cols + col
                if (idx < panels.size) LayoutNode.Leaf("dom-$idx") else null
            }
            LayoutNode.Split(LayoutNode.Direction.HORIZONTAL, children = rowPanels)
        }
        return WorkspaceConfig(
            id = generateId(),
            name = "${symbols.size}x DOM Grid",
            group = "Monitoring",
            providers = listOf(ProviderRef(id = "main", name = provider, symbols = symbols)),
            layout = LayoutNode.Split(LayoutNode.Direction.VERTICAL, children = rowNodes),
            panels = panels
        )
    }

    fun orderFlow(symbol: String = "BTCUSDT", provider: String = "Binance"): WorkspaceConfig {
        return WorkspaceConfig(
            id = generateId(),
            name = "$symbol Order Flow",
            group = "Analysis",
            providers = listOf(ProviderRef(id = "main", name = provider, symbols = listOf(symbol))),
            layout = LayoutNode.Split(
                direction = LayoutNode.Direction.VERTICAL,
                ratio = 0.6f,
                children = listOf(
                    LayoutNode.Leaf("chart"),
                    LayoutNode.Leaf("trades")
                )
            ),
            panels = listOf(
                PanelConfig("chart",  PanelType.CHART,  "main", symbol, PanelState.Chart(timeframe = "5m", chartMode = "FOOTPRINT")),
                PanelConfig("trades", PanelType.TRADES, "main", symbol, PanelState.Trades())
            )
        )
    }

    fun classic(symbol: String = "BTCUSDT", provider: String = "Binance"): WorkspaceConfig {
        return WorkspaceConfig(
            id = generateId(),
            name = "$symbol Classic",
            group = "Classic",
            providers = listOf(ProviderRef(id = "main", name = provider, symbols = listOf(symbol))),
            layout = LayoutNode.Split(
                direction = LayoutNode.Direction.HORIZONTAL,
                ratio = 0.6f,
                children = listOf(
                    LayoutNode.Leaf("chart"),
                    LayoutNode.Split(
                        direction = LayoutNode.Direction.HORIZONTAL,
                        ratio = 0.55f,
                        children = listOf(
                            LayoutNode.Leaf("dom"),
                            LayoutNode.Leaf("trades")
                        )
                    )
                )
            ),
            panels = listOf(
                PanelConfig("chart", PanelType.CHART, "main", symbol, PanelState.Chart(timeframe = "1h")),
                PanelConfig("dom", PanelType.DOM, "main", symbol, PanelState.Dom(depth = 10)),
                PanelConfig("trades", PanelType.TRADES, "main", symbol, PanelState.Trades())
            )
        )
    }

    fun multiChart(
        symbols: List<String> = listOf("BTCUSDT", "ETHUSDT"),
        provider: String = "Binance"
    ): WorkspaceConfig {
        val panels = symbols.mapIndexed { i, s ->
            PanelConfig(
                id = "chart-$i",
                type = PanelType.CHART,
                providerRef = "main",
                symbol = s,
                state = PanelState.Chart(timeframe = if (i == 0) "1h" else "5m")
            )
        }
        return WorkspaceConfig(
            id = generateId(),
            name = "${symbols.size} Charts",
            group = "Multi-Chart",
            providers = listOf(ProviderRef(id = "main", name = provider, symbols = symbols)),
            layout = LayoutNode.Split(
                direction = LayoutNode.Direction.HORIZONTAL,
                ratio = 0.5f,
                children = panels.map { LayoutNode.Leaf(it.id) }
            ),
            panels = panels
        )
    }

    fun empty(): WorkspaceConfig {
        return WorkspaceConfig(
            id = generateId(),
            name = "Empty Workspace",
            group = "",
            providers = listOf(ProviderRef(id = "main", name = "Binance")),
            layout = LayoutNode.Leaf("panel-0"),
            panels = listOf(
                PanelConfig("panel-0", PanelType.CHART, "main", "BTCUSDT", PanelState.Chart())
            )
        )
    }
}
