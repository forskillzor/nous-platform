/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator

import kotlinx.serialization.Serializable

/** Technical indicator supported by the chart. */
@Serializable
enum class IndicatorType {
    SMA,
    EMA,
    BOLLINGER_BANDS,
    VWAP,
    KELTNER_CHANNELS,
    RSI,
    MACD,
    ATR,
    STOCHASTIC,
    VOLUME,
    ADX,
    LIQUIDATIONS,
}

/** Where an indicator is rendered: over the price chart or in the bottom pane. */
@Serializable
enum class IndicatorPlacement {
    OVERLAY,
    PANE,
}

/** Visual style of one indicator output line. */
enum class IndicatorLineStyle {
    LINE,
    DASHED,
    HISTOGRAM,
}

/** Theme-independent color slot resolved by the chart renderer. */
enum class IndicatorColorRole {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    BULLISH,
    BEARISH,
    WARN,
    NEUTRAL,
}

/** How a histogram chooses its bar color. */
enum class HistogramColorMode {
    SIGN,
    CANDLE,
}
