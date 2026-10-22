/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.model

import androidx.compose.ui.geometry.Rect

data class ChartLayout(
    val canvasWidth: Float,
    val canvasHeight: Float,
    val priceScaleWidth: Float,
    val priceScaleArea: Rect,
    val chartPadding: Float = 8f,
    val timeScaleHeight: Float = 20f,
    val chartMainArea: Rect,
    val timeScaleArea: Rect,
    /** Tab strip above the visible indicator panes; null when there are no panes. */
    val indicatorTabsArea: Rect? = null,
    /** Per-pane title/legend rows; same order as [indicatorAreas]. */
    val indicatorHeaderAreas: List<Rect> = emptyList(),
    /** Drawable plot rectangles of the visible indicator panes. */
    val indicatorAreas: List<Rect> = emptyList(),
)
