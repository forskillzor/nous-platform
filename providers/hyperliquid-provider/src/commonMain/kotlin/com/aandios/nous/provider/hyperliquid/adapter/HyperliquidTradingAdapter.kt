/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid.adapter

import com.aandios.nous.api.market.ProviderConfig
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
import com.aandios.nous.provider.hyperliquid.HlAction
import com.aandios.nous.provider.hyperliquid.HyperliquidAccountResolver
import com.aandios.nous.provider.hyperliquid.HyperliquidActionSigner
import com.aandios.nous.provider.hyperliquid.HyperliquidActions
import com.aandios.nous.provider.hyperliquid.HyperliquidAsset
import com.aandios.nous.provider.hyperliquid.HyperliquidInfoApi
import com.aandios.nous.provider.hyperliquid.HyperliquidMarketData
import com.aandios.nous.provider.hyperliquid.HyperliquidOrderRegistry
import com.aandios.nous.provider.hyperliquid.HyperliquidStreamApi
import com.aandios.nous.provider.hyperliquid.asArray
import com.aandios.nous.provider.hyperliquid.asObject
import com.aandios.nous.provider.hyperliquid.double
import com.aandios.nous.provider.hyperliquid.hyperliquidAggressivePrice
import com.aandios.nous.provider.hyperliquid.hyperliquidBalanceFromState
import com.aandios.nous.provider.hyperliquid.hyperliquidBookTickerFromL2
import com.aandios.nous.provider.hyperliquid.hyperliquidClearinghouseStates
import com.aandios.nous.provider.hyperliquid.hyperliquidCurrentTimeMillis
import com.aandios.nous.provider.hyperliquid.hyperliquidDexOf
import com.aandios.nous.provider.hyperliquid.hyperliquidEnvironmentVariable
import com.aandios.nous.provider.hyperliquid.hyperliquidFill
import com.aandios.nous.provider.hyperliquid.hyperliquidOidFromStatus
import com.aandios.nous.provider.hyperliquid.hyperliquidPositionFromState
import com.aandios.nous.provider.hyperliquid.int
import com.aandios.nous.provider.hyperliquid.long
import com.aandios.nous.provider.hyperliquid.normalizeHyperliquidPrice
import com.aandios.nous.provider.hyperliquid.normalizeHyperliquidSize
import com.aandios.nous.provider.hyperliquid.plainHyperliquidDecimal
import com.aandios.nous.provider.hyperliquid.resolveHyperliquidCredentials
import com.aandios.nous.provider.hyperliquid.string
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.math.round

internal class HyperliquidTradingAdapter internal constructor(
    private val info: HyperliquidInfoApi,
    private val streams: HyperliquidStreamApi,
    private val marketData: HyperliquidMarketData,
    private val signer: HyperliquidActionSigner,
    config: ProviderConfig,
    private val clock: () -> Long = { hyperliquidCurrentTimeMillis() },
) : TradingAdapter {
    private val credentials = resolveHyperliquidCredentials(config)
    private val accountResolver = HyperliquidAccountResolver(info, credentials, signer)
    private val orderRegistry = HyperliquidOrderRegistry(info, streams, marketData, accountResolver, clock)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _notices = MutableSharedFlow<String>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val noticesFlow = _notices.asSharedFlow()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val marketSlippage = (config.customSettings["marketSlippage"]
        ?: hyperliquidEnvironmentVariable("HYPERLIQUID_MARKET_SLIPPAGE"))
        ?.toDoubleOrNull()
        ?.coerceIn(0.0, 0.5)
        ?: 0.05

    private val leverageMutex = Mutex()
    private val leverageCache = mutableMapOf<String, Pair<Int, Boolean>>()
    private val clearinghouseMutex = Mutex()
    private var clearinghouseCache: Map<String, JsonObject>? = null
    private var clearinghouseCacheAt = 0L
    private val posTpSlCache = mutableMapOf<Long, Pair<Double?, Double?>>()
    private var feeRatesCache: FeeRates? = null
    private var feeRatesAt = 0L
    private var dailyPnlCache: Double? = null
    private var dailyPnlAt = 0L

    override suspend fun placeOrder(request: OrderRequest): OrderResponse {
        if (!signer.canSign) return failure("Hyperliquid private key is not configured (HYPERLIQUID_PRIVATE_KEY)")
        val user = accountOrNull() ?: return failure("Hyperliquid account address is not configured")
        val asset = marketData.assetFor(request.symbol)
            ?: return failure("Unknown Hyperliquid symbol ${request.symbol}")
        if (asset.isDelisted) return failure("${asset.coin} is delisted")
        if (request.orderType == OrderType.FOK) {
            return failure("Hyperliquid does not support FOK; use IOC or LIMIT")
        }
        return try {
            val isBuy = request.side == OrderSide.BUY
            val isCross = effectiveIsCross(asset, request.marginMode)
            if (!request.reduceOnly) {
                val leverage = request.leverage ?: if (request.marginMode != null) {
                    getLeverageInfo(user, asset)?.first
                } else {
                    null
                }
                if (leverage != null && !ensureLeverage(user, asset, leverage, isCross)) {
                    return failure("Failed to set Hyperliquid leverage/margin")
                }
            }

            val size = normalizeHyperliquidSize(request.quantity, asset.szDecimals)
            val type = request.orderType
            val priceString: String
            val limitTif: String?
            val trigger: HyperliquidActions.TriggerType?
            when (type) {
                OrderType.MARKET -> {
                    val reference = bestPrice(asset.coin, isBuy)
                        ?: marketData.markPrice(asset.coin)
                        ?: return failure("No Hyperliquid price for ${asset.coin}")
                    priceString = normalizeHyperliquidPrice(
                        hyperliquidAggressivePrice(reference, isBuy, marketSlippage),
                        asset.szDecimals,
                    )
                    limitTif = "Ioc"
                    trigger = null
                }
                OrderType.LIMIT, OrderType.POST_ONLY, OrderType.IOC -> {
                    val price = request.price.takeIf { it > 0.0 }
                        ?: bestPrice(asset.coin, isBuy)
                        ?: return failure("No Hyperliquid price for ${asset.coin}")
                    priceString = normalizeHyperliquidPrice(price, asset.szDecimals)
                    limitTif = when (type) {
                        OrderType.POST_ONLY -> "Alo"
                        OrderType.IOC -> "Ioc"
                        else -> "Gtc"
                    }
                    trigger = null
                }
                OrderType.STOP -> {
                    priceString = normalizeHyperliquidPrice(request.price, asset.szDecimals)
                    limitTif = null
                    trigger = HyperliquidActions.TriggerType(
                        isMarket = true,
                        triggerPrice = priceString,
                        tpsl = "sl",
                    )
                }
                OrderType.FOK -> return failure("Hyperliquid does not support FOK; use IOC or LIMIT")
            }

            val parent = HyperliquidActions.order(
                asset = asset.assetId,
                isBuy = isBuy,
                price = priceString,
                size = size,
                reduceOnly = request.reduceOnly,
                limitTif = limitTif,
                trigger = trigger,
                cloid = validCloid(request.clientOrderId),
            )
            val children = mutableListOf<HlAction>()
            if (!request.reduceOnly && type != OrderType.STOP) {
                request.takeProfitPrice?.takeIf { it > 0.0 }?.let {
                    children += triggerOrder(asset, !isBuy, it, size, "tp")
                }
                request.stopLossPrice?.takeIf { it > 0.0 }?.let {
                    children += triggerOrder(asset, !isBuy, it, size, "sl")
                }
            }
            val grouping = if (children.isEmpty()) "na" else "normalTpsl"
            val action = HyperliquidActions.orderAction(listOf(parent) + children, grouping)
            val response = postAction(action)
            val error = actionError(response)
            if (error != null) {
                notify(error)
                return failure(error)
            }
            val statuses = actionStatuses(response)
            val first = statuses?.firstOrNull()?.asObject()
            val oid = first?.let(::hyperliquidOidFromStatus)
                ?: return failure("Hyperliquid accepted no order id")
            statuses?.drop(1)?.forEach { element ->
                element.asObject()?.string("error")?.let { notify("TP/SL: $it") }
            }
            orderRegistry.invalidateDex(asset.dex)
            scope.launch { runCatching { orderRegistry.orders(asset.coin) } }
            OrderResponse(orderId = oid.toString(), price = request.price, success = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure(e.message ?: "Order failed")
        }
    }

    override suspend fun cancelOrder(orderId: String): Boolean {
        if (!signer.canSign) return false
        val order = orderRegistry.findOrder(orderId) ?: return false
        val asset = marketData.assetFor(order.symbol) ?: return false
        val oid = order.orderId.toLongOrNull() ?: return false
        return runCatching {
            val response = postAction(
                HyperliquidActions.cancelAction(
                    listOf(HyperliquidActions.cancel(asset.assetId, oid)),
                ),
            )
            val error = actionError(response)
            if (error != null) {
                notify(error)
                return@runCatching false
            }
            orderRegistry.removeLocal(orderId)
            orderRegistry.invalidateDex(asset.dex)
            true
        }.getOrDefault(false)
    }

    override suspend fun cancelAllOrders(symbol: String?): Boolean {
        if (!signer.canSign) return false
        val open = orderRegistry.orders(symbol)
        if (open.isEmpty()) return true
        val cancels = open.mapNotNull { order ->
            val asset = marketData.assetFor(order.symbol) ?: return@mapNotNull null
            val oid = order.orderId.toLongOrNull() ?: return@mapNotNull null
            HyperliquidActions.cancel(asset.assetId, oid)
        }
        if (cancels.isEmpty()) return false
        return runCatching {
            val response = postAction(HyperliquidActions.cancelAction(cancels))
            val error = actionError(response)
            if (error != null) {
                notify(error)
                return@runCatching false
            }
            open.forEach { orderRegistry.removeLocal(it.orderId) }
            open.map { hyperliquidDexOf(it.symbol) }.distinct().forEach {
                orderRegistry.invalidateDex(it)
            }
            true
        }.getOrDefault(false)
    }

    override suspend fun closePosition(symbol: String, positionId: Long?, quantity: Double?): OrderResponse? {
        if (!signer.canSign) return failure("Hyperliquid private key is not configured")
        val position = getPositions().firstOrNull {
            it.symbol.equals(symbol, ignoreCase = true) && (positionId == null || it.positionId == positionId)
        } ?: return failure("Position not found for $symbol")
        return placeOrder(
            OrderRequest(
                symbol = position.symbol,
                side = if (position.side == TradeSide.BUY) OrderSide.SELL else OrderSide.BUY,
                orderType = OrderType.MARKET,
                quantity = quantity ?: position.quantity,
                reduceOnly = true,
                positionId = position.positionId,
            ),
        )
    }

    override suspend fun getBalances(): List<Balance> {
        val user = accountOrNull() ?: return emptyList()
        val states = fetchClearinghouses(user) ?: return emptyList()
        return balancesFromStates(states)
    }

    override suspend fun getPositions(): List<Position> {
        val user = accountOrNull() ?: return emptyList()
        val states = fetchClearinghouses(user) ?: return emptyList()
        val marks = runCatching { marketData.markPrices(states.keys) }.getOrDefault(emptyMap())
        val preliminary = positionsFromStates(states, marks)
        orderRegistry.setPositionContext(preliminary)
        val extra = orderRegistry.positionTpSl(preliminary)
        val mapped = preliminary.map { position ->
            val pair = if (extra != null) {
                position.positionId?.let { extra[it] }
            } else {
                position.positionId?.let { posTpSlCache[it] }
            }
            position.copy(
                takeProfitPrice = pair?.first,
                stopLossPrice = pair?.second,
            )
        }
        if (extra != null) {
            val activeIds = mapped.mapNotNull { it.positionId }.toSet()
            posTpSlCache.keys.retainAll(activeIds)
            mapped.forEach { position ->
                val id = position.positionId ?: return@forEach
                posTpSlCache[id] = position.takeProfitPrice to position.stopLossPrice
            }
        }
        return mapped
    }

    override suspend fun getOpenOrders(symbol: String?): List<Order> = orderRegistry.orders(symbol)

    override suspend fun getTradeHistory(symbol: String?, limit: Int): List<TradeFill> {
        val user = accountOrNull() ?: return emptyList()
        val body = buildJsonObject {
            put("type", "userFills")
            put("user", user)
        }
        val rows = runCatching { info.info(body) }.getOrNull()?.asArray().orEmpty()
        return rows.mapNotNull { it.asObject()?.let(::hyperliquidFill) }
            .filter { symbol == null || it.symbol.equals(symbol, ignoreCase = true) }
            .sortedByDescending { it.timestamp }
            .take(limit.coerceIn(1, 2_000))
    }

    override suspend fun setLeverage(symbol: String, leverage: Int, positionId: Long?): Boolean {
        if (!signer.canSign) return false
        val user = accountOrNull() ?: return false
        val asset = marketData.assetFor(symbol) ?: return false
        val current = getLeverageInfo(user, asset)
        val isCross = if (asset.requiresIsolatedMargin) false else (current?.second ?: true)
        return ensureLeverage(user, asset, leverage, isCross)
    }

    override suspend fun getLeverage(symbol: String): Int? {
        val user = accountOrNull() ?: return null
        val asset = marketData.assetFor(symbol) ?: return null
        return getLeverageInfo(user, asset)?.first
    }

    override suspend fun getPositionMode(): Int? = ONE_WAY_MODE

    override suspend fun setPositionMode(mode: Int): Boolean = mode == ONE_WAY_MODE

    override suspend fun adjustMargin(positionId: Long, amount: Double, add: Boolean): Boolean {
        if (!signer.canSign || amount <= 0.0) return false
        val position = getPositions().firstOrNull { it.positionId == positionId } ?: return false
        val asset = marketData.assetFor(position.symbol) ?: return false
        val notional = round(amount * 1_000_000.0).toLong() * if (add) 1L else -1L
        return runCatching {
            val response = postAction(
                HyperliquidActions.updateIsolatedMargin(
                    asset = asset.assetId,
                    isBuy = position.side == TradeSide.BUY,
                    notional = notional,
                ),
            )
            val error = actionError(response)
            if (error != null) notify(error)
            error == null
        }.getOrDefault(false)
    }

    override suspend fun setOrderTpSl(
        order: Order,
        takeProfitPrice: Double?,
        stopLossPrice: Double?,
    ): Boolean {
        if (!signer.canSign || order.reduceOnly || order.isTpSl) return false
        if (order.orderType == OrderType.STOP || order.orderType == OrderType.MARKET) return false
        val current = orderRegistry.findOrder(order.orderId) ?: return false
        val currentTp = current.takeProfitPrice
        val currentSl = current.stopLossPrice
        val finalTp = when {
            takeProfitPrice == null -> currentTp
            takeProfitPrice == 0.0 -> null
            else -> takeProfitPrice
        }
        val finalSl = when {
            stopLossPrice == null -> currentSl
            stopLossPrice == 0.0 -> null
            else -> stopLossPrice
        }
        if (samePrice(finalTp, currentTp) && samePrice(finalSl, currentSl)) return true
        val asset = marketData.assetFor(current.symbol) ?: return false
        if (current.quantity <= 0.0 || current.price <= 0.0) return false
        return runCatching {
            val currentOid = current.orderId.toLongOrNull() ?: return@runCatching false
            val cancelResponse = postAction(
                HyperliquidActions.cancelAction(
                    listOf(HyperliquidActions.cancel(asset.assetId, currentOid)),
                ),
            )
            val cancelError = actionError(cancelResponse)
            if (cancelError != null) {
                notify(cancelError)
                return@runCatching false
            }
            val isBuy = current.side == OrderSide.BUY
            val size = normalizeHyperliquidSize(current.quantity, asset.szDecimals)
            val price = normalizeHyperliquidPrice(current.price, asset.szDecimals)
            val tif = when (current.orderType) {
                OrderType.POST_ONLY -> "Alo"
                OrderType.IOC -> "Ioc"
                else -> "Gtc"
            }
            val parent = HyperliquidActions.order(
                asset = asset.assetId,
                isBuy = isBuy,
                price = price,
                size = size,
                reduceOnly = false,
                limitTif = tif,
            )
            val children = mutableListOf<HlAction>()
            finalTp?.let { children += triggerOrder(asset, !isBuy, it, size, "tp") }
            finalSl?.let { children += triggerOrder(asset, !isBuy, it, size, "sl") }
            val grouping = if (children.isEmpty()) "na" else "normalTpsl"
            val response = postAction(HyperliquidActions.orderAction(listOf(parent) + children, grouping))
            val error = actionError(response)
            if (error != null) {
                notify(error)
                return@runCatching false
            }
            orderRegistry.removeLocal(current.orderId)
            orderRegistry.invalidateDex(asset.dex)
            scope.launch { runCatching { orderRegistry.orders(asset.coin) } }
            true
        }.getOrDefault(false)
    }

    override suspend fun setPositionTpSl(
        position: Position,
        takeProfitPrice: Double?,
        stopLossPrice: Double?,
    ): Boolean {
        if (!signer.canSign) return false
        val positionId = position.positionId ?: return false
        val user = accountOrNull() ?: return false
        val asset = marketData.assetFor(position.symbol) ?: return false
        orderRegistry.ensureStarted(user)
        val fresh = orderRegistry.positionTpSl(listOf(position)) ?: return false
        val existing = fresh[positionId] ?: (null to null)
        val existingTp = existing.first
        val existingSl = existing.second
        val finalTp = when {
            takeProfitPrice == null -> existingTp
            takeProfitPrice == 0.0 -> null
            else -> takeProfitPrice
        }
        val finalSl = when {
            stopLossPrice == null -> existingSl
            stopLossPrice == 0.0 -> null
            else -> stopLossPrice
        }
        val tpChanged = !samePrice(finalTp, existingTp)
        val slChanged = !samePrice(finalSl, existingSl)
        if (!tpChanged && !slChanged) {
            posTpSlCache[positionId] = finalTp to finalSl
            return true
        }

        val stopOrders = orderRegistry.positionStopOrders(position)
        val toCancel = stopOrders.filter { stop ->
            val isTakeProfit = stop.isTakeProfit ?: inferTakeProfit(stop, position)
            (tpChanged && isTakeProfit) || (slChanged && !isTakeProfit)
        }
        if (toCancel.isNotEmpty()) {
            val cancels = toCancel.mapNotNull { stop ->
                val stopAsset = marketData.assetFor(stop.symbol) ?: return@mapNotNull null
                val oid = stop.orderId.toLongOrNull() ?: return@mapNotNull null
                HyperliquidActions.cancel(stopAsset.assetId, oid)
            }
            if (cancels.isNotEmpty()) {
                val cancelResponse = runCatching {
                    postAction(HyperliquidActions.cancelAction(cancels))
                }.getOrNull() ?: return false
                val cancelError = actionError(cancelResponse)
                if (cancelError != null) {
                    notify(cancelError)
                    return false
                }
                toCancel.forEach { orderRegistry.removeLocal(it.orderId) }
            }
        }

        if (finalTp != null || finalSl != null) {
            val size = normalizeHyperliquidSize(position.quantity, asset.szDecimals)
            val closeBuy = position.side != TradeSide.BUY
            val orders = mutableListOf<HlAction>()
            if (tpChanged && finalTp != null) orders += triggerOrder(asset, closeBuy, finalTp, size, "tp")
            if (slChanged && finalSl != null) orders += triggerOrder(asset, closeBuy, finalSl, size, "sl")
            if (orders.isNotEmpty()) {
                val response = runCatching {
                    postAction(HyperliquidActions.orderAction(orders, "positionTpsl"))
                }.getOrNull()
                if (response == null) {
                    posTpSlCache.remove(positionId)
                    return false
                }
                val error = actionError(response)
                if (error != null) {
                    notify(error)
                    posTpSlCache.remove(positionId)
                    return false
                }
            }
        }
        posTpSlCache[positionId] = finalTp to finalSl
        orderRegistry.invalidateDex(asset.dex)
        scope.launch { runCatching { orderRegistry.orders(asset.coin) } }
        return true
    }

    override fun subscribeToPositions(): Flow<Position>? {
        if (!canReadAccount()) return null
        return channelFlow {
            val user = accountOrNull() ?: return@channelFlow
            val initial = getPositions()
            val previous = LinkedHashMap<Long, Position>()
            initial.forEach { position ->
                val id = position.positionId ?: return@forEach
                previous[id] = position
                send(position)
            }
            val subscription = buildJsonObject {
                put("type", "allDexsClearinghouseState")
                put("user", user)
            }
            streams.subscribe("allDexsClearinghouseState|$user", subscription).collect { root ->
                val data = root["data"]?.asObject() ?: return@collect
                val states = hyperliquidClearinghouseStates(data)
                val marks = runCatching { marketData.markPrices(states.keys) }.getOrDefault(emptyMap())
                val preliminary = positionsFromStates(states, marks)
                orderRegistry.setPositionContext(preliminary)
                val extra = orderRegistry.positionTpSl(preliminary)
                val current = preliminary.map { position ->
                    val pair = if (extra != null) {
                        position.positionId?.let { extra[it] }
                    } else {
                        position.positionId?.let { posTpSlCache[it] }
                    }
                    position.copy(
                        takeProfitPrice = pair?.first,
                        stopLossPrice = pair?.second,
                    )
                }
                val currentIds = mutableSetOf<Long>()
                current.forEach { position ->
                    val id = position.positionId ?: return@forEach
                    currentIds += id
                    previous[id] = position
                    send(position)
                }
                previous.entries
                    .filter { it.key !in currentIds }
                    .forEach { (id, old) ->
                        previous.remove(id)
                        send(old.copy(quantity = 0.0))
                    }
                if (extra != null) {
                    val activeIds = current.mapNotNull { it.positionId }.toSet()
                    posTpSlCache.keys.retainAll(activeIds)
                    current.forEach { position ->
                        val id = position.positionId ?: return@forEach
                        posTpSlCache[id] = position.takeProfitPrice to position.stopLossPrice
                    }
                }
            }
        }
    }

    override fun subscribeToOrders(): Flow<Order>? {
        if (!canReadAccount()) return null
        return flow {
            val user = accountOrNull() ?: return@flow
            orderRegistry.ensureStarted(user)
            orderRegistry.updates.collect { order ->
                if (order.status == OrderStatus.REJECTED) {
                    notify("Hyperliquid rejected order ${order.orderId} (${order.symbol})")
                }
                emit(order)
            }
        }
    }

    override fun subscribeToBalances(): Flow<Balance>? {
        if (!canReadAccount()) return null
        return channelFlow {
            val user = accountOrNull() ?: return@channelFlow
            getBalances().forEach { send(it) }
            val subscription = buildJsonObject {
                put("type", "allDexsClearinghouseState")
                put("user", user)
            }
            streams.subscribe("allDexsClearinghouseState|$user", subscription).collect { root ->
                val data = root["data"]?.asObject() ?: return@collect
                balancesFromStates(hyperliquidClearinghouseStates(data)).forEach { send(it) }
            }
        }
    }

    override fun notices(): Flow<String>? = merge(
        noticesFlow,
        streams.errors.map(::formatStreamError),
    )

    override suspend fun getFeeRates(symbol: String): FeeRates? {
        val user = accountOrNull() ?: return null
        val now = clock()
        feeRatesCache?.takeIf { now - feeRatesAt < FEE_TTL_MS }?.let { return it }
        val body = buildJsonObject {
            put("type", "userFees")
            put("user", user)
        }
        val response = runCatching { info.info(body) }.getOrNull()?.asObject() ?: return null
        val schedule = response["feeSchedule"]?.asObject()
        val maker = response.double("userAddRate") ?: schedule?.double("add") ?: return null
        val taker = response.double("userCrossRate") ?: schedule?.double("cross") ?: return null
        val rates = FeeRates(maker = maker, taker = taker)
        feeRatesCache = rates
        feeRatesAt = clock()
        return rates
    }

    override suspend fun getDailyRealizedPnl(): Double? {
        val user = accountOrNull() ?: return null
        val now = clock()
        dailyPnlCache?.takeIf { now - dailyPnlAt < PNL_TTL_MS }?.let { return it }
        val dayStart = now - (now % DAY_MS)
        var start = dayStart
        var total = 0.0
        var pages = 0
        while (pages < 20) {
            val body = buildJsonObject {
                put("type", "userFillsByTime")
                put("user", user)
                put("startTime", start)
                put("endTime", now)
            }
            val rows = runCatching { info.info(body) }.getOrNull()?.asArray().orEmpty()
                .mapNotNull { it.asObject() }
            if (rows.isEmpty()) break
            var lastTime = start
            rows.forEach { row ->
                total += row.double("closedPnl") ?: 0.0
                lastTime = maxOf(lastTime, row.long("time") ?: start)
            }
            if (rows.size < 2_000 || lastTime <= start) break
            start = lastTime + 1
            pages++
        }
        dailyPnlCache = total
        dailyPnlAt = clock()
        return total
    }

    private suspend fun accountOrNull(): String? = accountResolver.resolve().user

    private fun canReadAccount(): Boolean = signer.canSign || credentials.accountAddress != null

    private fun effectiveIsCross(asset: HyperliquidAsset, marginMode: Int?): Boolean {
        if (asset.requiresIsolatedMargin) return false
        return marginMode != 1
    }

    private suspend fun ensureLeverage(
        user: String,
        asset: HyperliquidAsset,
        leverage: Int,
        isCross: Boolean,
    ): Boolean {
        val desired = leverage.coerceAtLeast(1)
        leverageMutex.withLock {
            val cached = leverageCache[asset.coin.lowercase()]
            if (cached == (desired to isCross)) return true
            val response = runCatching {
                postAction(HyperliquidActions.updateLeverage(asset.assetId, isCross, desired))
            }.getOrNull() ?: return false
            val error = actionError(response)
            if (error != null) {
                notify(error)
                return false
            }
            leverageCache[asset.coin.lowercase()] = desired to isCross
            return true
        }
    }

    private suspend fun getLeverageInfo(user: String, asset: HyperliquidAsset): Pair<Int, Boolean>? {
        leverageMutex.withLock {
            leverageCache[asset.coin.lowercase()]?.let { return it }
        }
        val body = buildJsonObject {
            put("type", "activeAssetData")
            put("user", user)
            put("coin", asset.coin)
        }
        val response = runCatching { info.info(body) }.getOrNull()?.asObject() ?: return null
        val leverage = response["leverage"]?.asObject() ?: return null
        val value = leverage.int("value") ?: return null
        val isCross = leverage.string("type") == "cross"
        leverageMutex.withLock { leverageCache[asset.coin.lowercase()] = value to isCross }
        return value to isCross
    }

    private suspend fun bestPrice(coin: String, isBuy: Boolean): Double? {
        val body = buildJsonObject {
            put("type", "l2Book")
            put("coin", coin)
        }
        val data = runCatching { info.info(body) }.getOrNull()?.asObject() ?: return null
        val ticker = hyperliquidBookTickerFromL2(data) ?: return null
        return if (isBuy) ticker.bestAsk else ticker.bestBid
    }

    private suspend fun postAction(action: HlAction): JsonObject {
        val account = accountResolver.resolve()
        return info.exchange(signer.signPayload(action, account.vaultAddress))
    }

    private fun actionStatuses(response: JsonObject): kotlinx.serialization.json.JsonArray? =
        response["response"]?.asObject()
            ?.get("data")?.asObject()
            ?.get("statuses")?.asArray()

    private fun actionError(response: JsonObject): String? {
        if (response.string("status") != "ok") {
            return response["response"]?.let { element ->
                (element as? JsonPrimitive)?.contentOrNull ?: element.toString()
            } ?: "Hyperliquid action failed"
        }
        val first = actionStatuses(response)?.firstOrNull()?.asObject() ?: return null
        return first.string("error")
    }

    private fun triggerOrder(
        asset: HyperliquidAsset,
        isBuy: Boolean,
        triggerPrice: Double,
        size: String,
        tpsl: String,
    ): HlAction {
        val price = normalizeHyperliquidPrice(triggerPrice, asset.szDecimals)
        return HyperliquidActions.order(
            asset = asset.assetId,
            isBuy = isBuy,
            price = price,
            size = size,
            reduceOnly = true,
            trigger = HyperliquidActions.TriggerType(
                isMarket = true,
                triggerPrice = price,
                tpsl = tpsl,
            ),
        )
    }

    private fun validCloid(value: String?): String? {
        val text = value?.trim().orEmpty()
        if (!text.startsWith("0x", ignoreCase = true) || text.length != 34) return null
        return if (text.drop(2).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) text else null
    }

    private fun inferTakeProfit(order: Order, position: Position): Boolean {
        val trigger = order.triggerPrice ?: return false
        return if (position.side == TradeSide.BUY) {
            trigger > position.avgPrice
        } else {
            trigger < position.avgPrice
        }
    }

    private fun samePrice(a: Double?, b: Double?): Boolean = when {
        a == null && b == null -> true
        a == null || b == null -> false
        else -> abs(a - b) < 1e-9
    }

    private fun positionsFromStates(
        states: Map<String, JsonObject>,
        marks: Map<String, Double>,
    ): List<Position> {
        val result = mutableListOf<Position>()
        states.values.forEach { state ->
            state["assetPositions"]?.asArray().orEmpty().forEach { element ->
                val row = element.asObject()?.get("position")?.asObject() ?: return@forEach
                val coin = row.string("coin") ?: return@forEach
                hyperliquidPositionFromState(
                    row = row,
                    markPrice = marks[coin],
                    takeProfitPrice = null,
                    stopLossPrice = null,
                )?.let { result += it }
            }
        }
        return result
    }

    private suspend fun balancesFromStates(states: Map<String, JsonObject>): List<Balance> {
        val collateral = runCatching { marketData.snapshot().dexCollateral }.getOrDefault(emptyMap())
        val aggregated = LinkedHashMap<String, BalanceAccumulator>()
        states.forEach { (dex, state) ->
            val currency = collateral[dex] ?: "USDC"
            val balance = hyperliquidBalanceFromState(state, currency) ?: return@forEach
            aggregated.getOrPut(currency) { BalanceAccumulator(currency) }.add(balance)
        }
        if (aggregated.isEmpty()) aggregated[USDC] = BalanceAccumulator(USDC)
        return aggregated.values
            .map { it.toBalance() }
            .sortedByDescending { it.equity.toDoubleOrNull() ?: 0.0 }
    }

    private suspend fun fetchClearinghouses(user: String): Map<String, JsonObject>? =
        clearinghouseMutex.withLock {
            val now = clock()
            clearinghouseCache?.takeIf { now - clearinghouseCacheAt < CLEARINGHOUSE_TTL_MS }
                ?.let { return it }
            val dexes = runCatching { marketData.snapshot().dexNames }.getOrDefault(listOf(""))
            val result = LinkedHashMap<String, JsonObject>()
            var anySuccess = false
            dexes.forEach { dex ->
                val body = buildJsonObject {
                    put("type", "clearinghouseState")
                    put("user", user)
                    if (dex.isNotEmpty()) put("dex", dex)
                }
                runCatching { info.info(body) }.getOrNull()?.asObject()?.let { state ->
                    result[dex] = state
                    anySuccess = true
                }
            }
            if (!anySuccess) return null
            clearinghouseCache = result
            clearinghouseCacheAt = clock()
            result
        }

    private fun formatStreamError(raw: String): String {
        val root = runCatching { json.parseToJsonElement(raw).asObject() }.getOrNull()
        val data = root?.get("data")
        return data?.asObject()?.string("notification")
            ?: (data as? JsonPrimitive)?.contentOrNull
            ?: raw
    }

    private fun notify(message: String) {
        _notices.tryEmit(message)
    }

    private fun failure(message: String?): OrderResponse =
        OrderResponse(orderId = "", success = false, message = message)

    private class BalanceAccumulator(private val currency: String) {
        private var amount = 0.0
        private var margin = 0.0
        private var equity = 0.0
        private var unrealized = 0.0

        fun add(balance: Balance) {
            amount += balance.amount.toDoubleOrNull() ?: 0.0
            margin += balance.margin.toDoubleOrNull() ?: 0.0
            equity += balance.equity.toDoubleOrNull() ?: 0.0
            unrealized += balance.unrealizedPnl.toDoubleOrNull() ?: 0.0
        }

        fun toBalance(): Balance = Balance(
            currency = currency,
            amount = plainHyperliquidDecimal(amount),
            frozen = "0",
            margin = plainHyperliquidDecimal(margin),
            equity = plainHyperliquidDecimal(equity),
            unrealizedPnl = plainHyperliquidDecimal(unrealized),
        )
    }

    private companion object {
        const val ONE_WAY_MODE = 2
        const val USDC = "USDC"
        const val DAY_MS = 86_400_000L
        const val CLEARINGHOUSE_TTL_MS = 2_000L
        const val FEE_TTL_MS = 10 * 60_000L
        const val PNL_TTL_MS = 60_000L
    }
}
