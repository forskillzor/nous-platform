/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator.calculators

import com.aandios.nous.research.indicator.IndicatorParamSpec

internal fun periodSpec(
    default: Int,
    key: String = "period",
    label: String = "Period",
): IndicatorParamSpec = IndicatorParamSpec(
    key = key,
    label = label,
    defaultValue = default.toDouble(),
    minValue = 1.0,
    maxValue = 500.0,
)

internal fun decimalSpec(
    key: String,
    label: String,
    default: Double,
    min: Double = 0.1,
    max: Double = 10.0,
): IndicatorParamSpec = IndicatorParamSpec(
    key = key,
    label = label,
    defaultValue = default,
    minValue = min,
    maxValue = max,
    step = 0.1,
    integer = false,
)
