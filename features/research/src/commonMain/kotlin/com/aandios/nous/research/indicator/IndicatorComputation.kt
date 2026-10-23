/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator

/**
 * Raw calculator output keyed by [IndicatorLineSpec.id]. [range] is an optional
 * dynamic range (for example MACD's symmetric scale); otherwise the definition
 * or auto-range decides.
 */
data class IndicatorComputation(
    val lines: Map<String, FloatArray>,
    val range: IndicatorValueRange? = null,
)
