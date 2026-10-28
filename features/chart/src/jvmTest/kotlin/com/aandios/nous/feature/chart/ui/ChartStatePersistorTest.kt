/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.api.market.model.ContractType
import com.aandios.nous.core.storage.StateStore
import com.aandios.nous.feature.dom.domain.model.AggregationLevel
import com.aandios.nous.research.indicator.createIndicator
import com.aandios.nous.research.indicator.IndicatorPaneLayout
import com.aandios.nous.research.indicator.IndicatorPaneState
import com.aandios.nous.research.indicator.IndicatorState
import com.aandios.nous.research.indicator.IndicatorType
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
            providerId = "mexc-nous-0.0.1",
            contractType = ContractType.COIN_M,
        )
        persistor.saveFootprint(
            fpAggregation = AggregationLevel.TenTick,
            fpProfileScale = FootprintProfileScale.P99,
            fpRingWidth = FootprintRingWidth.TWO,
            fpRingDiameter = FootprintRingDiameter.X3,
        )

        val restored = persistor.restore()
        assertEquals("ETHUSDT", restored.symbol)
        assertEquals("15m", restored.timeframe)
        assertEquals(ChartMode.FOOTPRINT, restored.chartMode)
        assertEquals(AggregationLevel.TenTick, restored.fpAggregation)
        assertEquals(FootprintProfileScale.P99, restored.fpProfileScale)
        assertEquals(FootprintRingWidth.TWO, restored.fpRingWidth)
        assertEquals(FootprintRingDiameter.X3, restored.fpRingDiameter)
        assertEquals("mexc-nous-0.0.1", restored.providerId)
        assertEquals(ContractType.COIN_M, restored.contractType)
    }

    @Test
    fun `restore from empty store returns nulls`() = runTest {
        val persistor = ChartStatePersistor(FakeStateStore())

        val restored = persistor.restore()

        assertNull(restored.symbol)
        assertNull(restored.timeframe)
        assertNull(restored.chartMode)
        assertNull(restored.fpAggregation)
        assertNull(restored.fpProfileScale)
        assertNull(restored.fpRingWidth)
        assertNull(restored.fpRingDiameter)
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
    fun `restore returns null for invalid profile settings`() = runTest {
        val store = FakeStateStore()
        store.data[ChartStatePersistor.KEY_FP_PROFILE_SCALE] = "NOT_A_SCALE"
        store.data[ChartStatePersistor.KEY_FP_RING_WIDTH] = "NOT_A_WIDTH"
        store.data[ChartStatePersistor.KEY_FP_RING_DIAMETER] = "NOT_A_DIAMETER"
        val persistor = ChartStatePersistor(store)

        val restored = persistor.restore()
        assertNull(restored.fpProfileScale)
        assertNull(restored.fpRingWidth)
        assertNull(restored.fpRingDiameter)
    }

    @Test
    fun `save with empty prefix uses base storage keys`() = runTest {
        val store = FakeStateStore()
        val persistor = ChartStatePersistor(store)

        persistor.save(
            "BTCUSDT",
            "1h",
            ChartMode.CANDLESTICK,
            "binance-nous-0.0.1",
            ContractType.USDT_M,
        )
        persistor.saveFootprint(
            AggregationLevel.HundredTick,
            FootprintProfileScale.MAX,
            FootprintRingWidth.FIVE,
            FootprintRingDiameter.X10,
        )

        assertEquals("BTCUSDT", store.data["chart_symbol"])
        assertEquals("1h", store.data["chart_timeframe"])
        assertEquals("CANDLESTICK", store.data["chart_mode"])
        assertEquals("HundredTick", store.data["fp_aggregation"])
        assertEquals("MAX", store.data["fp_profile_scale"])
        assertEquals("FIVE", store.data["fp_ring_width"])
        assertEquals("X10", store.data["fp_ring_diameter"])
        assertEquals("binance-nous-0.0.1", store.data["chart_provider_id"])
        assertEquals("USDT_M", store.data["chart_contract_type"])
    }

    @Test
    fun `panel scoped footprint settings stay isolated`() = runTest {
        val store = FakeStateStore()
        val left = ChartStatePersistor(store, "chart_ws_left_")
        val right = ChartStatePersistor(store, "chart_ws_right_")

        left.save("BTCUSDT", "1h", ChartMode.CANDLESTICK, "binance-nous-0.0.1", ContractType.USDT_M)
        left.saveFootprint(
            AggregationLevel.BaseTick, FootprintProfileScale.P90,
            FootprintRingWidth.ONE, FootprintRingDiameter.X1,
        )
        right.save("ETHUSDT", "5m", ChartMode.CANDLESTICK, "binance-nous-0.0.1", ContractType.USDT_M)
        right.saveFootprint(
            AggregationLevel.TenTick, FootprintProfileScale.MAX,
            FootprintRingWidth.FIVE, FootprintRingDiameter.X10,
        )

        val restoredLeft = left.restore()
        assertEquals(FootprintProfileScale.P90, restoredLeft.fpProfileScale)
        assertEquals(FootprintRingWidth.ONE, restoredLeft.fpRingWidth)
        assertEquals(FootprintRingDiameter.X1, restoredLeft.fpRingDiameter)
        assertEquals(AggregationLevel.BaseTick, restoredLeft.fpAggregation)

        val restoredRight = right.restore()
        assertEquals(FootprintProfileScale.MAX, restoredRight.fpProfileScale)
        assertEquals(FootprintRingWidth.FIVE, restoredRight.fpRingWidth)
        assertEquals(FootprintRingDiameter.X10, restoredRight.fpRingDiameter)
        assertEquals(AggregationLevel.TenTick, restoredRight.fpAggregation)

        assertEquals("ONE", store.data["chart_ws_left_${ChartStatePersistor.KEY_FP_RING_WIDTH}"])
        assertEquals("FIVE", store.data["chart_ws_right_${ChartStatePersistor.KEY_FP_RING_WIDTH}"])
        assertNull(store.data[ChartStatePersistor.KEY_FP_RING_WIDTH])
    }

    @Test
    fun `panel footprint settings ignore legacy global values`() = runTest {
        val store = FakeStateStore()
        store.data[ChartStatePersistor.KEY_FP_AGGREGATION] = "TenTick"
        store.data[ChartStatePersistor.KEY_FP_PROFILE_SCALE] = "P95"
        store.data[ChartStatePersistor.KEY_FP_RING_WIDTH] = "TWO_HALF"
        store.data[ChartStatePersistor.KEY_FP_RING_DIAMETER] = "X4"

        val restored = ChartStatePersistor(store, "chart_ws_legacy_").restore()

        assertNull(restored.fpAggregation)
        assertNull(restored.fpProfileScale)
        assertNull(restored.fpRingWidth)
        assertNull(restored.fpRingDiameter)
    }

    @Test
    fun `panel providers are isolated and ignore legacy global value`() = runTest {
        val store = FakeStateStore()
        store.data[ChartStatePersistor.KEY_PROVIDER_ID] = "binance-nous-0.0.1"
        val left = ChartStatePersistor(store, "chart_ws_left_")
        val right = ChartStatePersistor(store, "chart_ws_right_")

        left.save("BTCUSDT", "1h", ChartMode.CANDLESTICK, "bybit-nous-0.0.1", ContractType.USDT_M)
        right.save("ETHUSDT", "5m", ChartMode.CANDLESTICK, "mexc-nous-0.0.1", ContractType.USDT_M)

        assertEquals("bybit-nous-0.0.1", left.restore().providerId)
        assertEquals("mexc-nous-0.0.1", right.restore().providerId)
        assertNull(ChartStatePersistor(store, "chart_ws_missing_").restore().providerId)
        assertEquals("binance-nous-0.0.1", store.data["chart_provider_id"])
        assertEquals("bybit-nous-0.0.1", store.data["chart_ws_left_${ChartStatePersistor.KEY_PROVIDER_ID}"])
        assertEquals("mexc-nous-0.0.1", store.data["chart_ws_right_${ChartStatePersistor.KEY_PROVIDER_ID}"])
    }

    @Test
    fun `panel symbol timeframe mode and contract type are isolated`() = runTest {
        val store = FakeStateStore()
        val left = ChartStatePersistor(store, "chart_ws_left_")
        val right = ChartStatePersistor(store, "chart_ws_right_")

        left.save("BTCUSDT", "1h", ChartMode.CANDLESTICK, "binance-nous-0.0.1", ContractType.USDT_M)
        right.save("ETHUSDT", "5m", ChartMode.FOOTPRINT, "binance-nous-0.0.1", ContractType.COIN_M)

        val restoredLeft = left.restore()
        val restoredRight = right.restore()
        assertEquals("BTCUSDT", restoredLeft.symbol)
        assertEquals("1h", restoredLeft.timeframe)
        assertEquals(ChartMode.CANDLESTICK, restoredLeft.chartMode)
        assertEquals(ContractType.USDT_M, restoredLeft.contractType)
        assertEquals("ETHUSDT", restoredRight.symbol)
        assertEquals("5m", restoredRight.timeframe)
        assertEquals(ChartMode.FOOTPRINT, restoredRight.chartMode)
        assertEquals(ContractType.COIN_M, restoredRight.contractType)
    }

    @Test
    fun `restore accepts legacy ring diameter values`() = runTest {
        val store = FakeStateStore()
        store.data[ChartStatePersistor.KEY_FP_RING_DIAMETER] = "W500"
        assertEquals(FootprintRingDiameter.X5, ChartStatePersistor(store).restore().fpRingDiameter)

        store.data[ChartStatePersistor.KEY_FP_RING_DIAMETER] = "CANDLE"
        assertEquals(FootprintRingDiameter.X1, ChartStatePersistor(store).restore().fpRingDiameter)
    }

    @Test
    fun `restore accepts legacy aggregation values`() = runTest {
        val store = FakeStateStore()
        store.data[ChartStatePersistor.KEY_FP_AGGREGATION] = "10x"
        val persistor = ChartStatePersistor(store)

        assertEquals(AggregationLevel.TenTick, persistor.restore().fpAggregation)
    }

    @Test
    fun `indicator state roundtrips per panel`() = runTest {
        val store = FakeStateStore()
        val left = ChartStatePersistor(store, "chart_ws_left_")
        val right = ChartStatePersistor(store, "chart_ws_right_")
        val indicators = IndicatorState(
            overlays = listOf(
                createIndicator(IndicatorType.EMA, "ema-1").copy(
                    lineColors = mapOf("ema" to 0xFF00FF00L),
                ),
            ),
            panes = listOf(
                IndicatorPaneState(
                    id = "pane-rsi",
                    indicators = listOf(createIndicator(IndicatorType.RSI, "rsi-1")),
                    heightDp = 132f,
                ),
            ),
            activePaneId = "pane-rsi",
            paneLayout = IndicatorPaneLayout.STACKED,
            tabsHeightDp = 150f,
        )

        left.saveIndicators(indicators)

        assertEquals(indicators, left.restoreIndicators())
        assertEquals(indicators, left.restore().indicators)
        assertNull(right.restoreIndicators())
    }

    @Test
    fun `legacy indicator JSON gets pane layout defaults`() = runTest {
        val store = FakeStateStore()
        store.data[ChartStatePersistor.KEY_INDICATORS] =
            """{"overlays":[],"panes":[{"id":"pane-rsi","indicators":[]}],"activePaneId":"pane-rsi"}"""
        val persistor = ChartStatePersistor(store)

        val restored = persistor.restoreIndicators()

        assertEquals(IndicatorPaneLayout.TABS, restored?.paneLayout)
        assertNull(restored?.tabsHeightDp)
        assertEquals(80f, restored?.panes?.single()?.heightDp)
    }

    @Test
    fun `missing indicator state restores null`() = runTest {
        val persistor = ChartStatePersistor(FakeStateStore())

        assertNull(persistor.restoreIndicators())
        assertNull(persistor.restore().indicators)
    }

    @Test
    fun `empty indicator state stays empty instead of falling back to liquidations`() = runTest {
        val store = FakeStateStore()
        val persistor = ChartStatePersistor(store)

        persistor.saveIndicators(IndicatorState())

        assertEquals(IndicatorState(), persistor.restoreIndicators())
        assertEquals(true, store.data.containsKey(ChartStatePersistor.KEY_INDICATORS))
    }

    @Test
    fun `invalid indicator JSON restores null`() = runTest {
        val store = FakeStateStore()
        store.data[ChartStatePersistor.KEY_INDICATORS] = "{not-json"
        val persistor = ChartStatePersistor(store)

        assertNull(persistor.restoreIndicators())
        assertNull(persistor.restore().indicators)
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
