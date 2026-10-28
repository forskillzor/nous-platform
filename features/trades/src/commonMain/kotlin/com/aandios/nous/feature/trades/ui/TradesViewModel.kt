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
import com.aandios.nous.core.market.MarketOverviewStore
import com.aandios.nous.core.storage.StateStore
import com.aandios.nous.core.ui.component.SymbolExplorerRow
import com.aandios.nous.core.ui.component.toSymbolExplorerRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    private val marketOverviewStore: MarketOverviewStore? = null,
    coroutineDispatcher: CoroutineDispatcher? = null,
) : Disposable {
    private val dispatcher = coroutineDispatcher ?: Dispatchers.Main
    private val viewModelScope = CoroutineScope(dispatcher + SupervisorJob())
    private var subscriptionJob: Job? = null

    private val _state = MutableStateFlow<TradesState>(TradesState.Loading)
    val state: StateFlow<TradesState> = _state.asStateFlow()

    // Текущий провайдер данных (id из ProviderRegistry)
    private val _currentProviderId = MutableStateFlow(providerRegistry.first()?.providerId.orEmpty())
    val currentProviderId: StateFlow<String> = _currentProviderId.asStateFlow()

    // Retention: raw 20k (источник для reseed), feed 500 (ALL), filtered 5k
    // (retention применяется после фильтрации).
    private val history = TradesHistoryBuffer()

    private val _filteredBuffer = MutableStateFlow<List<Trade>>(emptyList())
    val filteredBuffer: StateFlow<List<Trade>> = _filteredBuffer.asStateFlow()

    // Символы, загруженные через symbolInfoRepository
    private val _loadedSymbols = MutableStateFlow<List<SymbolInfo>>(emptyList())
    val loadedSymbols: StateFlow<List<SymbolInfo>> = _loadedSymbols.asStateFlow()

    /** Все символы провайдера (без фильтра по типу контракта). */
    private var allSymbols: List<SymbolInfo> = emptyList()

    /**
     * Строки explorer-дропдауна из общего каталога рынка ([MarketOverviewStore]):
     * прелоад и фоновый REST-цикл — внутри стора, панель только маппит их.
     */
    val explorerRows: StateFlow<List<SymbolExplorerRow>> =
        (marketOverviewStore?.state?.map { state -> state.rows.map { it.toSymbolExplorerRow() } }
            ?: flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Символ, выбранный в explorer у ДРУГОЙ биржи: применяется после loadSymbols. */
    private var pendingExplorerSymbol: String? = null

    /** Тип контрактов панели: USDT-M / COIN-M — фильтр списка символов. */
    private val _contractType = MutableStateFlow(ContractType.USDT_M)
    val contractType: StateFlow<ContractType> = _contractType.asStateFlow()

    /** Ширина панели в dp (настройка ползунком); null — дефолт виджета. */
    private val _panelWidthDp = MutableStateFlow<Float?>(null)
    val panelWidthDp: StateFlow<Float?> = _panelWidthDp.asStateFlow()

    /**
     * Измеренная минимальная ширина панели (dp) по тексту строки сделки —
     * нижняя граница ползунка ширины в ⚙ тайтла (заполняет TradesWidget).
     */
    private val _measuredMinWidthDp = MutableStateFlow(120f)
    val measuredMinWidthDp: StateFlow<Float> = _measuredMinWidthDp.asStateFlow()

    private var panelKey: String? = null

    private val _providerRestored = MutableStateFlow(false)
    val providerRestored: StateFlow<Boolean> = _providerRestored.asStateFlow()

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
        // Список символов при старте; per-panel биржа/контракты — в attachPanel
        viewModelScope.launch {
            loadSymbols()
        }
    }

    /** Смена типа контрактов: фильтр списка символов + fallback текущего. */
    fun setContractType(type: ContractType) {
        if (_contractType.value == type) return
        _contractType.value = type
        viewModelScope.launch { stateStore?.putString(contractTypeStoreKey(), type.name) }
        applyContractTypeFilter()
        ensureSymbolMatchesContractType()
    }

    /** Привязка панели: своя биржа/контракты и персист ширины. */
    fun attachPanel(panelId: String?, preferredProviderId: String? = null) {
        panelKey = panelId
        val store = stateStore
        if (store == null) {
            _providerRestored.value = true
            return
        }
        viewModelScope.launch {
            store.getString(widthStoreKey())?.toFloatOrNull()?.takeIf { it > 0f }?.let {
                _panelWidthDp.value = it
            }
            store.getString(contractTypeStoreKey())?.let { raw ->
                runCatching { ContractType.valueOf(raw) }.getOrNull()?.let { _contractType.value = it }
            }
            val saved = preferredProviderId?.takeIf { providerRegistry.get(it) != null }
                ?: store.getString(providerStoreKey())?.takeIf { providerRegistry.get(it) != null }
            if (!saved.isNullOrBlank() && saved != _currentProviderId.value) {
                selectProvider(saved)
            }
            _providerRestored.value = true
        }
    }

    private fun providerStoreKey(): String = "trades_provider_${panelKey ?: "default"}"

    private fun contractTypeStoreKey(): String = "trades_contract_${panelKey ?: "default"}"

    /** Установить ширину панели (dp) — превью в UI без записи в стор. */
    fun setPanelWidth(widthDp: Float) {
        if (widthDp <= 0f) return
        _panelWidthDp.value = widthDp
    }

    /** Сохранить текущую ширину панели (по окончании жеста ползунка). */
    fun persistPanelWidth() {
        val width = _panelWidthDp.value ?: return
        viewModelScope.launch { stateStore?.putString(widthStoreKey(), width.toString()) }
    }

    /** Измеренная минимальная ширина строки сделки (нижняя граница ползунка). */
    fun setMeasuredMinWidth(widthDp: Float) {
        if (widthDp > 0f) _measuredMinWidthDp.value = widthDp
    }

    private fun widthStoreKey(): String = "trades_width_${panelKey ?: "default"}"

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
     * Пересобирает filtered из raw-истории при смене фильтра (или когда
     * SymbolInfo пришёл позже первых сделок). Raw не очищается.
     */
    private fun reseedFilteredBuffer() {
        val filter = _selectedSizeFilter.value
        val active = filter !is SizeFilter.All
        history.reseed(active) { matchesFilter(it, filter) }
        _filteredBuffer.value = if (active) history.filteredSnapshot() else emptyList()
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
        viewModelScope.launch { stateStore?.putString(providerStoreKey(), providerId) }
        history.clear()
        _filteredBuffer.value = emptyList()
        subscribedSymbol = ""
        loadSymbols()
        val symbol = _currentSymbol.value
        if (symbol.isNotEmpty()) subscribeToTrades(symbol)
    }

    /**
     * Выбор строки explorer-дропдауна: смена биржи переключает провайдера,
     * символ применяется после загрузки списка биржи.
     */
    fun selectExplorerRow(row: SymbolExplorerRow) {
        if (row.exchangeId != _currentProviderId.value) {
            pendingExplorerSymbol = row.symbol
            selectProvider(row.exchangeId)
            return
        }
        applyExplorerSymbol(row.symbol)
    }

    /** Применить отложенный выбор explorer'а после загрузки символов биржи. */
    private fun applyPendingExplorerSymbol() {
        val symbol = pendingExplorerSymbol ?: return
        pendingExplorerSymbol = null
        applyExplorerSymbol(symbol)
    }

    /**
     * Синхронизировать тип контрактов по SymbolInfo выбранной строки (как в
     * chart/DOM) и переключить панель на этот символ.
     */
    private fun applyExplorerSymbol(symbol: String) {
        val info = allSymbols.firstOrNull { it.symbol == symbol }
            ?: loadedSymbols.value.firstOrNull()
            ?: return
        val type = ContractType.of(info)
        if (_contractType.value != type) setContractType(type)
        if (_currentSymbol.value != info.symbol) subscribeToTrades(info.symbol)
    }

    fun subscribeToTrades(symbol: String) {
        if (symbol == subscribedSymbol && _state.value is TradesState.Connected) return
        subscribedSymbol = symbol
        _currentSymbol.value = symbol

        subscriptionJob?.cancel()
        _state.value = TradesState.Loading
        history.clear()
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
                    val filter = _selectedSizeFilter.value
                    val active = filter !is SizeFilter.All
                    val filteredChanged = history.add(trade, active) {
                        matchesFilter(it, filter)
                    }
                    _state.value = TradesState.Connected(history.feedSnapshot())
                    if (filteredChanged) {
                        _filteredBuffer.value = history.filteredSnapshot()
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
                    applyPendingExplorerSymbol()
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
                // Символ уже сменился, пока грузились метаданные.
                if (_currentSymbol.value != symbol) return@launch
                _currentSymbolInfo.value = symbolInfo
                // MinQty-фильтр зависит от SymbolInfo: если он пришёл позже
                // первых сделок — пересобираем filtered из raw.
                if (_selectedSizeFilter.value !is SizeFilter.All) {
                    reseedFilteredBuffer()
                }
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
    }
}
