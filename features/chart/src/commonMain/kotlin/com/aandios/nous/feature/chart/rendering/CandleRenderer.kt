/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.rendering

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextMeasurer
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.feature.chart.model.CandleMetrics
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.ui.ChartConfig
import com.aandios.nous.feature.chart.utils.calculateCandleMetrics
import com.aandios.nous.feature.chart.utils.priceToY
import kotlin.math.abs

/**
 * Рисует свечной график на Canvas.
 */
fun DrawScope.drawChart(
    candles: List<Candle>,
    priceRange: PriceRange,
    config: ChartConfig,
    chartArea: Rect,
    currentPrice: Float?,
    textMeasurer: TextMeasurer,
    scrollOffset: Float = 0f,
    zoomLevel: Float = 1f,
    visibleStartIndex: Int = 0,
    visibleEndIndex: Int = 0,
) {
    withTransform({
        translate(left = chartArea.left, top = chartArea.top)
        clipRect(0f, 0f, chartArea.width, chartArea.height)
    }) {
        // Сначала сетка
        drawGrid(config, chartArea.width, chartArea.height)

        // Потом свечи — только видимые
        val candleMetrics = calculateCandleMetrics(zoomLevel)
        val totalW = candleMetrics.width + candleMetrics.spacing
        for (i in visibleStartIndex until visibleEndIndex) {
            if (i in candles.indices) {
                val x = i * totalW - scrollOffset + candleMetrics.width / 2
                drawCandle(
                    candle = candles[i],
                    centerX = x,
                    priceRange = priceRange,
                    metrics = candleMetrics,
                    config = config,
                    chartHeight = chartArea.height
                )
            }
        }

        // Линия текущей цены
        if (currentPrice != null) {
            drawCurrentPriceLine(
                currentPrice = currentPrice,
                priceRange = priceRange,
                config = config,
                chartHeight = chartArea.height,
                chartWidth = chartArea.width
            )
        }
    }
}

/**
 * Рисует отдельную свечу.
 */
fun DrawScope.drawCandle(
    candle: Candle,
    centerX: Float,
    priceRange: PriceRange,
    metrics: CandleMetrics,
    config: ChartConfig,
    chartHeight: Float
) {
    val style = config.candleStyle

    // Функция для конвертации цены в Y координату
    fun priceToYLocal(price: Float): Float {
        return priceToY(price, priceRange, chartHeight)
    }

    val isBullish = candle.close >= candle.open
    val bodyColor = if (isBullish) style.bullishColor else style.bearishColor
    val shadowColor = style.shadowColor

    // Координаты для отрисовки
    val openY = priceToYLocal(candle.open)
    val closeY = priceToYLocal(candle.close)
    val highY = priceToYLocal(candle.high)
    val lowY = priceToYLocal(candle.low)

    // 1. Рисуем верхнюю тень
    if (style.showShadows) {
        val topOfBody = if (isBullish) closeY else openY
        if (highY < topOfBody) {
            drawLine(
                color = shadowColor,
                start = Offset(centerX, highY),
                end = Offset(centerX, topOfBody),
                strokeWidth = style.shadowWidth
            )
        }

        // 2. Рисуем нижнюю тень
        val bottomOfBody = if (isBullish) openY else closeY
        if (lowY > bottomOfBody) {
            drawLine(
                color = shadowColor,
                start = Offset(centerX, bottomOfBody),
                end = Offset(centerX, lowY),
                strokeWidth = style.shadowWidth
            )
        }
    }

    // 3. Рисуем тело свечи
    val bodyTop = if (isBullish) closeY else openY
    val bodyBottom = if (isBullish) openY else closeY
    val bodyHeight = abs(bodyBottom - bodyTop)

    if (bodyHeight > 0) {
        drawRect(
            color = bodyColor,
            topLeft = Offset(centerX - metrics.width / 2, bodyTop),
            size = androidx.compose.ui.geometry.Size(metrics.width, bodyHeight)
        )
    } else {
        // Для Doji свечей рисуем линию
        drawLine(
            color = bodyColor,
            start = Offset(centerX - metrics.width / 2, bodyTop),
            end = Offset(centerX + metrics.width / 2, bodyTop),
            strokeWidth = 2f
        )
    }
}

/**
 * Рисует сетку на графике.
 */
fun DrawScope.drawGrid(
    config: ChartConfig,
    width: Float,
    height: Float
) {
    if (!config.showGrid) return

    // Горизонтальные линии
    val horizontalLines = 5
    for (i in 0..horizontalLines) {
        val y = height * i / horizontalLines.toFloat()

        drawLine(
            color = config.gridColor,
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 1f
        )
    }

    // Вертикальные линии
    val verticalLines = 10
    for (i in 0..verticalLines) {
        val x = width * i / verticalLines.toFloat()

        drawLine(
            color = config.gridColor,
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = 1f
        )
    }
}

/**
 * Рисует пунктирную линию текущей цены.
 */
fun DrawScope.drawCurrentPriceLine(
    currentPrice: Float,
    priceRange: PriceRange,
    config: ChartConfig,
    chartHeight: Float,
    chartWidth: Float,
    alpha: Float = 0.7f,
    strokeWidth: Float = 1f
) {
    val y = priceToY(currentPrice, priceRange, chartHeight)

    // Пунктирная линия через весь график
    drawLine(
        color = androidx.compose.ui.graphics.Color.Green.copy(alpha = alpha),
        start = Offset(0f, y),
        end = Offset(chartWidth, y),
        strokeWidth = strokeWidth,
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 0f)
    )
}
