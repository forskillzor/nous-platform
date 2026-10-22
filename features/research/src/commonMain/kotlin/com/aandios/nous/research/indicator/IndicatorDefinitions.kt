/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator

/** Registry of supported indicators and their default parameters. */
object IndicatorDefinitions {

    const val LIQUIDATIONS_INSTANCE_ID = "liquidations"
    const val LIQUIDATIONS_PANE_ID = "pane-liquidations"

    private fun period(
        default: Int,
        key: String = "period",
        label: String = "Period",
    ) = IndicatorParamSpec(
        key = key,
        label = label,
        defaultValue = default.toDouble(),
        minValue = 1.0,
        maxValue = 500.0,
    )

    private fun decimal(
        key: String,
        label: String,
        default: Double,
        min: Double = 0.1,
        max: Double = 10.0,
    ) = IndicatorParamSpec(
        key = key,
        label = label,
        defaultValue = default,
        minValue = min,
        maxValue = max,
        step = 0.1,
        integer = false,
    )

    val all: List<IndicatorDefinition> = listOf(
        IndicatorDefinition(
            type = IndicatorType.SMA,
            label = "SMA",
            placement = IndicatorPlacement.OVERLAY,
            parameters = listOf(period(20)),
        ),
        IndicatorDefinition(
            type = IndicatorType.EMA,
            label = "EMA",
            placement = IndicatorPlacement.OVERLAY,
            parameters = listOf(period(20)),
        ),
        IndicatorDefinition(
            type = IndicatorType.BOLLINGER_BANDS,
            label = "Bollinger Bands",
            placement = IndicatorPlacement.OVERLAY,
            parameters = listOf(
                period(20),
                decimal("stdDev", "Std Dev", 2.0),
            ),
        ),
        IndicatorDefinition(
            type = IndicatorType.VWAP,
            label = "VWAP",
            placement = IndicatorPlacement.OVERLAY,
            parameters = listOf(period(20)),
        ),
        IndicatorDefinition(
            type = IndicatorType.KELTNER_CHANNELS,
            label = "Keltner Channels",
            placement = IndicatorPlacement.OVERLAY,
            parameters = listOf(
                period(20),
                decimal("multiplier", "Multiplier", 2.0),
            ),
        ),
        IndicatorDefinition(
            type = IndicatorType.RSI,
            label = "RSI",
            placement = IndicatorPlacement.PANE,
            parameters = listOf(period(14)),
        ),
        IndicatorDefinition(
            type = IndicatorType.MACD,
            label = "MACD",
            placement = IndicatorPlacement.PANE,
            parameters = listOf(
                period(12, key = "shortPeriod", label = "Fast"),
                period(26, key = "longPeriod", label = "Slow"),
                period(9, key = "signalPeriod", label = "Signal"),
            ),
        ),
        IndicatorDefinition(
            type = IndicatorType.ATR,
            label = "ATR",
            placement = IndicatorPlacement.PANE,
            parameters = listOf(period(14)),
        ),
        IndicatorDefinition(
            type = IndicatorType.STOCHASTIC,
            label = "Stochastic",
            placement = IndicatorPlacement.PANE,
            parameters = listOf(
                period(14, key = "kPeriod", label = "K"),
                period(3, key = "dPeriod", label = "D"),
            ),
        ),
        IndicatorDefinition(
            type = IndicatorType.VOLUME,
            label = "Volume",
            placement = IndicatorPlacement.PANE,
            parameters = listOf(period(20, label = "MA period")),
        ),
        IndicatorDefinition(
            type = IndicatorType.ADX,
            label = "ADX",
            placement = IndicatorPlacement.PANE,
            parameters = listOf(period(14)),
        ),
        IndicatorDefinition(
            type = IndicatorType.LIQUIDATIONS,
            label = "Liquidations",
            placement = IndicatorPlacement.PANE,
        ),
    )

    /** Editable color slots for one indicator line. */
    data class IndicatorColorTarget(
        val key: String,
        val label: String,
        val defaultRole: IndicatorColorRole = IndicatorColorRole.PRIMARY,
    )

    val colorTargets: Map<IndicatorType, List<IndicatorColorTarget>> = mapOf(
        IndicatorType.SMA to listOf(
            IndicatorColorTarget("sma", "Line"),
        ),
        IndicatorType.EMA to listOf(
            IndicatorColorTarget("ema", "Line"),
        ),
        IndicatorType.BOLLINGER_BANDS to listOf(
            IndicatorColorTarget("upper", "Upper"),
            IndicatorColorTarget("middle", "Middle", IndicatorColorRole.SECONDARY),
            IndicatorColorTarget("lower", "Lower"),
        ),
        IndicatorType.VWAP to listOf(
            IndicatorColorTarget("vwap", "Line", IndicatorColorRole.TERTIARY),
        ),
        IndicatorType.KELTNER_CHANNELS to listOf(
            IndicatorColorTarget("upper", "Upper", IndicatorColorRole.SECONDARY),
            IndicatorColorTarget("middle", "Middle", IndicatorColorRole.NEUTRAL),
            IndicatorColorTarget("lower", "Lower", IndicatorColorRole.SECONDARY),
        ),
        IndicatorType.RSI to listOf(
            IndicatorColorTarget("rsi", "RSI"),
        ),
        IndicatorType.MACD to listOf(
            IndicatorColorTarget("macd", "MACD"),
            IndicatorColorTarget("signal", "Signal", IndicatorColorRole.SECONDARY),
            IndicatorColorTarget("histogram", "Histogram", IndicatorColorRole.NEUTRAL),
        ),
        IndicatorType.ATR to listOf(
            IndicatorColorTarget("atr", "ATR"),
        ),
        IndicatorType.STOCHASTIC to listOf(
            IndicatorColorTarget("k", "K"),
            IndicatorColorTarget("d", "D", IndicatorColorRole.SECONDARY),
        ),
        IndicatorType.VOLUME to listOf(
            IndicatorColorTarget("volume", "Volume", IndicatorColorRole.NEUTRAL),
            IndicatorColorTarget("volume_ma", "MA", IndicatorColorRole.SECONDARY),
        ),
        IndicatorType.ADX to listOf(
            IndicatorColorTarget("adx", "ADX"),
            IndicatorColorTarget("plus_di", "+DI", IndicatorColorRole.BULLISH),
            IndicatorColorTarget("minus_di", "-DI", IndicatorColorRole.BEARISH),
        ),
        IndicatorType.LIQUIDATIONS to listOf(
            IndicatorColorTarget("long", "Long", IndicatorColorRole.BULLISH),
            IndicatorColorTarget("short", "Short", IndicatorColorRole.BEARISH),
        ),
    )

    private val byType: Map<IndicatorType, IndicatorDefinition> =
        all.associateBy { it.type }

    fun definition(type: IndicatorType): IndicatorDefinition =
        byType.getValue(type)

    fun defaultParams(type: IndicatorType): Map<String, Double> =
        definition(type).parameters.associate { it.key to it.defaultValue }

    fun create(type: IndicatorType, id: String): IndicatorInstance =
        IndicatorInstance(
            id = id,
            type = type,
            params = defaultParams(type),
        )

    fun defaultState(): IndicatorState = IndicatorState(
        panes = listOf(
            IndicatorPaneState(
                id = LIQUIDATIONS_PANE_ID,
                indicators = listOf(
                    IndicatorInstance(
                        id = LIQUIDATIONS_INSTANCE_ID,
                        type = IndicatorType.LIQUIDATIONS,
                    ),
                ),
            ),
        ),
        activePaneId = LIQUIDATIONS_PANE_ID,
    )
}
