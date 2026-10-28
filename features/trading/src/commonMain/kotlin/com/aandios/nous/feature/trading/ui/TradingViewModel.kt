/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trading.ui

import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.api.market.model.Balance
import com.aandios.nous.api.market.model.ContractType
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.OrderStatus
import com.aandios.nous.api.market.model.trading.Position
import com.aandios.nous.api.market.model.trading.TradeFill
import com.aandios.nous.api.market.paper.PaperTrading
import com.aandios.nous.api.market.paper.effectiveTrading
import com.aandios.nous.core.Disposable
import com.aandios.nous.core.data.repository.SymbolInfoRepositoryImpl
import com.aandios.nous.core.storage.StateStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Статус подключения торговой сессии для окна Manage connections. */
enum class TradingConnectionStatus { CONNECTED, CONNECTING, ERROR }

/** Строка окна Manage connections: REST API или приватный стрим счёта. */
data class TradingConnection(
    val id: String,
    val label: String,
    val status: TradingConnectionStatus,
    val detail: String? = null,
)

/**
 * Состояние торговой панели: позиции, ордера, балансы, история +
 * настройки (плечо, режим маржи, режим позиций) и действия
 * (cancel/cancel-all/close position).
 *
 * Данные берутся из TradingAdapter активного провайдера (ProviderRegistry);
 * при смене провайдера всё перечитывается. Живые push-потоки (если есть)
 * обновляют состояние без полного рефреша.
 */
class TradingViewModel(
    private val providerRegistry: ProviderRegistry,
    private val stateStore: StateStore? = null,
    private val paperModeState: PaperModeState? = null,
) : Disposable {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var refreshJob: Job? = null
    private var liveJobs = mutableListOf<Job>()

    private val _providerId = MutableStateFlow(providerRegistry.first()?.providerId.orEmpty())
    val providerId: StateFlow<String> = _providerId.asStateFlow()

    private val _positions = MutableStateFlow<List<Position>>(emptyList())
    val positions: StateFlow<List<Position>> = _positions.asStateFlow()

    private val _openOrders = MutableStateFlow<List<Order>>(emptyList())
    val openOrders: StateFlow<List<Order>> = _openOrders.asStateFlow()

    private val _balances = MutableStateFlow<List<Balance>>(emptyList())
    val balances: StateFlow<List<Balance>> = _balances.asStateFlow()

    private val _tradeHistory = MutableStateFlow<List<TradeFill>>(emptyList())
    val tradeHistory: StateFlow<List<TradeFill>> = _tradeHistory.asStateFlow()

    private val _positionMode = MutableStateFlow<Int?>(null)
    val positionMode: StateFlow<Int?> = _positionMode.asStateFlow()

    /** Режим маржи для НОВЫХ ордеров: 1 isolated, 2 cross. */
    private val _marginMode = MutableStateFlow(2)
    val marginMode: StateFlow<Int> = _marginMode.asStateFlow()

    /** Плечо для новых ордеров (null — дефолт биржи). */
    private val _leverage = MutableStateFlow<Int?>(null)
    val leverage: StateFlow<Int?> = _leverage.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _lastMessage = MutableStateFlow<String?>(null)
    val lastMessage: StateFlow<String?> = _lastMessage.asStateFlow()

    /** Активная вкладка панели (персистится в PanelState). */
    private val _activeTab = MutableStateFlow("POSITIONS")
    val activeTab: StateFlow<String> = _activeTab.asStateFlow()

    /**
     * Paper-режим ЭТОЙ панели (независимый): можно смотреть paper данные
     * или live, не влияя на графики/DOM.
     */
    private val _paperEnabled = MutableStateFlow(false)
    val paperEnabled: StateFlow<Boolean> = _paperEnabled.asStateFlow()

    /** Тип контрактов панели: USDT-M / COIN-M (фильтр таблиц и балансов). */
    private val _contractType = MutableStateFlow(ContractType.USDT_M)
    val contractType: StateFlow<ContractType> = _contractType.asStateFlow()

    /** symbol → тип контракта (из SymbolInfo провайдера) для фильтра таблиц. */
    private val _symbolTypes = MutableStateFlow<Map<String, ContractType>>(emptyMap())
    val symbolTypes: StateFlow<Map<String, ContractType>> = _symbolTypes.asStateFlow()

    /** Реализованный PnL за сегодня (null — адаптер не поддерживает). */
    private val _dailyPnl = MutableStateFlow<Double?>(null)
    val dailyPnl: StateFlow<Double?> = _dailyPnl.asStateFlow()

    /** Подключения сессии (REST API + приватный стрим) — окно Manage connections. */
    private val _connections = MutableStateFlow<List<TradingConnection>>(emptyList())
    val connections: StateFlow<List<TradingConnection>> = _connections.asStateFlow()

    fun selectTab(tab: String) {
        if (tab.isNotEmpty()) _activeTab.value = tab
    }

    fun restoreTab(tab: String) {
        if (tab.isNotEmpty()) _activeTab.value = tab
    }

    private fun tradingAdapter() =
        providerRegistry.get(_providerId.value)?.effectiveTrading(_paperEnabled.value)

    private var panelKey: String? = null

    private val _providerRestored = MutableStateFlow(false)
    val providerRestored: StateFlow<Boolean> = _providerRestored.asStateFlow()

    /** Привязка панели: своя биржа и тип контрактов (per-panel). */
    fun attachPanel(panelId: String?, preferredProviderId: String? = null) {
        panelKey = panelId
        val store = stateStore
        if (store == null) {
            _providerRestored.value = true
            return
        }
        scope.launch {
            store.getString(contractTypeStoreKey())?.let { raw ->
                runCatching { ContractType.valueOf(raw) }.getOrNull()?.let { _contractType.value = it }
            }
            val saved = preferredProviderId?.takeIf { providerRegistry.get(it) != null }
                ?: store.getString(providerStoreKey())?.takeIf { providerRegistry.get(it) != null }
            if (!saved.isNullOrBlank() && saved != _providerId.value) {
                selectProvider(saved)
            }
            _providerRestored.value = true
        }
    }

    private fun providerStoreKey(): String = "trading_provider_${panelKey ?: "default"}"

    private fun contractTypeStoreKey(): String = "trading_contract_${panelKey ?: "default"}"

    init {
        scope.launch {
            // Paper-режим панели переживает перезапуск (общий ключ панели);
            // при наличии общего PaperModeState — берём значение оттуда
            _paperEnabled.value = paperModeState?.enabled?.value
                ?: (stateStore?.getString(PaperTrading.STORE_KEY) == "1")
            // Биржа панели тоже переживает перезапуск (как paper-режим)
            stateStore?.getString(providerStoreKey())?.takeIf { it.isNotBlank() }?.let { saved ->
                if (providerRegistry.get(saved) != null) _providerId.value = saved
            }
            // Тип контрактов панели (USDT-M / COIN-M)
            stateStore?.getString(contractTypeStoreKey())?.let { raw ->
                runCatching { ContractType.valueOf(raw) }.getOrNull()?.let { _contractType.value = it }
            }
            restart()
        }
        // Внешние переключения Paper (например, свич в TopBar) — перечитываем
        paperModeState?.let { state ->
            scope.launch {
                state.enabled.collect { enabled ->
                    if (enabled != _paperEnabled.value) {
                        _paperEnabled.value = enabled
                        reload()
                    }
                }
            }
        }
    }

    override fun dispose() {
        refreshJob?.cancel()
        liveJobs.forEach { it.cancel() }
        scope.cancel()
    }

    fun selectProvider(providerId: String) {
        if (_providerId.value == providerId) return
        _providerId.value = providerId
        scope.launch {
            stateStore?.putString(providerStoreKey(), providerId)
        }
        _positions.value = emptyList()
        _openOrders.value = emptyList()
        _balances.value = emptyList()
        _tradeHistory.value = emptyList()
        restart()
    }

    /** Перезапуск подписок/рефреша (после смены paper/real режима). */
    fun reload() {
        restart()
    }

    /** Переключение paper/live этой панели: персист + перечитывание портфеля. */
    fun setPaperEnabled(enabled: Boolean) {
        if (_paperEnabled.value == enabled) return
        _paperEnabled.value = enabled
        val shared = paperModeState
        if (shared != null) {
            // Общий флаг: TopBar обновится; свой коллектор увидит equal — без двойного reload
            shared.set(enabled)
        } else {
            scope.launch {
                stateStore?.putString(PaperTrading.STORE_KEY, if (enabled) "1" else "0")
            }
        }
        reload()
    }

    private fun restart() {
        refreshJob?.cancel()
        liveJobs.forEach { it.cancel() }
        liveJobs.clear()
        resetConnections()
        loadSymbolTypes()
        refreshJob = scope.launch {
            val adapter = tradingAdapter()
            if (adapter == null) {
                failConnections("Trading adapter is unavailable for ${providerRegistry.displayName(_providerId.value)}")
                return@launch
            }
            refreshAll(adapter)
            startLive(adapter)
            while (isActive) {
                delay(30_000)
                refreshAll(adapter)
            }
        }
    }

    /** Смена типа контрактов панели (персист + перефильтрация UI). */
    fun setContractType(type: ContractType) {
        if (_contractType.value == type) return
        _contractType.value = type
        scope.launch { stateStore?.putString(contractTypeStoreKey(), type.name) }
    }

    /** Карта symbol → тип контракта активного провайдера. */
    private fun loadSymbolTypes() {
        val adapter = providerRegistry.get(_providerId.value)?.symbolInfo ?: return
        scope.launch {
            runCatching { SymbolInfoRepositoryImpl(adapter).getAllSymbolsInfo() }
                .onSuccess { infos ->
                    _symbolTypes.value = infos.associate { it.symbol.uppercase() to ContractType.of(it) }
                }
        }
    }

    /** Подходит ли символ панели выбранному типу контрактов. */
    fun matchesContractType(symbol: String, type: ContractType): Boolean {
        val known = _symbolTypes.value[symbol.uppercase()] ?: return true
        return known == type
    }

    /** Стейбл-валюты маржи (USDT-M / USDC-M). */
    private val stableCurrencies = setOf("USDT", "USDC", "USD1", "DAI", "USD")

    /** Подходит ли валюта баланса выбранному типу контрактов. */
    fun balanceMatchesContractType(currency: String, type: ContractType): Boolean =
        when (type) {
            ContractType.USDT_M -> currency.uppercase() in stableCurrencies
            ContractType.COIN_M -> currency.uppercase() !in stableCurrencies
        }

    /** Основной баланс для инфо-блока (USDT или крупнейшая монета COIN-M). */
    fun primaryBalance(balances: List<Balance>, type: ContractType): Balance? = when (type) {
        ContractType.USDT_M -> balances.firstOrNull { it.currency.uppercase() in stableCurrencies }
        ContractType.COIN_M -> balances
            .filterNot { it.currency.uppercase() in stableCurrencies }
            .maxByOrNull { it.equity.toDoubleOrNull() ?: 0.0 }
    }

    private suspend fun refreshAll(adapter: com.aandios.nous.api.market.adapters.TradingAdapter) {
        var restError: String? = null
        fun recordRestFailure(e: Throwable) {
            if (restError == null) restError = e.message ?: "request failed"
        }
        runCatching { adapter.getPositions() }.onSuccess { _positions.value = it }.onFailure { recordRestFailure(it) }
        runCatching { adapter.getOpenOrders() }.onSuccess { _openOrders.value = it }.onFailure { recordRestFailure(it) }
        runCatching { adapter.getBalances() }.onSuccess { _balances.value = it }.onFailure { recordRestFailure(it) }
        // История — сразу, чтобы счётчик на табе был точным
        runCatching { adapter.getTradeHistory(limit = 100) }.onSuccess { _tradeHistory.value = it }
        runCatching { adapter.getPositionMode() }.onSuccess { if (it != null) _positionMode.value = it }
        // Реализованный PnL за сегодня (у Binance-заглушки — null)
        _dailyPnl.value = runCatching { adapter.getDailyRealizedPnl() }.getOrNull()
        // Paper-движок локальный — его состояние не отражает REST-подключение.
        if (_paperEnabled.value) return
        val error = restError
        setConnectionStatus(
            REST_CONNECTION_ID,
            if (error == null) TradingConnectionStatus.CONNECTED else TradingConnectionStatus.ERROR,
            error,
        )
    }

    fun refreshTradeHistory() {
        scope.launch {
            val adapter = tradingAdapter() ?: return@launch
            runCatching { adapter.getTradeHistory(limit = 100) }.onSuccess { _tradeHistory.value = it }
        }
    }

    private fun startLive(adapter: com.aandios.nous.api.market.adapters.TradingAdapter) {
        val positionsFlow = runCatching { adapter.subscribeToPositions() }.getOrNull()
        val ordersFlow = runCatching { adapter.subscribeToOrders() }.getOrNull()
        val balancesFlow = runCatching { adapter.subscribeToBalances() }.getOrNull()
        when {
            // Paper-движок живёт в процессе — стрим не зависит от ключей биржи.
            _paperEnabled.value ->
                setConnectionStatus(STREAM_CONNECTION_ID, TradingConnectionStatus.CONNECTED, "Paper engine")

            positionsFlow != null || ordersFlow != null || balancesFlow != null ->
                setConnectionStatus(STREAM_CONNECTION_ID, TradingConnectionStatus.CONNECTED, null)

            else ->
                setConnectionStatus(
                    STREAM_CONNECTION_ID,
                    TradingConnectionStatus.ERROR,
                    "Private stream is unavailable — add API keys for this exchange",
                )
        }
        positionsFlow?.let { flow ->
            liveJobs += scope.launch {
                try {
                    flow.collect { update ->
                        _positions.value = upsertPosition(_positions.value, update)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    setConnectionStatus(STREAM_CONNECTION_ID, TradingConnectionStatus.ERROR, e.message ?: "stream error")
                }
            }
        }
        ordersFlow?.let { flow ->
            liveJobs += scope.launch {
                try {
                    flow.collect { update ->
                        _openOrders.value = upsertOrder(_openOrders.value, update)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    setConnectionStatus(STREAM_CONNECTION_ID, TradingConnectionStatus.ERROR, e.message ?: "stream error")
                }
            }
        }
        balancesFlow?.let { flow ->
            liveJobs += scope.launch {
                try {
                    flow.collect { update ->
                        _balances.value = upsertBalance(_balances.value, update)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    setConnectionStatus(STREAM_CONNECTION_ID, TradingConnectionStatus.ERROR, e.message ?: "stream error")
                }
            }
        }
        // Уведомления движка (причины отказов) — в сообщение панели
        adapter.notices()?.let { flow ->
            liveJobs += scope.launch {
                flow.collect { text -> _lastMessage.value = text }
            }
        }
    }

    // ── Manage connections (REST API + приватный стрим) ──

    /** Сброс статусов перед подключением: paper — сразу connected, live — connecting. */
    private fun resetConnections() {
        val paper = _paperEnabled.value
        fun row(id: String, label: String) = TradingConnection(
            id = id,
            label = label,
            status = if (paper) TradingConnectionStatus.CONNECTED else TradingConnectionStatus.CONNECTING,
            detail = if (paper) "Paper engine" else null,
        )
        _connections.value = listOf(
            row(REST_CONNECTION_ID, "REST API"),
            row(STREAM_CONNECTION_ID, "Account stream"),
        )
    }

    private fun setConnectionStatus(id: String, status: TradingConnectionStatus, detail: String?) {
        _connections.value = _connections.value.map {
            if (it.id == id) it.copy(status = status, detail = detail) else it
        }
    }

    private fun failConnections(message: String) {
        _connections.value = _connections.value.map {
            it.copy(status = TradingConnectionStatus.ERROR, detail = message)
        }
    }

    private fun upsertPosition(list: List<Position>, update: Position): List<Position> {
        if (update.quantity == 0.0) return list.filterNot { it.positionId == update.positionId }
        val exists = list.any { it.positionId == update.positionId }
        return if (exists) list.map { if (it.positionId == update.positionId) update else it }
        else list + update
    }

    private fun upsertOrder(list: List<Order>, update: Order): List<Order> {
        // Отменённые/исполненные/отклонённые ордера уходят из открытых
        if (update.status != OrderStatus.OPEN) return list.filterNot { it.orderId == update.orderId }
        val exists = list.any { it.orderId == update.orderId }
        return if (exists) list.map { if (it.orderId == update.orderId) update else it }
        else list + update
    }

    private fun upsertBalance(list: List<Balance>, update: Balance): List<Balance> {
        val exists = list.any { it.currency == update.currency }
        return if (exists) list.map { if (it.currency == update.currency) update else it }
        else list + update
    }

    // ── Действия ──

    fun cancelOrder(orderId: String) {
        scope.launch {
            val adapter = tradingAdapter() ?: return@launch
            _busy.value = true
            val ok = runCatching { adapter.cancelOrder(orderId) }.getOrDefault(false)
            _lastMessage.value = if (ok) "Order $orderId canceled" else "Failed to cancel $orderId"
            _busy.value = false
            refreshAll(adapter)
        }
    }

    fun cancelAllOrders(symbol: String? = null) {
        scope.launch {
            val adapter = tradingAdapter() ?: return@launch
            _busy.value = true
            val ok = runCatching { adapter.cancelAllOrders(symbol) }.getOrDefault(false)
            _lastMessage.value = if (ok) "All orders canceled" else "Failed to cancel orders"
            _busy.value = false
            refreshAll(adapter)
        }
    }

    fun closePosition(position: Position) {
        scope.launch {
            val adapter = tradingAdapter() ?: return@launch
            _busy.value = true
            val result = runCatching { adapter.closePosition(position.symbol, position.positionId, position.quantity) }
                .getOrNull()
            _lastMessage.value = when {
                result == null -> "Failed to close ${position.symbol}"
                result.success -> "Closed ${position.symbol}: ${result.orderId}"
                else -> "Close failed: ${result.message}"
            }
            _busy.value = false
            refreshAll(adapter)
        }
    }

    fun closeAllPositions() {
        positions.value.forEach { closePosition(it) }
    }

    fun setMarginMode(mode: Int) {
        _marginMode.value = mode
    }

    fun setLeverage(leverage: Int) {
        _leverage.value = leverage
    }

    /** Применяет плечо к позиции (или к символу, если позиции нет). */
    fun applyLeverage(symbol: String, leverage: Int, positionId: Long? = null) {
        scope.launch {
            val adapter = tradingAdapter() ?: return@launch
            val ok = runCatching { adapter.setLeverage(symbol, leverage, positionId) }.getOrDefault(false)
            _lastMessage.value = if (ok) "Leverage set to ${leverage}x" else "Failed to set leverage"
            refreshAll(adapter)
        }
    }

    fun switchPositionMode(mode: Int) {
        scope.launch {
            val adapter = tradingAdapter() ?: return@launch
            val ok = runCatching { adapter.setPositionMode(mode) }.getOrDefault(false)
            _lastMessage.value = if (ok) "Position mode switched" else "Failed to switch position mode (закройте ордера/позиции)"
            refreshAll(adapter)
        }
    }

    fun clearMessage() {
        _lastMessage.value = null
    }

    private companion object {
        /** Идентификаторы строк окна Manage connections. */
        const val REST_CONNECTION_ID = "rest"
        const val STREAM_CONNECTION_ID = "stream"
    }
}
