/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.rendering

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.core.ui.format.plainDecimalFixed
import com.aandios.nous.core.ui.theme.TerminalFontFamily
import com.aandios.nous.feature.chart.ui.ChartConfig
import com.aandios.nous.feature.chart.utils.generatePriceLevels
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

fun indicatorValueAtY(
    y: Float,
    area: Rect,
    minValue: Float,
    maxValue: Float,
): Float {
    if (area.height <= 0f) return maxValue
    val span = maxValue - minValue
    val ratio = ((y - area.top) / area.height).coerceIn(0f, 1f)
    return maxValue - ratio * span
}

fun indicatorValueToY(
    value: Float,
    area: Rect,
    minValue: Float,
    maxValue: Float,
): Float {
    val span = (maxValue - minValue).takeIf { it > 0f } ?: 1f
    return area.top + (1f - (value - minValue) / span) * area.height
}

fun formatIndicatorValue(
    value: Float,
    rangeMin: Float,
    rangeMax: Float,
): String {
    if (!value.isFinite()) return "-"
    if (value == 0f) return "0"
    val span = abs(rangeMax - rangeMin)
    val magnitude = max(abs(value.toDouble()), span.toDouble())
    if (magnitude >= 1_000_000.0) {
        return SymbolFormatter.formatCompactVolume(value.toDouble())
    }
    val decimals = when {
        span >= 100f -> 0
        span >= 10f -> 1
        span >= 1f -> 2
        span >= 0.1f -> 3
        span >= 0.01f -> 4
        span >= 0.001f -> 5
        span >= 0.0001f -> 6
        else -> 8
    }
    return plainDecimalFixed(value.toDouble(), decimals)
}

/**
 * Right-side value axis for a bottom indicator pane. Mirrors the main price
 * scale: pane background, labels and the separator from the plot.
 */
fun DrawScope.drawIndicatorValueScale(
    scaleArea: Rect,
    rangeMin: Float,
    rangeMax: Float,
    config: ChartConfig,
    textMeasurer: TextMeasurer,
) {
    if (scaleArea.width <= 0f || scaleArea.height <= 0f) return
    val levelTextStyle = TextStyle(
        color = config.axisTextColor,
        fontSize = 10.sp,
        fontFamily = TerminalFontFamily,
        textAlign = TextAlign.Right,
    )
    val levelCount = (scaleArea.height / 28.dp.toPx()).roundToInt().coerceIn(2, 5)
    val levels = generatePriceLevels(
        min = minOf(rangeMin, rangeMax),
        max = maxOf(rangeMin, rangeMax),
        count = levelCount,
    )

    clipRect(scaleArea.left, scaleArea.top, scaleArea.right, scaleArea.bottom) {
        drawRect(
            color = config.backgroundColor,
            topLeft = scaleArea.topLeft,
            size = Size(scaleArea.width, scaleArea.height),
        )
        levels.forEach { value ->
            val textLayout = textMeasurer.measure(
                text = AnnotatedString(formatIndicatorValue(value, rangeMin, rangeMax)),
                style = levelTextStyle,
            )
            val y = indicatorValueToY(value, scaleArea, rangeMin, rangeMax)
            val textTop = (y - textLayout.size.height / 2f)
                .coerceIn(scaleArea.top, (scaleArea.bottom - textLayout.size.height).coerceAtLeast(scaleArea.top))
            drawText(
                textLayoutResult = textLayout,
                topLeft = Offset(
                    x = scaleArea.right - textLayout.size.width - 4f,
                    y = textTop,
                ),
            )
        }
    }

    drawLine(
        color = config.gridColor.copy(alpha = 0.5f),
        start = Offset(scaleArea.left, scaleArea.top),
        end = Offset(scaleArea.left, scaleArea.bottom),
        strokeWidth = 1f,
    )
}

/**
 * Crosshair value badge on an indicator pane's right axis.
 */
fun DrawScope.drawIndicatorValueBadge(
    value: Float,
    rangeMin: Float,
    rangeMax: Float,
    mouseY: Float,
    scaleArea: Rect,
    config: ChartConfig,
    textMeasurer: TextMeasurer,
) {
    val textLayout = textMeasurer.measure(
        text = AnnotatedString(formatIndicatorValue(value, rangeMin, rangeMax)),
        style = TextStyle(
            color = config.axisTextColor,
            fontSize = 10.sp,
            fontFamily = TerminalFontFamily,
        ),
    )
    val padding = 3f
    val badgeWidth = textLayout.size.width + padding * 2
    val badgeHeight = textLayout.size.height + padding * 2
    val badgeLeft = (scaleArea.right - badgeWidth).coerceAtLeast(scaleArea.left)
    val badgeTop = (mouseY - badgeHeight / 2f)
        .coerceIn(scaleArea.top, (scaleArea.bottom - badgeHeight).coerceAtLeast(scaleArea.top))

    drawRect(
        color = config.backgroundColor,
        topLeft = Offset(badgeLeft, badgeTop),
        size = Size(badgeWidth, badgeHeight),
    )
    drawText(
        textLayoutResult = textLayout,
        topLeft = Offset(badgeLeft + padding, badgeTop + padding),
    )
}
