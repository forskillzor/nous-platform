/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.core.storage.StateStore
import com.aandios.nous.feature.dom.domain.model.AggregationLevel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChartStatePersistorTest {

    private class FakeStateStore : StateStore {
        val data = mutableMapOf<String, String>()
        override suspend fun getString(key: String): String? = data[key]
        override suspend fun putString(key: String, value: String) {
            data[key] = value
        }
    }

    @Test
    fun `save then restore returns the same values`() = runTest {
        val store = FakeStateStore()
        val persistor = ChartStatePersistor(store)

        persistor.save(
            symbol = "ETHUSDT",
            timeframe = "15m",
            chartMode = ChartMode.FOOTPRINT,
            fpAggregation = AggregationLevel.TenTick,
            providerId = "mexc-nous-0.0.1",
        )

        val restored = persistor.restore()
        assertEquals("ETHUSDT", restored.symbol)
        assertEquals("15m", restored.timeframe)
        assertEquals(ChartMode.FOOTPRINT, restored.chartMode)
        assertEquals(AggregationLevel.TenTick, restored.fpAggregation)
        assertEquals("mexc-nous-0.0.1", restored.providerId)
    }

    @Test
    fun `restore from empty store returns nulls`() = runTest {
        val persistor = ChartStatePersistor(FakeStateStore())

        val restored = persistor.restore()

        assertNull(restored.symbol)
        assertNull(restored.timeframe)
        assertNull(restored.chartMode)
        assertNull(restored.fpAggregation)
    }

    @Test
    fun `restore falls back to CANDLESTICK for invalid mode`() = runTest {
        val store = FakeStateStore()
        store.data[ChartStatePersistor.KEY_CHART_MODE] = "NOT_A_MODE"
        val persistor = ChartStatePersistor(store)

        assertEquals(ChartMode.CANDLESTICK, persistor.restore().chartMode)
    }

    @Test
    fun `restore falls back to BaseTick for invalid aggregation`() = runTest {
        val store = FakeStateStore()
        store.data[ChartStatePersistor.KEY_FP_AGGREGATION] = "NOT_AN_AGGREGATION"
        val persistor = ChartStatePersistor(store)

        assertEquals(AggregationLevel.BaseTick, persistor.restore().fpAggregation)
    }

    @Test
    fun `save uses legacy storage keys`() = runTest {
        val store = FakeStateStore()
        val persistor = ChartStatePersistor(store)

        persistor.save("BTCUSDT", "1h", ChartMode.CANDLESTICK, AggregationLevel.HundredTick, "binance-nous-0.0.1")

        assertEquals("BTCUSDT", store.data["chart_symbol"])
        assertEquals("1h", store.data["chart_timeframe"])
        assertEquals("CANDLESTICK", store.data["chart_mode"])
        assertEquals("HundredTick", store.data["fp_aggregation"])
        assertEquals("binance-nous-0.0.1", store.data["chart_provider_id"])
    }

    @Test
    fun `restore accepts legacy aggregation values`() = runTest {
        val store = FakeStateStore()
        store.data[ChartStatePersistor.KEY_FP_AGGREGATION] = "10x"
        val persistor = ChartStatePersistor(store)

        assertEquals(AggregationLevel.TenTick, persistor.restore().fpAggregation)
    }

    @Test
    fun `zoom roundtrip and missing key`() = runTest {
        val store = FakeStateStore()
        val persistor = ChartStatePersistor(store)

        assertNull(persistor.restoreZoom())

        persistor.saveZoom(2.5f)
        assertEquals(2.5f, persistor.restoreZoom())

        store.data[ChartStatePersistor.KEY_ZOOM] = "not-a-float"
        assertNull(persistor.restoreZoom())
    }
}
