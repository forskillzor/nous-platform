/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.feature.dom.domain.model.AggregationLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartUiStateTest {

    @Test
    fun `defaults match initial chart state`() {
        val state = ChartUiState()

        assertTrue(state.chartState is ChartState.Loading)
        assertEquals("BTCUSDT", state.currentSymbol)
        assertEquals("1h", state.currentTimeframe)
        assertEquals(listOf("BTCUSDT", "ETHUSDT"), state.symbols)
        assertEquals(0, state.historyLoadCount)
        assertTrue(state.hasMoreHistory)
        assertTrue(state.footprintCandles.isEmpty())
        assertEquals(null, state.liveFootprintCandle)
        assertEquals(null, state.footprintCurrentPrice)
        assertEquals(false, state.footprintLoading)
        assertEquals(null, state.footprintError)
        assertEquals(ChartMode.CANDLESTICK, state.chartMode)
        assertTrue(state.symbolsWithFootprint.isEmpty())
        assertEquals(AggregationLevel.BaseTick, state.fpAggregation)
        assertTrue(state.hasMoreFootprintHistory)
        assertEquals(0, state.footprintHistoryLoadCount)
    }

    @Test
    fun `copy updates only targeted fields`() {
        val state = ChartUiState()
        val updated = state.copy(currentSymbol = "SOLUSDT", chartMode = ChartMode.FOOTPRINT)

        assertEquals("SOLUSDT", updated.currentSymbol)
        assertEquals(ChartMode.FOOTPRINT, updated.chartMode)
        assertEquals(state.currentTimeframe, updated.currentTimeframe)
        assertEquals(state.symbols, updated.symbols)
        assertEquals(state.chartState, updated.chartState)
    }
}
