/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc.model

import com.aandios.nous.api.market.model.BookTicker
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.api.market.model.orderbook.BookWindowLevels
import com.aandios.nous.api.market.model.orderbook.PriceUpdate
import com.aandios.nous.api.market.model.trades.Trade
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.provider.mexc.MexcTimeframes
import com.aandios.nous.provider.mexc.fromMexcSymbol
import kotlinx.serialization.Serializable

// ── REST: kline ─────────────────────────────────────────────────────────────

/**
 * Ответ MEXC Futures `/api/v1/contract/kline/{symbol}`:
 * параллельные массивы, а не объекты на бар.
 */
@Serializable
data class MexcKlineResponse(
    val success: Boolean = false,
    val code: Int = 0,
    val data: MexcKlineData? = null,
) {
    fun toCandles(): List<Candle> {
        val d = data ?: return emptyList()
        val count = minOf(
            d.time.size, d.open.size, d.high.size, d.low.size, d.close.size, d.vol.size
        )
        return (0 until count).map { i ->
            Candle(
                open = d.open[i].toFloat(),
                high = d.high[i].toFloat(),
                low = d.low[i].toFloat(),
                close = d.close[i].toFloat(),
                timestamp = MexcTimeframes.timeToMillis(d.time[i]),
                volume = d.vol[i].toFloat(),
            )
        }
    }
}

@Serializable
data class MexcKlineData(
    val time: List<Long> = emptyList(),
    val open: List<Double> = emptyList(),
    val close: List<Double> = emptyList(),
    val high: List<Double> = emptyList(),
    val low: List<Double> = emptyList(),
    val vol: List<Double> = emptyList(),
    val amount: List<Double> = emptyList(),
)

// ── REST/WS: сделки ─────────────────────────────────────────────────────────

/**
 * Сделка MEXC Futures (REST `/deals` и WS `push.deal`).
 * `T` — направление: 1 = buy, 2 = sell. `i` — id сделки (только REST),
 * `t` — время в миллисекундах.
 */
@Serializable
data class MexcDeal(
    val p: Double = 0.0,
    val v: Double = 0.0,
    val T: Int = 0,
    val O: Int = 0,
    val M: Int = 0,
    val t: Long = 0,
    val i: String? = null,
) {
    fun toTrade(symbol: String, idFallback: Long): Trade {
        val isSell = T == 2
        return Trade(
            id = i?.toLongOrNull() ?: idFallback,
            symbol = symbol,
            price = p,
            quantity = v,
            timestamp = t,
            isBuyerMaker = isSell,
            side = if (isSell) TradeSide.SELL else TradeSide.BUY,
        )
    }
}

@Serializable
data class MexcDealsResponse(
    val success: Boolean = false,
    val code: Int = 0,
    val data: List<MexcDeal> = emptyList(),
)

// ── REST/WS: стакан ─────────────────────────────────────────────────────────

/**
 * Уровни стакана MEXC: строка = `[price, volume, orderCount]` (orderCount
 * может отсутствовать). Цены приходят числами (не строками).
 */
@Serializable
data class MexcDepthData(
    val asks: List<List<Double>> = emptyList(),
    val bids: List<List<Double>> = emptyList(),
) {
    fun toBookWindowLevels(): BookWindowLevels = BookWindowLevels(
        bids = bids.mapNotNull { row ->
            val p = row.getOrNull(0) ?: return@mapNotNull null
            val q = row.getOrNull(1) ?: return@mapNotNull null
            PriceUpdate(p, q)
        },
        asks = asks.mapNotNull { row ->
            val p = row.getOrNull(0) ?: return@mapNotNull null
            val q = row.getOrNull(1) ?: return@mapNotNull null
            PriceUpdate(p, q)
        },
    )
}

@Serializable
data class MexcDepthResponse(
    val success: Boolean = false,
    val code: Int = 0,
    val data: MexcDepthData? = null,
)

// ── REST/WS: тикер ──────────────────────────────────────────────────────────

@Serializable
data class MexcTickerData(
    val symbol: String = "",
    val lastPrice: Double = 0.0,
    val bid1: Double = 0.0,
    val ask1: Double = 0.0,
    val timestamp: Long = 0,
) {
    fun toBookTicker(): BookTicker = BookTicker(
        symbol = fromMexcSymbol(symbol), // единый формат платформы (BTCUSDT)
        bestBid = bid1,
        bestBidQty = 0.0, // MEXC ticker не отдаёт объёмы по лучшим ценам
        bestAsk = ask1,
        bestAskQty = 0.0,
        lastPrice = lastPrice,
        timestamp = timestamp,
    )
}

@Serializable
data class MexcTickerResponse(
    val success: Boolean = false,
    val code: Int = 0,
    val data: MexcTickerData? = null,
)

// ── REST: контракты (symbol info) ───────────────────────────────────────────

/**
 * Контракт MEXC Futures (`/api/v1/contract/detail`).
 *
 * MEXC торгует в контрактах: qty/vol у биржи — контракты, шаг и минимум —
 * в контрактах. Платформа работает в базовом активе, поэтому stepSize и
 * minQty переводятся через contractSize (шаг = volUnit * contractSize),
 * сам contractSize прокидывается в SymbolInfo — адаптер конвертирует
 * qty ордеров/позиций в контракты и обратно.
 */
@Serializable
data class MexcContractDetail(
    val symbol: String = "",
    val displayName: String = "",
    val displayNameEn: String = "",
    val baseCoin: String = "",
    val quoteCoin: String = "",
    val settleCoin: String = "",
    val contractSize: Double = 0.0,
    val priceScale: Int = 0,
    val volScale: Int = 0,
    val priceUnit: Double = 0.0,
    val volUnit: Double = 0.0,
    val minVol: Double = 0.0,
    val maxVol: Double = 0.0,
    val state: Int = -1,
) {
    fun toSymbolInfo(): SymbolInfo {
        val cs = contractSize.takeIf { it > 0.0 } ?: 1.0
        // Inverse (COIN-M, напр. BTC_USD): settleCoin — монета, qty в контрактах,
        // contractSize — USD-номинал. Linear (USDT-M/USDC-M) — qty в базовом активе.
        val inverse = settleCoin.isNotBlank() && settleCoin.uppercase() !in STABLE_MARGIN_ASSETS
        // Linear: шаг/минимум в базовом активе (volUnit * contractSize).
        // Inverse: как у MEXC — в контрактах.
        val step = if (inverse) {
            volUnit.takeIf { it > 0 } ?: 1.0
        } else {
            (volUnit.takeIf { it > 0 } ?: 0.001) * cs
        }
        return SymbolInfo(
            symbol = fromMexcSymbol(symbol), // единый формат платформы (BTCUSDT)
            tickSize = priceUnit.takeIf { it > 0 } ?: 0.01,
            stepSize = step,
            minQty = if (inverse) minVol else minVol * cs,
            minNotional = 0.0, // у MEXC нет фильтра минимального notionла
            status = if (state == 0) "TRADING" else "HALT",
            baseAsset = baseCoin,
            quoteAsset = quoteCoin,
            contractType = "PERPETUAL",
            marginAsset = settleCoin,
            contractSize = cs,
        )
    }
}

@Serializable
data class MexcDetailResponse(
    val success: Boolean = false,
    val code: Int = 0,
    val data: List<MexcContractDetail> = emptyList(),
)

/** Стейбл-маржа: всё остальное (BTC/ETH/…) — inverse (COIN-M). */
private val STABLE_MARGIN_ASSETS = setOf("USDT", "USDC", "USD1", "DAI", "USD")

// ── WS: push-конверты ───────────────────────────────────────────────────────

@Serializable
data class MexcKlinePush(
    val channel: String = "",
    val symbol: String = "",
    val data: MexcKlinePushData? = null,
    val ts: Long = 0,
)

@Serializable
data class MexcKlinePushData(
    val a: Double = 0.0, // amount
    val c: Double = 0.0, // close
    val h: Double = 0.0,
    val l: Double = 0.0,
    val o: Double = 0.0,
    val q: Double = 0.0, // volume
    val t: Long = 0,     // window start (seconds)
    val interval: String = "",
) {
    fun toCandle(): Candle = Candle(
        open = o.toFloat(),
        high = h.toFloat(),
        low = l.toFloat(),
        close = c.toFloat(),
        timestamp = MexcTimeframes.timeToMillis(t),
        volume = q.toFloat(),
    )
}

@Serializable
data class MexcDepthPush(
    val channel: String = "",
    val symbol: String = "",
    val data: MexcDepthData? = null,
    val ts: Long = 0,
)

@Serializable
data class MexcTickerPush(
    val channel: String = "",
    val symbol: String = "",
    val data: MexcTickerData? = null,
    val ts: Long = 0,
)
