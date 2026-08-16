/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.domain.model

/**
 * Уровень агрегации цен в стакане заявок (DOM) на основе множителя тика инструмента.
 * Определяет размер агрегации как multiplier × baseTickSize.
 *
 * Например, для инструмента с baseTickSize = 0.01:
 * - BaseTick (1×) → агрегация 0.01
 * - TenTick (10×) → агрегация 0.1  
 * - HundredTick (100×) → агрегация 1.0
 */
sealed class AggregationLevel(val multiplier: Double) {
    /** Базовый тик инструмента (без агрегации) */
    object BaseTick : AggregationLevel(1.0)

    /** Агрегация 10× от базового тика */
    object TenTick : AggregationLevel(10.0)

    /** Агрегация 100× от базового тика */
    object HundredTick : AggregationLevel(100.0)

    companion object {
        /**
         * Возвращает список всех уровней агрегации в порядке увеличения множителя.
         */
        fun all(): List<AggregationLevel> = listOf(BaseTick, TenTick, HundredTick)

        /**
         * Возвращает уровень агрегации по его строковому представлению.
         * @throws IllegalArgumentException если строка не соответствует ни одному уровню
         */
        fun fromString(value: String): AggregationLevel = when (value) {
            "BaseTick", "1×", "1x", "1.0" -> BaseTick
            "TenTick", "10×", "10x", "10.0" -> TenTick
            "HundredTick", "100×", "100x", "100.0" -> HundredTick
            else -> throw IllegalArgumentException("Unknown aggregation level: $value")
        }
    }

    /**
     * Возвращает эффективный размер тика агрегации для данного инструмента.
     * @param baseTickSize базовый тик инструмента (из API биржи)
     */
    fun effectiveTickSize(baseTickSize: Double): Double = baseTickSize * multiplier

    /**
     * Округляет цену вниз до ближайшего эффективного тика агрегации.
     * @param price цена для округления
     * @param baseTickSize базовый тик инструмента
     */
    fun roundDown(price: Double, baseTickSize: Double): Double {
        val tick = effectiveTickSize(baseTickSize)
        if (tick <= 0.0) return price
        return (price / tick).toInt() * tick
    }

    /**
     * Вычисляет ключ агрегации для заданной цены.
     * @param price цена (строковое представление)
     * @param baseTickSize базовый тик инструмента
     * @return ключ агрегации (цена, округлённая вниз до эффективного тика)
     */
    fun aggregationKey(price: String, baseTickSize: Double): String {
        val priceDouble = price.toDoubleOrNull() ?: return price
        val rounded = roundDown(priceDouble, baseTickSize)
        // Форматируем без лишних нулей
        return if (rounded == rounded.toInt().toDouble()) {
            rounded.toInt().toString()
        } else {
            rounded.toString().trimEnd('0').trimEnd('.')
        }
    }

    /**
     * Возвращает строковое представление уровня агрегации для отображения в UI.
     * @param baseTickSize базовый тик инструмента (для отображения эффективного тика)
     */
    fun displayName(baseTickSize: Double? = null): String {
        val suffix = if (baseTickSize != null) {
            val tick = effectiveTickSize(baseTickSize)
            val tickStr = tick.toString().trimEnd('0').trimEnd('.')
            " ($tickStr)"
        } else ""
        
        return when (this) {
            BaseTick -> "Базовый${suffix}"
            TenTick -> "10×${suffix}"
            HundredTick -> "100×${suffix}"
        }
    }
}