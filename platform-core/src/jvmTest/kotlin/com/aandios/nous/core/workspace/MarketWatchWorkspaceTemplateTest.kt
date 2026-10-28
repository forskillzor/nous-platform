package com.aandios.nous.core.workspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MarketWatchWorkspaceTemplateTest {

    @Test
    fun `nous template matches the target single chart and right-side panels layout`() {
        val template = Templates.nous()
        val root = template.layout as LayoutNode.Split
        val chartAndPositions = root.children[0] as LayoutNode.Split

        assertEquals("Nous", template.group)
        assertEquals(LayoutNode.Direction.HORIZONTAL, root.direction)
        assertEquals(LayoutNode.Direction.VERTICAL, chartAndPositions.direction)
        assertEquals(0.72f, chartAndPositions.ratio)
        assertEquals(
            listOf(LayoutNode.Leaf("chart"), LayoutNode.Leaf("trading")),
            chartAndPositions.children,
        )
        assertEquals(
            listOf(LayoutNode.Leaf("dom"), LayoutNode.Leaf("trades")),
            root.children.drop(1),
        )
        assertEquals(
            listOf(PanelType.CHART, PanelType.TRADING, PanelType.DOM, PanelType.TRADES),
            template.panels.map { it.type },
        )
        assertEquals(PanelState.Trading(), template.panels.single { it.id == "trading" }.state)
        assertTrue(builtinWorkspaceTemplates().any { it.id == "builtin-nous" })
    }

    @Test
    fun `applies selected market symbol and exchange to a fresh template workspace`() {
        val template = Templates.multiChart()

        val opened = template.forMarketWatchSymbol(
            symbol = "ETHUSDT",
            providerId = "bybit-nous-0.0.1",
            providerName = "Bybit",
            templateName = "Multi-Chart",
        )

        assertNotEquals(template.id, opened.id)
        assertEquals("ETHUSDT Multi-Chart", opened.name)
        assertTrue(opened.panels.isNotEmpty())
        assertTrue(opened.panels.all { it.symbol == "ETHUSDT" })
        assertEquals(listOf("ETHUSDT"), opened.providers.single().symbols)
        assertEquals("Bybit", opened.providers.single().name)
        assertEquals("bybit-nous-0.0.1", opened.providers.single().providerId)
        assertTrue(opened.panels.all { it.providerId == "bybit-nous-0.0.1" })
        assertTrue(opened.panels.all { it.providerName == "Bybit" })
        assertEquals(template.layout, opened.layout)
    }

    @Test
    fun `applying a market symbol to the current workspace preserves id, name and layout`() {
        val workspace = Templates.nous(symbol = "BTCUSDT")

        val applied = workspace.withMarketWatchSymbol(
            symbol = "SOLUSDT",
            providerId = "mexc-nous-0.0.1",
            providerName = "MEXC",
        )

        assertEquals(workspace.id, applied.id)
        assertEquals(workspace.name, applied.name)
        assertEquals(workspace.layout, applied.layout)
        assertEquals(workspace.createdAt, applied.createdAt)
        assertTrue(applied.panels.all { it.symbol == "SOLUSDT" })
        assertEquals(listOf("SOLUSDT"), applied.providers.single().symbols)
        assertEquals("MEXC", applied.providers.single().name)
        assertEquals("mexc-nous-0.0.1", applied.providers.single().providerId)
        assertTrue(applied.panels.all { it.providerId == "mexc-nous-0.0.1" })
        assertTrue(applied.panels.all { it.providerName == "MEXC" })
        assertTrue(applied.updatedAt >= workspace.updatedAt)
    }
}
