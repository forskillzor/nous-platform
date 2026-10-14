package com.aandios.nous.core.workspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MarketWatchWorkspaceTemplateTest {

    @Test
    fun `applies selected market symbol and exchange to a fresh template workspace`() {
        val template = Templates.multiChart()

        val opened = template.forMarketWatchSymbol(
            symbol = "ETHUSDT",
            providerName = "Bybit",
            templateName = "Multi-Chart",
        )

        assertNotEquals(template.id, opened.id)
        assertEquals("ETHUSDT Multi-Chart", opened.name)
        assertTrue(opened.panels.isNotEmpty())
        assertTrue(opened.panels.all { it.symbol == "ETHUSDT" })
        assertEquals(listOf("ETHUSDT"), opened.providers.single().symbols)
        assertEquals("Bybit", opened.providers.single().name)
        assertEquals(template.layout, opened.layout)
    }
}
