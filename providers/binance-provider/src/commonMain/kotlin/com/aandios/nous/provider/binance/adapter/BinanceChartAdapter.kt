/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.adapter

import com.aandios.nous.api.market.adapters.ChartAdapter
import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.provider.binance.model.BinanceCandle
import com.aandios.nous.provider.binance.model.BinanceWebSocketCandle
import com.aandios.nous.provider.binance.model.BinanceWebSocketResponse
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json

class BinanceChartAdapter(
    private val client: HttpClient,
    private val config: ProviderConfig,
): ChartAdapter {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override suspend fun getCandles(
        symbol: String,
        interval: String,
        limit: Int
    ): List<Candle> {
        // Меняем на фьючерсный endpoint
        val response: List<List<String>> = client.get("https://fapi.binance.com/fapi/v1/klines") {
            url {
                parameters.append("symbol", symbol)
                parameters.append("interval", interval)
                parameters.append("limit", limit.toString())
            }
        }.body()

        return response.map { rawCandle ->
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
    ): List<Candle> {
        val response: List<List<String>> = client.get("https://fapi.binance.com/fapi/v1/klines") {
            url {
                parameters.append("symbol", symbol)
                parameters.append("interval", interval)
                parameters.append("endTime", endTime.toString())
                parameters.append("limit", limit.toString())
            }
        }.body()

        return response.map { rawCandle ->
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
    ): Flow<Candle> = callbackFlow {
        // Kline - market stream: wss://fstream.binance.com/market/ws/<symbol>@kline_<interval>
        val streamName = "${symbol.lowercase()}@kline_${interval}"
        val endpoint = "wss://fstream.binance.com/market/ws/$streamName"

        try {
            client.webSocket(urlString = endpoint) {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            try {
                                val text = frame.readText()
                                val wsResponse = json.decodeFromString<BinanceWebSocketResponse>(text)

                                if (wsResponse.eventType == "kline") {
                                    val kline = wsResponse.kline

                                    val webSocketCandle = BinanceWebSocketCandle(
                                        symbol = wsResponse.symbol,
                                        openTime = kline.startTime,
                                        closeTime = kline.endTime,
                                        open = kline.open,
                                        high = kline.high,
                                        low = kline.low,
                                        close = kline.close,
                                        volume = kline.volume,
                                        isClosed = kline.isClosed
                                    )

                                    trySend(webSocketCandle.toCandle())
                                }
                            } catch (_: Exception) {
                                // Игнорируем ошибки парсинга
                            }
                        }
                        else -> {}
                    }
                }
            }
        } catch (_: Exception) {
            // WebSocket ошибки логируются выше
        }

        close()
    }
}
