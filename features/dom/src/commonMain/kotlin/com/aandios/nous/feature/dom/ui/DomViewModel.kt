/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui

import androidx.compose.runtime.mutableStateMapOf
import com.aandios.nous.api.market.commands.*
import com.aandios.nous.core.Disposable
import com.aandios.nous.core.domain.repository.DomRepository
import com.aandios.nous.core.domain.repository.SymbolInfoRepository
import com.aandios.nous.feature.dom.domain.DomOptions
import com.aandios.nous.feature.dom.domain.TradingProvider
import com.aandios.nous.feature.dom.domain.TradingSymbol
import com.aandios.nous.api.market.model.orderbook.DomEvent
import com.aandios.nous.feature.dom.domain.model.OrderIntent
import com.aandios.nous.feature.dom.ui.model.DomLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlin.math.roundToLong

class DomViewModel(
    private val domRepository: DomRepository,
    private val symbolInfoRepository: SymbolInfoRepository? = null,
    private val coroutineDispatcher: CoroutineDispatcher? = null,
) : Disposable {
    private val dispatcher = coroutineDispatcher ?: Dispatchers.Default
    private val viewModelScope = CoroutineScope(dispatcher + SupervisorJob())
    private var subscriptionJob: Job? = null

    private val _domOptions = MutableStateFlow(DomOptions.default())
    val domOptions: StateFlow<DomOptions> = _domOptions.asStateFlow()

    private val _selectedPrice = MutableStateFlow<Double?>(null)
    val selectedPrice: StateFlow<Double?> = _selectedPrice.asStateFlow()

    private val _orderQuantity = MutableStateFlow("0.01")
    val orderQuantity: StateFlow<String> = _orderQuantity.asStateFlow()

    private val _isTradingEnabled = MutableStateFlow(true)
    val isTradingEnabled: StateFlow<Boolean> = _isTradingEnabled.asStateFlow()

    private val _lastCommandResult = MutableStateFlow<CommandResult?>(null)
    val lastCommandResult: StateFlow<CommandResult?> = _lastCommandResult.asStateFlow()

    private val _loadedSymbols = MutableStateFlow<List<TradingSymbol>>(emptyList())
    val loadedSymbols: StateFlow<List<TradingSymbol>> = _loadedSymbols.asStateFlow()

    private val _symbolTickSize = MutableStateFlow<Double?>(null)
    val symbolTickSize: StateFlow<Double?> = _symbolTickSize.asStateFlow()

    private val _symbolStepSize = MutableStateFlow<Double?>(null)
    val symbolStepSize: StateFlow<Double?> = _symbolStepSize.asStateFlow()

    // --- Scaled-Long модель ---

    private var tickSize: Double = 0.0
    private var stepSize: Double = 0.0
    private val scaleReady: Boolean get() = tickSize > 0.0 && stepSize > 0.0

    private val rawBids = HashMap<Long, Long>()
    private val rawAsks = HashMap<Long, Long>()

    private val bidBuckets = HashMap<Long, Long>()
    private val askBuckets = HashMap<Long, Long>()

    private var aggMultiplier: Long = 1

    private val _displayLevels = ArrayList<DomLevel>()
    private val _displayLevelsFlow = MutableStateFlow<List<DomLevel>>(emptyList())
    val displayLevels: StateFlow<List<DomLevel>> = _displayLevelsFlow.asStateFlow()

    private fun publishDisplayLevels() {
        _displayLevelsFlow.value = ArrayList(_displayLevels)
    }

    private val _incrementalBestBid = MutableStateFlow<Double?>(null)
    val incrementalBestBid: StateFlow<Double?> = _incrementalBestBid.asStateFlow()

    private val _incrementalBestAsk = MutableStateFlow<Double?>(null)
    val incrementalBestAsk: StateFlow<Double?> = _incrementalBestAsk.asStateFlow()

    private val _incrementalBestBidQuantity = MutableStateFlow<Double?>(null)
    val incrementalBestBidQuantity: StateFlow<Double?> = _incrementalBestBidQuantity.asStateFlow()

    private val _incrementalBestAskQuantity = MutableStateFlow<Double?>(null)
    val incrementalBestAskQuantity: StateFlow<Double?> = _incrementalBestAskQuantity.asStateFlow()

    private var bestBidTicks: Long? = null
    private var bestAskTicks: Long? = null

    private val _bestBidDisplayTicks = MutableStateFlow<Long?>(null)
    val bestBidDisplayTicks: StateFlow<Long?> = _bestBidDisplayTicks.asStateFlow()

    private val _bestAskDisplayTicks = MutableStateFlow<Long?>(null)
    val bestAskDisplayTicks: StateFlow<Long?> = _bestAskDisplayTicks.asStateFlow()

    private val _incrementalBids = mutableStateMapOf<Double, Double>()
    internal val incrementalBids: Map<Double, Double> get() = _incrementalBids

    private val _incrementalAsks = mutableStateMapOf<Double, Double>()
    internal val incrementalAsks: Map<Double, Double> get() = _incrementalAsks

    override fun dispose() {
        subscriptionJob?.cancel()
        viewModelScope.cancel()
    }

    init {
        loadSymbols()
        viewModelScope.launch {
            delay(500)
            fetchSymbolMetadata(_domOptions.value.symbol.symbol)
        }
        restartSubscription(_domOptions.value)
    }

    fun updateDomOptions(newOptions: DomOptions) {
        val oldOptions = _domOptions.value
        if (oldOptions != newOptions) {
            println("📊 VM: DomOptions updated")
            val aggChanged = oldOptions.aggregation.multiplier != newOptions.aggregation.multiplier
            _domOptions.value = newOptions
            updateAggMultiplier()

            val subscriptionChanged =
                oldOptions.provider != newOptions.provider ||
                oldOptions.symbol != newOptions.symbol ||
                oldOptions.depth != newOptions.depth

            if (subscriptionChanged) {
                restartSubscription(newOptions)
            }
            if (oldOptions.symbol != newOptions.symbol) {
                fetchSymbolMetadata(newOptions.symbol.symbol)
            }
            if (aggChanged && !subscriptionChanged) {
                rebuildBucketsAndDisplay()
            }
        }
    }

    private fun updateAggMultiplier() {
        aggMultiplier = _domOptions.value.aggregation.multiplier.roundToLong()
    }

    private fun restartSubscription(options: DomOptions) {
        subscriptionJob?.cancel()
        subscriptionJob = viewModelScope.launch {
            updateAggMultiplier()
            subscribeToIncrementalDom(options)
        }
    }

    private suspend fun subscribeToIncrementalDom(options: DomOptions) {
        rawBids.clear(); rawAsks.clear()
        bidBuckets.clear(); askBuckets.clear()
        _displayLevels.clear()
        _incrementalBids.clear(); _incrementalAsks.clear()
        _incrementalBestBid.value = null; _incrementalBestAsk.value = null
        _incrementalBestBidQuantity.value = null; _incrementalBestAskQuantity.value = null
        bestBidTicks = null; bestAskTicks = null
        _bestBidDisplayTicks.value = null; _bestAskDisplayTicks.value = null

        domRepository.subscribeToDomEvents(
            symbol = options.symbol.symbol,
            depth = options.depth.value
        ).catch { e ->
            println("❌ DOM Events Error: ${e.message}")
            e.printStackTrace()
        }.collect { event ->
            processDomEvent(event)
        }
    }

    // ── Обработка событий ──

    private fun processDomEvent(event: DomEvent) {
        when (event) {
            is DomEvent.Snapshot -> handleSnapshot(event)
            is DomEvent.UpdateBid -> handleUpdate(event.price, event.quantity, Side.BID)
            is DomEvent.UpdateAsk -> handleUpdate(event.price, event.quantity, Side.ASK)
            is DomEvent.BestPrices -> handleBestPrices(event)
            DomEvent.Reset -> handleReset()
        }
    }

    private fun handleSnapshot(event: DomEvent.Snapshot) {
        rawBids.clear(); rawAsks.clear()
        bidBuckets.clear(); askBuckets.clear()
        _displayLevels.clear()
        _incrementalBids.clear(); _incrementalAsks.clear()

        event.snapshot.bids.forEach { (priceStr, qtyStr) ->
            val dPrice = priceStr.toDoubleOrNull() ?: return@forEach
            val dQty = qtyStr.toDoubleOrNull() ?: return@forEach
            _incrementalBids[dPrice] = dQty
            if (!scaleReady) return@forEach
            val pt = toPriceTicks(dPrice)
            val qs = toQtySteps(dQty)
            if (qs <= 0L) return@forEach
            rawBids[pt] = qs
            accumulateBucket(bidBuckets, pt, qs)
        }

        event.snapshot.asks.forEach { (priceStr, qtyStr) ->
            val dPrice = priceStr.toDoubleOrNull() ?: return@forEach
            val dQty = qtyStr.toDoubleOrNull() ?: return@forEach
            _incrementalAsks[dPrice] = dQty
            if (!scaleReady) return@forEach
            val pt = toPriceTicks(dPrice)
            val qs = toQtySteps(dQty)
            if (qs <= 0L) return@forEach
            rawAsks[pt] = qs
            accumulateBucket(askBuckets, pt, qs)
        }

        rebuildDisplayFromBuckets()
    }

    private fun handleUpdate(price: Double, quantity: Double, side: Side) {
        if (side == Side.BID) {
            if (quantity == 0.0) _incrementalBids.remove(price) else _incrementalBids[price] = quantity
        } else {
            if (quantity == 0.0) _incrementalAsks.remove(price) else _incrementalAsks[price] = quantity
        }
        if (!scaleReady) return

        val pt = toPriceTicks(price)
        val qs = toQtySteps(quantity)
        val raw = if (side == Side.BID) rawBids else rawAsks
        val buckets = if (side == Side.BID) bidBuckets else askBuckets

        val oldSteps = raw[pt] ?: 0L
        val deltaSteps = qs - oldSteps
        if (deltaSteps == 0L) return

        if (qs == 0L) raw.remove(pt) else raw[pt] = qs

        val bi = bucketIndex(pt)
        val currentTotal = buckets[bi] ?: 0L
        val newTotal = currentTotal + deltaSteps
        if (newTotal <= 0L) buckets.remove(bi) else buckets[bi] = newTotal

        if (isOutsideDepthWindow(bi)) return
        patchDisplayLevel(bi)
    }

    private fun isOutsideDepthWindow(bucketIndex: Long): Boolean {
        val depth = _domOptions.value.depth.value
        val bbBucket = bestBidTicks?.let { bucketIndex(it) }
        val baBucket = bestAskTicks?.let { bucketIndex(it) }
        if (bbBucket == null || baBucket == null) return false
        val lower = bbBucket - depth
        val upper = baBucket + depth
        return bucketIndex < lower || bucketIndex > upper
    }

    private fun handleBestPrices(event: DomEvent.BestPrices) {
        _incrementalBestBid.value = event.bestBid
        _incrementalBestAsk.value = event.bestAsk
        _incrementalBestBidQuantity.value = event.bestBidQuantity
        _incrementalBestAskQuantity.value = event.bestAskQuantity
        bestBidTicks = toPriceTicksOrNull(event.bestBid)
        bestAskTicks = toPriceTicksOrNull(event.bestAsk)
        _bestBidDisplayTicks.value = bestBidTicks?.let { displayBucket(it) }
        _bestAskDisplayTicks.value = bestAskTicks?.let { displayBucket(it) }
    }

    private fun handleReset() {
        rawBids.clear(); rawAsks.clear()
        bidBuckets.clear(); askBuckets.clear()
        _displayLevels.clear()
        _incrementalBids.clear(); _incrementalAsks.clear()
        _incrementalBestBid.value = null; _incrementalBestAsk.value = null
        _incrementalBestBidQuantity.value = null; _incrementalBestAskQuantity.value = null
        bestBidTicks = null; bestAskTicks = null
        _bestBidDisplayTicks.value = null; _bestAskDisplayTicks.value = null
        publishDisplayLevels()
    }

    // ── Управление списком ──

    private fun patchDisplayLevel(bucketIndex: Long) {
        val priceTicks = bucketIndex * aggMultiplier
        val bid = bidBuckets[bucketIndex] ?: 0L
        val ask = askBuckets[bucketIndex] ?: 0L

        if (bid == 0L && ask == 0L) {
            removeDisplayLevel(priceTicks)
            publishDisplayLevels()
            return
        }

        val level = DomLevel(priceTicks, bid, ask)
        putDisplayLevel(level)
        enforceDepth()
        publishDisplayLevels()
    }

    private fun putDisplayLevel(level: DomLevel) {
        for (i in _displayLevels.indices) {
            val existing = _displayLevels[i]
            if (existing.priceTicks == level.priceTicks) {
                if (existing != level) _displayLevels[i] = level
                return
            }
            if (existing.priceTicks < level.priceTicks) {
                _displayLevels.add(i, level)
                return
            }
        }
        _displayLevels.add(level)
    }

    private fun removeDisplayLevel(priceTicks: Long) {
        val idx = _displayLevels.indexOfFirst { it.priceTicks == priceTicks }
        if (idx >= 0) _displayLevels.removeAt(idx)
    }

    private fun enforceDepth() {
        val depth = _domOptions.value.depth.value
        val bbBucket = bestBidTicks?.let { bucketIndex(it) }
        val baBucket = bestAskTicks?.let { bucketIndex(it) }

        // До прихода BookTicker — усекаем до 2*depth верхних по цене
        if (bbBucket == null && baBucket == null) {
            while (_displayLevels.size > depth * 2) {
                _displayLevels.removeAt(_displayLevels.lastIndex)
            }
            return
        }

        var bidCount = 0
        var askCount = 0
        val toRemove = mutableListOf<Int>()

        for (i in _displayLevels.indices) {
            val pt = _displayLevels[i].priceTicks
            when (classify(pt, bbBucket, baBucket)) {
                PriceZone.BID -> {
                    if (bidCount >= depth) toRemove.add(i) else bidCount++
                }
                PriceZone.ASK -> {
                    if (askCount >= depth) toRemove.add(i) else askCount++
                }
                PriceZone.BETWEEN -> {}
            }
        }
        if (toRemove.isNotEmpty()) {
            for (i in toRemove.reversed()) _displayLevels.removeAt(i)
        }
    }

    private enum class PriceZone { BID, ASK, BETWEEN }
    private enum class Side { BID, ASK }

    private fun classify(pt: Long, bb: Long?, ba: Long?): PriceZone {
        if (bb != null && pt <= bb) return PriceZone.BID
        if (ba != null && pt >= ba) return PriceZone.ASK
        return PriceZone.BETWEEN
    }

    private fun accumulateBucket(buckets: HashMap<Long, Long>, priceTicks: Long, steps: Long) {
        val bi = bucketIndex(priceTicks)
        buckets[bi] = (buckets[bi] ?: 0L) + steps
    }

    private fun rebuildDisplayFromBuckets() {
        _displayLevels.clear()
        val ptToBid = HashMap<Long, Long>()
        bidBuckets.forEach { (bi, s) -> ptToBid[bi * aggMultiplier] = (ptToBid[bi * aggMultiplier] ?: 0L) + s }
        val ptToAsk = HashMap<Long, Long>()
        askBuckets.forEach { (bi, s) -> ptToAsk[bi * aggMultiplier] = (ptToAsk[bi * aggMultiplier] ?: 0L) + s }
        val allPts = LinkedHashSet<Long>()
        ptToBid.keys.sortedDescending().forEach { allPts.add(it) }
        ptToAsk.keys.sortedDescending().forEach { allPts.add(it) }
        for (pt in allPts.sortedDescending()) {
            val bid = ptToBid[pt] ?: 0L
            val ask = ptToAsk[pt] ?: 0L
            if (bid > 0L || ask > 0L) _displayLevels.add(DomLevel(pt, bid, ask))
        }
        enforceDepth()
        publishDisplayLevels()
    }

    private fun rebuildBucketsAndDisplay() {
        bidBuckets.clear(); askBuckets.clear()
        rawBids.forEach { (pt, qs) -> accumulateBucket(bidBuckets, pt, qs) }
        rawAsks.forEach { (pt, qs) -> accumulateBucket(askBuckets, pt, qs) }
        rebuildDisplayFromBuckets()
    }

    // ── Конвертация ──

    private fun bucketIndex(priceTicks: Long): Long = priceTicks / aggMultiplier
    private fun displayBucket(priceTicks: Long): Long = bucketIndex(priceTicks) * aggMultiplier

    private fun toPriceTicks(price: Double): Long = (price / tickSize).roundToLong()
    private fun toQtySteps(qty: Double): Long = (qty / stepSize).roundToLong()
    private fun toPriceTicksOrNull(price: Double): Long? = if (scaleReady) toPriceTicks(price) else null
    private fun toQtyStepsOrNull(qty: Double): Long? = if (scaleReady) toQtySteps(qty) else null

    // ── Команды (без изменений) ──

    fun executeCommand(command: TradingCommand?) {
        if (command != null) {
            viewModelScope.launch {
                if (!_isTradingEnabled.value && command !is TradeOffCommand) {
                    _lastCommandResult.value = CommandResult.TradingDisabled
                    return@launch
                }
                if (!command.canExecute()) {
                    _lastCommandResult.value = CommandResult.Error("Cannot execute command: ${command.getDescription()}")
                    return@launch
                }
                command.execute()
            }
        }
    }

    fun selectPrice(price: Double?) {
        if (_selectedPrice.value != price) _selectedPrice.value = price
    }

    fun updateOrderQuantity(quantity: String) {
        _orderQuantity.value = quantity
    }

    fun handleOrderIntent(intent: OrderIntent) {
        val command = when (intent) {
            is OrderIntent.MarketBuy -> BuyMarketCommand(intent.symbol, intent.quantity) { _lastCommandResult.value = it }
            is OrderIntent.MarketSell -> SellMarketCommand(intent.symbol, intent.quantity) { _lastCommandResult.value = it }
            is OrderIntent.LimitBuy -> BuyLimitCommand(intent.symbol, intent.price, intent.quantity) { _lastCommandResult.value = it }
            is OrderIntent.LimitSell -> SellLimitCommand(intent.symbol, intent.price, intent.quantity) { _lastCommandResult.value = it }
            is OrderIntent.BestBidBuy -> BuyBestBidCommand(intent.symbol, intent.bestBidPrice, intent.quantity) { _lastCommandResult.value = it }
            is OrderIntent.BestAskSell -> SellBestAskCommand(intent.symbol, intent.bestAskPrice, intent.quantity) { _lastCommandResult.value = it }
            OrderIntent.ToggleTrading -> TradeOffCommand { _lastCommandResult.value = it }
        }
        executeCommand(command)
    }

    // ── Загрузка метаданных ──

    private fun loadSymbols() {
        if (symbolInfoRepository == null) return
        viewModelScope.launch {
            try {
                val allSymbols = symbolInfoRepository.getAllSymbolsInfo() ?: emptyList()
                val tradingSymbols = allSymbols
                    .filter { it.status == "TRADING" }
                    .map { TradingSymbol.fromSymbolInfo(it, _domOptions.value.provider) }
                    .sortedBy { it.symbol }
                if (tradingSymbols.isNotEmpty()) _loadedSymbols.value = tradingSymbols
            } catch (e: Exception) {
                println("⚠️ Failed to load symbols from SymbolInfoRepository: ${e.message}")
            }
        }
    }

    private fun fetchSymbolMetadata(symbol: String) {
        if (symbolInfoRepository == null) return
        viewModelScope.launch {
            try {
                val info = symbolInfoRepository.getSymbolInfo(symbol) ?: return@launch
                tickSize = info.tickSize
                stepSize = info.stepSize
                _symbolTickSize.value = tickSize
                _symbolStepSize.value = stepSize
            } catch (e: Exception) {
                println("❌ Failed to fetch metadata for $symbol: ${e.message}")
            }
        }
    }
}
