/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

internal const val HYPERLIQUID_MAINNET_REST = "https://api.hyperliquid.xyz"
internal const val HYPERLIQUID_TESTNET_REST = "https://api.hyperliquid-testnet.xyz"
internal const val HYPERLIQUID_MAINNET_WS = "wss://api.hyperliquid.xyz/ws"
internal const val HYPERLIQUID_TESTNET_WS = "wss://api.hyperliquid-testnet.xyz/ws"

internal fun hyperliquidRestBase(isTestnet: Boolean): String =
    if (isTestnet) HYPERLIQUID_TESTNET_REST else HYPERLIQUID_MAINNET_REST

internal fun hyperliquidWsUrl(isTestnet: Boolean): String =
    if (isTestnet) HYPERLIQUID_TESTNET_WS else HYPERLIQUID_MAINNET_WS

/** Hyperliquid supports these candle intervals; the platform uses the same names. */
internal fun hyperliquidInterval(interval: String): String = when (interval) {
    "1m", "3m", "5m", "15m", "30m", "1h", "2h", "4h", "8h", "12h", "1d", "3d", "1w", "1M" -> interval
    "1mo" -> "1M"
    else -> "1h"
}

internal fun hyperliquidIntervalMillis(interval: String): Long = when (hyperliquidInterval(interval)) {
    "1m" -> 60_000L
    "3m" -> 180_000L
    "5m" -> 300_000L
    "15m" -> 900_000L
    "30m" -> 1_800_000L
    "1h" -> 3_600_000L
    "2h" -> 7_200_000L
    "4h" -> 14_400_000L
    "8h" -> 28_800_000L
    "12h" -> 43_200_000L
    "1d" -> 86_400_000L
    "3d" -> 259_200_000L
    "1w" -> 604_800_000L
    "1M" -> 2_592_000_000L
    else -> 3_600_000L
}

/** Default perp dex is represented by an empty string. HIP-3 coins are `dex:coin`. */
internal fun hyperliquidDexOf(coin: String): String =
    coin.substringBefore(':', missingDelimiterValue = "")

internal fun hyperliquidIsHip3(coin: String): Boolean = ':' in coin

internal fun hyperliquidAssetId(dexIndex: Int, indexInMeta: Int): Int =
    if (dexIndex == 0) indexInMeta else 100_000 + dexIndex * 10_000 + indexInMeta
