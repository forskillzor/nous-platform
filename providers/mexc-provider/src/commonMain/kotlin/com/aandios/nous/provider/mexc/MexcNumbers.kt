/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc

/**
 * Форматирует Double в plain-десятичную строку без экспоненты
 * (MEXC требует decimal-строки, "1.0E-5" не принимает).
 */
internal fun formatDecimal(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "0"
    if (value == 0.0) return "0"

    val sign = if (value < 0) "-" else ""
    val s = kotlin.math.abs(value).toString()
    val eIndex = s.indexOfFirst { it == 'E' || it == 'e' }
    if (eIndex < 0) return sign + trimTrailingZeros(s)

    // Раскрываем экспоненциальную запись: m.mmmE±p → полную десятичную строку
    val mantissa = s.substring(0, eIndex)
    val exp = s.substring(eIndex + 1).toInt()

    val dotIndex = mantissa.indexOf('.')
    val intPart = if (dotIndex >= 0) mantissa.substring(0, dotIndex) else mantissa
    val fracPart = if (dotIndex >= 0) mantissa.substring(dotIndex + 1) else ""
    val digitsRaw = (intPart + fracPart).trimEnd('0')
    val digits = digitsRaw.trimStart('0').ifEmpty { "0" }
    val leadingZeros = digitsRaw.length - digits.length

    val pointPos = intPart.length + exp - leadingZeros
    val out = when {
        pointPos <= 0 -> "0." + "0".repeat(-pointPos) + digits
        pointPos >= digits.length -> digits + "0".repeat(pointPos - digits.length)
        else -> digits.substring(0, pointPos) + "." + digits.substring(pointPos)
    }
    return sign + out
}

/**
 * Округляет цену до шага тика через целочисленную арифметику
 * (без float-артефактов: 85578.66/0.1 → ровно 85578.7).
 */
internal fun roundToTick(price: Double, tickSize: Double): Double {
    if (tickSize <= 0.0) return price
    val scale = 1_000_000_000.0
    val tickUnits = kotlin.math.round(tickSize * scale).toLong().coerceAtLeast(1)
    val priceUnits = kotlin.math.round(price * scale).toLong()
    val rounded = (priceUnits + tickUnits / 2) / tickUnits * tickUnits
    return rounded.toDouble() / scale
}

/** Убирает хвостовые нули десятичной строки ("1.5000" → "1.5"). */
internal fun trimTrailingZeros(s: String): String {
    if ('.' !in s) return s
    var end = s.length
    while (end > 0 && s[end - 1] == '0') end--
    return if (end > 0 && s[end - 1] == '.') s.substring(0, end - 1) else s.substring(0, end)
}
