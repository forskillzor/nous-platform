/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc.adapter

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.DomAdapter
import com.aandios.nous.api.market.model.orderbook.BookWindowLevels
import com.aandios.nous.provider.mexc.MEXC_BASE_URL
import com.aandios.nous.provider.mexc.MexcDepthBook
import com.aandios.nous.provider.mexc.MexcRestGate
import com.aandios.nous.provider.mexc.MexcStreamHub
import com.aandios.nous.provider.mexc.MexcSubscriptions
import com.aandios.nous.provider.mexc.MexcWeights
import com.aandios.nous.provider.mexc.currentTimeMillis
import com.aandios.nous.provider.mexc.domQuantityScale
import com.aandios.nous.provider.mexc.model.MexcDepthData
import com.aandios.nous.provider.mexc.model.MexcDepthPush
import com.aandios.nous.provider.mexc.toMexcSymbol
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * DOM MEXC Futures: локальная книга из ИНКРЕМЕНТАЛЬНОГО канала `sub.depth`
 * (compress=false, ~сотни диффов/с) + REST-снапшот для старта и ресинков по
 * разрыву version. В UI отдаём полные окна топ-N не чаще 100 мс — как
 * partial-стримы Binance (`depth20@100ms`), поэтому DomViewModel/UI не знают,
 * что под ними инкремент.
 *
 * Процедура MEXC: снимок `contract/depth` (базовая version) → apply диффов,
 * version обязан быть ровно prev+1 (количества абсолютные, 0 = снять уровень).
 */
class MexcDomAdapter(
    private val client: HttpClient,
    @Suppress("unused") private val config: ProviderConfig,
    private val restGate: MexcRestGate,
    private val streamHub: MexcStreamHub,
) : DomAdapter {

    companion object {
        /** Окно в UI — не чаще, чем раз в 100 мс (как Binance partial). */
        private const val WINDOW_INTERVAL_MS = 100L

        /** Тик добора «хвоста»: применили диффы, но окно ещё не отдавали. */
        private const val TAIL_TICK_MS = 50L

        /** Буфер сырых диффов хаба: ~сотни msg/s, чтобы не терять version. */
        private const val DEPTH_BUFFER_CAPACITY = 512
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val symbolInfoAdapter = MexcSymbolInfoAdapter(client, config, restGate)

    override suspend fun subscribeToBookWindow(symbol: String, depth: Int): Flow<BookWindowLevels> =
        channelFlow {
            val mexcSymbol = toMexcSymbol(symbol)
            val book = MexcDepthBook()
            val raw = Channel<MexcDepthPush>(Channel.UNLIMITED)

            // Объёмы MEXC depth — в контрактах; в UI отдаём в единицах
            // SymbolInfo (базовый актив / контракты для inverse) — инвариант DOM
            val quantityScale = domQuantityScale(
                runCatching { symbolInfoAdapter.getSymbolInfo(symbol) }.getOrNull()
            )

            // Сырые диффы принимаем сразу — ни один не потеряется до снапшота
            launch {
                streamHub.subscribe(
                    MexcSubscriptions.depth(mexcSymbol),
                    bufferCapacity = DEPTH_BUFFER_CAPACITY,
                ).collect { text ->
                    runCatching { json.decodeFromString<MexcDepthPush>(text) }
                        .getOrNull()
                        ?.let { raw.trySend(it) }
                }
            }

            // Стартовый снапшот через REST (version — база для диффов)
            var lastEmit = 0L
            fetchDepthSnapshot(mexcSymbol)?.let { snap ->
                book.reset(asks = snap.asks, bids = snap.bids, version = snap.version)
                send(book.window(depth, quantityScale))
                lastEmit = currentTimeMillis()
            }

            // Один потребитель диффов: применяем быстро, отдаём окно не чаще 100 мс
            var dirty = false
            while (isActive) {
                val push = withTimeoutOrNull(TAIL_TICK_MS) { raw.receive() }
                val data = push?.data
                if (data != null) {
                    when (book.apply(asks = data.asks, bids = data.bids, version = data.version)) {
                        MexcDepthBook.ApplyResult.APPLIED -> {
                            val now = currentTimeMillis()
                            if (now - lastEmit >= WINDOW_INTERVAL_MS) {
                                send(book.window(depth, quantityScale))
                                lastEmit = now
                                dirty = false
                            } else {
                                dirty = true
                            }
                        }
                        MexcDepthBook.ApplyResult.IGNORED -> Unit
                        MexcDepthBook.ApplyResult.GAP -> {
                            // Потеря version — переснапшот и продолжаем
                            fetchDepthSnapshot(mexcSymbol)?.let { snap ->
                                book.reset(asks = snap.asks, bids = snap.bids, version = snap.version)
                                send(book.window(depth, quantityScale))
                                lastEmit = currentTimeMillis()
                                dirty = false
                            }
                        }
                    }
                }
                // Хвостовой эмит: диффы затихли — доставляем последнее состояние
                if (dirty && currentTimeMillis() - lastEmit >= WINDOW_INTERVAL_MS) {
                    send(book.window(depth, quantityScale))
                    lastEmit = currentTimeMillis()
                    dirty = false
                }
            }
        }

    /** REST-снапшот стакана (asks/bids/version) через общий gate. */
    internal suspend fun fetchDepthSnapshot(mexcSymbol: String): MexcDepthData? =
        restGate.execute(key = "dom:depth:$mexcSymbol", weight = MexcWeights.DEPTH) {
            val element = client.get("$MEXC_BASE_URL/api/v1/contract/depth/$mexcSymbol").body<String>()
            val data = json.parseToJsonElement(element).jsonObject["data"]
                ?: return@execute null
            json.decodeFromJsonElement<MexcDepthData>(data)
        }
}
