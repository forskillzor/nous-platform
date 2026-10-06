/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trading.ui

import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.api.market.model.Balance
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.OrderStatus
import com.aandios.nous.api.market.model.trading.Position
import com.aandios.nous.api.market.model.trading.TradeFill
import com.aandios.nous.api.market.paper.PaperTrading
import com.aandios.nous.api.market.paper.effectiveTrading
import com.aandios.nous.core.Disposable
import com.aandios.nous.core.storage.StateStore
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

    fun selectTab(tab: String) {
        if (tab.isNotEmpty()) _activeTab.value = tab
    }

    fun restoreTab(tab: String) {
        if (tab.isNotEmpty()) _activeTab.value = tab
    }

    private fun tradingAdapter() =
        providerRegistry.get(_providerId.value)?.effectiveTrading(_paperEnabled.value)

    init {
        scope.launch {
            // Paper-режим панели переживает перезапуск (общий ключ панели)
            _paperEnabled.value = stateStore?.getString(PaperTrading.STORE_KEY) == "1"
            // Биржа панели тоже переживает перезапуск (как paper-режим)
            stateStore?.getString(PROVIDER_STORE_KEY)?.takeIf { it.isNotBlank() }?.let { saved ->
                if (providerRegistry.get(saved) != null) _providerId.value = saved
            }
            restart()
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
            stateStore?.putString(PROVIDER_STORE_KEY, providerId)
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
        scope.launch {
            stateStore?.putString(PaperTrading.STORE_KEY, if (enabled) "1" else "0")
        }
        reload()
    }

    private fun restart() {
        refreshJob?.cancel()
        liveJobs.forEach { it.cancel() }
        liveJobs.clear()
        refreshJob = scope.launch {
            val adapter = tradingAdapter() ?: return@launch
            refreshAll(adapter)
            startLive(adapter)
            while (isActive) {
                delay(30_000)
                refreshAll(adapter)
            }
        }
    }

    private suspend fun refreshAll(adapter: com.aandios.nous.api.market.adapters.TradingAdapter) {
        runCatching { adapter.getPositions() }.onSuccess { _positions.value = it }
        runCatching { adapter.getOpenOrders() }.onSuccess { _openOrders.value = it }
        runCatching { adapter.getBalances() }.onSuccess { _balances.value = it }
        // История — сразу, чтобы счётчик на табе был точным
        runCatching { adapter.getTradeHistory(limit = 100) }.onSuccess { _tradeHistory.value = it }
        runCatching { adapter.getPositionMode() }.onSuccess { if (it != null) _positionMode.value = it }
    }

    fun refreshTradeHistory() {
        scope.launch {
            val adapter = tradingAdapter() ?: return@launch
            runCatching { adapter.getTradeHistory(limit = 100) }.onSuccess { _tradeHistory.value = it }
        }
    }

    private fun startLive(adapter: com.aandios.nous.api.market.adapters.TradingAdapter) {
        adapter.subscribeToPositions()?.let { flow ->
            liveJobs += scope.launch {
                flow.collect { update ->
                    _positions.value = upsertPosition(_positions.value, update)
                }
            }
        }
        adapter.subscribeToOrders()?.let { flow ->
            liveJobs += scope.launch {
                flow.collect { update ->
                    _openOrders.value = upsertOrder(_openOrders.value, update)
                }
            }
        }
        adapter.subscribeToBalances()?.let { flow ->
            liveJobs += scope.launch {
                flow.collect { update ->
                    _balances.value = upsertBalance(_balances.value, update)
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
        /** Ключ StateStore: выбранная биржа docked trading panel. */
        const val PROVIDER_STORE_KEY = "trading_provider"
    }
}
