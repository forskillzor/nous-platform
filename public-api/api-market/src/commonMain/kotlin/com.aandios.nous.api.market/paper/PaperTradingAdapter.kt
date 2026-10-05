/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.paper

import com.aandios.nous.api.market.adapters.TradingAdapter
import com.aandios.nous.api.market.model.Balance
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.OrderRequest
import com.aandios.nous.api.market.model.trading.OrderResponse
import com.aandios.nous.api.market.model.trading.OrderStatus
import com.aandios.nous.api.market.model.trading.Position
import com.aandios.nous.api.market.model.trading.TradeFill
import com.aandios.nous.api.market.model.trading.TradeSide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Снимок всего портфолио paper-торговли.
 */
data class PaperPortfolio(
    val balances: List<Balance>,
    val positions: List<Position>,
    val openOrders: List<Order>,
    val history: List<TradeFill>,
)

/**
 * Paper trading (демо-торговля): in-memory реализация [TradingAdapter]
 * без подключения к бирже. Пригодится для тестов, UI-разработки и обучения.
 *
 * Провайдер-независимый: подключается вместо `provider.trading` через
 * [com.aandios.nous.api.market.paper.effectiveTrading] с любым провайдером.
 *
 * Упрощённая модель:
 *  * счёт — USDT, изолированная маржа 1x (open: available → margin);
 *  * MARKET — мгновенное исполнение по mark-цене (или price, если задан);
 *  * LIMIT — исполняется сразу, если цена пересечена (buy: mark ≤ price,
 *    sell: mark ≥ price), иначе висит в открытых и исполнится при
 *    [setMarkPrice];
 *  * reduce-only/closePosition — закрытие позиции с реализованным PnL;
 *  * [topUp] — пополнение баланса (тесты/демо).
 */
class PaperTradingAdapter(
    initialBalance: Balance = Balance("USDT", "10000.0", equity = "10000.0"),
) : TradingAdapter {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mutex = Mutex()

    private val _balances = MutableStateFlow<List<Balance>>(listOf(initialBalance))
    private val _positions = MutableStateFlow<List<Position>>(emptyList())
    private val _openOrders = MutableStateFlow<List<Order>>(emptyList())
    private val _history = MutableStateFlow<List<TradeFill>>(emptyList())

    val balancesFlow: StateFlow<List<Balance>> = _balances.asStateFlow()
    val positionsFlow: StateFlow<List<Position>> = _positions.asStateFlow()
    val openOrdersFlow: StateFlow<List<Order>> = _openOrders.asStateFlow()
    val historyFlow: StateFlow<List<TradeFill>> = _history.asStateFlow()

    private val markPrices = mutableMapOf<String, Double>()
    private val leverageMap = mutableMapOf<String, Int>()
    private var positionModeValue = 2 // 2 = one-way
    private var seq = 0L

    private fun newId(): String = "P${++seq}"

    private fun now(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    // ── Демо-операции ──

    /** Пополнение баланса (или создание нового актива). */
    suspend fun topUp(currency: String, amount: Double) = mutex.withLock {
        val upper = currency.uppercase()
        val existing = _balances.value.firstOrNull { it.currency == upper }
        _balances.value = if (existing != null) {
            val newAmount = (existing.amount.toDoubleOrNull() ?: 0.0) + amount
            _balances.value.map { if (it.currency == upper) it.copy(amount = fmt(newAmount), equity = fmt(newAmount)) else it }
        } else {
            _balances.value + Balance(upper, fmt(amount), equity = fmt(amount))
        }
    }

    /** Задать mark-цену символа: пересчитывает PnL и исполняет пересечённые лимитки. */
    suspend fun setMarkPrice(symbol: String, price: Double) = mutex.withLock {
        val sym = symbol.uppercase()
        markPrices[sym] = price
        repriceLocked(sym, price)
    }

    /** Полный сброс состояния (для тестов). */
    suspend fun reset() = mutex.withLock {
        _balances.value = listOf(Balance("USDT", "10000.0", equity = "10000.0"))
        _positions.value = emptyList()
        _openOrders.value = emptyList()
        _history.value = emptyList()
        markPrices.clear()
        leverageMap.clear()
        seq = 0
    }

    /** Снимок всего портфолио. */
    suspend fun portfolio(): PaperPortfolio = mutex.withLock {
        PaperPortfolio(
            balances = _balances.value,
            positions = _positions.value,
            openOrders = _openOrders.value,
            history = _history.value,
        )
    }

    // ── TradingAdapter ──

    override suspend fun placeOrder(request: OrderRequest): OrderResponse = mutex.withLock {
        val symbol = request.symbol.uppercase()
        if (request.quantity <= 0) {
            return@withLock OrderResponse("", success = false, message = "Quantity must be positive")
        }
        val mark = markPrices[symbol] ?: 0.0

        val isMarket = request.orderType == OrderType.MARKET
        val execPrice = when {
            isMarket -> mark.takeIf { it > 0 } ?: request.price.takeIf { it > 0 }
                ?: return@withLock OrderResponse("", success = false, message = "No price for $symbol (setMarkPrice first)")
            request.price > 0 -> request.price
            else -> return@withLock OrderResponse("", success = false, message = "Price required for limit order")
        }

        val crossed = when (request.side) {
            OrderSide.BUY -> mark > 0 && mark <= execPrice
            OrderSide.SELL -> mark > 0 && mark >= execPrice
        }
        val immediate = isMarket || crossed

        if (immediate) {
            executeFillLocked(symbol, request, execPrice, mark)
            OrderResponse(newId(), execPrice, success = true)
        } else {
            val order = Order(
                orderId = newId(),
                symbol = symbol,
                side = request.side,
                orderType = request.orderType,
                price = execPrice,
                quantity = request.quantity,
                reduceOnly = request.reduceOnly,
                status = OrderStatus.OPEN,
                clientOrderId = request.clientOrderId,
                timestamp = now(),
            )
            _openOrders.value = _openOrders.value + order
            OrderResponse(order.orderId, execPrice, success = true)
        }
    }

    override suspend fun cancelOrder(orderId: String): Boolean = mutex.withLock {
        val before = _openOrders.value.size
        _openOrders.value = _openOrders.value.filterNot { it.orderId == orderId }
        _openOrders.value.size < before
    }

    override suspend fun cancelAllOrders(symbol: String?): Boolean = mutex.withLock {
        _openOrders.value = if (symbol != null) {
            val sym = symbol.uppercase()
            _openOrders.value.filterNot { it.symbol == sym }
        } else emptyList()
        true
    }

    override suspend fun closePosition(
        symbol: String,
        positionId: Long?,
        quantity: Double?,
    ): OrderResponse? = mutex.withLock {
        val sym = symbol.uppercase()
        val pos = _positions.value.firstOrNull {
            it.symbol == sym && (positionId == null || it.positionId == positionId)
        } ?: return@withLock null
        val mark = markPrices[sym] ?: pos.markPrice.takeIf { it > 0 } ?: pos.avgPrice
        val qty = quantity ?: pos.quantity
        val side = if (pos.side == TradeSide.BUY) OrderSide.SELL else OrderSide.BUY
        executeFillLocked(
            sym,
            OrderRequest(sym, side, OrderType.MARKET, qty, reduceOnly = true, positionId = pos.positionId),
            mark,
            mark,
        )
        OrderResponse(newId(), mark, success = true)
    }

    override suspend fun getBalances(): List<Balance> = mutex.withLock { _balances.value }

    override suspend fun getPositions(): List<Position> = mutex.withLock { _positions.value }

    override suspend fun getOpenOrders(symbol: String?): List<Order> = mutex.withLock {
        if (symbol == null) _openOrders.value
        else _openOrders.value.filter { it.symbol == symbol.uppercase() }
    }

    override suspend fun getTradeHistory(symbol: String?, limit: Int): List<TradeFill> = mutex.withLock {
        val filtered = if (symbol == null) _history.value
        else _history.value.filter { it.symbol == symbol.uppercase() }
        filtered.takeLast(limit.coerceAtLeast(1))
    }

    override suspend fun setLeverage(symbol: String, leverage: Int, positionId: Long?): Boolean = mutex.withLock {
        leverageMap[symbol.uppercase()] = leverage
        true
    }

    override suspend fun getLeverage(symbol: String): Int? = mutex.withLock {
        leverageMap[symbol.uppercase()]
    }

    override suspend fun getPositionMode(): Int? = mutex.withLock { positionModeValue }

    override suspend fun setPositionMode(mode: Int): Boolean = mutex.withLock {
        if (_openOrders.value.isNotEmpty() || _positions.value.isNotEmpty()) {
            return@withLock false
        }
        positionModeValue = mode
        true
    }

    override suspend fun adjustMargin(positionId: Long, amount: Double, add: Boolean): Boolean = mutex.withLock {
        val pos = _positions.value.firstOrNull { it.positionId == positionId } ?: return@withLock false
        val usdt = balancesIndex("USDT")
        if (usdt < 0) return@withLock false
        val b = _balances.value[usdt]
        val margin = b.margin.toDoubleOrNull() ?: 0.0
        val available = b.amount.toDoubleOrNull() ?: 0.0
        if (add) {
            if (available < amount) return@withLock false
            updateBalance("USDT", available - amount, margin + amount)
        } else {
            val released = amount.coerceAtMost(margin)
            updateBalance("USDT", available + released, margin - released)
        }
        true
    }

    override fun subscribeToPositions(): Flow<Position>? = null
    override fun subscribeToOrders(): Flow<Order>? = null
    override fun subscribeToBalances(): Flow<Balance>? = null

    // ── Внутреннее ──

    private fun executeFillLocked(symbol: String, request: OrderRequest, execPrice: Double, mark: Double) {
        val qty = request.quantity
        val fillId = newId()

        if (request.reduceOnly) {
            val pos = _positions.value.firstOrNull { it.symbol == symbol } ?: return
            val pnl = if (pos.side == TradeSide.BUY) (execPrice - pos.avgPrice) * qty
            else (pos.avgPrice - execPrice) * qty
            val marginReleased = pos.avgPrice * qty.coerceAtMost(pos.quantity)
            val b = balancesIndex("USDT")
            val available = (_balances.value[b].amount.toDoubleOrNull() ?: 0.0) + marginReleased + pnl
            val margin = (_balances.value[b].margin.toDoubleOrNull() ?: 0.0) - marginReleased
            updateBalance("USDT", available, margin.coerceAtLeast(0.0))

            val remaining = pos.quantity - qty
            _positions.value = if (remaining <= 1e-9) {
                _positions.value.filterNot { it.positionId == pos.positionId }
            } else {
                _positions.value.map {
                    if (it.positionId == pos.positionId) it.copy(quantity = remaining) else it
                }
            }
            _history.value = _history.value + TradeFill(
                id = fillId,
                symbol = symbol,
                side = request.side,
                price = execPrice,
                quantity = qty,
                pnl = pnl,
                timestamp = now(),
            )
        } else {
            val cost = execPrice * qty
            val b = balancesIndex("USDT")
            val available = (_balances.value[b].amount.toDoubleOrNull() ?: 0.0) - cost
            val margin = (_balances.value[b].margin.toDoubleOrNull() ?: 0.0) + cost
            updateBalance("USDT", available.coerceAtLeast(0.0), margin)

            val side = if (request.side == OrderSide.BUY) TradeSide.BUY else TradeSide.SELL
            val existing = _positions.value.firstOrNull { it.symbol == symbol && it.side == side }
            _positions.value = if (existing != null) {
                val totalQty = existing.quantity + qty
                val avg = (existing.avgPrice * existing.quantity + execPrice * qty) / totalQty
                _positions.value.map {
                    if (it.positionId == existing.positionId) {
                        it.copy(quantity = totalQty, avgPrice = avg, markPrice = mark)
                    } else it
                }
            } else {
                _positions.value + Position(
                    symbol = symbol,
                    side = side,
                    positionId = fillId.toLongOrNull(),
                    quantity = qty,
                    avgPrice = execPrice,
                    markPrice = mark,
                    leverage = leverageMap[symbol],
                    marginMode = request.marginMode,
                )
            }
            _history.value = _history.value + TradeFill(
                id = fillId,
                symbol = symbol,
                side = request.side,
                price = execPrice,
                quantity = qty,
                pnl = 0.0,
                timestamp = now(),
            )
        }
    }

    /** Пересчёт mark → unrealized PnL + исполнение пересечённых лимиток. */
    private fun repriceLocked(symbol: String, price: Double) {
        _positions.value = _positions.value.map { pos ->
            if (pos.symbol != symbol) pos
            else {
                val unrealized = if (pos.side == TradeSide.BUY) (price - pos.avgPrice) * pos.quantity
                else (pos.avgPrice - price) * pos.quantity
                pos.copy(markPrice = price, unrealizedPnl = unrealized)
            }
        }

        val toExecute = mutableListOf<Pair<Order, OrderRequest>>()
        _openOrders.value = _openOrders.value.filterNot { order ->
            if (order.symbol != symbol) return@filterNot false
            val crossed = when (order.side) {
                OrderSide.BUY -> price <= order.price
                OrderSide.SELL -> price >= order.price
            }
            if (crossed) {
                toExecute += order to OrderRequest(
                    symbol = symbol,
                    side = order.side,
                    orderType = order.orderType,
                    quantity = order.quantity,
                    price = order.price,
                    reduceOnly = order.reduceOnly,
                    clientOrderId = order.clientOrderId,
                )
                true
            } else false
        }
        toExecute.forEach { (_, req) -> executeFillLocked(symbol, req, req.price, price) }
    }

    private fun balancesIndex(currency: String): Int =
        _balances.value.indexOfFirst { it.currency == currency }

    private fun updateBalance(currency: String, available: Double, margin: Double) {
        val idx = balancesIndex(currency)
        if (idx < 0) return
        _balances.value = _balances.value.mapIndexed { i, b ->
            if (i != idx) b else b.copy(
                amount = fmt(available),
                margin = fmt(margin),
                equity = fmt(available + margin),
            )
        }
    }

    private fun fmt(v: Double): String {
        val rounded = kotlin.math.round(v * 1e8) / 1e8
        var s = rounded.toString()
        if ('.' in s) s = s.trimEnd('0').trimEnd('.')
        return s
    }
}
