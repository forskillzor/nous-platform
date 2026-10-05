/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.api.market.commands.*
import com.aandios.nous.api.market.model.orderbook.DomEvent
import com.aandios.nous.core.Disposable
import com.aandios.nous.core.data.repository.SymbolInfoRepositoryImpl
import com.aandios.nous.core.domain.repository.DomRepository
import com.aandios.nous.core.domain.repository.SymbolInfoRepository
import com.aandios.nous.feature.dom.data.repository.DomRepositoryImpl
import com.aandios.nous.feature.dom.domain.DomOptions
import com.aandios.nous.feature.dom.domain.TradingSymbol
import com.aandios.nous.feature.dom.domain.model.OrderIntent
import com.aandios.nous.feature.dom.ui.model.BestPricesState
import com.aandios.nous.feature.dom.ui.model.DomLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlin.math.roundToLong

/**
 * Стакан (DOM). Архитектура «одна таблица» на partial-стриме:
 * каждое окно (топ-N уровней с абсолютными объёмами) полностью заменяет
 * книгу — [levels] (SnapshotStateMap, O(1), точечная инвалидация), а UI
 * читает отсортированный вид [sortedLevels] (derivedStateOf).
 *
 * Провайдер данных выбирается через [ProviderRegistry]: адаптеры резолвятся
 * по [DomOptions.provider] (id зарегистрированного провайдера), переключение
 * — обычная смена опций с переподпиской.
 */
class DomViewModel(
    private val providerRegistry: ProviderRegistry,
    private val coroutineDispatcher: CoroutineDispatcher? = null,
) : Disposable {
    private val dispatcher = coroutineDispatcher ?: Dispatchers.Main
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

    private var aggMultiplier: Long = 1

    // ЕДИНСТВЕННАЯ таблица уровней: её читает UI (лесенка: levelAt(key) по строке цены)
    private val levels = mutableStateMapOf<Long, DomLevel>()
    private val sortedLevelsState = derivedStateOf {
        levels.values.sortedByDescending { it.priceTicks }
    }
    val sortedLevels: List<DomLevel> get() = sortedLevelsState.value

    /** Таблица уровней для рендера лесенки (чтение по ключу реактивно). */
    internal val levelsMap: Map<Long, DomLevel> get() = levels

    /** Шаг строки лесенки в тиках (размер корзины агрегации). */
    internal val ladderStepTicks: Long get() = aggMultiplier

    // Одно состояние лучших цен (1 запись на тик вместо шести)
    private val _bestPrices = MutableStateFlow(BestPricesState())
    val bestPrices: StateFlow<BestPricesState> = _bestPrices.asStateFlow()

    // Последнее окно книги по цене (Double) — источник для пересборки при смене агрегации
    private val bidsByPrice = HashMap<Double, Double>()
    private val asksByPrice = HashMap<Double, Double>()

    // Читаются только тестами
    internal val windowBids: Map<Double, Double> get() = bidsByPrice
    internal val windowAsks: Map<Double, Double> get() = asksByPrice

    override fun dispose() {
        subscriptionJob?.cancel()
        viewModelScope.cancel()
    }

    init {
        loadSymbols(_domOptions.value.provider)
        viewModelScope.launch {
            delay(500)
            fetchSymbolMetadata(_domOptions.value.symbol.symbol)
        }
        restartSubscription(_domOptions.value)
    }

    fun updateDomOptions(newOptions: DomOptions) {
        val oldOptions = _domOptions.value
        if (oldOptions != newOptions) {
            val aggChanged = oldOptions.aggregation.multiplier != newOptions.aggregation.multiplier
            _domOptions.value = newOptions
            updateAggMultiplier()

            val providerChanged = oldOptions.provider != newOptions.provider
            val subscriptionChanged =
                providerChanged ||
                oldOptions.symbol != newOptions.symbol ||
                oldOptions.depth != newOptions.depth

            if (subscriptionChanged) {
                restartSubscription(newOptions)
            }
            if (providerChanged) {
                loadSymbols(newOptions.provider)
                fetchSymbolMetadata(newOptions.symbol.symbol)
            } else if (oldOptions.symbol != newOptions.symbol) {
                fetchSymbolMetadata(newOptions.symbol.symbol)
            }
            if (aggChanged && !subscriptionChanged) {
                rebuildLevelsFromWindow()
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
            subscribeToBookWindows(options)
        }
    }

    private suspend fun subscribeToBookWindows(options: DomOptions) {
        bidsByPrice.clear(); asksByPrice.clear()
        levels.clear()
        _bestPrices.value = BestPricesState()

        val provider = providerRegistry.get(options.provider) ?: run {
            println("❌ DOM: provider ${options.provider} not registered")
            return
        }
        val domAdapter = provider.dom
        val bookTickerAdapter = provider.bookTicker
        if (domAdapter == null || bookTickerAdapter == null) {
            println("❌ DOM: provider ${provider.config.displayName} has no DOM/bookTicker adapters")
            return
        }
        val repository: DomRepository = DomRepositoryImpl(domAdapter, bookTickerAdapter)

        repository.subscribeToDomEvents(
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
            is DomEvent.BookWindow -> handleBookWindow(event)
            is DomEvent.BestPrices -> handleBestPrices(event)
        }
    }

    /** Окно полностью заменяет книгу (partial-стрим, абсолютные объёмы). */
    private fun handleBookWindow(event: DomEvent.BookWindow) {
        bidsByPrice.clear()
        asksByPrice.clear()
        event.bids.forEach { u -> bidsByPrice[u.price] = u.quantity }
        event.asks.forEach { u -> asksByPrice[u.price] = u.quantity }

        // Метаданные (tickSize/stepSize) могли ещё не прийти — окно пропускаем,
        // следующее (через 100мс) отрисует книгу целиком
        if (scaleReady) rebuildLevelsFromWindow()
    }

    private fun handleBestPrices(event: DomEvent.BestPrices) {
        val bidTicks = toPriceTicksOrNull(event.bestBid)
        val askTicks = toPriceTicksOrNull(event.bestAsk)
        _bestPrices.value = BestPricesState(
            bestBid = event.bestBid,
            bestAsk = event.bestAsk,
            bestBidQuantity = event.bestBidQuantity,
            bestAskQuantity = event.bestAskQuantity,
            bestBidDisplayTicks = bidTicks?.let { bucketKey(it) },
            bestAskDisplayTicks = askTicks?.let { bucketKey(it) },
            lastPriceDisplayTicks = if (event.lastPrice > 0.0) {
                toPriceTicksOrNull(event.lastPrice)?.let { bucketKey(it) }
            } else null,
        )
    }

    // ── Пересборка и обрезка ──

    /** Пересборка уровней из последнего окна (новое окно, смена агрегации). */
    private fun rebuildLevelsFromWindow() {
        levels.clear()
        bidsByPrice.forEach { (price, qty) ->
            val pt = toPriceTicks(price)
            val qs = toQtySteps(qty)
            if (qs <= 0L) return@forEach
            val key = bucketKey(pt)
            val cur = levels[key]?.bidSteps ?: 0L
            levels[key] = DomLevel(key, bidSteps = cur + qs)
        }
        // ask вытесняет bid при совпадении ключа (правило одной стороны)
        asksByPrice.forEach { (price, qty) ->
            val pt = toPriceTicks(price)
            val qs = toQtySteps(qty)
            if (qs <= 0L) return@forEach
            val key = bucketKey(pt)
            val cur = levels[key]?.askSteps ?: 0L
            levels[key] = DomLevel(key, askSteps = cur + qs)
        }
    }

    // ── Конвертация ──

    private fun bucketKey(priceTicks: Long): Long = priceTicks / aggMultiplier * aggMultiplier

    private fun toPriceTicks(price: Double): Long = (price / tickSize).roundToLong()
    private fun toQtySteps(qty: Double): Long = (qty / stepSize).roundToLong()
    private fun toPriceTicksOrNull(price: Double): Long? = if (scaleReady) toPriceTicks(price) else null

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

    private fun loadSymbols(providerId: String) {
        val symbolInfoAdapter = providerRegistry.get(providerId)?.symbolInfo ?: return
        val repository: SymbolInfoRepository = SymbolInfoRepositoryImpl(symbolInfoAdapter)
        viewModelScope.launch {
            try {
                val allSymbols = repository.getAllSymbolsInfo()
                val tradingSymbols = allSymbols
                    .filter { it.status == "TRADING" }
                    .map { TradingSymbol.fromSymbolInfo(it, providerId) }
                    .sortedBy { it.symbol }
                if (tradingSymbols.isNotEmpty()) _loadedSymbols.value = tradingSymbols
            } catch (e: Exception) {
                println("⚠️ Failed to load symbols from SymbolInfoRepository: ${e.message}")
            }
        }
    }

    private fun fetchSymbolMetadata(symbol: String) {
        val symbolInfoRepository = providerRegistry.get(_domOptions.value.provider)
            ?.symbolInfo?.let { SymbolInfoRepositoryImpl(it) }
            ?: return
        viewModelScope.launch {
            try {
                val info = symbolInfoRepository.getSymbolInfo(symbol) ?: return@launch
                tickSize = info.tickSize
                stepSize = info.stepSize
                _symbolTickSize.value = tickSize
                _symbolStepSize.value = stepSize
                // Метаданные могли прийти после первого окна — пересобираем
                // книгу из последнего окна, чтобы стакан появился сразу
                if (bidsByPrice.isNotEmpty() || asksByPrice.isNotEmpty()) {
                    rebuildLevelsFromWindow()
                }
            } catch (e: Exception) {
                println("❌ Failed to fetch metadata for $symbol: ${e.message}")
            }
        }
    }
}
