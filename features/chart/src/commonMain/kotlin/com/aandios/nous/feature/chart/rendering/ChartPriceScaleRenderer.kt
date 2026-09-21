/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.rendering

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.sp
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.ui.ChartConfig
import com.aandios.nous.feature.chart.utils.formatPrice
import com.aandios.nous.feature.chart.utils.generatePriceLevels
import com.aandios.nous.feature.chart.utils.priceToY
import kotlin.math.abs

/**
 * Рисует шкалу цен справа от графика.
 */
fun DrawScope.drawPriceScale(
    priceRange: PriceRange,
    config: ChartConfig,
    priceScaleArea: androidx.compose.ui.geometry.Rect,
    currentPrice: Float?,
    textMeasurer: TextMeasurer
) {
    withTransform({
        translate(left = priceScaleArea.left, top = priceScaleArea.top)
        clipRect(0f, 0f, priceScaleArea.width, priceScaleArea.height)
    }) {
        val numberOfLevels = 8

        // Генерируем уровни цен
        val priceLevels = generatePriceLevels(
            min = priceRange.visibleMin,
            max = priceRange.visibleMax,
            count = numberOfLevels
        )

        // Сначала рисуем обычные уровни цен
        priceLevels.forEach { price ->
            val y = priceToY(price, priceRange, priceScaleArea.height)

            // Пропускаем текущую цену - ее нарисуем отдельно
            val isCurrentPrice = currentPrice != null &&
                    abs(price - currentPrice) / priceRange.range < 0.001

            if (!isCurrentPrice) {
                drawPriceLevel(
                    price = price,
                    y = y,
                    config = config,
                    priceScaleWidth = priceScaleArea.width,
                    textMeasurer = textMeasurer
                )
            }
        }

        // Затем рисуем badge текущей цены поверх всех остальных
        if (currentPrice != null) {
            val y = priceToY(currentPrice, priceRange, priceScaleArea.height)
            drawCurrentPriceBadge(
                price = currentPrice,
                y = y,
                priceScaleWidth = priceScaleArea.width,
                textMeasurer = textMeasurer,
                config = config
            )
        }
    }
}

/**
 * Рисует badge текущей цены на шкале цен.
 */
fun DrawScope.drawCurrentPriceBadge(
    price: Float,
    y: Float,
    priceScaleWidth: Float,
    textMeasurer: TextMeasurer,
    config: ChartConfig
) {
    val priceText = formatPrice(price, config.priceFormatter)

    val textStyle = TextStyle(
        color = Color.Green,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
    )

    val textLayoutResult = textMeasurer.measure(
        text = AnnotatedString(priceText),
        style = textStyle
    )

    val textWidth = textLayoutResult.size.width
    val textHeight = textLayoutResult.size.height

    // Размеры badge с паддингами
    val padding = 4f
    val badgeWidth = textWidth + padding * 2
    val badgeHeight = textHeight + padding * 2

    // Позиция badge - выравниваем по правому краю шкалы
    val badgeLeft = priceScaleWidth - badgeWidth
    val badgeTop = y - badgeHeight / 2

    // Проверяем, чтобы badge не выходил за границы шкалы
    val adjustedBadgeTop = when {
        badgeTop < 0f -> 0f
        badgeTop + badgeHeight > size.height -> size.height - badgeHeight
        else -> badgeTop
    }

    drawRect(
        color = Color.Green.copy(alpha = 0.2f),
        topLeft = Offset(badgeLeft, adjustedBadgeTop),
        size = androidx.compose.ui.geometry.Size(badgeWidth, badgeHeight)
    )

    drawText(
        textLayoutResult = textLayoutResult,
        topLeft = Offset(badgeLeft + padding, adjustedBadgeTop + padding)
    )
}

/**
 * Рисует обычный уровень цены на шкале.
 */
fun DrawScope.drawPriceLevel(
    price: Float,
    y: Float,
    config: ChartConfig,
    priceScaleWidth: Float,
    textMeasurer: TextMeasurer
) {
    val priceText = formatPrice(price, config.priceFormatter)
    val textStyle = TextStyle(
        color = config.axisTextColor,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Right
    )

    val textLayoutResult = textMeasurer.measure(
        text = AnnotatedString(priceText),
        style = textStyle
    )

    drawText(
        textLayoutResult = textLayoutResult,
        topLeft = Offset(priceScaleWidth - textLayoutResult.size.width - 4f,
            y - textLayoutResult.size.height / 2)
    )
}
