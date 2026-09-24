/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.rendering

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.api.market.model.FootprintLevel
import com.aandios.nous.core.ui.theme.ChartColors
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.feature.chart.model.CandleMetrics
import com.aandios.nous.feature.chart.model.ChartLayout
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.ui.ChartConfig
import com.aandios.nous.feature.chart.utils.calculateCandleMetrics
import com.aandios.nous.feature.chart.utils.priceFromY
import com.aandios.nous.feature.chart.utils.priceToY
import com.aandios.nous.feature.dom.domain.model.AggregationLevel
import kotlin.math.abs
import kotlin.math.min

fun DrawScope.drawFootprintChart(
    candles: List<FootprintCandle>,
    priceRange: PriceRange,
    config: ChartConfig,
    chartArea: Rect,
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
        drawGrid(config, chartArea.width, chartArea.height)

        val candleMetrics = calculateCandleMetrics(zoomLevel)
        val fpWidth = candleMetrics.width
        val fpSpacing = candleMetrics.spacing
        val totalW = fpWidth + fpSpacing

        // Viewport max volume for gradient normalization
        var viewportMaxVol = 0f
        for (i in visibleStartIndex until visibleEndIndex) {
            if (i in candles.indices) {
                val v = candles[i].maxVolume
                if (v > viewportMaxVol) viewportMaxVol = v
            }
        }
        if (viewportMaxVol <= 0f) viewportMaxVol = 1f

        for (i in visibleStartIndex until visibleEndIndex) {
            if (i in candles.indices) {
                val x = i * totalW - scrollOffset + fpWidth / 2
                drawFootprintCandle(
                    candle = candles[i],
                    centerX = x,
                    priceRange = priceRange,
                    metrics = CandleMetrics(fpWidth * 2f, fpSpacing), // 2x wider
                    config = config,
                    chartHeight = chartArea.height,
                    viewportMaxVol = viewportMaxVol,
                    tickSize = config.footprintConfig.tickSize
                )
            }
        }
    }
}

fun DrawScope.drawFootprintCandle(
    candle: FootprintCandle,
    centerX: Float,
    priceRange: PriceRange,
    metrics: CandleMetrics,
    config: ChartConfig,
    chartHeight: Float,
    viewportMaxVol: Float = 1f,
    tickSize: Double = 0.01
) {
    val levels = if (config.footprintConfig.aggregationLevel != AggregationLevel.BaseTick && tickSize > 0.0) {
        aggregateLevels(candle.levels, config.footprintConfig.aggregationLevel, tickSize)
    } else {
        candle.levels
    }
    if (levels.isEmpty()) return

    val halfWidth = metrics.width / 2
    val maxBarWidth = halfWidth
    val minAlpha = 0.12f

    for (i in levels.indices) {
        val level = levels[i]
        val priceY = priceToY(level.priceFloat, priceRange, chartHeight)
        val nextPrice = if (i < levels.size - 1) {
            levels[i + 1].priceFloat
        } else {
            level.priceFloat - (levels.getOrNull(levels.size - 2)?.priceFloat?.let {
                levels.last().priceFloat - it
            } ?: 1f)
        }
        val nextPriceY = priceToY(nextPrice, priceRange, chartHeight)
        val levelHeight = abs(nextPriceY - priceY).coerceAtLeast(1f)
        val topY = minOf(priceY, nextPriceY)

        val bidWidth = (level.bidVolumeFloat / viewportMaxVol * maxBarWidth).coerceAtLeast(if (level.bidVolumeFloat > 0f) 1f else 0f)
        val askWidth = (level.askVolumeFloat / viewportMaxVol * maxBarWidth).coerceAtLeast(if (level.askVolumeFloat > 0f) 1f else 0f)

        if (askWidth > 0f) {
            val alpha = (minAlpha + (1f - minAlpha) * (level.askVolumeFloat / viewportMaxVol)).coerceIn(minAlpha, 1f)
            drawRect(
                color = ChartColors.bearish.copy(alpha = alpha),
                topLeft = Offset(centerX - halfWidth + (maxBarWidth - askWidth), topY),
                size = Size(askWidth, levelHeight)
            )
        }
        if (bidWidth > 0f) {
            val alpha = (minAlpha + (1f - minAlpha) * (level.bidVolumeFloat / viewportMaxVol)).coerceIn(minAlpha, 1f)
            drawRect(
                color = ChartColors.bullish.copy(alpha = alpha),
                topLeft = Offset(centerX + halfWidth - maxBarWidth, topY),
                size = Size(bidWidth, levelHeight)
            )
        }
    }
}

fun DrawScope.drawCrosshairForFootprint(
    mousePosition: Offset,
    candles: List<FootprintCandle>,
    priceRange: PriceRange,
    config: ChartConfig,
    chartLayout: ChartLayout,
    textMeasurer: TextMeasurer,
    scrollOffset: Float = 0f,
    zoomLevel: Float = 1f,
) {
    val candleMetrics = calculateCandleMetrics(zoomLevel)
    val totalW = candleMetrics.width + candleMetrics.spacing
    val virtualX = mousePosition.x + scrollOffset
    val candleIndex = (virtualX / totalW).toInt().coerceIn(0, candles.size - 1)
    val candle = candles.getOrNull(candleIndex) ?: return
    val candleX = candleIndex * totalW - scrollOffset + candleMetrics.width / 2

    val crosshairColor = config.gridColor.copy(alpha = 0.8f)

    drawLine(color = crosshairColor, start = Offset(candleX, 0f), end = Offset(candleX, chartLayout.chartMainArea.height), strokeWidth = 1f)
    drawLine(color = crosshairColor, start = Offset(0f, mousePosition.y), end = Offset(chartLayout.chartMainArea.width, mousePosition.y), strokeWidth = 1f)

    val info = buildString {
        appendLine("O: ${candle.open}")
        appendLine("H: ${candle.high}")
        appendLine("L: ${candle.low}")
        appendLine("C: ${candle.close}")
        appendLine("Ticks: ${candle.totalTicks}")
    }
    val textStyle = TextStyle(color = config.axisTextColor, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
    val layoutResult = textMeasurer.measure(AnnotatedString(info), textStyle)

    val panelX = if (candleX + layoutResult.size.width + 20f > chartLayout.chartMainArea.width) candleX - layoutResult.size.width - 10f else candleX + 10f
    val panelY = 10f
    drawRect(color = config.backgroundColor.copy(alpha = 0.85f), topLeft = Offset(panelX - 4f, panelY - 2f), size = Size(layoutResult.size.width + 8f, layoutResult.size.height + 4f))
    drawText(layoutResult, topLeft = Offset(panelX, panelY))
}

/**
 * Ctrl+hover popup: rectangle with compact bid/ask volume table for the candle under cursor.
 * When Ctrl pressed and mouse over footprint candle вЂ” shows price levels table.
 */
fun DrawScope.drawFootprintPopup(
    mousePosition: Offset,
    candles: List<FootprintCandle>,
    priceRange: PriceRange,
    config: ChartConfig,
    chartLayout: ChartLayout,
    textMeasurer: TextMeasurer,
    scrollOffset: Float = 0f,
    zoomLevel: Float = 1f,
) {
    val fontSize = 12.sp
    val candleMetrics = calculateCandleMetrics(zoomLevel)
    val totalW = candleMetrics.width + candleMetrics.spacing
    val virtualX = mousePosition.x - chartLayout.chartMainArea.left + scrollOffset
    val candleIndex = (virtualX / totalW).toInt().coerceIn(0, candles.size - 1)
    val candle = candles.getOrNull(candleIndex) ?: return
    if (candle.levels.isEmpty()) return

    val levels = if (config.footprintConfig.aggregationLevel != AggregationLevel.BaseTick && config.footprintConfig.tickSize > 0.0) {
        aggregateLevels(candle.levels, config.footprintConfig.aggregationLevel, config.footprintConfig.tickSize)
    } else {
        candle.levels
    }
    if (levels.isEmpty()) return

    // Find price level nearest to mouse Y
    val mousePrice = priceFromY(mousePosition.y - chartLayout.chartMainArea.top, priceRange, chartLayout.chartMainArea.height)
    var centerIdx = -1
    var minDist = Float.MAX_VALUE
    levels.forEachIndexed { i, level ->
        val dist = kotlin.math.abs(level.priceFloat - mousePrice)
        if (dist < minDist) { minDist = dist; centerIdx = i }
    }
    if (centerIdx < 0) centerIdx = levels.size / 2

    val visibleLevels = 22
    val halfVisible = visibleLevels / 2
    val startIdx = (centerIdx - halfVisible).coerceAtLeast(0)
    val endIdx = (centerIdx + halfVisible).coerceAtMost(levels.size)
    val viewLevels = levels.subList(startIdx, endIdx).reversed()

    val popupBg = Color(0xFF0D1117)
    val textWhite = Color(0xFFE0E0E0)
    val darkGreen = Color(0xFF1B5E20)
    val darkRed = Color(0xFF5D1A1A)

    val title = "     Price         Ask        Bid"
    val titleStyle = TextStyle(color = Color(0xFF5B9BD5), fontSize = fontSize, fontFamily = FontFamily.Monospace)
    val titleLayout = textMeasurer.measure(AnnotatedString(title), titleStyle)

    // Measure column positions: Price | Ask | Bid
    val priceX = 4f
    val askX = priceX + textMeasurer.measure(AnnotatedString("  64321.12 "), TextStyle(fontSize = fontSize, fontFamily = FontFamily.Monospace)).size.width + 4f
    val bidX = askX + textMeasurer.measure(AnnotatedString(" 12345 "), TextStyle(fontSize = fontSize, fontFamily = FontFamily.Monospace)).size.width + 4f

    val fmt = SymbolFormatter.DEFAULT

    val panelW = textMeasurer.measure(AnnotatedString("  64321.12   12345   12345"), TextStyle(fontSize = fontSize, fontFamily = FontFamily.Monospace)).size.width + 50f
    val gapBetweenRows = 2f
    val rowH = 14f
    val panelH = titleLayout.size.height + viewLevels.size * rowH + (viewLevels.size - 1) * gapBetweenRows + 10f

    // Position: right of candle if space, else left
    val candleX = candleIndex * totalW - scrollOffset + candleMetrics.width / 2
    val panelX = if (candleX + panelW + 20f > chartLayout.chartMainArea.width) maxOf(candleX - panelW - 10f, 2f) else candleX + 10f
    val panelY = maxOf(mousePosition.y - chartLayout.chartMainArea.top - panelH / 2, 0f)

    // Background
    drawRect(color = popupBg, topLeft = Offset(panelX, panelY), size = Size(panelW, panelH))
    drawRect(color = Color(0xFF5B9BD5).copy(alpha = 0.3f), topLeft = Offset(panelX, panelY), size = Size(panelW, panelH), style = Stroke(1f))

    // Title
    drawText(titleLayout, topLeft = Offset(panelX + 4f, panelY + 3f))

    val maxVol = viewLevels.maxOfOrNull { maxOf(it.bidVolumeFloat, it.askVolumeFloat) } ?: 1f

    // Rows
    var yOff = panelY + titleLayout.size.height + 5f
    viewLevels.forEachIndexed { i, level ->
        val originalIdx = viewLevels.size - 1 - i + startIdx
        val isCurrent = (originalIdx == centerIdx)

        if (isCurrent) {
            drawRect(color = Color.White.copy(alpha = 0.06f), topLeft = Offset(panelX + 2f, yOff - 1f), size = Size(panelW - 4f, rowH + 2f))
        }

        // Price text
        val priceText = fmt.formatPrice(level.priceFloat)
        val priceLayout = textMeasurer.measure(AnnotatedString(priceText), TextStyle(color = textWhite, fontSize = fontSize, fontFamily = FontFamily.Monospace))
        drawText(priceLayout, topLeft = Offset(panelX + priceX, yOff))

        // Ask volume bar + text (left column after price)
        val askVol = level.askVolumeFloat
        val askBarW = (askVol / maxVol * 30f).coerceAtLeast(if (askVol > 0f) 2f else 0f)
        val askText = fmt.formatVolume(askVol)
        val askLayout = textMeasurer.measure(AnnotatedString(askText), TextStyle(color = textWhite, fontSize = fontSize, fontFamily = FontFamily.Monospace))
        val askTextX = panelX + askX
        if (askBarW > 0f) drawRect(color = darkRed, topLeft = Offset(askTextX + askLayout.size.width + 2f, yOff + 2f), size = Size(askBarW, maxOf(rowH - 4f, 1f)))
        drawText(askLayout, topLeft = Offset(askTextX, yOff))

        // Bid volume bar + text (right column after ask)
        val bidVol = level.bidVolumeFloat
        val bidBarW = (bidVol / maxVol * 30f).coerceAtLeast(if (bidVol > 0f) 2f else 0f)
        val bidText = fmt.formatVolume(bidVol)
        val bidLayout = textMeasurer.measure(AnnotatedString(bidText), TextStyle(color = textWhite, fontSize = fontSize, fontFamily = FontFamily.Monospace))
        val bidTextX = panelX + bidX
        if (bidBarW > 0f) drawRect(color = darkGreen, topLeft = Offset(bidTextX + bidLayout.size.width + 2f, yOff + 2f), size = Size(bidBarW, maxOf(rowH - 4f, 1f)))
        drawText(bidLayout, topLeft = Offset(bidTextX, yOff))

        yOff += rowH + gapBetweenRows
    }
}

fun aggregateLevels(levels: List<FootprintLevel>, aggLevel: AggregationLevel, tickSize: Double): List<FootprintLevel> {
    if (levels.isEmpty() || tickSize <= 0.0 || aggLevel == AggregationLevel.BaseTick) return levels

    data class Accumulator(var bidVol: Float = 0f, var askVol: Float = 0f, var bidCnt: Int = 0, var askCnt: Int = 0)

    val grouped = linkedMapOf<String, Accumulator>()
    for (level in levels) {
        val key = aggLevel.aggregationKey(level.price, tickSize)
        val acc = grouped.getOrPut(key) { Accumulator() }
        acc.bidVol += level.bidVolumeFloat
        acc.askVol += level.askVolumeFloat
        acc.bidCnt += level.bidCount
        acc.askCnt += level.askCount
    }

    return grouped.map { (price, acc) ->
        FootprintLevel(
            price = price,
            bidVolume = acc.bidVol.toString(),
            askVolume = acc.askVol.toString(),
            bidCount = acc.bidCnt,
            askCount = acc.askCnt
        )
    }
}
