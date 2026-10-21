/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.rendering
import com.aandios.nous.core.ui.theme.TerminalFontFamily

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
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.api.market.model.FootprintLevel
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.feature.chart.model.CandleMetrics
import com.aandios.nous.feature.chart.model.ChartLayout
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.ui.ChartConfig
import com.aandios.nous.feature.chart.ui.FootprintRingDiameter
import com.aandios.nous.feature.chart.utils.calculateCandleMetrics
import com.aandios.nous.feature.chart.utils.priceFromY
import com.aandios.nous.feature.chart.utils.priceToY
import com.aandios.nous.feature.dom.domain.model.AggregationLevel
import kotlin.math.abs
import kotlin.math.min

/**
 * Масштаб Profile-баров по выбранному перцентилю объёмов видимого окна.
 *
 * Бары до [linearMax] масштабируются линейно. Всё выше [outlierThreshold]
 * рисуется полной шириной и обводится цветом своей стороны, но не участвует
 * в расчёте масштаба.
 */
data class ProfileVolumeScale(
    val linearMax: Float,
    val outlierThreshold: Float,
) {
    fun isOutlier(volume: Float): Boolean =
        volume.isFinite() && volume > outlierThreshold

    companion object {
        val DEFAULT = ProfileVolumeScale(linearMax = 1f, outlierThreshold = Float.MAX_VALUE)
    }
}

data class ProfileOutlierMarker(
    val center: Offset,
    val diameter: Float,
    val color: Color,
)

/**
 * Сортирует [values] на месте (хвост за [count] заполняется +Infinity) и
 * возвращает масштаб по [percentileFraction] (0.90 = P90, 1.0 = MAX).
 */
internal fun computeProfileVolumeScale(
    values: FloatArray,
    count: Int = values.size,
    percentileFraction: Float = 0.95f,
): ProfileVolumeScale {
    val n = count.coerceIn(0, values.size)
    if (n == 0) return ProfileVolumeScale.DEFAULT

    for (i in n until values.size) values[i] = Float.POSITIVE_INFINITY
    values.sort()

    val fraction = percentileFraction.coerceIn(0f, 1f)
    val value = percentile(values, n, fraction)
    val linearMax = if (value.isFinite() && value > 0f) value else 1f

    return ProfileVolumeScale(
        linearMax = linearMax,
        outlierThreshold = linearMax,
    )
}

private fun percentile(sorted: FloatArray, count: Int, fraction: Float): Float {
    if (count <= 0) return 0f
    if (count == 1) return sorted[0]
    val position = (count - 1) * fraction
    val lower = position.toInt()
    val upper = min(lower + 1, count - 1)
    val weight = position - lower
    return sorted[lower] + (sorted[upper] - sorted[lower]) * weight
}

internal fun profileBarWidth(
    volume: Float,
    scale: ProfileVolumeScale,
    maxBarWidth: Float,
): Float {
    if (!volume.isFinite() || volume <= 0f || maxBarWidth <= 0f) return 0f
    val linearMax = if (scale.linearMax > 0f) scale.linearMax else 1f
    val maxBarWidthNormalize = maxOf(1.0f, maxBarWidth)
    return (volume / linearMax * maxBarWidth).coerceIn(1.0f, maxBarWidthNormalize)
}

internal fun profileBarAlpha(
    volume: Float,
    scale: ProfileVolumeScale,
    minAlpha: Float,
): Float {
    if (!volume.isFinite() || volume <= 0f) return minAlpha
    if (scale.isOutlier(volume)) return 1f
    val linearMax = if (scale.linearMax > 0f) scale.linearMax else 1f
    val ratio = (volume / linearMax).coerceIn(0f, 1f)
    return (minAlpha + (1f - minAlpha) * ratio).coerceIn(minAlpha, 1f)
}

internal fun profileRingDiameter(
    candleWidth: Float,
    diameter: FootprintRingDiameter,
): Float = candleWidth * diameter.ratio

fun DrawScope.drawFootprintChart(
    candles: List<FootprintCandle>,
    priceRange: PriceRange,
    config: ChartConfig,
    chartArea: Rect,
    textMeasurer: TextMeasurer,
    profileVolumeScale: ProfileVolumeScale = ProfileVolumeScale.DEFAULT,
    scrollOffset: Float = 0f,
    zoomLevel: Float = 1f,
    visibleStartIndex: Int = 0,
    visibleEndIndex: Int = 0,
    aggregatedLevelsProvider: ((FootprintCandle) -> List<FootprintLevel>)? = null,
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
        val fpMetrics = CandleMetrics(fpWidth * 2f, fpSpacing) // 2x wider
        val outlierMarkers = mutableListOf<ProfileOutlierMarker>()

        for (i in visibleStartIndex until visibleEndIndex) {
            if (i in candles.indices) {
                val x = i * totalW - scrollOffset + fpWidth / 2
                outlierMarkers += drawFootprintCandle(
                    candle = candles[i],
                    centerX = x,
                    priceRange = priceRange,
                    metrics = fpMetrics,
                    config = config,
                    chartHeight = chartArea.height,
                    profileVolumeScale = profileVolumeScale,
                    tickSize = config.footprintConfig.tickSize,
                    aggregatedLevels = aggregatedLevelsProvider?.invoke(candles[i]),
                )
            }
        }

        val markerStroke = config.footprintConfig.ringWidth.width.toPx()
        for (marker in outlierMarkers) {
            drawCircle(
                color = marker.color,
                radius = marker.diameter / 2f,
                center = marker.center,
                style = Stroke(width = markerStroke),
            )
        }
    }
}

internal fun footprintLevelVisible(
    price: Float,
    nextPrice: Float,
    visibleMin: Float,
    visibleMax: Float,
): Boolean =
    price.isFinite() &&
        nextPrice.isFinite() &&
        maxOf(price, nextPrice) >= visibleMin &&
        minOf(price, nextPrice) <= visibleMax

fun DrawScope.drawFootprintCandle(
    candle: FootprintCandle,
    centerX: Float,
    priceRange: PriceRange,
    metrics: CandleMetrics,
    config: ChartConfig,
    chartHeight: Float,
    profileVolumeScale: ProfileVolumeScale = ProfileVolumeScale.DEFAULT,
    tickSize: Double = 0.01,
    aggregatedLevels: List<FootprintLevel>? = null,
): List<ProfileOutlierMarker> {
    val levels = aggregatedLevels ?: if (config.footprintConfig.aggregationLevel != AggregationLevel.BaseTick && tickSize > 0.0) {
        aggregateLevels(candle.levels, config.footprintConfig.aggregationLevel, tickSize)
    } else {
        candle.levels
    }
    if (levels.isEmpty()) return emptyList()

    val halfWidth = metrics.width / 2
    val maxBarWidth = halfWidth
    val ringDiameter = profileRingDiameter(halfWidth, config.footprintConfig.ringDiameter)
    val minAlpha = 0.12f

    val visibleMin = priceRange.visibleMin
    val visibleMax = priceRange.visibleMax
    val outlierMarkers = mutableListOf<ProfileOutlierMarker>()

    for (i in levels.indices) {
        val level = levels[i]
        val price = level.priceFloat
        val bidVolume = level.bidVolumeFloat
        val askVolume = level.askVolumeFloat
        val nextPrice = if (i < levels.size - 1) {
            levels[i + 1].priceFloat
        } else {
            price - (levels.getOrNull(levels.size - 2)?.priceFloat?.let {
                levels.last().priceFloat - it
            } ?: 1f)
        }
        if (!footprintLevelVisible(price, nextPrice, visibleMin, visibleMax)) continue
        val priceY = priceToY(price, priceRange, chartHeight)
        val nextPriceY = priceToY(nextPrice, priceRange, chartHeight)
        val levelHeight = abs(nextPriceY - priceY).coerceAtLeast(1f)
        val topY = minOf(priceY, nextPriceY)

        val bidWidth = profileBarWidth(bidVolume, profileVolumeScale, maxBarWidth)
        val askWidth = profileBarWidth(askVolume, profileVolumeScale, maxBarWidth)

        if (askWidth > 0f) {
            val alpha = profileBarAlpha(askVolume, profileVolumeScale, minAlpha)
            drawRect(
                color = config.footprintConfig.askColor.copy(alpha = alpha),
                topLeft = Offset(centerX - askWidth, topY),
                size = Size(askWidth, levelHeight)
            )
            if (profileVolumeScale.isOutlier(askVolume)) {
                outlierMarkers += ProfileOutlierMarker(
                    center = Offset(centerX - askWidth / 2f, topY + levelHeight / 2f),
                    diameter = ringDiameter,
                    color = config.footprintConfig.askColor,
                )
            }
        }
        if (bidWidth > 0f) {
            val alpha = profileBarAlpha(bidVolume, profileVolumeScale, minAlpha)
            drawRect(
                color = config.footprintConfig.bidColor.copy(alpha = alpha),
                topLeft = Offset(centerX, topY),
                size = Size(bidWidth, levelHeight)
            )
            if (profileVolumeScale.isOutlier(bidVolume)) {
                outlierMarkers += ProfileOutlierMarker(
                    center = Offset(centerX + bidWidth / 2f, topY + levelHeight / 2f),
                    diameter = ringDiameter,
                    color = config.footprintConfig.bidColor,
                )
            }
        }
    }

    return outlierMarkers
}

/**
 * Alt+hover popup: rectangle with compact bid/ask volume table for the candle under cursor.
 * When Alt pressed and mouse over footprint candle — shows price levels table.
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

    val title = "Price       Ask    Bid"
    val titleStyle = TextStyle(color = config.accentColor, fontSize = fontSize, fontFamily = TerminalFontFamily)
    val titleLayout = textMeasurer.measure(AnnotatedString(title), titleStyle)
    val rowStyle = TextStyle(color = textWhite, fontSize = fontSize, fontFamily = TerminalFontFamily)
    val measureStyle = TextStyle(fontSize = fontSize, fontFamily = TerminalFontFamily)

    // Measure column positions: Price | Ask | Bid
    val priceX = 4f
    val askX = priceX + textMeasurer.measure(AnnotatedString("  64321.12 "), measureStyle).size.width + 4f
    val bidX = askX + textMeasurer.measure(AnnotatedString(" 12345 "), measureStyle).size.width + 4f

    val fmt = config.priceFormatter

    val panelW = textMeasurer.measure(AnnotatedString("  64321.12   12345   12345"), measureStyle).size.width + 50f
    val gapBetweenRows = 2f
    val rowH = 14f
    val panelH = titleLayout.size.height + viewLevels.size * rowH + (viewLevels.size - 1) * gapBetweenRows + 10f

    // Position: right of candle if space, else left
    val candleX = candleIndex * totalW - scrollOffset + candleMetrics.width / 2
    val panelX = if (candleX + panelW + 20f > chartLayout.chartMainArea.width) maxOf(candleX - panelW - 10f, 2f) else candleX + 10f
    val panelY = maxOf(mousePosition.y - chartLayout.chartMainArea.top - panelH / 2, 0f)

    // Background
    drawRect(color = popupBg, topLeft = Offset(panelX, panelY), size = Size(panelW, panelH))
    drawRect(color = config.accentColor.copy(alpha = 0.3f), topLeft = Offset(panelX, panelY), size = Size(panelW, panelH), style = Stroke(1f))

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
        val priceLayout = textMeasurer.measure(AnnotatedString(priceText), rowStyle)
        drawText(priceLayout, topLeft = Offset(panelX + priceX, yOff))

        // Ask volume bar + text (left column after price)
        val askVol = level.askVolumeFloat
        val askBarW = (askVol / maxVol * 30f).coerceAtLeast(if (askVol > 0f) 2f else 0f)
        val askText = fmt.formatVolume(askVol)
        val askLayout = textMeasurer.measure(AnnotatedString(askText), rowStyle)
        val askTextX = panelX + askX
        if (askBarW > 0f) drawRect(color = darkRed, topLeft = Offset(askTextX + askLayout.size.width + 2f, yOff + 2f), size = Size(askBarW, maxOf(rowH - 4f, 1f)))
        drawText(askLayout, topLeft = Offset(askTextX, yOff))

        // Bid volume bar + text (right column after ask)
        val bidVol = level.bidVolumeFloat
        val bidBarW = (bidVol / maxVol * 30f).coerceAtLeast(if (bidVol > 0f) 2f else 0f)
        val bidText = fmt.formatVolume(bidVol)
        val bidLayout = textMeasurer.measure(AnnotatedString(bidText), rowStyle)
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
