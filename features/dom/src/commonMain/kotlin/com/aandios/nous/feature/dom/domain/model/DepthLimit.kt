/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.domain.model

/**
 * Ограничение глубины отображения книги заявок — число АГРЕГИРОВАННЫХ уровней.
 * Диапазон значений: от 5 до 20 уровней.
 *
 * Фактическая подписка шире: `depth × aggregation.multiplier`
 * (см. DomOptions.rawDepth). Binance Futures partial-стримы
 * (`depth<levels>@100ms`) существуют только для 5/10/20, поэтому для большей
 * сырой глубины используется локальная diff-книга; Binance/Bybit отдают
 * максимум 1000 сырых уровней.
 */
data class DepthLimit(
    val value: Int
) {
    init {
        require(value in MIN_VALUE..MAX_VALUE) {
            "Depth limit must be between $MIN_VALUE and $MAX_VALUE, got $value"
        }
    }

    companion object {
        const val MIN_VALUE = 5
        const val MAX_VALUE = 20
        const val DEFAULT_VALUE = 20

        /**
         * Создает DepthLimit с значением по умолчанию (20 уровней).
         */
        fun default(): DepthLimit = DepthLimit(DEFAULT_VALUE)

        /**
         * Создает DepthLimit с указанным значением, ограничивая его допустимым диапазоном.
         */
        fun create(value: Int): DepthLimit = DepthLimit(
            value.coerceIn(MIN_VALUE, MAX_VALUE)
        )

        /**
         * Список стандартных значений для выбора в UI.
         */
        val standardValues = listOf(5, 10, 20)
    }

    /**
     * Проверяет, является ли значение стандартным (из списка standardValues).
     */
    fun isStandard(): Boolean = value in standardValues

    override fun toString(): String = value.toString()
}