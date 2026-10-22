/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.rendering
import com.aandios.nous.core.ui.theme.TerminalFontFamily

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.feature.chart.model.ChartLayout
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.ui.ChartConfig
import com.aandios.nous.feature.chart.utils.findNearestCandleIndex
import com.aandios.nous.feature.chart.utils.formatPrice
import com.aandios.nous.feature.chart.utils.formatTime
import com.aandios.nous.feature.chart.utils.priceFromY

/**
 * Рисует перекрестие (crosshair) при наведении мыши на график.
 *
 * Crosshair всегда включён (TradingView-стиль): вертикальная линия через
 * главный график и панели индикаторов, горизонтальная — в области под курсором,
 * плюс badge значения на правой шкале и badge времени внизу.
 */
fun DrawScope.drawCrosshair(
    mousePosition: Offset,
    candles: List<Candle>,
    priceRange: PriceRange,
    config: ChartConfig,
    chartLayout: ChartLayout,
    textMeasurer: TextMeasurer,
    scrollOffset: Float = 0f,
    zoomLevel: Float = 1f,
    /** Value ranges of the visible indicator panes, same order as their areas. */
    indicatorValueRanges: List<Pair<Float, Float>?> = emptyList(),
    /** Trading ON: горизонтальная линия красится цветом будущей стороны. */
    tradingEnabled: Boolean = false,
    /** Опорная цена (last) для определения long/short под курсором. */
    currentPrice: Float? = null,
) {
    val mainArea = chartLayout.chartMainArea

    // Курсор должен быть над plot-областью главного графика или индикатора.
    if (mousePosition.x < mainArea.left || mousePosition.x > mainArea.right) return
    val inMainArea = mousePosition.y >= mainArea.top && mousePosition.y <= mainArea.bottom
    val paneIndex = chartLayout.indicatorAreas.indexOfFirst { area ->
        mousePosition.y >= area.top && mousePosition.y <= area.bottom
    }
    if (!inMainArea && paneIndex < 0) return

    val crosshairBottom = chartLayout.indicatorAreas.lastOrNull()?.bottom ?: mainArea.bottom
    drawLine(
        color = config.crosshairColor.copy(alpha = 0.7f),
        start = Offset(mousePosition.x, mainArea.top),
        end = Offset(mousePosition.x, crosshairBottom),
        strokeWidth = 1f
    )

    if (inMainArea) {
        // Цена под курсором (нужна и для бейджа, и для цвета линии в трейдинге)
        val priceAtCursor = priceFromY(
            y = mousePosition.y,
            priceRange = priceRange,
            chartHeight = mainArea.height
        )

        // Во время трейдинга линия сплошная и цветом стороны, которая будет
        // размещена по клику: ниже last — Long (зелёный), выше — Short (красный).
        val horizontalColor = if (tradingEnabled) {
            val reference = currentPrice?.takeIf { it > 0f }
            when {
                reference == null -> config.crosshairColor
                priceAtCursor <= reference -> config.candleStyle.bullishColor
                else -> config.candleStyle.bearishColor
            }
        } else {
            config.crosshairColor.copy(alpha = 0.7f)
        }
        drawLine(
            color = horizontalColor,
            start = Offset(mainArea.left, mousePosition.y),
            end = Offset(mainArea.right, mousePosition.y),
            strokeWidth = if (tradingEnabled) 1.5f else 1f
        )

        drawPriceBadgeOnScale(
            price = priceAtCursor,
            mouseY = mousePosition.y,
            chartLayout = chartLayout,
            textMeasurer = textMeasurer,
            config = config
        )
    } else {
        val paneArea = chartLayout.indicatorAreas[paneIndex]
        drawLine(
            color = config.crosshairColor.copy(alpha = 0.7f),
            start = Offset(paneArea.left, mousePosition.y),
            end = Offset(paneArea.right, mousePosition.y),
            strokeWidth = 1f
        )

        val range = indicatorValueRanges.getOrNull(paneIndex)
        if (range != null) {
            drawIndicatorValueBadge(
                value = indicatorValueAtY(
                    y = mousePosition.y,
                    area = paneArea,
                    minValue = range.first,
                    maxValue = range.second,
                ),
                rangeMin = range.first,
                rangeMax = range.second,
                mouseY = mousePosition.y,
                scaleArea = Rect(
                    left = chartLayout.priceScaleArea.left,
                    top = paneArea.top,
                    right = chartLayout.priceScaleArea.right,
                    bottom = paneArea.bottom,
                ),
                config = config,
                textMeasurer = textMeasurer,
            )
        }
    }

    // Проекция на шкалу времени: ближайшая свеча к позиции курсора по X
    val candleIndex = findNearestCandleIndex(
        mouseX = mousePosition.x,
        candles = candles,
        scrollOffset = scrollOffset,
        zoomLevel = zoomLevel,
    )

    if (candleIndex in candles.indices) {
        drawTimeBadgeOnScale(
            candle = candles[candleIndex],
            mouseX = mousePosition.x,
            chartLayout = chartLayout,
            textMeasurer = textMeasurer,
            config = config
        )
    }
}

/**
 * Рисует badge цены crosshair на ценовой шкале (справа).
 */
private fun DrawScope.drawPriceBadgeOnScale(
    price: Float,
    mouseY: Float,
    chartLayout: ChartLayout,
    textMeasurer: TextMeasurer,
    config: ChartConfig
) {
    val priceText = formatPrice(price, config.priceFormatter)

    val textStyle = TextStyle(
        color = config.axisTextColor,
        fontSize = 10.sp,
        fontFamily = TerminalFontFamily,
    )

    val textLayoutResult = textMeasurer.measure(
        text = AnnotatedString(priceText),
        style = textStyle
    )

    val padding = 3f
    val badgeWidth = textLayoutResult.size.width + padding * 2
    val badgeHeight = textLayoutResult.size.height + padding * 2

    val scale = chartLayout.priceScaleArea

    // Выравниваем по правому краю шкалы (как badge текущей цены)
    val badgeLeft = scale.right - badgeWidth
    val badgeTop = (mouseY - badgeHeight / 2).coerceIn(scale.top, scale.bottom - badgeHeight)

    drawRect(
        color = config.backgroundColor,
        topLeft = Offset(badgeLeft, badgeTop),
        size = Size(badgeWidth, badgeHeight)
    )

    drawText(
        textLayoutResult = textLayoutResult,
        topLeft = Offset(badgeLeft + padding, badgeTop + padding)
    )
}

/**
 * Рисует badge времени crosshair на шкале времени (внизу).
 */
private fun DrawScope.drawTimeBadgeOnScale(
    candle: Candle,
    mouseX: Float,
    chartLayout: ChartLayout,
    textMeasurer: TextMeasurer,
    config: ChartConfig
) {
    val timeText = formatTime(candle.timestamp)

    val textStyle = TextStyle(
        color = config.axisTextColor,
        fontSize = 10.sp,
        fontFamily = TerminalFontFamily,
    )

    val textLayoutResult = textMeasurer.measure(
        text = AnnotatedString(timeText),
        style = textStyle
    )

    val padding = 3f
    val badgeWidth = textLayoutResult.size.width + padding * 2
    val badgeHeight = textLayoutResult.size.height + padding * 2

    val scale = chartLayout.timeScaleArea

    // По центру под курсором, в пределах шкалы времени
    val badgeLeft = (mouseX - badgeWidth / 2).coerceIn(scale.left, scale.right - badgeWidth)
    val badgeTop = (scale.top + (scale.height - badgeHeight) / 2).coerceAtLeast(scale.top)

    drawRect(
        color = config.backgroundColor,
        topLeft = Offset(badgeLeft, badgeTop),
        size = Size(badgeWidth, badgeHeight)
    )

    drawText(
        textLayoutResult = textLayoutResult,
        topLeft = Offset(badgeLeft + padding, badgeTop + padding)
    )
}
