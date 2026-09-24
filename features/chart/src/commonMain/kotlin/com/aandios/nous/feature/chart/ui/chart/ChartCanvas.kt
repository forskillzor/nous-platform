/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui.chart

import androidx.compose.ui.text.TextMeasurer
import com.aandios.nous.feature.chart.model.ChartLayout
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.ui.ChartConfig

/**
 * Контекст отрисовки для серий и оверлеев: всё, что нужно DrawScope-функции.
 * Аналог Pane + шкалы в lightweight-charts, но без мутабельной модели —
 * значения вычисляются движком один раз на кадр.
 */
class ChartCanvas(
    val layout: ChartLayout,
    val config: ChartConfig,
    val textMeasurer: TextMeasurer,
    val scrollOffset: Float,
    val zoomLevel: Float,
    val priceRange: PriceRange,
    val visibleStartIndex: Int,
    val visibleEndIndex: Int,
    val currentPrice: Float? = null,
)
