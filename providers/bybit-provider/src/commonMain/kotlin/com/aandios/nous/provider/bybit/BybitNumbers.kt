/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit

internal fun plainBybitDecimal(value: Double): String {
    if (!value.isFinite() || value == 0.0) return "0"
    val negative = value < 0
    val text = kotlin.math.abs(value).toString()
    val exponentAt = text.indexOfFirst { it == 'E' || it == 'e' }
    if (exponentAt < 0) return (if (negative) "-" else "") + trimDecimalZeros(text)

    val mantissa = text.substring(0, exponentAt)
    val exponent = text.substring(exponentAt + 1).toInt()
    val decimalAt = mantissa.indexOf('.')
    val integerPart = if (decimalAt < 0) mantissa else mantissa.substring(0, decimalAt)
    val fractionalPart = if (decimalAt < 0) "" else mantissa.substring(decimalAt + 1)
    val digits = (integerPart + fractionalPart).trimEnd('0')
    val point = integerPart.length + exponent
    val expanded = when {
        point <= 0 -> "0." + "0".repeat(-point) + digits
        point >= digits.length -> digits + "0".repeat(point - digits.length)
        else -> digits.substring(0, point) + "." + digits.substring(point)
    }
    return (if (negative) "-" else "") + trimDecimalZeros(expanded)
}

private fun trimDecimalZeros(value: String): String {
    if ('.' !in value) return value
    return value.trimEnd('0').trimEnd('.')
}
