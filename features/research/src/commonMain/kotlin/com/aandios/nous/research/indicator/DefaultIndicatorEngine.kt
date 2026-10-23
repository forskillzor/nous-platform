/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator

import com.aandios.nous.api.market.model.Candle
import kotlinx.coroutines.CancellationException

/**
 * Registry-driven engine. One failing calculator must not hide the output of
 * the other indicators, so errors are isolated per instance.
 */
class DefaultIndicatorEngine(
    private val inputSource: IndicatorInputSource,
    calculators: List<IndicatorCalculator> = IndicatorRegistry.all,
) : IndicatorEngine {

    private val byType = calculators.associateBy { it.type }
    private val requiredInputs = calculators
        .flatMap { it.requiredInputs }
        .toSet()

    override fun compute(
        candles: List<Candle>,
        instances: List<IndicatorInstance>,
    ): Map<String, IndicatorOutput> {
        if (candles.isEmpty() || instances.isEmpty()) return emptyMap()
        val input = inputSource.snapshot(requiredInputs, candles)
        if (input.candles.isEmpty()) return emptyMap()

        val result = LinkedHashMap<String, IndicatorOutput>()
        for (instance in instances) {
            val calculator = byType[instance.type] ?: continue
            if (!input.supports(calculator.requiredInputs)) continue
            try {
                val computation = calculator.compute(input, instance.params)
                result[instance.id] = IndicatorOutputBuilder.build(
                    instance = instance,
                    definition = calculator.definition,
                    computation = computation,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Keep the other indicators alive; contract errors are tested.
            }
        }
        return result
    }
}
