/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.rendering

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
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
 * Crosshair всегда включён (TradingView-стиль): две линии через область графика
 * и проекции на шкалы — badge цены на ценовой шкале (значение под курсором)
 * и badge времени на шкале времени (ближайшая свеча).
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
    /** Trading ON: горизонтальная линия красится цветом будущей стороны. */
    tradingEnabled: Boolean = false,
    /** Опорная цена (last) для определения long/short под курсором. */
    currentPrice: Float? = null,
) {
    val mainArea = chartLayout.chartMainArea

    // Проверяем находится ли курсор в области графика (без шкалы времени)
    if (mousePosition.x < mainArea.left ||
        mousePosition.x > mainArea.right ||
        mousePosition.y < mainArea.top ||
        mousePosition.y > mainArea.bottom) {
        return // Курсор вне области графика
    }

    // Цена под курсором (нужна и для бейджа, и для цвета линии в трейдинге)
    val priceAtCursor = priceFromY(
        y = mousePosition.y,
        priceRange = priceRange,
        chartHeight = mainArea.height
    )

    // Вертикальная линия через весь график
    drawLine(
        color = Color.White.copy(alpha = 0.3f),
        start = Offset(mousePosition.x, mainArea.top),
        end = Offset(mousePosition.x, mainArea.bottom),
        strokeWidth = 1f
    )

    // Горизонтальная линия через весь график.
    // Во время трейдинга она сплошная и цветом стороны, которая будет
    // размещена по клику: ниже last — Long (зелёный), выше — Short (красный).
    val horizontalColor = if (tradingEnabled) {
        val reference = currentPrice?.takeIf { it > 0f }
        when {
            reference == null -> Color.White.copy(alpha = 0.9f)
            priceAtCursor <= reference -> Color(0xFF26A69A)
            else -> Color(0xFFEF5350)
        }
    } else {
        Color.White.copy(alpha = 0.3f)
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
        color = Color.White,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
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
        color = Color.Black.copy(alpha = 0.85f),
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
        color = Color.White,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
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
        color = Color.Black.copy(alpha = 0.85f),
        topLeft = Offset(badgeLeft, badgeTop),
        size = Size(badgeWidth, badgeHeight)
    )

    drawText(
        textLayoutResult = textLayoutResult,
        topLeft = Offset(badgeLeft + padding, badgeTop + padding)
    )
}
