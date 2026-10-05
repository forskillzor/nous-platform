/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.paper

import com.aandios.nous.api.market.adapters.TradingAdapter
import com.aandios.nous.api.market.model.Balance
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.trading.FeeRates
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
 * Виртуальный TP/SL план-ордер, привязанный к позиции: срабатывает при
 * пересечении mark-ценой [triggerPrice] и закрывает позицию reduce-only.
 */
data class PaperPlanOrder(
    val planId: String,
    val symbol: String,
    /** Сторона закрытия позиции. */
    val side: OrderSide,
    val triggerPrice: Double,
    val takeProfit: Boolean,
    val positionId: Long?,
)

/**
 * Персистируемое состояние движка (для сброса/восстановления).
 */
data class PaperState(
    val balances: List<Balance> = emptyList(),
    val positions: List<Position> = emptyList(),
    val openOrders: List<Order> = emptyList(),
    val history: List<TradeFill> = emptyList(),
    val plans: List<PaperPlanOrder> = emptyList(),
    val leverageMap: Map<String, Int> = emptyMap(),
    val positionMode: Int = 2,
    val seq: Long = 0,
)

/**
 * Paper trading engine: in-memory реализация [TradingAdapter] без биржи,
 * повторяющая основные механики фьючерсной торговли:
 *
 *  * mark-цена приходит извне ([feedMarkPrice] / [setMarkPrice]) — от графика
 *    (свечи/footprint) или DOM (bookTicker), от активного провайдера;
 *  * MARKET — taker-филл по mark; LIMIT — рест, исполняется при пересечении
 *    (maker); POST_ONLY — пересечение при постановке отклоняется; IOC/FOK —
 *    всё-или-отмена;
 *  * позиции one-way (2): усреднение/уменьшение/переворот; hedge (1):
 *    раздельные long/short; reduce-only закрывает строго противоположную
 *    сторону;
 *  * маржа = entry×qty/leverage (по умолчанию 1x), available↔margin,
 *    реализованный PnL в баланс и историю;
 *  * TP/SL — виртуальные план-ордера, закрывающие позицию при триггере;
 *  * комиссии — из ставок активной биржи ([setFeeRates]; taker/maker),
 *    без ставок — 0.
 *
 * Провайдер-независим: подключается вместо `provider.trading` через
 * [com.aandios.nous.api.market.paper.effectiveTrading] с любым провайдером.
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

    /** Живые обновления (для UI-подписок): ордера, позиции, балансы. */
    private val _orderUpdates = MutableSharedFlow<Order>(extraBufferCapacity = 64)
    private val _positionUpdates = MutableSharedFlow<Position>(extraBufferCapacity = 64)
    private val _balanceUpdates = MutableSharedFlow<Balance>(extraBufferCapacity = 64)

    val balancesFlow: StateFlow<List<Balance>> = _balances.asStateFlow()
    val positionsFlow: StateFlow<List<Position>> = _positions.asStateFlow()
    val openOrdersFlow: StateFlow<List<Order>> = _openOrders.asStateFlow()
    val historyFlow: StateFlow<List<TradeFill>> = _history.asStateFlow()

    private val markPrices = mutableMapOf<String, Double>()
    private val leverageMap = mutableMapOf<String, Int>()
    private val feeRates = mutableMapOf<String, FeeRates>()
    private var plans = mutableListOf<PaperPlanOrder>()
    private var positionModeValue = 2 // 2 = one-way
    private var seq = 0L

    private fun newId(): String = "P${++seq}"

    private fun newPlanId(): String = "PL${++seq}"

    private fun now(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    // ── Цены (фиды от графика/DOM) и комиссии ──

    /**
     * Подать mark-цену из фида (график/DOM). Не-suspend; одинаковые значения
     * отсекаются в [setMarkPrice] под локом. Работает только при Paper ON.
     */
    fun feedMarkPrice(symbol: String, price: Double) {
        if (!PaperTrading.enabled || price <= 0.0) return
        scope.launch { setMarkPrice(symbol, price) }
    }

    /** Задать mark-цену символа: пересчёт PnL, исполнение лимиток и TP/SL. */
    suspend fun setMarkPrice(symbol: String, price: Double) = mutex.withLock {
        if (price <= 0.0) return@withLock
        val sym = symbol.uppercase()
        if (markPrices[sym] == price) return@withLock
        markPrices[sym] = price
        repriceLocked(sym, price)
    }

    /**
     * Ставки комиссий символа от активного провайдера (maker/taker).
     * Каждая биржа даёт свои — движок лишь применяет их.
     */
    suspend fun setFeeRates(symbol: String, rates: FeeRates?) = mutex.withLock {
        val sym = symbol.uppercase()
        if (rates == null) feeRates.remove(sym) else feeRates[sym] = rates
    }

    private fun feeRateLocked(symbol: String, taker: Boolean): Double {
        val r = feeRates[symbol] ?: return 0.0
        return if (taker) r.taker else r.maker
    }

    // ── Управление балансом (paper) ──

    /** Пополнение баланса (или создание нового актива). */
    suspend fun topUp(currency: String, amount: Double) = mutex.withLock {
        val upper = currency.uppercase()
        val existing = _balances.value.firstOrNull { it.currency == upper }
        val updated = if (existing != null) {
            val newAmount = (existing.amount.toDoubleOrNull() ?: 0.0) + amount
            existing.copy(amount = fmt(newAmount), equity = fmt(newAmount + (existing.margin.toDoubleOrNull() ?: 0.0)))
        } else {
            Balance(upper, fmt(amount), equity = fmt(amount))
        }
        _balances.value = if (existing != null) {
            _balances.value.map { if (it.currency == upper) updated else it }
        } else {
            _balances.value + updated
        }
        emitBalance(upper)
    }

    /** Задать точный available-баланс (equity = amount + занятая маржа). */
    suspend fun setBalance(currency: String, amount: Double) = mutex.withLock {
        val upper = currency.uppercase()
        val idx = balancesIndex(upper)
        val current = if (idx >= 0) _balances.value[idx] else null
        val margin = current?.margin?.toDoubleOrNull() ?: 0.0
        val updated = Balance(
            currency = upper,
            amount = fmt(amount),
            frozen = current?.frozen ?: "0",
            margin = fmt(margin),
            equity = fmt(amount + margin),
        )
        _balances.value = if (idx >= 0) {
            _balances.value.mapIndexed { i, b -> if (i == idx) updated else b }
        } else {
            _balances.value + updated
        }
        emitBalance(upper)
    }

    /**
     * Сброс баланса: закрывает все позиции (по mark/avg), снимает ордера
     * и план-ордера, ставит USDT = [usdtAmount]. История сделок сохраняется —
     * её чистит отдельный [resetHistory].
     */
    suspend fun resetBalance(usdtAmount: Double = 10_000.0) = mutex.withLock {
        _openOrders.value.forEach { _orderUpdates.tryEmit(it.copy(status = OrderStatus.CANCELED)) }
        _openOrders.value = emptyList()
        _positions.value.forEach { p ->
            _positionUpdates.tryEmit(p.copy(quantity = 0.0))
        }
        _positions.value = emptyList()
        plans.clear()
        _balances.value = listOf(Balance("USDT", fmt(usdtAmount), equity = fmt(usdtAmount)))
        emitBalance("USDT")
    }

    /** Сброс только истории сделок. */
    suspend fun resetHistory() = mutex.withLock {
        _history.value = emptyList()
    }

    /** Полный сброс (для настроек/тестов). */
    suspend fun reset(usdtAmount: Double = 10_000.0) = mutex.withLock {
        _openOrders.value.forEach { _orderUpdates.tryEmit(it.copy(status = OrderStatus.CANCELED)) }
        _openOrders.value = emptyList()
        _positions.value.forEach { p -> _positionUpdates.tryEmit(p.copy(quantity = 0.0)) }
        _positions.value = emptyList()
        _history.value = emptyList()
        plans.clear()
        markPrices.clear()
        leverageMap.clear()
        _balances.value = listOf(Balance("USDT", fmt(usdtAmount), equity = fmt(usdtAmount)))
        emitBalance("USDT")
        positionModeValue = 2
        seq = 0
    }

    /** Снимок всего портфолио (как в UI-таблицах). */
    suspend fun portfolio(): PaperPortfolio = mutex.withLock {
        PaperPortfolio(
            balances = _balances.value,
            positions = _positions.value,
            openOrders = _openOrders.value,
            history = _history.value,
        )
    }

    /** Полное состояние движка (для персиста). */
    suspend fun snapshot(): PaperState = mutex.withLock {
        PaperState(
            balances = _balances.value,
            positions = _positions.value,
            openOrders = _openOrders.value,
            history = _history.value,
            plans = plans.toList(),
            leverageMap = leverageMap.toMap(),
            positionMode = positionModeValue,
            seq = seq,
        )
    }

    /** Восстановление состояния (персист между запусками). */
    suspend fun restore(state: PaperState) = mutex.withLock {
        _balances.value = state.balances.ifEmpty { listOf(Balance("USDT", "10000.0", equity = "10000.0")) }
        _positions.value = state.positions
        _openOrders.value = state.openOrders
        _history.value = state.history
        plans = state.plans.toMutableList()
        leverageMap.clear()
        leverageMap.putAll(state.leverageMap)
        positionModeValue = state.positionMode
        seq = state.seq
        _balances.value.forEach { _balanceUpdates.tryEmit(it) }
        _positions.value.forEach { _positionUpdates.tryEmit(it) }
        _openOrders.value.forEach { _orderUpdates.tryEmit(it) }
    }

    // ── TradingAdapter: ордера ──

    override suspend fun placeOrder(request: OrderRequest): OrderResponse = mutex.withLock {
        val symbol = request.symbol.uppercase()
        if (request.quantity <= 0) {
            return@withLock OrderResponse("", success = false, message = "Quantity must be positive")
        }
        val mark = markPrices[symbol] ?: 0.0
        val type = request.orderType
        val isMarket = type == OrderType.MARKET

        if (isMarket) {
            val exec = mark.takeIf { it > 0 } ?: request.price.takeIf { it > 0 }
                ?: return@withLock OrderResponse("", success = false, message = "No price for $symbol")
            return@withLock fillOrderLocked(symbol, request, exec, mark.takeIf { it > 0 } ?: exec, taker = true)
        }

        if (request.price <= 0) {
            return@withLock OrderResponse("", success = false, message = "Price required for limit order")
        }
        val crossed = mark > 0.0 && when (request.side) {
            OrderSide.BUY -> request.price >= mark
            OrderSide.SELL -> request.price <= mark
        }

        when (type) {
            OrderType.POST_ONLY -> {
                if (crossed) {
                    return@withLock OrderResponse("", success = false, message = "Post-only order would immediately match")
                }
                restOrderLocked(symbol, request)
            }
            OrderType.IOC -> {
                if (!crossed) return@withLock OrderResponse("", success = false, message = "IOC order could not be filled")
                fillOrderLocked(symbol, request, mark, mark, taker = true)
            }
            OrderType.FOK -> {
                if (!crossed) return@withLock OrderResponse("", success = false, message = "FOK order could not be filled")
                fillOrderLocked(symbol, request, mark, mark, taker = true)
            }
            else -> {
                if (crossed) fillOrderLocked(symbol, request, mark, mark, taker = true)
                else restOrderLocked(symbol, request)
            }
        }
    }

    override suspend fun cancelOrder(orderId: String): Boolean = mutex.withLock {
        val order = _openOrders.value.firstOrNull { it.orderId == orderId } ?: return@withLock false
        _openOrders.value = _openOrders.value.filterNot { it.orderId == orderId }
        _orderUpdates.tryEmit(order.copy(status = OrderStatus.CANCELED))
        true
    }

    override suspend fun cancelAllOrders(symbol: String?): Boolean = mutex.withLock {
        val removed = if (symbol != null) {
            val sym = symbol.uppercase()
            val gone = _openOrders.value.filter { it.symbol == sym }
            _openOrders.value = _openOrders.value.filterNot { it.symbol == sym }
            gone
        } else {
            val gone = _openOrders.value
            _openOrders.value = emptyList()
            gone
        }
        removed.forEach { _orderUpdates.tryEmit(it.copy(status = OrderStatus.CANCELED)) }
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
        val qty = (quantity ?: pos.quantity).coerceAtMost(pos.quantity)
        val side = if (pos.side == TradeSide.BUY) OrderSide.SELL else OrderSide.BUY
        fillOrderLocked(
            sym,
            OrderRequest(sym, side, OrderType.MARKET, qty, reduceOnly = true, positionId = pos.positionId),
            mark,
            mark,
            taker = true,
        )
    }

    // ── TradingAdapter: счёт/позиции/история/настройки ──

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

    override fun subscribeToPositions(): Flow<Position>? = _positionUpdates.asSharedFlow()
    override fun subscribeToOrders(): Flow<Order>? = _orderUpdates.asSharedFlow()
    override fun subscribeToBalances(): Flow<Balance>? = _balanceUpdates.asSharedFlow()

    // ── Внутреннее: постановка/филл/репрайс ──

    private fun restOrderLocked(symbol: String, request: OrderRequest): OrderResponse {
        val order = Order(
            orderId = newId(),
            symbol = symbol,
            side = request.side,
            orderType = request.orderType,
            price = request.price,
            quantity = request.quantity,
            reduceOnly = request.reduceOnly,
            status = OrderStatus.OPEN,
            clientOrderId = request.clientOrderId,
            timestamp = now(),
        )
        _openOrders.value = _openOrders.value + order
        _orderUpdates.tryEmit(order)
        return OrderResponse(order.orderId, request.price, success = true)
    }

    /** Немедленный филл (market/taker) c семантикой позиций и комиссий. */
    private fun fillOrderLocked(
        symbol: String,
        request: OrderRequest,
        execPrice: Double,
        mark: Double,
        taker: Boolean,
        emit: Boolean = true,
    ): OrderResponse {
        val qty = request.quantity
        val feeRate = feeRateLocked(symbol, taker)

        if (request.reduceOnly) {
            val targetSide = if (request.side == OrderSide.BUY) TradeSide.SELL else TradeSide.BUY
            val pos = _positions.value.firstOrNull {
                it.symbol == symbol && it.side == targetSide &&
                    (request.positionId == null || it.positionId == request.positionId)
            } ?: return OrderResponse("", success = false, message = "No position to reduce")
            val closeQty = qty.coerceAtMost(pos.quantity)
            closePositionPartLocked(symbol, pos, closeQty, execPrice, feeRate)
            val order = filledOrder(request, symbol, execPrice, closeQty)
            if (emit) _orderUpdates.tryEmit(order)
            return OrderResponse(order.orderId, execPrice, success = true)
        }

        val leverage = (request.leverage ?: leverageMap[symbol] ?: 1).coerceAtLeast(1)
        val openSide = if (request.side == OrderSide.BUY) TradeSide.BUY else TradeSide.SELL
        val oppositeSide = if (openSide == TradeSide.BUY) TradeSide.SELL else TradeSide.BUY
        val same = _positions.value.firstOrNull { it.symbol == symbol && it.side == openSide }
        val opposite = _positions.value.firstOrNull { it.symbol == symbol && it.side == oppositeSide }

        var openQty = qty
        var realizedPnl = 0.0

        // One-way: противоположная позиция уменьшается/переворачивается
        if (positionModeValue == 2 && opposite != null && same == null) {
            val closeQty = qty.coerceAtMost(opposite.quantity)
            realizedPnl += closePositionPartLocked(symbol, opposite, closeQty, execPrice, feeRate)
            openQty = qty - closeQty
        }

        if (openQty > 1e-12) {
            val effLeverage = same?.leverage ?: leverage
            val marginAdd = execPrice * openQty / effLeverage
            val fee = execPrice * openQty * feeRate
            val b = balancesIndex("USDT")
            if (b < 0) return OrderResponse("", success = false, message = "USDT balance not found")
            val available = _balances.value[b].amount.toDoubleOrNull() ?: 0.0
            if (available < marginAdd + fee) {
                return OrderResponse("", success = false, message = "Insufficient margin")
            }
            updateBalance("USDT", available - marginAdd - fee, (_balances.value[b].margin.toDoubleOrNull() ?: 0.0) + marginAdd)

            val unrealized = if (openSide == TradeSide.BUY) (mark - execPrice) * openQty else (execPrice - mark) * openQty
            val position = if (same != null) {
                val totalQty = same.quantity + openQty
                val avg = (same.avgPrice * same.quantity + execPrice * openQty) / totalQty
                same.copy(quantity = totalQty, avgPrice = avg, markPrice = mark, unrealizedPnl = unrealized)
            } else {
                Position(
                    symbol = symbol,
                    side = openSide,
                    positionId = ++seq,
                    quantity = openQty,
                    avgPrice = execPrice,
                    markPrice = mark,
                    leverage = leverage,
                    unrealizedPnl = unrealized,
                    marginMode = request.marginMode,
                )
            }
            upsertPositionLocked(position)
            // TP/SL из заявки — виртуальные план-ордера на закрытие позиции
            setPlansLocked(symbol, position, request.takeProfitPrice, request.stopLossPrice)
        }

        _history.value = _history.value + TradeFill(
            id = newId(),
            symbol = symbol,
            side = request.side,
            price = execPrice,
            quantity = qty,
            fee = execPrice * qty * feeRate,
            feeCurrency = "USDT",
            pnl = realizedPnl,
            timestamp = now(),
        )
        val order = filledOrder(request, symbol, execPrice, qty)
        if (emit) _orderUpdates.tryEmit(order)
        return OrderResponse(order.orderId, execPrice, success = true)
    }

    /** Закрытие части позиции: маржа+pnl обратно в available, минус комиссия. */
    private fun closePositionPartLocked(
        symbol: String,
        pos: Position,
        closeQty: Double,
        execPrice: Double,
        feeRate: Double,
    ): Double {
        val leverage = (pos.leverage ?: 1).coerceAtLeast(1)
        val pnl = if (pos.side == TradeSide.BUY) (execPrice - pos.avgPrice) * closeQty
        else (pos.avgPrice - execPrice) * closeQty
        val marginReleased = pos.avgPrice * closeQty / leverage
        val fee = execPrice * closeQty * feeRate
        val b = balancesIndex("USDT")
        if (b >= 0) {
            val available = (_balances.value[b].amount.toDoubleOrNull() ?: 0.0) + marginReleased + pnl - fee
            val margin = ((_balances.value[b].margin.toDoubleOrNull() ?: 0.0) - marginReleased).coerceAtLeast(0.0)
            updateBalance("USDT", available, margin)
        }

        val remaining = pos.quantity - closeQty
        if (remaining <= 1e-9) {
            _positions.value = _positions.value.filterNot { it.positionId == pos.positionId }
            _positionUpdates.tryEmit(pos.copy(quantity = 0.0))
            plans.removeAll { it.positionId == pos.positionId }
        } else {
            val updated = pos.copy(quantity = remaining, pnl = pos.pnl + pnl)
            upsertPositionLocked(updated)
        }
        _history.value = _history.value + TradeFill(
            id = newId(),
            symbol = symbol,
            side = if (pos.side == TradeSide.BUY) OrderSide.SELL else OrderSide.BUY,
            price = execPrice,
            quantity = closeQty,
            fee = fee,
            feeCurrency = "USDT",
            pnl = pnl,
            timestamp = now(),
        )
        return pnl
    }

    /** TP/SL: пересоздать план-ордера позиции из параметров заявки. */
    private fun setPlansLocked(
        symbol: String,
        pos: Position,
        takeProfitPrice: Double?,
        stopLossPrice: Double?,
    ) {
        if (takeProfitPrice == null && stopLossPrice == null) return
        plans.removeAll { it.positionId == pos.positionId }
        val closeSide = if (pos.side == TradeSide.BUY) OrderSide.SELL else OrderSide.BUY
        takeProfitPrice?.takeIf { it > 0 }?.let {
            plans += PaperPlanOrder(newPlanId(), symbol, closeSide, it, takeProfit = true, pos.positionId)
        }
        stopLossPrice?.takeIf { it > 0 }?.let {
            plans += PaperPlanOrder(newPlanId(), symbol, closeSide, it, takeProfit = false, pos.positionId)
        }
    }

    private fun filledOrder(request: OrderRequest, symbol: String, price: Double, qty: Double): Order =
        Order(
            orderId = newId(),
            symbol = symbol,
            side = request.side,
            orderType = request.orderType,
            price = price,
            quantity = qty,
            filledQuantity = qty,
            reduceOnly = request.reduceOnly,
            status = OrderStatus.FILLED,
            clientOrderId = request.clientOrderId,
            timestamp = now(),
        )

    private fun upsertPositionLocked(position: Position) {
        val exists = _positions.value.any { it.positionId == position.positionId }
        _positions.value = if (exists) {
            _positions.value.map { if (it.positionId == position.positionId) position else it }
        } else {
            _positions.value + position
        }
        _positionUpdates.tryEmit(position)
    }

    /** Пересчёт mark → unrealized PnL, исполнение лимиток (maker) и TP/SL. */
    private fun repriceLocked(symbol: String, price: Double) {
        _positions.value = _positions.value.map { pos ->
            if (pos.symbol != symbol) pos
            else {
                val unrealized = if (pos.side == TradeSide.BUY) (price - pos.avgPrice) * pos.quantity
                else (pos.avgPrice - price) * pos.quantity
                pos.copy(markPrice = price, unrealizedPnl = unrealized)
            }
        }

        // Рестовые лимитки: пересечение → maker-филл по цене ордера
        val toFill = mutableListOf<Order>()
        val toCancel = mutableListOf<Order>()
        _openOrders.value = _openOrders.value.filterNot { order ->
            if (order.symbol != symbol) return@filterNot false
            val crossed = when (order.side) {
                OrderSide.BUY -> price <= order.price
                OrderSide.SELL -> price >= order.price
            }
            if (!crossed) return@filterNot false
            val qty = order.quantity
            val request = OrderRequest(
                symbol = symbol,
                side = order.side,
                orderType = order.orderType,
                quantity = qty,
                price = order.price,
                reduceOnly = order.reduceOnly,
                clientOrderId = order.clientOrderId,
            )
            val response = fillOrderLocked(symbol, request, order.price, price, taker = false, emit = false)
            if (response.success) toFill += order else toCancel += order
            true
        }
        toFill.forEach { _orderUpdates.tryEmit(it.copy(status = OrderStatus.FILLED, filledQuantity = it.quantity)) }
        toCancel.forEach { _orderUpdates.tryEmit(it.copy(status = OrderStatus.CANCELED)) }

        // TP/SL план-ордера: триггер → закрытие позиции по mark (taker)
        val triggered = plans.filter { plan ->
            if (plan.symbol != symbol) return@filter false
            if (plan.takeProfit) {
                val pos = _positions.value.firstOrNull { it.positionId == plan.positionId } ?: return@filter false
                if (pos.side == TradeSide.BUY) price >= plan.triggerPrice else price <= plan.triggerPrice
            } else {
                val pos = _positions.value.firstOrNull { it.positionId == plan.positionId } ?: return@filter false
                if (pos.side == TradeSide.BUY) price <= plan.triggerPrice else price >= plan.triggerPrice
            }
        }
        triggered.forEach { plan ->
            val pos = _positions.value.firstOrNull { it.positionId == plan.positionId } ?: return@forEach
            val feeRate = feeRateLocked(symbol, taker = true)
            closePositionPartLocked(symbol, pos, pos.quantity, price, feeRate)
            _orderUpdates.tryEmit(
                Order(
                    orderId = plan.planId,
                    symbol = symbol,
                    side = plan.side,
                    orderType = OrderType.MARKET,
                    price = price,
                    quantity = pos.quantity,
                    filledQuantity = pos.quantity,
                    reduceOnly = true,
                    status = OrderStatus.FILLED,
                    timestamp = now(),
                )
            )
        }
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
        emitBalance(currency)
    }

    private fun emitBalance(currency: String) {
        _balances.value.firstOrNull { it.currency == currency }?.let { _balanceUpdates.tryEmit(it) }
    }

    private fun fmt(v: Double): String {
        val rounded = kotlin.math.round(v * 1e8) / 1e8
        var s = rounded.toString()
        if ('.' in s) s = s.trimEnd('0').trimEnd('.')
        return s
    }
}
