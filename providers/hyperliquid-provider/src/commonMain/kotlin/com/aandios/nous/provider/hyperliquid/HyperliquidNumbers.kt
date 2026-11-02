/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

private const val PRICE_SIGNIFICANT_FIGURES = 5
private const val PERP_MAX_DECIMALS = 6
internal const val HYPERLIQUID_MIN_NOTIONAL = 10.0

/** Plain decimal without scientific notation; Hyperliquid hashes exact strings. */
internal fun plainHyperliquidDecimal(value: Double, trimZeros: Boolean = true): String {
    if (!value.isFinite() || value == 0.0) return "0"
    val negative = value < 0
    val text = abs(value).toString()
    val exponentAt = text.indexOfFirst { it == 'E' || it == 'e' }
    val plain = if (exponentAt < 0) {
        text
    } else {
        val mantissa = text.substring(0, exponentAt)
        val exponent = text.substring(exponentAt + 1).toInt()
        val decimalAt = mantissa.indexOf('.')
        val integerPart = if (decimalAt < 0) mantissa else mantissa.substring(0, decimalAt)
        val fractionalPart = if (decimalAt < 0) "" else mantissa.substring(decimalAt + 1)
        val digits = integerPart + fractionalPart
        val point = integerPart.length + exponent
        when {
            point <= 0 -> "0." + "0".repeat(-point) + digits
            point >= digits.length -> digits + "0".repeat(point - digits.length)
            else -> digits.substring(0, point) + "." + digits.substring(point)
        }
    }
    val trimmed = if (trimZeros && '.' in plain) plain.trimEnd('0').trimEnd('.') else plain
    val body = if (trimmed.isEmpty() || trimmed == ".") "0" else trimmed
    return if (negative) "-$body" else body
}

internal fun hyperliquidStepSize(szDecimals: Int): Double =
    10.0.pow(-szDecimals.coerceIn(0, 8))

/**
 * Prices may have at most 5 significant figures and no more than
 * `6 - szDecimals` decimal places. Integer prices are always valid.
 */
internal fun normalizeHyperliquidPrice(price: Double, szDecimals: Int): String {
    require(price.isFinite() && price > 0.0) { "Invalid Hyperliquid price: $price" }
    if (abs(price - round(price)) < 1e-9) {
        return plainHyperliquidDecimal(round(price))
    }
    val maxDecimals = (PERP_MAX_DECIMALS - szDecimals).coerceAtLeast(0)
    val significantDecimals = (PRICE_SIGNIFICANT_FIGURES - 1) - floor(log10(abs(price))).toInt()
    val decimals = significantDecimals.coerceIn(0, maxDecimals)
    return plainHyperliquidDecimal(roundToDecimals(price, decimals))
}

internal fun normalizeHyperliquidSize(size: Double, szDecimals: Int): String {
    require(size.isFinite() && size > 0.0) { "Invalid Hyperliquid size: $size" }
    val rounded = roundToDecimals(size, szDecimals.coerceIn(0, 8))
    require(rounded > 0.0) { "Hyperliquid size is below the lot step" }
    return plainHyperliquidDecimal(rounded)
}

/** Smallest valid display tick for the current price magnitude. */
internal fun hyperliquidTickSize(markPrice: Double, szDecimals: Int): Double {
    val maxDecimals = (PERP_MAX_DECIMALS - szDecimals).coerceAtLeast(0)
    val minimumTick = 10.0.pow(-maxDecimals)
    if (!markPrice.isFinite() || markPrice <= 0.0) return minimumTick
    val significantTick = 10.0.pow(floor(log10(markPrice)).toInt() - (PRICE_SIGNIFICANT_FIGURES - 1))
    return maxOf(significantTick, minimumTick)
}

/** Quantity that satisfies Hyperliquid's minimum order value at [markPrice]. */
internal fun hyperliquidMinQty(markPrice: Double, szDecimals: Int): Double {
    val step = hyperliquidStepSize(szDecimals)
    if (!markPrice.isFinite() || markPrice <= 0.0) return step
    val steps = ceil(HYPERLIQUID_MIN_NOTIONAL / markPrice / step - 1e-9).coerceAtLeast(1.0)
    return roundToDecimals(steps * step, szDecimals)
}

internal fun roundToDecimals(value: Double, decimals: Int): Double {
    var factor = 1.0
    repeat(decimals.coerceIn(0, 8)) { factor *= 10.0 }
    val scaled = value * factor
    val epsilon = 1e-9
    val rounded = if (scaled >= 0.0) {
        kotlin.math.floor(scaled + 0.5 + epsilon)
    } else {
        kotlin.math.ceil(scaled - 0.5 - epsilon)
    }
    return rounded / factor
}

internal fun hyperliquidAggressivePrice(reference: Double, isBuy: Boolean, slippage: Double): Double {
    val boundedSlippage = slippage.coerceIn(0.0, 0.5)
    return if (isBuy) reference * (1.0 + boundedSlippage) else reference * (1.0 - boundedSlippage)
}
