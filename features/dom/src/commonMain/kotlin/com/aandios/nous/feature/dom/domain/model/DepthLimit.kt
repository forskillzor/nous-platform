/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.domain.model

/**
 * Ограничение глубины отображения книги заявок (количество уровней).
 * Диапазон значений: от 20 до 500 уровней.
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
        const val MIN_VALUE = 20
        const val MAX_VALUE = 1000
        const val DEFAULT_VALUE = 1000

        /**
         * Создает DepthLimit с значением по умолчанию (100 уровней).
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
        val standardValues = listOf(10, 20, 50, 100, 200, 500, 1000)
    }

    /**
     * Проверяет, является ли значение стандартным (из списка standardValues).
     */
    fun isStandard(): Boolean = value in standardValues

    override fun toString(): String = value.toString()
}