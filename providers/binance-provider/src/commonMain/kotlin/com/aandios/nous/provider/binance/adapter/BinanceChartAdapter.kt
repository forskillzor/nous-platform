/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.adapter

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.ChartAdapter
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.provider.binance.BinanceRestGate
import com.aandios.nous.provider.binance.BinanceStreamHub
import com.aandios.nous.provider.binance.model.BinanceCandle
import com.aandios.nous.provider.binance.model.BinanceWebSocketCandle
import com.aandios.nous.provider.binance.model.BinanceWebSocketResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json

class BinanceChartAdapter(
    private val client: HttpClient,
    private val config: ProviderConfig,
    private val restGate: BinanceRestGate,
    private val streamHub: BinanceStreamHub,
) : ChartAdapter {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override suspend fun getCandles(
        symbol: String,
        interval: String,
        limit: Int
    ): List<Candle> = restGate.execute(
        key = "klines:$symbol:$interval:$limit",
        weight = if (limit > 499) 2 else 1,
    ) {
        val response: List<List<String>> = client.get("https://fapi.binance.com/fapi/v1/klines") {
            url {
                parameters.append("symbol", symbol)
                parameters.append("interval", interval)
                parameters.append("limit", limit.toString())
            }
        }.body()

        response.map { rawCandle ->
            BinanceCandle(
                openTime = rawCandle[0].toLong(),
                open = rawCandle[1],
                high = rawCandle[2],
                low = rawCandle[3],
                close = rawCandle[4],
                volume = rawCandle[5],
                closeTime = rawCandle[6].toLong(),
                quoteAssetVolume = rawCandle[7],
                numberOfTrades = rawCandle[8].toInt(),
                takerBuyBaseAssetVolume = rawCandle[9],
                takerBuyQuoteAssetVolume = rawCandle[10]
            ).toCandle()
        }
    }

    override suspend fun getCandlesBefore(
        symbol: String,
        interval: String,
        endTime: Long,
        limit: Int
    ): List<Candle> = restGate.execute(
        key = "klinesBefore:$symbol:$interval:$endTime:$limit",
        weight = if (limit > 499) 2 else 1,
    ) {
        val response: List<List<String>> = client.get("https://fapi.binance.com/fapi/v1/klines") {
            url {
                parameters.append("symbol", symbol)
                parameters.append("interval", interval)
                parameters.append("endTime", endTime.toString())
                parameters.append("limit", limit.toString())
            }
        }.body()

        response.map { rawCandle ->
            BinanceCandle(
                openTime = rawCandle[0].toLong(),
                open = rawCandle[1],
                high = rawCandle[2],
                low = rawCandle[3],
                close = rawCandle[4],
                volume = rawCandle[5],
                closeTime = rawCandle[6].toLong(),
                quoteAssetVolume = rawCandle[7],
                numberOfTrades = rawCandle[8].toInt(),
                takerBuyBaseAssetVolume = rawCandle[9],
                takerBuyQuoteAssetVolume = rawCandle[10]
            ).toCandle()
        }
    }

    override fun subscribeToCandles(
        symbol: String,
        interval: String
    ): Flow<Candle> {
        val streamName = "${symbol.lowercase()}@kline_$interval"
        return streamHub.subscribe(streamName).map { text ->
            json.decodeFromString<BinanceWebSocketResponse>(text)
        }.mapNotNull { wsResponse ->
            if (wsResponse.eventType != "kline") return@mapNotNull null
            val kline = wsResponse.kline
            BinanceWebSocketCandle(
                symbol = wsResponse.symbol,
                openTime = kline.startTime,
                closeTime = kline.endTime,
                open = kline.open,
                high = kline.high,
                low = kline.low,
                close = kline.close,
                volume = kline.volume,
                isClosed = kline.isClosed
            ).toCandle()
        }
    }
}
