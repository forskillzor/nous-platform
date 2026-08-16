/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.rendering

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.feature.chart.ui.ChartConfig
import com.aandios.nous.feature.chart.utils.calculateCandleMetrics
import com.aandios.nous.feature.chart.utils.formatTime
import kotlin.math.max

/**
 * Рисует шкалу времени внизу графика.
 */
fun DrawScope.drawTimeScale(
    candles: List<Candle>,
    config: ChartConfig,
    timeScaleArea: androidx.compose.ui.geometry.Rect,
    textMeasurer: TextMeasurer,
    scrollOffset: Float = 0f,
    zoomLevel: Float = 1f,
) {
    if (candles.isEmpty()) return

    withTransform({
        translate(left = timeScaleArea.left, top = timeScaleArea.top)
        clipRect(0f, 0f, timeScaleArea.width, timeScaleArea.height)
    }) {
        // Фон шкалы времени
        drawRect(
            color = config.backgroundColor,
            topLeft = Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(timeScaleArea.width, timeScaleArea.height)
        )

        // Разделительная линия сверху
        drawLine(
            color = config.gridColor.copy(alpha = 0.5f),
            start = Offset(0f, 0f),
            end = Offset(timeScaleArea.width, 0f),
            strokeWidth = 1f
        )

        // Рассчитываем метрики свечей для правильного позиционирования меток времени
        val candleMetrics = calculateCandleMetrics(zoomLevel)
        val totalW = candleMetrics.width + candleMetrics.spacing

        // Определяем видимый диапазон индексов по скроллу
        val visibleStartIdx = (scrollOffset / totalW).toInt().coerceIn(0, max(0, candles.size - 1))
        val visibleEndIdx = ((scrollOffset + timeScaleArea.width) / totalW + 1).toInt().coerceIn(0, candles.size)
        val visibleCount = visibleEndIdx - visibleStartIdx

        // Выбираем шаг меток — примерно 5–7 на видимую область
        val step = (visibleCount / 6).coerceAtLeast(1)

        // Показываем метку на первой видимой свече
        val firstLabelIdx = visibleStartIdx + (step - visibleStartIdx % step) % step

        for (i in firstLabelIdx until visibleEndIdx step step) {
            if (i in candles.indices) {
                val x = i * totalW - scrollOffset + candleMetrics.width / 2

                // Форматируем время
                val timeText = formatTime(candles[i].timestamp)

                // Стиль текста для шкалы времени
                val textStyle = TextStyle(
                    color = config.axisTextColor,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )

                val textLayoutResult = textMeasurer.measure(
                    text = AnnotatedString(timeText),
                    style = textStyle
                )

                // Рисуем вертикальную черточку
                drawLine(
                    color = config.gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, 4f),
                    strokeWidth = 1f
                )

                // Рисуем текст времени
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        x - textLayoutResult.size.width / 2,
                        timeScaleArea.height - textLayoutResult.size.height - 2f
                    )
                )
            }
        }
    }
}
