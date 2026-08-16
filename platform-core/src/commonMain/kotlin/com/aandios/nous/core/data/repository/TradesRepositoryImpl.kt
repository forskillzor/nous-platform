/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.data.repository

import com.aandios.nous.api.market.adapters.TradesAdapter
import com.aandios.nous.api.market.model.trades.Trade
import com.aandios.nous.core.domain.repository.TradesRepository
import kotlinx.coroutines.flow.Flow

/**
 * Реализация [TradesRepository].
 *
 * Упрощённая версия без кеширования потоков: каждый вызов [getTradesStream]
 * возвращает свежий Flow от адаптера. TradesViewModel управляет жизненным
 * циклом подписки через [kotlinx.coroutines.Job], отменяя предыдущую при смене символа.
 *
 * Ранее использовался паттерн `MutableStateFlow<Map<...>>` + `flatMapLatest`,
 * который содержал race condition: мутация StateFlow внутри лямбды flatMapLatest
 * вызывала рекурсивную переподписку и обрыв WebSocket-соединения.
 */
class TradesRepositoryImpl(
    private val tradesAdapter: TradesAdapter
) : TradesRepository {

    override fun getTradesStream(symbol: String): Flow<Trade> {
        println("📊 Trades Repository: getTradesStream for $symbol")
        return tradesAdapter.subscribeToTrades(symbol)
    }
}