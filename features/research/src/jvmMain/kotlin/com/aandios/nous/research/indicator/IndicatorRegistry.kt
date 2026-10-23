/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator

import com.aandios.nous.research.indicator.calculators.AdxCalculator
import com.aandios.nous.research.indicator.calculators.AtrCalculator
import com.aandios.nous.research.indicator.calculators.BollingerBandsCalculator
import com.aandios.nous.research.indicator.calculators.EmaCalculator
import com.aandios.nous.research.indicator.calculators.KeltnerChannelsCalculator
import com.aandios.nous.research.indicator.calculators.LiquidationsCalculator
import com.aandios.nous.research.indicator.calculators.MacdCalculator
import com.aandios.nous.research.indicator.calculators.RsiCalculator
import com.aandios.nous.research.indicator.calculators.SmaCalculator
import com.aandios.nous.research.indicator.calculators.StochasticCalculator
import com.aandios.nous.research.indicator.calculators.VolumeCalculator
import com.aandios.nous.research.indicator.calculators.VwapCalculator

actual object IndicatorRegistry {

    actual val all: List<IndicatorCalculator> = listOf(
        SmaCalculator(),
        EmaCalculator(),
        BollingerBandsCalculator(),
        VwapCalculator(),
        KeltnerChannelsCalculator(),
        RsiCalculator(),
        MacdCalculator(),
        AtrCalculator(),
        StochasticCalculator(),
        VolumeCalculator(),
        AdxCalculator(),
        LiquidationsCalculator(),
    )

    private val byType: Map<IndicatorType, IndicatorCalculator> =
        all.associateBy { it.type }

    actual fun get(type: IndicatorType): IndicatorCalculator =
        byType[type] ?: error("No indicator calculator registered for $type")
}
