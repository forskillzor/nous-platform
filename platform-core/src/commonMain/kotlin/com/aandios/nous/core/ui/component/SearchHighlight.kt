/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

/**
 * Единая подсветка совпавшего фрагмента поиска (explorer, Market Watch,
 * навигатор workspace'ов): заливка [highlightColor] 24% + accent-цвет +
 * bold. Невалидный диапазон — просто текст без подсветки.
 */
fun highlightedMatch(
    text: String,
    matchRange: IntRange?,
    highlightColor: Color,
): AnnotatedString = buildAnnotatedString {
    val range = matchRange
    if (range == null || range.isEmpty() || range.first !in text.indices) {
        append(text)
        return@buildAnnotatedString
    }
    val start = range.first.coerceIn(0, text.length)
    val endExclusive = (range.last + 1).coerceIn(start, text.length)
    append(text.substring(0, start))
    pushStyle(
        SpanStyle(
            background = highlightColor.copy(alpha = 0.24f),
            color = highlightColor,
            fontWeight = FontWeight.Bold,
        )
    )
    append(text.substring(start, endExclusive))
    pop()
    append(text.substring(endExclusive))
}
