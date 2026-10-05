/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.di

import com.aandios.nous.api.market.NetworkManager
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.core.di.coreModule
import com.aandios.nous.core.domain.cache.CandleCacheStore
import com.aandios.nous.core.domain.cache.FootprintCacheStore
import com.aandios.nous.core.storage.StateStore
import com.aandios.nous.feature.chart.footprint.FootprintApiClient
import com.aandios.nous.feature.chart.ui.ChartViewModel
import com.aandios.nous.feature.localstorage.LocalStorage
import com.aandios.nous.provider.binance.BinanceProviderFactory
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Инициализация Koin ТОЛЬКО для превью/изолированного запуска ChartWindow.
 * - Останавливаем старый контекст
 * - Не включаем binanceProviderModule (чтобы не было конфликта)
 * - Создаём Provider вручную через фабрику
 */
fun initKoinForPreview() {
    stopKoin()
    startKoin {
        modules(
            coreModule,           // NetworkManager, HttpClient
            featureChartModule,   // Модуль фичи Chart
        )
    }
}

val featureChartModule = module {

    // 1. Провайдер Binance (превью живут на одном провайдере) + реестр
    single<Provider> {
        BinanceProviderFactory().createProvider(
            config = ProviderConfig(displayName = "Binance"),
            networkManager = get<NetworkManager>(),
        )
    }
    single<ProviderRegistry> { ProviderRegistry(getAll<Provider>()) }

    // 2. Footprint API client (подключается к market-data-server)
    single<FootprintApiClient> {
        FootprintApiClient(httpClient = get<NetworkManager>().httpClient)
    }

    // 3. Локальное хранилище и кэш свечей/footprint (та же БД, что и в composeApp)
    single<LocalStorage> { LocalStorage() }
    single<StateStore> { get<LocalStorage>() }
    single<CandleCacheStore> { get<LocalStorage>() }
    single<FootprintCacheStore> { get<LocalStorage>() }

    // 4. ViewModel — адаптеры резолвятся из реестра по выбранному провайдеру
    factory {
        ChartViewModel(
            providerRegistry = get(),
            footprintApiClient = get(),
            stateStore = get(),
            candleCache = get(),
            footprintCache = get(),
        )
    }
}
