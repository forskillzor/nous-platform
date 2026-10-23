/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator

import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.liquidation.LiquidationOrder
import com.aandios.nous.api.market.model.trading.TradeSide
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal const val TEST_CANDLE_START = 1_700_000_000_000L
internal const val TEST_CANDLE_INTERVAL = 300_000L

internal fun testCandles(count: Int = 80): List<Candle> =
    List(count) { index ->
        val close = 100f + index * 0.5f
        Candle(
            open = close - 0.25f,
            high = close + 1f,
            low = close - 1f,
            close = close,
            timestamp = TEST_CANDLE_START + index * TEST_CANDLE_INTERVAL,
            volume = 1_000f + index,
        )
    }

internal fun testLiquidation(
    timestamp: Long,
    quantity: Double = 1.0,
    side: TradeSide = TradeSide.SELL,
): LiquidationOrder = LiquidationOrder(
    symbol = "BTCUSDT",
    price = 100.0,
    quantity = quantity,
    timestamp = timestamp,
    side = side,
)

internal class StaticIndicatorInputSource(
    private val liquidations: List<LiquidationOrder> = emptyList(),
) : IndicatorInputSource {

    override fun snapshot(
        required: Set<InputKind>,
        candles: List<Candle>,
    ): IndicatorInput = IndicatorInput(
        candles = candles,
        liquidations = if (InputKind.LIQUIDATIONS in required) liquidations else emptyList(),
    )

    override fun updates(): Flow<Unit> = emptyFlow()
}
