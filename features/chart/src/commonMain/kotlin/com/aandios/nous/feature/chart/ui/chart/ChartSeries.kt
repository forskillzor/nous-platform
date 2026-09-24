/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui.chart

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.rendering.drawChart
import com.aandios.nous.feature.chart.rendering.drawFootprintChart
import com.aandios.nous.feature.chart.ui.ChartConfig
import com.aandios.nous.feature.chart.utils.calculatePriceRangeWithCurrentPrice

/**
 * Серия графика — единица данных + рендер, по образу ISeries в
 * lightweight-charts. Движок (ChartInteraction) знает только этот контракт,
 * поэтому свечи и footprint работают в одном движке без дублирования
 * жестов и layout.
 */
interface ChartSeries {
    /** Каркасные свечи: время, скролл, зум, crosshair, autoscale. */
    val skeleton: List<Candle>

    /** Диапазон цен по видимым свечам (autoscale). */
    fun priceRange(visibleStart: Int, visibleEnd: Int): PriceRange =
        calculatePriceRangeWithCurrentPrice(
            skeleton.subList(visibleStart, visibleEnd.coerceAtMost(skeleton.size)),
            currentPrice = null,
        )

    /** Footprint-свечи, если серия footprint (для popup/crosshair); null у свечей. */
    val footprintCandles: List<FootprintCandle>?
        get() = null

    fun draw(scope: DrawScope, canvas: ChartCanvas)
}

/** Серия японских свечей. */
class CandlestickSeries(
    override val skeleton: List<Candle>,
    private val config: ChartConfig,
) : ChartSeries {

    override fun draw(scope: DrawScope, canvas: ChartCanvas) {
        scope.drawChart(
            candles = skeleton,
            priceRange = canvas.priceRange,
            config = config,
            chartArea = canvas.layout.chartMainArea,
            currentPrice = canvas.currentPrice,
            textMeasurer = canvas.textMeasurer,
            scrollOffset = canvas.scrollOffset,
            zoomLevel = canvas.zoomLevel,
            visibleStartIndex = canvas.visibleStartIndex,
            visibleEndIndex = canvas.visibleEndIndex,
        )
    }
}

/** Серия footprint (кластерный график). */
class FootprintSeries(
    override val skeleton: List<Candle>,
    override val footprintCandles: List<FootprintCandle>,
    private val config: ChartConfig,
) : ChartSeries {

    override fun draw(scope: DrawScope, canvas: ChartCanvas) {
        scope.drawFootprintChart(
            candles = footprintCandles,
            priceRange = canvas.priceRange,
            config = config,
            chartArea = canvas.layout.chartMainArea,
            textMeasurer = canvas.textMeasurer,
            scrollOffset = canvas.scrollOffset,
            zoomLevel = canvas.zoomLevel,
            visibleStartIndex = canvas.visibleStartIndex,
            visibleEndIndex = canvas.visibleEndIndex,
        )
    }
}
