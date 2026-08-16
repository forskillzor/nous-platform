/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trades.di

import com.aandios.nous.api.market.NetworkManager
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.SymbolInfoAdapter
import com.aandios.nous.api.market.adapters.TradesAdapter
import com.aandios.nous.core.data.repository.SymbolInfoRepositoryImpl
import com.aandios.nous.core.data.repository.TradesRepositoryImpl
import com.aandios.nous.core.di.coreModule
import com.aandios.nous.core.domain.repository.SymbolInfoRepository
import com.aandios.nous.core.domain.repository.TradesRepository
import com.aandios.nous.feature.trades.ui.TradesViewModel
import com.aandios.nous.provider.binance.BinanceProviderFactory
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Инициализация Koin ТОЛЬКО для превью/изолированного запуска TradesWindow.
 */
fun initKoinForPreview() {
    stopKoin()
    startKoin {
        modules(
            coreModule,
            featureTradesModule,
        )
    }
}

val featureTradesModule = module {

    // 1. Конфигурация для превью
    single<ProviderConfig> {
        ProviderConfig(
            apiKey = null,
            secretKey = null,
            isTestnet = false,
            customSettings = emptyMap()
        )
    }

    // 2. Создаём Provider через фабрику
    single<Provider> {
        val config = get<ProviderConfig>()
        val networkManager = get<NetworkManager>()

        BinanceProviderFactory().createProvider(
            config = config,
            networkManager = networkManager
        )
    }

    // 3. Адаптеры из провайдера
    single<TradesAdapter> {
        get<Provider>().trades ?: error("Trades adapter not available")
    }

    single<SymbolInfoAdapter> {
        get<Provider>().symbolInfo ?: error("SymbolInfo adapter not available")
    }

    // 4. Репозитории
    single<TradesRepository> {
        TradesRepositoryImpl(tradesAdapter = get())
    }

    single<SymbolInfoRepository> {
        SymbolInfoRepositoryImpl(symbolInfoAdapter = get())
    }

    // 5. ViewModel
    factory {
        TradesViewModel(
            tradesRepository = get(),
            symbolInfoRepository = get()
        )
    }
}
