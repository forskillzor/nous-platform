/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.OrderStatus
import com.aandios.nous.api.market.model.trading.Position
import com.aandios.nous.api.market.model.trading.TradeSide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Live + REST-backed cache of the user's open orders. Keeping this local avoids
 * hammering the 1200 weight/min info limit when chart, DOM and trading panel
 * reconcile every 10-30 seconds.
 */
internal class HyperliquidOrderRegistry(
    private val info: HyperliquidInfoApi,
    private val streams: HyperliquidStreamApi,
    private val marketData: HyperliquidMarketData,
    private val accountResolver: HyperliquidAccountResolver,
    private val clock: () -> Long = { hyperliquidCurrentTimeMillis() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutex = Mutex()
    private val orders = LinkedHashMap<String, Order>()
    private val dexRefreshAt = mutableMapOf<String, Long>()
    private val removedAt = mutableMapOf<String, Long>()
    private val positions = mutableMapOf<String, Position>()
    private val childOrderIdsByDex = mutableMapOf<String, Set<String>>()
    private val _updates = MutableSharedFlow<Order>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val updates: SharedFlow<Order> = _updates.asSharedFlow()
    private var streamStarted = false
    private var streamJob: Job? = null

    val hasStream: Boolean get() = streamStarted

    suspend fun setPositionContext(positions: List<Position>) = mutex.withLock {
        this.positions.clear()
        positions.forEach { position -> this.positions[position.symbol.lowercase()] = position }
    }

    suspend fun ensureStarted(user: String) = mutex.withLock {
        if (streamStarted) return@withLock
        streamStarted = true
        streamJob = scope.launch {
            val subscription = buildJsonObject {
                put("type", "orderUpdates")
                put("user", user)
            }
            streams.subscribe(ORDER_UPDATES_KEY, subscription).collect { root ->
                handleOrderUpdates(root)
            }
        }
        Unit
    }

    suspend fun orders(symbol: String?): List<Order> {
        val account = accountResolver.resolve()
        val user = account.user ?: return emptyList()
        ensureStarted(user)
        if (symbol == null) {
            refreshAllIfStale(user)
        } else {
            refreshDexIfStale(user, hyperliquidDexOf(symbol), DEX_TTL_MS)
        }
        return mutex.withLock {
            orders.values.filter { order ->
                order.status == OrderStatus.OPEN &&
                    (symbol == null || order.symbol.equals(symbol, ignoreCase = true))
            }
        }
    }

    suspend fun positionTpSl(positions: List<Position>): Map<Long, Pair<Double?, Double?>>? {
        if (positions.isEmpty()) return emptyMap()
        val account = accountResolver.resolve()
        val user = account.user ?: return emptyMap()
        ensureStarted(user)
        setPositionContext(positions)
        val dexes = positions.map { hyperliquidDexOf(it.symbol) }.toSet()
        var allSucceeded = true
        dexes.forEach { dex ->
            if (!refreshDexIfStale(user, dex, DEX_TTL_MS)) allSucceeded = false
        }
        if (!allSucceeded) return null
        return mutex.withLock {
            positions.mapNotNull { position ->
                val positionId = position.positionId ?: return@mapNotNull null
                val childIds = childOrderIdsByDex[hyperliquidDexOf(position.symbol)].orEmpty()
                val related = orders.values.filter { order ->
                    order.isTpSl &&
                        order.symbol.equals(position.symbol, ignoreCase = true) &&
                        order.status == OrderStatus.OPEN &&
                        (order.parentPositionId == positionId ||
                            (order.parentPositionId == null && order.orderId !in childIds))
                }
                val takeProfit = related
                    .filter { it.isTakeProfit ?: inferTakeProfit(it, position) }
                    .maxByOrNull { it.timestamp }
                    ?.triggerPrice
                val stopLoss = related
                    .filterNot { it.isTakeProfit ?: inferTakeProfit(it, position) }
                    .maxByOrNull { it.timestamp }
                    ?.triggerPrice
                positionId to (takeProfit to stopLoss)
            }.toMap()
        }
    }

    suspend fun positionStopOrders(position: Position): List<Order> = mutex.withLock {
        val childIds = childOrderIdsByDex[hyperliquidDexOf(position.symbol)].orEmpty()
        orders.values.filter { order ->
            order.isTpSl &&
                order.status == OrderStatus.OPEN &&
                order.symbol.equals(position.symbol, ignoreCase = true) &&
                (order.parentPositionId == position.positionId ||
                    (order.parentPositionId == null && order.orderId !in childIds))
        }
    }

    suspend fun findOrder(orderId: String): Order? {
        mutex.withLock { orders[orderId] }?.let { return it }
        val account = accountResolver.resolve()
        val user = account.user ?: return null
        ensureStarted(user)
        val oidValue = orderId.toLongOrNull()
        val response = runCatching {
            info.info(
                buildJsonObject {
                    put("type", "orderStatus")
                    put("user", user)
                    if (oidValue != null) put("oid", oidValue) else put("oid", orderId)
                },
            )
        }.getOrNull()?.asObject() ?: return null
        val row = response["order"]?.asObject() ?: return null
        val details = row["order"]?.asObject() ?: return null
        val status = hyperliquidOrderStatus(row.string("status"))
        val position = details.string("coin")?.let { positions[it.lowercase()] }
        val mapped = hyperliquidOrder(
            row = details,
            status = status,
            updatedAt = row.long("statusTimestamp"),
            positionEntryPrice = position?.avgPrice,
            positionSide = position?.side,
        ) ?: return null
        val childIds = mutex.withLock {
            childOrderIdsByDex[hyperliquidDexOf(mapped.symbol)].orEmpty()
        }
        val associated = associateTopLevelTpSl(mapped, childIds)
        if (associated.status == OrderStatus.OPEN) {
            mutex.withLock { orders[associated.orderId] = associated }
        }
        return associated
    }

    suspend fun applyLocal(order: Order) = mutex.withLock {
        if (order.status == OrderStatus.OPEN) {
            orders[order.orderId] = order
            removedAt.remove(order.orderId)
        } else {
            orders.remove(order.orderId)
            removedAt[order.orderId] = clock()
        }
        _updates.tryEmit(order)
    }

    suspend fun removeLocal(orderId: String) {
        mutex.withLock {
            orders.remove(orderId)
            removedAt[orderId] = clock()
        }
    }

    suspend fun invalidateDex(dex: String) = mutex.withLock {
        dexRefreshAt.remove(dex)
        Unit
    }

    private suspend fun refreshAllIfStale(user: String): Boolean {
        val now = clock()
        if (mutex.withLock { now - lastFullRefreshAt() < FULL_TTL_MS }) return true
        val dexes = runCatching { marketData.snapshot().dexNames }.getOrDefault(listOf(""))
        var allSucceeded = true
        dexes.forEach { dex ->
            val refreshed = refreshDexIfStale(user, dex, DEX_TTL_MS)
            if (!refreshed) allSucceeded = false
        }
        mutex.withLock { lastFullRefreshAtValue = clock() }
        return allSucceeded
    }

    private var lastFullRefreshAtValue = 0L
    private fun lastFullRefreshAt(): Long = lastFullRefreshAtValue

    private suspend fun refreshDexIfStale(user: String, dex: String, ttlMs: Long): Boolean {
        val now = clock()
        if (mutex.withLock { now - (dexRefreshAt[dex] ?: 0L) < ttlMs }) return true
        return refreshDex(user, dex)
    }

    private suspend fun refreshDex(user: String, dex: String): Boolean = runCatching {
        val startedAt = clock()
        val body = buildJsonObject {
            put("type", "frontendOpenOrders")
            put("user", user)
            if (dex.isNotEmpty()) put("dex", dex)
        }
        val rows = info.info(body).asArray().orEmpty()
            .mapNotNull { it.asObject() }
        val positionSnapshot = mutex.withLock { positions.toMap() }
        val flattened = hyperliquidFlattenOpenOrders(rows, positionSnapshot)
        val mapped = flattened.orders.map { associateTopLevelTpSl(it, flattened.childOrderIds) }
        mutateDex(dex, mapped, startedAt, flattened.childOrderIds)
        true
    }.getOrDefault(false)

    private suspend fun mutateDex(
        dex: String,
        fetched: List<Order>,
        startedAt: Long,
        childOrderIds: Set<String>,
    ) = mutex.withLock {
        childOrderIdsByDex[dex] = childOrderIds
        val now = clock()
        removedAt.entries.removeAll { now - it.value > 60_000L }
        val fetchedIds = fetched.map { it.orderId }.toSet()
        val iterator = orders.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val order = entry.value
            if (hyperliquidDexOf(order.symbol) == dex &&
                entry.key !in fetchedIds &&
                order.updatedAt <= startedAt
            ) {
                iterator.remove()
            }
        }
        fetched.forEach { order ->
            val removedAfterStart = removedAt[order.orderId]?.let { it > startedAt } == true
            if (removedAfterStart) return@forEach
            val existing = orders[order.orderId]
            if (existing == null || existing.updatedAt <= startedAt) {
                orders[order.orderId] = order
            }
        }
        dexRefreshAt[dex] = clock()
    }

    private suspend fun handleOrderUpdates(root: JsonObject) {
        val rows = root["data"]?.asArray().orEmpty()
        val account = accountResolver.resolve()
        val user = account.user ?: return
        rows.forEach { element ->
            val update = element.asObject() ?: return@forEach
            val basic = update["order"]?.asObject() ?: return@forEach
            val oid = basic.long("oid") ?: return@forEach
            val status = hyperliquidOrderStatus(update.string("status"))
            val positionSnapshot = mutex.withLock { positions.toMap() }
            val coin = basic.string("coin")
            val position = coin?.let { positionSnapshot[it.lowercase()] }
            val existing = mutex.withLock { orders[oid.toString()] }
            val mapped = hyperliquidOrder(
                row = basic,
                status = status,
                updatedAt = update.long("statusTimestamp"),
                positionEntryPrice = position?.avgPrice,
                positionSide = position?.side,
                existing = existing,
            ) ?: return@forEach
            val childIds = mutex.withLock {
                childOrderIdsByDex[hyperliquidDexOf(mapped.symbol)].orEmpty()
            }
            val associated = associateTopLevelTpSl(mapped, childIds)
            mutex.withLock {
                if (associated.status == OrderStatus.OPEN) {
                    orders[associated.orderId] = associated
                    removedAt.remove(associated.orderId)
                } else {
                    orders.remove(associated.orderId)
                    removedAt[associated.orderId] = clock()
                }
            }
            _updates.tryEmit(associated)
            if (mapped.status == OrderStatus.OPEN && existing == null && coin != null) {
                val dex = hyperliquidDexOf(coin)
                scope.launch {
                    if (refreshDexIfStale(user, dex, DEX_TTL_MS)) {
                        mutex.withLock { orders[mapped.orderId] }?.let { _updates.tryEmit(it) }
                    }
                }
            }
        }
    }

    /**
     * Hyperliquid does not always set isPositionTpsl for position TP/SL orders.
     * Treat top-level reduce-only triggers as position stops; nested OCO children
     * stay order-level until they become top-level after the parent fills.
     */
    private fun associateTopLevelTpSl(order: Order, childOrderIds: Set<String>): Order =
        if (order.isTpSl && order.parentPositionId == null && order.orderId !in childOrderIds) {
            order.copy(parentPositionId = hyperliquidPositionId(order.symbol))
        } else {
            order
        }

    private fun inferTakeProfit(order: Order, position: Position): Boolean {
        val trigger = order.triggerPrice ?: return false
        return if (position.side == TradeSide.BUY) {
            trigger > position.avgPrice
        } else {
            trigger < position.avgPrice
        }
    }

    private companion object {
        const val ORDER_UPDATES_KEY = "orderUpdates"
        const val DEX_TTL_MS = 15_000L
        const val FULL_TTL_MS = 60_000L
    }
}
