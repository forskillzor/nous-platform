/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trades.ui

import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.api.market.model.ContractType
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.api.market.model.trades.Trade
import com.aandios.nous.core.data.repository.SymbolInfoRepositoryImpl
import com.aandios.nous.core.data.repository.TradesRepositoryImpl
import com.aandios.nous.core.Disposable
import com.aandios.nous.core.storage.StateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Фильтр размера сделки.
 * Значения генерируются на основе minQty из SymbolInfo.
 */
sealed class SizeFilter {
    abstract val label: String
    data object All : SizeFilter() { override val label = "All" }
    data object MinQty : SizeFilter() { override val label = "≥ min" }
    data object MinQtyx10 : SizeFilter() { override val label = "≥ ×10" }
    data object MinQtyx100 : SizeFilter() { override val label = "≥ ×100" }
    data class Custom(val value: Double) : SizeFilter() {
        override val label: String get() {
            val s = value.toString().trimEnd('0').trimEnd('.')
            return "≥ $s"
        }
    }
}

sealed class TradesState {
    data object Loading : TradesState()
    data class Connected(val trades: List<Trade>) : TradesState()
    data class Error(val message: String) : TradesState()
}

class TradesViewModel(
    private val providerRegistry: ProviderRegistry,
    private val stateStore: StateStore? = null,
) : Disposable {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var subscriptionJob: Job? = null

    private val _state = MutableStateFlow<TradesState>(TradesState.Loading)
    val state: StateFlow<TradesState> = _state.asStateFlow()

    // Текущий провайдер данных (id из ProviderRegistry)
    private val _currentProviderId = MutableStateFlow(providerRegistry.first()?.providerId.orEmpty())
    val currentProviderId: StateFlow<String> = _currentProviderId.asStateFlow()

    // Основной фид: держим как можно дольше (500 последних сделок).
    private val maxTrades = 500
    // Отдельный долгоживущий буфер сделок под фильтр: редкие крупные сделки
    // живут долго и не вытесняются потоком мелких из основного фида.
    private val maxFilteredTrades = 2000

    private val _filteredBuffer = MutableStateFlow<List<Trade>>(emptyList())
    val filteredBuffer: StateFlow<List<Trade>> = _filteredBuffer.asStateFlow()

    // Символы, загруженные через symbolInfoRepository
    private val _loadedSymbols = MutableStateFlow<List<SymbolInfo>>(emptyList())
    val loadedSymbols: StateFlow<List<SymbolInfo>> = _loadedSymbols.asStateFlow()

    /** Все символы провайдера (без фильтра по типу контракта). */
    private var allSymbols: List<SymbolInfo> = emptyList()

    /** Тип контрактов панели: USDT-M / COIN-M — фильтр списка символов. */
    private val _contractType = MutableStateFlow(ContractType.USDT_M)
    val contractType: StateFlow<ContractType> = _contractType.asStateFlow()

    // Информация о текущем символе (minQty, tickSize и т.д.)
    private val _currentSymbol = MutableStateFlow("")
    val currentSymbol: StateFlow<String> = _currentSymbol.asStateFlow()

    private val _currentSymbolInfo = MutableStateFlow<SymbolInfo?>(null)
    val currentSymbolInfo: StateFlow<SymbolInfo?> = _currentSymbolInfo.asStateFlow()

    // Минимальный размер сделки из SymbolInfo
    val minTradeSize: Double? get() = _currentSymbolInfo.value?.minQty

    // Выбранный фильтр размера
    private val _selectedSizeFilter = MutableStateFlow<SizeFilter>(SizeFilter.All)
    val selectedSizeFilter: StateFlow<SizeFilter> = _selectedSizeFilter.asStateFlow()

    // Текст в поле кастомного фильтра
    private val _filterText = MutableStateFlow("")
    val filterText: StateFlow<String> = _filterText.asStateFlow()

    // Пользовательские пресеты
    private val _customPresets = MutableStateFlow<List<Double>>(emptyList())
    val customPresets: StateFlow<List<Double>> = _customPresets.asStateFlow()

    private var subscribedSymbol: String = ""

    override fun dispose() {
        subscriptionJob?.cancel()
        viewModelScope.cancel()
    }

    init {
        // Биржа панели переживает перезапуск + список символов при старте
        viewModelScope.launch {
            stateStore?.getString(PROVIDER_STORE_KEY)?.takeIf { it.isNotBlank() }?.let { saved ->
                if (providerRegistry.get(saved) != null) _currentProviderId.value = saved
            }
            // Тип контрактов панели (USDT-M / COIN-M) — до загрузки списка
            stateStore?.getString(CONTRACT_TYPE_STORE_KEY)?.let { raw ->
                runCatching { ContractType.valueOf(raw) }.getOrNull()?.let { _contractType.value = it }
            }
            loadSymbols()
        }
    }

    /** Смена типа контрактов: фильтр списка символов + fallback текущего. */
    fun setContractType(type: ContractType) {
        if (_contractType.value == type) return
        _contractType.value = type
        viewModelScope.launch { stateStore?.putString(CONTRACT_TYPE_STORE_KEY, type.name) }
        applyContractTypeFilter()
        ensureSymbolMatchesContractType()
    }

    /**
     * Проверяет, проходит ли сделка текущий/заданный фильтр размера.
     */
    private fun matchesFilter(trade: Trade, filter: SizeFilter): Boolean {
        return when (filter) {
            is SizeFilter.All -> true
            is SizeFilter.MinQty -> {
                val mq = minTradeSize ?: return true
                trade.quantity >= mq
            }
            is SizeFilter.MinQtyx10 -> {
                val mq = minTradeSize ?: return true
                trade.quantity >= mq * 10
            }
            is SizeFilter.MinQtyx100 -> {
                val mq = minTradeSize ?: return true
                trade.quantity >= mq * 100
            }
            is SizeFilter.Custom -> trade.quantity >= filter.value
        }
    }

    /**
     * Пересобирает буфер фильтра: при All — пустой, иначе — совпадения
     * из текущего фида (историю дольше фида не храним).
     */
    private fun reseedFilteredBuffer() {
        val filter = _selectedSizeFilter.value
        val feed = (_state.value as? TradesState.Connected)?.trades ?: emptyList()
        _filteredBuffer.value = if (filter is SizeFilter.All) emptyList()
        else feed.filter { matchesFilter(it, filter) }.take(maxFilteredTrades)
    }

    /**
     * Список для отображения: с фильтром — долгоживущий буфер совпадений,
     * без фильтра — основной фид.
     */
    fun visibleTrades(feed: List<Trade>): List<Trade> {
        return if (_selectedSizeFilter.value is SizeFilter.All) feed
        else _filteredBuffer.value
    }

    fun updateSizeFilter(filter: SizeFilter) {
        _selectedSizeFilter.value = filter
        _filterText.value = ""
        reseedFilteredBuffer()
    }

    fun setCustomFilterThreshold(value: String) {
        _filterText.value = value
        val parsed = value.trim().toDoubleOrNull()
        _selectedSizeFilter.value = if (parsed != null && parsed > 0) {
            SizeFilter.Custom(parsed)
        } else {
            SizeFilter.All
        }
        reseedFilteredBuffer()
    }

    fun addPreset(value: Double) {
        _customPresets.value = (_customPresets.value + value).sorted()
    }

    fun editPreset(index: Int, value: Double) {
        val list = _customPresets.value.toMutableList()
        if (index in list.indices) {
            list[index] = value
            _customPresets.value = list.sorted()
        }
    }

    fun deletePreset(index: Int) {
        val list = _customPresets.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _customPresets.value = list
        }
    }

    fun setPresets(presets: List<Double>) {
        _customPresets.value = presets.sorted()
    }

    /** Смена провайдера данных: переподписка на текущий символ + перезагрузка символов. */
    fun selectProvider(providerId: String) {
        if (_currentProviderId.value == providerId) return
        _currentProviderId.value = providerId
        viewModelScope.launch { stateStore?.putString(PROVIDER_STORE_KEY, providerId) }
        _filteredBuffer.value = emptyList()
        subscribedSymbol = ""
        loadSymbols()
        val symbol = _currentSymbol.value
        if (symbol.isNotEmpty()) subscribeToTrades(symbol)
    }

    fun subscribeToTrades(symbol: String) {
        if (symbol == subscribedSymbol && _state.value is TradesState.Connected) return
        subscribedSymbol = symbol
        _currentSymbol.value = symbol

        subscriptionJob?.cancel()
        _state.value = TradesState.Loading
        _filteredBuffer.value = emptyList()

        // Загружаем SymbolInfo для нового символа
        fetchSymbolInfo(symbol)

        val provider = providerRegistry.get(_currentProviderId.value)
        val tradesAdapter = provider?.trades
        if (tradesAdapter == null) {
            println("❌ Trades: provider ${provider?.config?.displayName ?: _currentProviderId.value} has no trades adapter")
            _state.value = TradesState.Error("Trades adapter not available")
            return
        }
        val repository = TradesRepositoryImpl(tradesAdapter = tradesAdapter)

        subscriptionJob = viewModelScope.launch {
            repository.getTradesStream(symbol)
                .catch { e ->
                    println("❌ Trades subscription error: ${e.message}")
                    _state.value = TradesState.Error("Ошибка: ${e.message}")
                }
                .collect { trade ->
                    val feed = listOf(trade) +
                        (_state.value as? TradesState.Connected)?.trades.orEmpty().take(maxTrades - 1)
                    _state.value = TradesState.Connected(feed)
                    // Сделка под текущим фильтром — копим в долгоживущий буфер
                    val filter = _selectedSizeFilter.value
                    if (filter !is SizeFilter.All && matchesFilter(trade, filter)) {
                        _filteredBuffer.value = (listOf(trade) + _filteredBuffer.value)
                            .take(maxFilteredTrades)
                    }
                }
        }
    }

    private fun loadSymbols() {
        val symbolInfoAdapter = providerRegistry.get(_currentProviderId.value)?.symbolInfo ?: return

        viewModelScope.launch {
            try {
                val repository = SymbolInfoRepositoryImpl(symbolInfoAdapter)
                allSymbols = repository.getAllSymbolsInfo()
                    .filter { it.status == "TRADING" }
                    .sortedBy { it.symbol }
                if (allSymbols.isNotEmpty()) {
                    applyContractTypeFilter()
                    ensureSymbolMatchesContractType()
                }
            } catch (e: Exception) {
                println("⚠️ TradesVM: Failed to load symbols: ${e.message}")
            }
        }
    }

    /** Показать только символы выбранного типа контрактов (USDT-M / COIN-M). */
    private fun applyContractTypeFilter() {
        val type = _contractType.value
        val filtered = allSymbols.filter { type.matches(it) }
        if (filtered.isNotEmpty()) _loadedSymbols.value = filtered
    }

    /** Если текущий символ не того типа — переключиться на первый подходящий. */
    private fun ensureSymbolMatchesContractType() {
        val info = _currentSymbolInfo.value ?: return
        if (_contractType.value.matches(info)) return
        val fallback = _loadedSymbols.value.firstOrNull() ?: return
        subscribeToTrades(fallback.symbol)
    }

    private fun fetchSymbolInfo(symbol: String) {
        val symbolInfoAdapter = providerRegistry.get(_currentProviderId.value)?.symbolInfo ?: return

        viewModelScope.launch {
            try {
                val symbolInfo = SymbolInfoRepositoryImpl(symbolInfoAdapter).getSymbolInfo(symbol)
                _currentSymbolInfo.value = symbolInfo
            } catch (e: Exception) {
                println("❌ TradesVM: Failed to fetch symbolInfo for $symbol: ${e.message}")
            }
        }
    }

    fun formatTime(timestamp: Long): String {
        val seconds = timestamp / 1000
        val hours = (seconds / 3600) % 24
        val minutes = (seconds / 60) % 60
        val secs = seconds % 60
        return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    }

    fun formatPrice(price: Double): String = fmt.formatPrice(price)

    fun formatQuantity(quantity: Double): String = fmt.formatVolume(quantity)

    fun clear() {
        viewModelScope.coroutineContext.cancelChildren()
        viewModelScope.cancel()
    }

    companion object {
        private val fmt = com.aandios.nous.core.ui.format.SymbolFormatter.DEFAULT

        /** Ключ StateStore: выбранная биржа trades-панели. */
        const val PROVIDER_STORE_KEY = "trades_provider"

        /** Ключ StateStore: тип контрактов панели (USDT_M / COIN_M). */
        const val CONTRACT_TYPE_STORE_KEY = "trades_contract_type"
    }
}
