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
) {
    // Проверяем находится ли курсор в области графика (без шкалы времени)
    if (mousePosition.x < chartLayout.chartMainArea.left ||
        mousePosition.x > chartLayout.chartMainArea.right ||
        mousePosition.y < chartLayout.chartMainArea.top ||
        mousePosition.y > chartLayout.chartMainArea.bottom) {
        return // Курсор вне области графика
    }

    // 1. Вертикальная линия через весь график
    drawLine(
        color = Color.White.copy(alpha = 0.3f),
        start = Offset(mousePosition.x, chartLayout.chartMainArea.top),
        end = Offset(mousePosition.x, chartLayout.chartMainArea.bottom),
        strokeWidth = 1f
    )

    drawLine(
        color = Color.White.copy(alpha = 0.3f),
        start = Offset(chartLayout.chartMainArea.left, mousePosition.y),
        end = Offset(chartLayout.chartMainArea.right, mousePosition.y),
        strokeWidth = 1f
    )

    // 3. Находим ближайшую свечу к позиции курсора по X
    val candleIndex = findNearestCandleIndex(
        mouseX = mousePosition.x,
        candles = candles,
        scrollOffset = scrollOffset,
        zoomLevel = zoomLevel,
    )

    // 4. Если нашли свечу, показываем информацию о ней
    if (candleIndex in candles.indices) {
        val candle = candles[candleIndex]

        // Рассчитываем Y для цен свечи
        fun getYForPrice(price: Float): Float {
            return chartLayout.chartMainArea.height -
                    ((price - priceRange.visibleMin) / priceRange.range) *
                    chartLayout.chartMainArea.height
        }

        // 5. Маленькие маркеры на уровнях цен свечи
        val highY = getYForPrice(candle.high)
        val lowY = getYForPrice(candle.low)
        val openY = getYForPrice(candle.open)
        val closeY = getYForPrice(candle.close)

        // Маркер на high
        drawCircle(
            color = Color.Red.copy(alpha = 0.7f),
            center = Offset(mousePosition.x, highY),
            radius = 3f
        )

        // Маркер на low
        drawCircle(
            color = Color.Green.copy(alpha = 0.7f),
            center = Offset(mousePosition.x, lowY),
            radius = 3f
        )

        // 6. Информационная панель в углу
        drawInfoPanel(
            candle = candle,
            mousePosition = mousePosition,
            chartLayout = chartLayout,
            textMeasurer = textMeasurer,
            config = config
        )

        // 7. Метка цены на оси Y
        val currentPriceAtCursor = priceFromY(
            y = mousePosition.y,
            priceRange = priceRange,
            chartHeight = chartLayout.chartMainArea.height
        )

        drawPriceLabelOnAxis(
            price = currentPriceAtCursor,
            mouseY = mousePosition.y,
            chartLayout = chartLayout,
            textMeasurer = textMeasurer,
            config = config
        )

        // 8. Метка времени на оси X
        drawTimeLabelOnAxis(
            candle = candle,
            mouseX = mousePosition.x,
            chartLayout = chartLayout,
            textMeasurer = textMeasurer,
            config = config
        )
    }
}

/**
 * Рисует информационную панель с данными свечи.
 */
private fun DrawScope.drawInfoPanel(
    candle: Candle,
    mousePosition: Offset,
    chartLayout: ChartLayout,
    textMeasurer: TextMeasurer,
    config: ChartConfig
) {
    val panelWidth = 120f
    val panelHeight = 80f

    // Позиция панели (правый верхний угол)
    val panelLeft = mousePosition.x + 10f
    val panelTop = mousePosition.y + 10f

    // Проверяем чтобы панель не выходила за границы
    val adjustedLeft = if (panelLeft + panelWidth > chartLayout.chartMainArea.right) {
        mousePosition.x - panelWidth - 10f
    } else {
        panelLeft
    }

    val adjustedTop = if (panelTop + panelHeight > chartLayout.chartMainArea.bottom) {
        mousePosition.y - panelHeight - 10f
    } else {
        panelTop
    }

    // Фон панели
    drawRect(
        color = Color.Black.copy(alpha = 0.8f),
        topLeft = Offset(adjustedLeft, adjustedTop),
        size = Size(panelWidth, panelHeight)
    )

    // Время свечи
    val timeText = "Time: ${formatTime(candle.timestamp)}"
    drawTextLine(
        text = timeText,
        x = adjustedLeft + 4f,
        y = adjustedTop + 15f,
        textMeasurer = textMeasurer,
        color = Color.White
    )

    // Цены
    drawTextLine(
        text = "O: ${formatPrice(candle.open, config.priceFormatter)}",
        x = adjustedLeft + 4f,
        y = adjustedTop + 30f,
        textMeasurer = textMeasurer,
        color = Color.White
    )

    drawTextLine(
        text = "H: ${formatPrice(candle.high, config.priceFormatter)}",
        x = adjustedLeft + 4f,
        y = adjustedTop + 45f,
        textMeasurer = textMeasurer,
        color = if (candle.high >= candle.open) Color.Green else Color.Red
    )

    drawTextLine(
        text = "L: ${formatPrice(candle.low, config.priceFormatter)}",
        x = adjustedLeft + 4f,
        y = adjustedTop + 60f,
        textMeasurer = textMeasurer,
        color = if (candle.low <= candle.open) Color.Red else Color.Green
    )
}

/**
 * Рисует метку цены на оси Y (справа) при crosshair.
 */
private fun DrawScope.drawPriceLabelOnAxis(
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

    // Позиция на правой стороне графика
    val labelX = chartLayout.chartMainArea.right - textLayoutResult.size.width - 4f
    val labelY = mouseY - textLayoutResult.size.height / 2

    drawRect(
        color = Color.Black.copy(alpha = 0.7f),
        topLeft = Offset(labelX, labelY),
        size = Size(
            textLayoutResult.size.width.toFloat(),
            textLayoutResult.size.height.toFloat()
        )
    )

    drawText(
        textLayoutResult = textLayoutResult,
        topLeft = Offset(labelX, labelY)
    )
}

/**
 * Рисует метку времени на оси X (внизу) при crosshair.
 */
private fun DrawScope.drawTimeLabelOnAxis(
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

    // Позиция внизу графика
    val labelX = mouseX - textLayoutResult.size.width / 2
    val labelY = chartLayout.chartMainArea.bottom + 4f

    // Проверяем границы
    val adjustedX = labelX.coerceIn(
        0f,
        chartLayout.chartMainArea.right - textLayoutResult.size.width
    )

    drawRect(
        color = Color.Black.copy(alpha = 0.7f),
        topLeft = Offset(adjustedX, labelY),
        size = Size(
            textLayoutResult.size.width.toFloat(),
            textLayoutResult.size.height.toFloat()
        )
    )

    drawText(
        textLayoutResult = textLayoutResult,
        topLeft = Offset(adjustedX, labelY)
    )
}
