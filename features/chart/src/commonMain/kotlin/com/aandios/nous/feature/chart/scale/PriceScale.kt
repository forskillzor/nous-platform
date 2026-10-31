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

    /**
     * Диапазон с учётом вертикального скролла и вертикального зума.
     *
     * [anchorPrice] — цена, которая остаётся на месте при зуме (обычно под
     * курсором). `null` — центр текущего диапазона.
     */
    fun range(
        verticalScroll: Float,
        chartHeight: Float,
        verticalZoom: Float = 1f,
        anchorPrice: Float? = null,
    ): PriceRange {
        val shifted = shiftPriceRange(baseRange, verticalScroll, chartHeight)
        if (verticalZoom == 1f || shifted.range <= 0f || !verticalZoom.isFinite() || verticalZoom <= 0f) {
            return shifted
        }
        val anchor = anchorPrice?.takeIf { it.isFinite() }
            ?: ((shifted.visibleMin + shifted.visibleMax) / 2f)
        val scale = 1f / verticalZoom
        fun scaled(value: Float) = anchor + (value - anchor) * scale
        return PriceRange(
            max = scaled(shifted.max),
            min = scaled(shifted.min),
            visibleMax = scaled(shifted.visibleMax),
            visibleMin = scaled(shifted.visibleMin),
            range = shifted.range * scale,
        )
    }
}
