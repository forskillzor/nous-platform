/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trading.paper

import com.aandios.nous.api.market.model.Balance
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.OrderStatus
import com.aandios.nous.api.market.model.trading.Position
import com.aandios.nous.api.market.model.trading.TradeFill
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.api.market.paper.PaperPlanOrder
import com.aandios.nous.api.market.paper.PaperState
import com.aandios.nous.api.market.paper.PaperTrading
import com.aandios.nous.core.storage.StateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Сохранение/восстановление состояния paper-движка между запусками
 * (SQLite через [StateStore], JSON). Работает с любым провайдером — движок
 * не знает про биржи.
 */
class PaperPersistenceService(
    private val stateStore: StateStore?,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var job: Job? = null

    fun start() {
        val store = stateStore ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            // Восстановление до первых действий пользователя
            runCatching {
                store.getString(KEY)?.takeIf { it.isNotBlank() }?.let { raw ->
                    PaperTrading.adapter.restore(json.decodeFromString<PaperStateDto>(raw).toState())
                }
            }
            startAutoSave()
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun startAutoSave() {
        merge(
            PaperTrading.adapter.balancesFlow,
            PaperTrading.adapter.positionsFlow,
            PaperTrading.adapter.openOrdersFlow,
            PaperTrading.adapter.historyFlow,
        ).debounce(SAVE_DEBOUNCE_MS).collect {
            persistNow()
        }
    }

    /** Немедленное сохранение (например, перед закрытием приложения). */
    suspend fun persistNow() {
        val store = stateStore ?: return
        runCatching {
            val state = PaperTrading.adapter.snapshot()
            store.putString(KEY, json.encodeToString(PaperStateDto.from(state)))
        }
    }

    companion object {
        const val KEY = "paper_state"
        private const val SAVE_DEBOUNCE_MS = 1000L
    }
}

// ── DTO (движок в api-market не зависит от сериализации) ──

@Serializable
private data class BalanceDto(
    val currency: String,
    val amount: String,
    val frozen: String = "0",
    val margin: String = "0",
    val equity: String = "0",
    val unrealizedPnl: String = "0",
)

@Serializable
private data class PositionDto(
    val symbol: String,
    val side: String,
    val positionId: Long? = null,
    val quantity: Double,
    val avgPrice: Double,
    val markPrice: Double,
    val leverage: Int? = null,
    val pnl: Double = 0.0,
    val unrealizedPnl: Double = 0.0,
    val liquidatePrice: Double? = null,
    val marginMode: Int? = null,
)

@Serializable
private data class OrderDto(
    val orderId: String,
    val symbol: String,
    val side: String,
    val orderType: String,
    val price: Double,
    val quantity: Double,
    val filledQuantity: Double = 0.0,
    val reduceOnly: Boolean = false,
    val status: String,
    val clientOrderId: String? = null,
    val timestamp: Long = 0,
)

@Serializable
private data class FillDto(
    val id: String,
    val orderId: String? = null,
    val symbol: String,
    val side: String,
    val price: Double,
    val quantity: Double,
    val fee: Double = 0.0,
    val feeCurrency: String = "",
    val pnl: Double = 0.0,
    val timestamp: Long = 0,
)

@Serializable
private data class PlanDto(
    val planId: String,
    val symbol: String,
    val side: String,
    val triggerPrice: Double,
    val takeProfit: Boolean,
    val positionId: Long? = null,
)

@Serializable
private data class PaperStateDto(
    val balances: List<BalanceDto> = emptyList(),
    val positions: List<PositionDto> = emptyList(),
    val openOrders: List<OrderDto> = emptyList(),
    val history: List<FillDto> = emptyList(),
    val plans: List<PlanDto> = emptyList(),
    val leverageMap: Map<String, Int> = emptyMap(),
    val positionMode: Int = 2,
    val seq: Long = 0,
) {
    fun toState(): PaperState = PaperState(
        balances = balances.map { Balance(it.currency, it.amount, it.frozen, it.margin, it.equity, it.unrealizedPnl) },
        positions = positions.map {
            Position(
                symbol = it.symbol,
                side = enumOr(it.side, TradeSide.BUY),
                positionId = it.positionId,
                quantity = it.quantity,
                avgPrice = it.avgPrice,
                markPrice = it.markPrice,
                leverage = it.leverage,
                pnl = it.pnl,
                unrealizedPnl = it.unrealizedPnl,
                liquidatePrice = it.liquidatePrice,
                marginMode = it.marginMode,
            )
        },
        openOrders = openOrders.map {
            Order(
                orderId = it.orderId,
                symbol = it.symbol,
                side = enumOr(it.side, OrderSide.BUY),
                orderType = enumOr(it.orderType, OrderType.LIMIT),
                price = it.price,
                quantity = it.quantity,
                filledQuantity = it.filledQuantity,
                reduceOnly = it.reduceOnly,
                status = enumOr(it.status, OrderStatus.OPEN),
                clientOrderId = it.clientOrderId,
                timestamp = it.timestamp,
            )
        },
        history = history.map {
            TradeFill(
                id = it.id,
                orderId = it.orderId,
                symbol = it.symbol,
                side = enumOr(it.side, OrderSide.BUY),
                price = it.price,
                quantity = it.quantity,
                fee = it.fee,
                feeCurrency = it.feeCurrency,
                pnl = it.pnl,
                timestamp = it.timestamp,
            )
        },
        plans = plans.map {
            PaperPlanOrder(
                planId = it.planId,
                symbol = it.symbol,
                side = enumOr(it.side, OrderSide.BUY),
                triggerPrice = it.triggerPrice,
                takeProfit = it.takeProfit,
                positionId = it.positionId,
            )
        },
        leverageMap = leverageMap,
        positionMode = positionMode,
        seq = seq,
    )

    companion object {
        fun from(state: PaperState): PaperStateDto = PaperStateDto(
            balances = state.balances.map {
                BalanceDto(it.currency, it.amount, it.frozen, it.margin, it.equity, it.unrealizedPnl)
            },
            positions = state.positions.map {
                PositionDto(
                    symbol = it.symbol,
                    side = it.side.name,
                    positionId = it.positionId,
                    quantity = it.quantity,
                    avgPrice = it.avgPrice,
                    markPrice = it.markPrice,
                    leverage = it.leverage,
                    pnl = it.pnl,
                    unrealizedPnl = it.unrealizedPnl,
                    liquidatePrice = it.liquidatePrice,
                    marginMode = it.marginMode,
                )
            },
            openOrders = state.openOrders.map {
                OrderDto(
                    orderId = it.orderId,
                    symbol = it.symbol,
                    side = it.side.name,
                    orderType = it.orderType.name,
                    price = it.price,
                    quantity = it.quantity,
                    filledQuantity = it.filledQuantity,
                    reduceOnly = it.reduceOnly,
                    status = it.status.name,
                    clientOrderId = it.clientOrderId,
                    timestamp = it.timestamp,
                )
            },
            history = state.history.map {
                FillDto(
                    id = it.id,
                    orderId = it.orderId,
                    symbol = it.symbol,
                    side = it.side.name,
                    price = it.price,
                    quantity = it.quantity,
                    fee = it.fee,
                    feeCurrency = it.feeCurrency,
                    pnl = it.pnl,
                    timestamp = it.timestamp,
                )
            },
            plans = state.plans.map {
                PlanDto(it.planId, it.symbol, it.side.name, it.triggerPrice, it.takeProfit, it.positionId)
            },
            leverageMap = state.leverageMap,
            positionMode = state.positionMode,
            seq = state.seq,
        )
    }
}

private inline fun <reified T : Enum<T>> enumOr(name: String, fallback: T): T =
    runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
