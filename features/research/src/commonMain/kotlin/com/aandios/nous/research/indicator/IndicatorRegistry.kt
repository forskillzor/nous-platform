/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator

const val LIQUIDATIONS_INSTANCE_ID = "liquidations"
const val LIQUIDATIONS_PANE_ID = "pane-liquidations"

/**
 * Calculator registry. The actual implementation lives in the JVM source set
 * because TA4J is JVM-only; all UI and engine code stays common.
 */
expect object IndicatorRegistry {
    val all: List<IndicatorCalculator>
    fun get(type: IndicatorType): IndicatorCalculator
}

fun indicatorDefinition(type: IndicatorType): IndicatorDefinition =
    IndicatorRegistry.get(type).definition

fun defaultIndicatorParams(type: IndicatorType): Map<String, Double> =
    indicatorDefinition(type).parameters.associate { it.key to it.defaultValue }

fun createIndicator(type: IndicatorType, id: String): IndicatorInstance =
    IndicatorInstance(
        id = id,
        type = type,
        params = defaultIndicatorParams(type),
    )

fun defaultIndicatorState(): IndicatorState = IndicatorState(
    panes = listOf(
        IndicatorPaneState(
            id = LIQUIDATIONS_PANE_ID,
            indicators = listOf(createIndicator(IndicatorType.LIQUIDATIONS, LIQUIDATIONS_INSTANCE_ID)),
        ),
    ),
    activePaneId = LIQUIDATIONS_PANE_ID,
)
