/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.commands

import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType
import kotlin.time.Clock

// Базовый интерфейс команды
interface TradingCommand {
    suspend fun execute()
    fun canExecute(): Boolean
    fun getDescription(): String
}

// Данные ордера
data class OrderData(
    val symbol: String,
    val side: OrderSide,
    val type: OrderType,
    val price: Double? = null,  // null для market ордеров
    val quantity: Double,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds()
)

// Результат выполнения
sealed class CommandResult {
    data class Success(val orderData: OrderData) : CommandResult()
    data class Error(val message: String) : CommandResult()
    object TradingDisabled : CommandResult()
}