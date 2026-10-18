/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.di

import com.aandios.nous.api.market.NetworkManager
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.core.di.coreModule
import com.aandios.nous.core.domain.cache.CandleCacheStore
import com.aandios.nous.core.domain.cache.FootprintCacheStore
import com.aandios.nous.core.storage.StateStore
import com.aandios.nous.core.ui.component.SymbolWatchlistRepository
import com.aandios.nous.feature.localstorage.LocalStorage
import com.aandios.nous.feature.chart.footprint.FootprintApiClient
import com.aandios.nous.feature.chart.ui.ChartViewModel
import com.aandios.nous.feature.dom.ui.DomViewModel
import com.aandios.nous.feature.trades.ui.TradesViewModel
import com.aandios.nous.feature.trading.ui.PaperModeState
import com.aandios.nous.feature.trading.ui.TradingViewModel
import com.aandios.nous.provider.binance.BinanceProviderFactory
import com.aandios.nous.provider.bybit.BybitProviderFactory
import com.aandios.nous.provider.mexc.MexcProviderFactory
import com.aandios.nous.core.workspace.WorkspaceRepository
import com.aandios.nous.core.workspace.AppStateRepository
import com.aandios.nous.core.workspace.TemplateRepository
import com.aandios.nous.core.workspace.WorkspaceBus
import com.aandios.nous.core.workspace.TabDragBus
import com.aandios.nous.core.workspace.viewmodel.TabManager
import com.aandios.nous.core.workspace.viewmodel.WindowManager
import org.koin.core.qualifier.named
import com.aandios.nous_platform.ui.terminalLayout.TerminalStateViewModel
import com.aandios.nous_platform.ui.terminalLayout.MarketWatchStore
import com.aandios.nous_platform.ui.terminalLayout.ThemeSettingsController
import org.koin.core.context.startKoin
import org.koin.dsl.module

// Unified DI module using new feature-module classes
val appModule = module {

    // 1. Core (NetworkManager + HttpClient)
    includes(coreModule)

    // 2. Провайдеры: регистрируются ТОЛЬКО реально реализованные —
    //    реестр (ProviderRegistry) становится единым источником списка бирж.
    single<Provider>(named("binance")) {
        BinanceProviderFactory().createProvider(
            config = ProviderConfig(displayName = "Binance"),
            networkManager = get<NetworkManager>(),
        )
    }

    single<Provider>(named("bybit")) {
        BybitProviderFactory().createProvider(
            config = ProviderConfig(displayName = "Bybit"),
            networkManager = get<NetworkManager>(),
        )
    }

    single<Provider>(named("mexc")) {
        MexcProviderFactory().createProvider(
            config = ProviderConfig(displayName = "MEXC"),
            networkManager = get<NetworkManager>(),
        )
    }

    single<ProviderRegistry> { ProviderRegistry(getAll<Provider>()) }

    // Единый каталог рынка (спеки + статистика 24ч): прелоад при старте и
    // фоновый REST-цикл внутри стора; explorer и Market Watch — потребители.
    single(createdAtStart = true) {
        com.aandios.nous.core.market.MarketOverviewStore(get()).also { it.start() }
    }
    single { MarketWatchStore(get()) }
    single { ThemeSettingsController(get()) }

    // 3. Footprint API client (market-data-server)
    single<FootprintApiClient> {
        FootprintApiClient(httpClient = get<NetworkManager>().httpClient)
    }

    // 4. Local storage (SQLite)
    single<LocalStorage> { LocalStorage() }
    single<StateStore> { get<LocalStorage>() }
    single { SymbolWatchlistRepository(get()) }
    single<CandleCacheStore> { get<LocalStorage>() }
    single<FootprintCacheStore> { get<LocalStorage>() }

    // 4.7 Workspace system
    single<WorkspaceRepository> { WorkspaceRepository(get()) }
    single<AppStateRepository> { AppStateRepository(get()) }
    single<TemplateRepository> { TemplateRepository(get()) }
    single<WorkspaceBus> { WorkspaceBus() }
    single<TabDragBus> { TabDragBus() }
    single<TabManager> { TabManager() }
    single<WindowManager> { WindowManager(get(), get(), get()) }

    // 5. ViewModels — адаптеры резолвятся из ProviderRegistry по выбранному провайдеру
    factory {
        ChartViewModel(
            providerRegistry = get(),
            footprintApiClient = get(),
            stateStore = get(),
            candleCache = get(),
            footprintCache = get(),
            marketOverviewStore = get(),
        )
    }

    factory {
        DomViewModel(providerRegistry = get(), stateStore = get(), marketOverviewStore = get())
    }

    factory {
        TradesViewModel(providerRegistry = get(), stateStore = get(), marketOverviewStore = get())
    }

    factory {
        TradingViewModel(providerRegistry = get(), stateStore = get(), paperModeState = get())
    }

    // Paper: общий флаг (TopBar + docked trading panel) + окно настроек
    single { PaperModeState(stateStore = get()) }
    single(createdAtStart = true) {
        com.aandios.nous.feature.trading.paper.PaperPersistenceService(get())
            .also { it.start() }
    }
    single { com.aandios.nous.feature.trading.ui.PaperSettingsController() }

    factory {
        TerminalStateViewModel()
    }
}


// Simple initialization
fun initKoin() {
    if (org.koin.core.context.GlobalContext.getOrNull() == null) {
        startKoin {
            modules(appModule)
        }
    }
}
