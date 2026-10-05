/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc

/**
 * Подписка на канал MEXC WebSocket: ключ маршрутизации + JSON-сообщения
 * subscribe/unsubscribe.
 */
data class MexcSub(
    /** Ключ маршрутизации входящих push-сообщений (например "kline:BTC_USDT:Min5"). */
    val key: String,
    /** JSON-сообщение подписки (метод sub.*). */
    val subscribeJson: String,
    /** JSON-сообщение отписки (метод unsub.*). */
    val unsubscribeJson: String,
)

/**
 * Фабрика подписок MEXC Futures WebSocket API (`wss://contract.mexc.com/edge`).
 */
object MexcSubscriptions {

    const val PING = """{"method":"ping"}"""

    fun kline(symbol: String, mexcInterval: String): MexcSub = MexcSub(
        key = "kline:$symbol:$mexcInterval",
        subscribeJson = """{"method":"sub.kline","param":{"symbol":"$symbol","interval":"$mexcInterval"}}""",
        unsubscribeJson = """{"method":"unsub.kline","param":{"symbol":"$symbol","interval":"$mexcInterval"}}""",
    )

    fun deal(symbol: String): MexcSub = MexcSub(
        key = "deal:$symbol",
        subscribeJson = """{"method":"sub.deal","param":{"symbol":"$symbol"}}""",
        unsubscribeJson = """{"method":"unsub.deal","param":{"symbol":"$symbol"}}""",
    )

    /**
     * Полный срез стакана (топ-N). MEXC поддерживает limit 5/10/20 — берём
     * максимум, обрезку до нужной глубины делает адаптер.
     */
    fun depthFull(symbol: String, limit: Int = 20): MexcSub = MexcSub(
        key = "depth:$symbol",
        subscribeJson = """{"method":"sub.depth.full","param":{"symbol":"$symbol","limit":$limit}}""",
        unsubscribeJson = """{"method":"unsub.depth.full","param":{"symbol":"$symbol"}}""",
    )

    fun ticker(symbol: String): MexcSub = MexcSub(
        key = "ticker:$symbol",
        subscribeJson = """{"method":"sub.ticker","param":{"symbol":"$symbol"}}""",
        unsubscribeJson = """{"method":"unsub.ticker","param":{"symbol":"$symbol"}}""",
    )
}
