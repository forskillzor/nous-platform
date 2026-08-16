/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart2

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.feature.chart.model.ChartLayout
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.rendering.drawChart
import com.aandios.nous.feature.chart.rendering.drawCrosshair
import com.aandios.nous.feature.chart.rendering.drawFootprintChart
import com.aandios.nous.feature.chart.ui.ChartConfig
import com.aandios.nous.feature.dom.domain.model.AggregationLevel

/**
 * Pluggable chart renderer contract — like RecyclerView.ViewHolder.
 *
 * Usage:
 * ```kotlin
 * Chart(
 *     candles = candles,
 *     renderers = listOf(
 *         CandleStickRenderer("main"),
 *         VolumeRenderer("volume", height = 80.dp),
 *         IndicatorRenderer("sma9", height = 60.dp) { drawSma(...) }
 *     )
 * )
 * ```
 *
 * NOTE: Full pluggable rendering requires refactoring the internal render functions
 * to use unified [ChartLayout] and [PriceRange] parameters. This interface defines
 * the target contract. Current implementation delegates to existing [CandleStickChart]
 * and [FootprintChart] composables for backward compatibility.
 */
interface ChartRenderer {
    val id: String
    val height: Dp

    fun DrawScope.render(
        candles: List<Candle>,
        footprint: List<FootprintCandle>?,
        priceRange: PriceRange,
        layout: ChartLayout,
        config: ChartConfig
    )
}

/**
 * CandleStick renderer.
 * When plugged into a unified Chart, this renders OHLCV candles.
 */
class CandleStickRenderer(
    override val id: String = "candlestick",
    override val height: Dp = Dp.Unspecified
) : ChartRenderer {
    override fun DrawScope.render(
        candles: List<Candle>,
        footprint: List<FootprintCandle>?,
        priceRange: PriceRange,
        layout: ChartLayout,
        config: ChartConfig
    ) { /* Delegates to Chart composable which calls drawChart internally */ }
}

/**
 * Footprint renderer with configurable aggregation.
 */
class FootprintRendererV2(
    override val id: String = "footprint",
    override val height: Dp = Dp.Unspecified,
    val aggregationLevel: AggregationLevel = AggregationLevel.TenTick,
    val tickSize: Double = 0.01
) : ChartRenderer {
    override fun DrawScope.render(
        candles: List<Candle>,
        footprint: List<FootprintCandle>?,
        priceRange: PriceRange,
        layout: ChartLayout,
        config: ChartConfig
    ) { /* Delegates to Chart composable which calls drawFootprintChart internally */ }
}

/**
 * Bar renderer (OHLC bars instead of candles).
 */
class BarRenderer(
    override val id: String = "bars",
    override val height: Dp = Dp.Unspecified
) : ChartRenderer {
    override fun DrawScope.render(
        candles: List<Candle>,
        footprint: List<FootprintCandle>?,
        priceRange: PriceRange,
        layout: ChartLayout,
        config: ChartConfig
    ) { /* Bar rendering — future implementation */ }
}

/**
 * Crosshair overlay — interaction layer, not data.
 */
interface ChartOverlay {
    val id: String

    fun DrawScope.renderOverlay(
        mousePosition: Offset?,
        candles: List<Candle>,
        priceRange: PriceRange,
        layout: ChartLayout,
        config: ChartConfig
    )
}

class CrosshairOverlay(
    override val id: String = "crosshair"
) : ChartOverlay {
    override fun DrawScope.renderOverlay(
        mousePosition: Offset?,
        candles: List<Candle>,
        priceRange: PriceRange,
        layout: ChartLayout,
        config: ChartConfig
    ) { /* Delegates to Chart composable which calls drawCrosshair internally */ }
}
