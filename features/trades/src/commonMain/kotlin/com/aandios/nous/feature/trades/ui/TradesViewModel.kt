/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trades.ui

import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.api.market.model.trades.Trade
import com.aandios.nous.core.domain.repository.SymbolInfoRepository
import com.aandios.nous.core.domain.repository.TradesRepository
import com.aandios.nous.core.Disposable
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
    private val tradesRepository: TradesRepository,
    private val symbolInfoRepository: SymbolInfoRepository? = null,
) : Disposable {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var subscriptionJob: Job? = null

    private val _state = MutableStateFlow<TradesState>(TradesState.Loading)
    val state: StateFlow<TradesState> = _state.asStateFlow()

    private val maxTrades = 100

    // Символы, загруженные через symbolInfoRepository
    private val _loadedSymbols = MutableStateFlow<List<SymbolInfo>>(emptyList())
    val loadedSymbols: StateFlow<List<SymbolInfo>> = _loadedSymbols.asStateFlow()

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
        // Загружаем список символов при старте
        loadSymbols()
    }

    /**
     * Фильтрует список сделок по выбранному размеру.
     */
    private fun filterTrades(trades: List<Trade>): List<Trade> {
        val filter = _selectedSizeFilter.value
        return when (filter) {
            is SizeFilter.All -> trades
            is SizeFilter.MinQty -> {
                val mq = minTradeSize ?: return trades
                trades.filter { it.quantity >= mq }
            }
            is SizeFilter.MinQtyx10 -> {
                val mq = minTradeSize ?: return trades
                trades.filter { it.quantity >= mq * 10 }
            }
            is SizeFilter.MinQtyx100 -> {
                val mq = minTradeSize ?: return trades
                trades.filter { it.quantity >= mq * 100 }
            }
            is SizeFilter.Custom -> trades.filter { it.quantity >= filter.value }
        }
    }

    fun updateSizeFilter(filter: SizeFilter) {
        _selectedSizeFilter.value = filter
        _filterText.value = ""
        // Переприменяем фильтр к текущему стейту
        val currentState = _state.value
        if (currentState is TradesState.Connected) {
            // Просто обновляем состояние, чтобы триггернуть рекомпозицию
            _state.value = TradesState.Connected(currentState.trades)
        }
    }

    fun setCustomFilterThreshold(value: String) {
        _filterText.value = value
        val parsed = value.trim().toDoubleOrNull()
        if (parsed != null && parsed > 0) {
            _selectedSizeFilter.value = SizeFilter.Custom(parsed)
        } else {
            _selectedSizeFilter.value = SizeFilter.All
        }
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

    fun subscribeToTrades(symbol: String) {
        if (symbol == subscribedSymbol && _state.value is TradesState.Connected) return
        subscribedSymbol = symbol
        _currentSymbol.value = symbol

        subscriptionJob?.cancel()
        _state.value = TradesState.Loading

        // Загружаем SymbolInfo для нового символа
        fetchSymbolInfo(symbol)

        subscriptionJob = viewModelScope.launch {
            tradesRepository.getTradesStream(symbol)
                .catch { e ->
                    println("❌ Trades subscription error: ${e.message}")
                    _state.value = TradesState.Error("Ошибка: ${e.message}")
                }
                .collect { trade ->
                    val trades = listOf(trade) + (_state.value as? TradesState.Connected)?.trades.orEmpty().take(maxTrades - 1)
                    _state.value = TradesState.Connected(trades)
                }
        }
    }

    /**
     * Подписывается на сделки для переданного symbol и сразу возвращает
     * отфильтрованный список (если active filter != All).
     */
    fun getFilteredTrades(allTrades: List<Trade>): List<Trade> {
        return filterTrades(allTrades)
    }

    private fun loadSymbols() {
        if (symbolInfoRepository == null) return

        viewModelScope.launch {
            try {
                val allSymbols = symbolInfoRepository?.getAllSymbolsInfo() ?: emptyList()
                val tradingSymbols = allSymbols
                    .filter { it.status == "TRADING" }
                    .sortedBy { it.symbol }
                if (tradingSymbols.isNotEmpty()) {
                    _loadedSymbols.value = tradingSymbols
                }
            } catch (e: Exception) {
                println("⚠️ TradesVM: Failed to load symbols: ${e.message}")
            }
        }
    }

    private fun fetchSymbolInfo(symbol: String) {
        if (symbolInfoRepository == null) return

        viewModelScope.launch {
            try {
                val symbolInfo = symbolInfoRepository.getSymbolInfo(symbol)
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
    }
}
