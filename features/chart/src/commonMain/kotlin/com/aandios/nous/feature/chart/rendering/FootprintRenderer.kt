/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.rendering

import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.text.font.FontWeight
import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.api.market.model.FootprintLevel
import com.aandios.nous.core.ui.theme.TerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily
import com.aandios.nous.feature.chart.model.CandleMetrics
import com.aandios.nous.feature.chart.model.ChartLayout
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.ui.ChartConfig
import com.aandios.nous.feature.chart.ui.FootprintRingDiameter
import com.aandios.nous.feature.chart.utils.calculateCandleMetrics
import com.aandios.nous.feature.chart.utils.priceFromY
import com.aandios.nous.feature.chart.utils.priceToY
import com.aandios.nous.feature.dom.domain.model.AggregationLevel

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
    val upper = kotlin.math.min(lower + 1, count - 1)
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
        val levelHeight = kotlin.math.abs(nextPriceY - priceY).coerceAtLeast(1f)
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
 * Alt+hover popup: mirrored footprint view for the candle under the cursor.
 * Price stays in the center; ask volume grows from the center to the left and
 * bid volume from the center to the right, matching the footprint candle look.
 * The shell is built from [TerminalDesignTokens] (panel surface/border/radius,
 * typography, spacing, selection color).
 */
fun DrawScope.drawFootprintPopup(
    mousePosition: Offset,
    candles: List<FootprintCandle>,
    priceRange: PriceRange,
    config: ChartConfig,
    chartLayout: ChartLayout,
    textMeasurer: TextMeasurer,
    tokens: TerminalDesignTokens,
    scrollOffset: Float = 0f,
    zoomLevel: Float = 1f,
) {
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

    val fmt = config.priceFormatter
    val askColor = config.footprintConfig.askColor
    val bidColor = config.footprintConfig.bidColor

    val headerStyle = TextStyle(
        color = tokens.colors.accent,
        fontSize = tokens.typography.microSize,
        letterSpacing = tokens.typography.compactLetterSpacing,
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.Medium,
    )
    val rowStyle = TextStyle(
        color = tokens.colors.axisText,
        fontSize = tokens.typography.compactSize,
        lineHeight = tokens.typography.compactLineHeight,
        fontFamily = TerminalFontFamily,
    )
    val askTextStyle = rowStyle.copy(color = askColor)
    val bidTextStyle = rowStyle.copy(color = bidColor)
    val currentPriceStyle = rowStyle.copy(color = tokens.colors.accent, fontWeight = FontWeight.Bold)

    val headerAsk = textMeasurer.measure(AnnotatedString("ASK"), headerStyle)
    val headerPrice = textMeasurer.measure(AnnotatedString("PRICE"), headerStyle)
    val headerBid = textMeasurer.measure(AnnotatedString("BID"), headerStyle)
    val headerHeight = maxOf(headerAsk.size.height, headerPrice.size.height, headerBid.size.height)

    val paddingX = tokens.spacing.sm.toPx()
    val paddingY = tokens.spacing.sm.toPx()
    val columnGap = tokens.spacing.xs.toPx()
    val rowGap = tokens.spacing.xxs.toPx()
    val headerGap = tokens.spacing.xs.toPx()
    val sampleRow = textMeasurer.measure(AnnotatedString("0"), rowStyle)
    val rowHeight = maxOf(sampleRow.size.height.toFloat(), tokens.typography.compactLineHeight.toPx())
    val rowStep = rowHeight + rowGap

    // Сколько строк реально влезает в область графика (максимум 22).
    val availableHeight = chartLayout.chartMainArea.height - paddingY * 2 - headerHeight - headerGap
    val maxRows = (availableHeight / rowStep).toInt().coerceAtLeast(1)
    val visibleLevels = minOf(22, maxRows)
    val halfVisible = visibleLevels / 2
    var startIdx = (centerIdx - halfVisible).coerceAtLeast(0)
    var endIdx = (startIdx + visibleLevels).coerceAtMost(levels.size)
    startIdx = (endIdx - visibleLevels).coerceAtLeast(0)
    val viewLevels = levels.subList(startIdx, endIdx).reversed()

    // Реальные (отформатированные) тексты — без магических sample-строк.
    val priceTexts = viewLevels.map { fmt.formatPrice(it.priceFloat) }
    val askTexts = viewLevels.map { fmt.formatVolume(it.askVolumeFloat) }
    val bidTexts = viewLevels.map { fmt.formatVolume(it.bidVolumeFloat) }
    val priceTextWidth = priceTexts.maxOfOrNull {
        textMeasurer.measure(AnnotatedString(it), rowStyle).size.width.toFloat()
    } ?: 0f
    val volumeTextWidth = maxOf(
        askTexts.maxOfOrNull {
            textMeasurer.measure(AnnotatedString(it), askTextStyle).size.width.toFloat()
        } ?: 0f,
        bidTexts.maxOfOrNull {
            textMeasurer.measure(AnnotatedString(it), bidTextStyle).size.width.toFloat()
        } ?: 0f,
    )
    val barMax = maxOf(priceTextWidth, volumeTextWidth) * 1.35f
    val priceColumnWidth = priceTextWidth + columnGap * 2
    val contentWidth = priceColumnWidth + barMax * 2
    val panelWidth = contentWidth + paddingX * 2
    val panelHeight = paddingY * 2 + headerHeight + headerGap +
        viewLevels.size * rowHeight + (viewLevels.size - 1) * rowGap

    // Position: right of candle if space, else left
    val candleX = candleIndex * totalW - scrollOffset + candleMetrics.width / 2
    val panelX = if (candleX + panelWidth + 20f > chartLayout.chartMainArea.width) {
        maxOf(candleX - panelWidth - 10f, 2f)
    } else {
        candleX + 10f
    }
    val panelY = (mousePosition.y - chartLayout.chartMainArea.top - panelHeight / 2f)
        .coerceIn(0f, (chartLayout.chartMainArea.height - panelHeight).coerceAtLeast(0f))

    val shellRadius = CornerRadius(tokens.cornerRadii.workspacePanel.toPx())
    drawRoundRect(
        color = tokens.colors.panelSurface,
        topLeft = Offset(panelX, panelY),
        size = Size(panelWidth, panelHeight),
        cornerRadius = shellRadius,
    )
    drawRoundRect(
        color = tokens.colors.panelBorder,
        topLeft = Offset(panelX, panelY),
        size = Size(panelWidth, panelHeight),
        cornerRadius = shellRadius,
        style = Stroke(tokens.dimensions.borderWidth.toPx()),
    )

    val contentLeft = panelX + paddingX
    val contentRight = panelX + panelWidth - paddingX
    val centerX = panelX + panelWidth / 2f
    val priceLeft = centerX - priceTextWidth / 2f
    val priceRight = centerX + priceTextWidth / 2f
    val askValueRight = priceLeft - columnGap
    val bidValueLeft = priceRight + columnGap

    // Header: ASK слева · PRICE по центру · BID справа
    val headerTop = panelY + paddingY
    drawText(
        textLayoutResult = headerAsk,
        topLeft = Offset(askValueRight - headerAsk.size.width, headerTop),
    )
    drawText(
        textLayoutResult = headerPrice,
        topLeft = Offset(centerX - headerPrice.size.width / 2f, headerTop),
    )
    drawText(
        textLayoutResult = headerBid,
        topLeft = Offset(bidValueLeft, headerTop),
    )
    val dividerY = headerTop + headerHeight + headerGap / 2f
    drawLine(
        color = tokens.colors.panelBorder,
        start = Offset(contentLeft, dividerY),
        end = Offset(contentRight, dividerY),
        strokeWidth = tokens.dimensions.dividerThickness.toPx(),
    )

    val maxVol = viewLevels.maxOfOrNull { maxOf(it.bidVolumeFloat, it.askVolumeFloat) }
        ?.takeIf { it > 0f } ?: 1f

    val rowRadius = CornerRadius(tokens.cornerRadii.smallCard.toPx())
    var rowTop = headerTop + headerHeight + headerGap
    viewLevels.forEachIndexed { i, level ->
        val originalIdx = viewLevels.size - 1 - i + startIdx
        val isCurrent = originalIdx == centerIdx

        if (isCurrent) {
            drawRoundRect(
                color = tokens.colors.selection,
                topLeft = Offset(contentLeft, rowTop - rowGap / 2f),
                size = Size(contentWidth, rowHeight + rowGap),
                cornerRadius = rowRadius,
            )
        }

        val askVol = level.askVolumeFloat
        val bidVol = level.bidVolumeFloat
        val askBarWidth = footprintPopupBarWidth(askVol, maxVol, barMax)
        val bidBarWidth = footprintPopupBarWidth(bidVol, maxVol, barMax)

        // Ask: гистограмма от центра влево
        if (askBarWidth > 0f) {
            drawRect(
                color = askColor.copy(alpha = 0.35f),
                topLeft = Offset(priceLeft - askBarWidth, rowTop),
                size = Size(askBarWidth, rowHeight),
            )
        }
        // Bid: гистограмма от центра вправо
        if (bidBarWidth > 0f) {
            drawRect(
                color = bidColor.copy(alpha = 0.35f),
                topLeft = Offset(priceRight, rowTop),
                size = Size(bidBarWidth, rowHeight),
            )
        }

        val priceLayout = textMeasurer.measure(
            AnnotatedString(priceTexts[i]),
            if (isCurrent) currentPriceStyle else rowStyle,
        )
        drawText(
            textLayoutResult = priceLayout,
            topLeft = Offset(
                centerX - priceLayout.size.width / 2f,
                rowTop + (rowHeight - priceLayout.size.height) / 2f,
            ),
        )

        val askLayout = textMeasurer.measure(AnnotatedString(askTexts[i]), askTextStyle)
        drawText(
            textLayoutResult = askLayout,
            topLeft = Offset(
                askValueRight - askLayout.size.width,
                rowTop + (rowHeight - askLayout.size.height) / 2f,
            ),
        )

        val bidLayout = textMeasurer.measure(AnnotatedString(bidTexts[i]), bidTextStyle)
        drawText(
            textLayoutResult = bidLayout,
            topLeft = Offset(
                bidValueLeft,
                rowTop + (rowHeight - bidLayout.size.height) / 2f,
            ),
        )

        rowTop += rowStep
    }
}

/** Ширина popup-гистограммы: линейно по объёму, максимум — [maxWidth]. */
internal fun footprintPopupBarWidth(volume: Float, maxVolume: Float, maxWidth: Float): Float {
    if (volume <= 0f || maxVolume <= 0f || maxWidth <= 0f) return 0f
    return (volume / maxVolume).coerceIn(0f, 1f) * maxWidth
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
