/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator

import com.aandios.nous.api.market.model.trading.TradeSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndicatorRegistryTest {

    @Test
    fun everyTypeHasCalculator() {
        assertEquals(
            IndicatorType.entries.toSet(),
            IndicatorRegistry.all.map { it.type }.toSet(),
        )
    }

    @Test
    fun definitionsExposeEveryTypeAndDefaults() {
        for (calculator in IndicatorRegistry.all) {
            val definition = calculator.definition
            assertEquals(calculator.type, definition.type)
            assertTrue(definition.lines.isNotEmpty())

            for (param in definition.parameters) {
                assertTrue(param.defaultValue in param.minValue..param.maxValue)
            }

            val instance = createIndicator(definition.type, "test")
            assertEquals(definition.type, instance.type)
            assertEquals(
                definition.parameters.map { it.key }.toSet(),
                instance.params.keys,
            )
        }
    }

    @Test
    fun defaultStateContainsLiquidationsPane() {
        val state = defaultIndicatorState()

        assertEquals(LIQUIDATIONS_PANE_ID, state.activePaneId)
        assertEquals(
            listOf(IndicatorType.LIQUIDATIONS),
            state.panes.single().indicators.map { it.type },
        )
    }

    @Test
    fun everyCalculatorReturnsDeclaredLineKeys() {
        val candles = testCandles(120)
        val source = StaticIndicatorInputSource(
            liquidations = listOf(testLiquidation(candles[60].timestamp, 1.0, TradeSide.SELL)),
        )

        for (calculator in IndicatorRegistry.all) {
            val instance = createIndicator(calculator.type, "test")
            val input = source.snapshot(calculator.requiredInputs, candles)
            val computation = calculator.compute(input, instance.params)
            assertEquals(
                calculator.definition.lines.map { it.id }.toSet(),
                computation.lines.keys,
                "line keys for ${calculator.type}",
            )
        }
    }

    @Test
    fun liquidationsDeclareTheirFeedRequirement() {
        assertEquals(
            setOf(InputKind.LIQUIDATIONS),
            IndicatorRegistry.get(IndicatorType.LIQUIDATIONS).requiredInputs,
        )
    }
}
