/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.rendering

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.sp

/**
 * Рисует строку текста в указанной позиции.
 */
fun DrawScope.drawTextLine(
    text: String,
    x: Float,
    y: Float,
    textMeasurer: TextMeasurer,
    color: Color
) {
    val textStyle = TextStyle(
        color = color,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace
    )

    val textLayoutResult = textMeasurer.measure(
        text = AnnotatedString(text),
        style = textStyle
    )

    drawText(
        textLayoutResult = textLayoutResult,
        topLeft = Offset(x, y)
    )
}
