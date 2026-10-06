/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.format

/**
 * Double в plain-десятичную строку без экспоненты:
 * 1.0E-4 → "0.0001", 2.5E-5 → "0.000025". Нужен для qty контрактных
 * рынков (MEXC BTC: шаг 0.0001), где `toString()` даёт "1.0E-4".
 *
 * @param trimZeros убирать хвостовые нули дроби ("0.00010" → "0.0001")
 */
fun plainDecimalString(value: Double, trimZeros: Boolean = true): String {
    if (value == 0.0 || value.isNaN() || value.isInfinite()) return "0"
    val negative = value < 0
    val absText = kotlin.math.abs(value).toString()
    val eIndex = absText.indexOfFirst { it == 'E' || it == 'e' }
    val plain = if (eIndex < 0) {
        absText
    } else {
        // Разворачиваем мантиссу с экспонентой в обычную десятичную строку
        val mantissa = absText.substring(0, eIndex)
        val exp = absText.substring(eIndex + 1).toIntOrNull() ?: 0
        val dot = mantissa.indexOf('.')
        val intPart = if (dot >= 0) mantissa.substring(0, dot) else mantissa
        val fracPart = if (dot >= 0) mantissa.substring(dot + 1) else ""
        val digits = intPart + fracPart
        val pointPos = intPart.length + exp
        when {
            pointPos <= 0 -> "0." + "0".repeat(-pointPos) + digits
            pointPos >= digits.length -> digits + "0".repeat(pointPos - digits.length)
            else -> digits.substring(0, pointPos) + "." + digits.substring(pointPos)
        }
    }
    val trimmed = if (trimZeros && '.' in plain) plain.trimEnd('0').trimEnd('.') else plain
    val body = if (trimmed.isEmpty() || trimmed == ".") "0" else trimmed
    return if (negative) "-$body" else body
}

/**
 * Double в plain-строку с ФИКСИРОВАННЫМ числом знаков (без экспоненты),
 * с добором нулей: 75.82336328956 → "75.8234", 0.0 → "0.0000".
 * Для сумм/балансов в стиле MEXC (4 знака).
 */
fun plainDecimalFixed(value: Double, decimals: Int): String {
    if (value.isNaN() || value.isInfinite()) return if (decimals <= 0) "0" else "0." + "0".repeat(decimals)
    if (decimals <= 0) return kotlin.math.round(value).toLong().toString()
    var factor = 1.0
    repeat(decimals) { factor *= 10.0 }
    val rounded = kotlin.math.round(value * factor) / factor
    val s = plainDecimalString(rounded, trimZeros = false)
    val dot = s.indexOf('.')
    return if (dot < 0) {
        "$s." + "0".repeat(decimals)
    } else {
        s + "0".repeat((decimals - (s.length - dot - 1)).coerceAtLeast(0))
    }
}
