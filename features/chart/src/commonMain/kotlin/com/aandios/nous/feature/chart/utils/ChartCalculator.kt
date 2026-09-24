/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.utils

import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.feature.chart.model.CandleMetrics
import com.aandios.nous.feature.chart.model.PriceRange
import kotlin.math.max

/**
 * Рассчитывает метрики свечей на основе zoomLevel.
 * Ширина свечи НЕ зависит от количества свечей — только от zoomLevel.
 */
fun calculateCandleMetrics(zoomLevel: Float): CandleMetrics {
    val width = BASE_CANDLE_WIDTH * zoomLevel
    val spacing = width * 0.3f / 0.7f  // сохраняем пропорцию 70/30
    return CandleMetrics(width, spacing)
}

/**
 * Вычисляет PriceRange по списку свечей с учётом currentPrice.
 * Добавляет 5% padding сверху и снизу.
 */
fun calculatePriceRangeWithCurrentPrice(
    candles: List<Candle>,
    currentPrice: Float?
): PriceRange {
    if (candles.isEmpty() && currentPrice == null) {
        return PriceRange(0f, 0f, 0f, 0f, 0f)
    }

    val priceList = mutableListOf<Float>().apply {
        addAll(candles.map { it.high })
        addAll(candles.map { it.low })
        currentPrice?.let { add(it) }
    }

    val maxPrice = priceList.maxOrNull() ?: 0f
    val minPrice = priceList.minOrNull() ?: 0f
    val priceRange = maxPrice - minPrice

    // Добавляем 5% padding сверху и снизу
    val padding = priceRange * 0.05f
    val visibleMax = maxPrice + padding
    val visibleMin = minPrice - padding
    val visibleRange = visibleMax - visibleMin

    return PriceRange(
        max = maxPrice,
        min = minPrice,
        visibleMax = visibleMax,
        visibleMin = visibleMin,
        range = visibleRange
    )
}

/**
 * Конвертирует цену в Y координату с учётом высоты области.
 */
fun priceToY(price: Float, priceRange: PriceRange, height: Float): Float {
    val range = if (priceRange.range <= 0f) 0.01f else priceRange.range
    val visibleMin = priceRange.visibleMin
    if (height <= 0f) return 0f
    return height - ((price - visibleMin) / range) * height
}

/**
 * Конвертирует Y координату в цену.
 */
fun priceFromY(
    y: Float,
    priceRange: PriceRange,
    chartHeight: Float
): Float {
    val range = if (priceRange.range <= 0f) 0.01f else priceRange.range
    if (chartHeight <= 0f) return priceRange.visibleMax
    return priceRange.visibleMax - (y / chartHeight) * range
}

/**
 * Генерирует список уровней цен для отображения на шкале.
 */
fun generatePriceLevels(min: Float, max: Float, count: Int): List<Float> {
    if (count <= 0) return emptyList()
    if (count == 1) return listOf(max)

    val range = max - min
    val step = range / (count - 1)

    return List(count) { i ->
        max - (step * i)
    }
}

/**
 * Находит индекс ближайшей свечи по X координате мыши.
 */
fun findNearestCandleIndex(
    mouseX: Float,
    candles: List<Candle>,
    scrollOffset: Float = 0f,
    zoomLevel: Float = 1f,
): Int {
    if (candles.isEmpty()) return -1

    val candleMetrics = calculateCandleMetrics(zoomLevel)
    val totalWidthPerCandle = candleMetrics.width + candleMetrics.spacing

    // mouseX — координата на видимой области, свечи смещены на -scrollOffset в виртуальном пространстве
    val virtualX = mouseX + scrollOffset
    val index = (virtualX / totalWidthPerCandle).toInt()
    return index.coerceIn(0, candles.size - 1)
}

/**
 * Сдвигает диапазон цен при вертикальном скролле footprint.
 * ratio = verticalScroll / chartHeight, сдвиг = range * ratio.
 */
fun shiftPriceRange(base: PriceRange, verticalScroll: Float, chartHeight: Float): PriceRange {
    if (chartHeight <= 0f || verticalScroll == 0f) return base
    val ratio = verticalScroll / chartHeight
    val shift = base.range * ratio
    return PriceRange(
        max = base.max + shift,
        min = base.min + shift,
        visibleMax = base.visibleMax + shift,
        visibleMin = base.visibleMin + shift,
        range = base.range,
    )
}

/**
 * Максимальный скролл для текущего зума: ширина всего ряда минус ширина области графика.
 */
fun calculateMaxScroll(
    candleCount: Int,
    candleMetrics: CandleMetrics,
    chartWidth: Float,
): Float {
    val totalW = candleMetrics.width + candleMetrics.spacing
    return max(0f, candleCount * totalW - chartWidth)
}

/**
 * Новое значение scrollOffset при зуме.
 *
 * @param anchorAtMouse false — фиксируем правый край (самая новая свеча);
 *                      true — фиксируем точку под курсором (Ctrl+zoom).
 */
fun calculateZoomScrollOffset(
    scrollOffset: Float,
    chartWidth: Float,
    mouseX: Float,
    actualFactor: Float,
    anchorAtMouse: Boolean,
): Float {
    return if (anchorAtMouse) {
        (mouseX + scrollOffset) * actualFactor - mouseX
    } else {
        (scrollOffset + chartWidth) * actualFactor - chartWidth
    }
}
