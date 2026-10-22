/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator

/** Values aligned to the source candle list; warm-up entries are [Float.NaN]. */
data class IndicatorLine(
    val id: String,
    val label: String,
    val values: FloatArray,
    val style: IndicatorLineStyle = IndicatorLineStyle.LINE,
    val colorRole: IndicatorColorRole = IndicatorColorRole.PRIMARY,
    val histogramColor: HistogramColorMode = HistogramColorMode.SIGN,
    /** User-selected ARGB override; `null` keeps the theme [colorRole]. */
    val colorArgb: Long? = null,
)

/** Auto or fixed scale for a bottom-pane indicator. */
data class IndicatorValueRange(
    val min: Float,
    val max: Float,
    val fixed: Boolean = false,
)

/** Horizontal reference line (RSI 30/70, Stochastic 20/80, ADX 25). */
data class IndicatorLevel(
    val value: Float,
    val label: String? = null,
)

/** Band fill between two named lines (Bollinger, Keltner). */
data class IndicatorBandFill(
    val upperLineId: String,
    val lowerLineId: String,
    val colorRole: IndicatorColorRole = IndicatorColorRole.PRIMARY,
)

/** Computed values for one [IndicatorInstance]. */
data class IndicatorOutput(
    val instanceId: String,
    val type: IndicatorType,
    val lines: List<IndicatorLine>,
    val range: IndicatorValueRange = IndicatorValueRange(0f, 1f),
    val levels: List<IndicatorLevel> = emptyList(),
    val zeroLine: Boolean = false,
    val bandFill: IndicatorBandFill? = null,
)
