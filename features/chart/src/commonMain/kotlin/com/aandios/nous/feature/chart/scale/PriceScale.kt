/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.scale

import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.utils.shiftPriceRange

/**
 * Вертикальная шкала цен: базовый диапазон (autoscale по видимым данным)
 * и сдвиг при вертикальном скролле footprint.
 *
 * По образу price-scale из lightweight-charts: диапазон пересчитывается
 * по видимым свечам, сдвиг — состояние жеста, а не данных.
 */
class PriceScale {

    private var baseRange: PriceRange = PriceRange(0f, 0f, 0f, 0f, 0f)

    /** Пересчитать базовый диапазон по видимым данным. */
    fun fit(provider: () -> PriceRange) {
        baseRange = provider()
    }

    /** Диапазон с учётом вертикального скролла. */
    fun range(verticalScroll: Float, chartHeight: Float): PriceRange =
        shiftPriceRange(baseRange, verticalScroll, chartHeight)
}
