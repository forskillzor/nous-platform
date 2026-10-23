/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator

/**
 * One indicator implementation. Definitions are declarative metadata; the
 * calculator only produces aligned line values.
 */
interface IndicatorCalculator {

    val type: IndicatorType

    val definition: IndicatorDefinition

    /** Extra feeds required besides candles. Candles are always supplied. */
    val requiredInputs: Set<InputKind>

    fun compute(
        input: IndicatorInput,
        params: Map<String, Double>,
    ): IndicatorComputation
}
