/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.scale

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import com.aandios.nous.feature.chart.model.CandleMetrics
import com.aandios.nous.feature.chart.utils.calculateCandleMetrics
import com.aandios.nous.feature.chart.utils.calculateMaxScroll
import com.aandios.nous.feature.chart.utils.calculateZoomScrollOffset
import kotlin.math.max

/**
 * Горизонтальная шкала времени: скролл, зум и преобразования «индекс свечи ↔ X».
 * Состояние — Compose snapshot state, поэтому изменение скролла/зума из жестов
 * сразу инвалидирует Canvas.
 *
 * По образу time-scale из lightweight-charts: шкала ничего не знает про
 * серии, только про метрики свечей и видимый диапазон.
 */
class TimeScale(initialZoom: Float = 1f) {

    var scrollOffset by mutableFloatStateOf(0f)
        private set

    var zoomLevel by mutableFloatStateOf(initialZoom)
        private set

    fun metrics(): CandleMetrics = calculateCandleMetrics(zoomLevel)

    /** Полная ширина одной свечи (тело + отступ). */
    fun totalWidth(): Float {
        val metrics = metrics()
        return metrics.width + metrics.spacing
    }

    /** Максимальный скролл: ширина всего ряда минус ширина области графика. */
    fun maxScroll(candleCount: Int, chartWidth: Float): Float =
        calculateMaxScroll(candleCount, metrics(), chartWidth)

    /** Экранный X центра свечи по индексу (относительно левого края области). */
    fun indexToX(index: Int): Float =
        index * totalWidth() - scrollOffset + metrics().width / 2f

    /** Индекс свечи по экранной координате X. */
    fun xToIndex(x: Float): Int =
        ((x + scrollOffset) / totalWidth()).toInt()

    /** Панорамирование: deltaX > 0 — перетаскивание вправо. */
    fun panBy(deltaX: Float, candleCount: Int, chartWidth: Float) {
        scrollOffset = (scrollOffset - deltaX)
            .coerceIn(-MAX_SCROLL_LEFT, maxScroll(candleCount, chartWidth))
    }

    /**
     * Зум с сохранением якоря.
     * @param anchorAtMouse false — фиксируем правый край (самая новая свеча);
     *                      true — фиксируем точку под курсором (Ctrl+zoom).
     */
    fun zoomAt(
        factor: Float,
        mouseX: Float,
        anchorAtMouse: Boolean,
        chartWidth: Float,
        candleCount: Int,
        minZoom: Float,
        maxZoom: Float,
    ) {
        val oldZoom = zoomLevel
        val newZoom = (oldZoom * factor).coerceIn(minZoom, maxZoom)
        val actualFactor = newZoom / oldZoom

        val newScroll = calculateZoomScrollOffset(
            scrollOffset = scrollOffset,
            chartWidth = chartWidth,
            mouseX = mouseX,
            actualFactor = actualFactor,
            anchorAtMouse = anchorAtMouse,
        )
        // maxScroll для НОВОГО зума: кламп по старому значению ломает якорь
        val newMaxScroll = calculateMaxScroll(candleCount, calculateCandleMetrics(newZoom), chartWidth)

        zoomLevel = newZoom
        scrollOffset = newScroll.coerceIn(-MAX_SCROLL_LEFT, newMaxScroll)
    }

    fun setZoom(zoom: Float) {
        zoomLevel = zoom
    }

    /** Мгновенный переход к самой новой свече. */
    fun scrollToLatest(candleCount: Int, chartWidth: Float) {
        scrollOffset = maxScroll(candleCount, chartWidth)
    }

    /** Коррекция скролла после добавления [addedCount] свечей слева. */
    fun offsetAfterPrepend(addedCount: Int, candleCount: Int, chartWidth: Float) {
        scrollOffset = (scrollOffset + addedCount * totalWidth())
            .coerceIn(-MAX_SCROLL_LEFT, maxScroll(candleCount, chartWidth))
    }

    /** Истинно, если видимая область упирается в самую новую свечу. */
    fun isAtLatest(candleCount: Int, chartWidth: Float, tolerance: Float = 1f): Boolean =
        max(0f, maxScroll(candleCount, chartWidth)) - scrollOffset < tolerance

    companion object {
        /** Запас «пустой зоны» слева для инициации подгрузки истории. */
        const val MAX_SCROLL_LEFT = 300f
    }
}
