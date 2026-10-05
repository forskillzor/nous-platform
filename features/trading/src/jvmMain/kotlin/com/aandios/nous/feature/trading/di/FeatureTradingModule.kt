/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trading.di

import com.aandios.nous.api.market.NetworkManager
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.core.di.coreModule
import com.aandios.nous.feature.trading.ui.TradingViewModel
import com.aandios.nous.provider.binance.BinanceProviderFactory
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Инициализация Koin ТОЛЬКО для превью/изолированного запуска TradingWindow.
 */
fun initKoinForPreview() {
    stopKoin()
    startKoin {
        modules(
            coreModule,
            featureTradingModule,
        )
    }
}

val featureTradingModule = module {

    single<Provider> {
        BinanceProviderFactory().createProvider(
            config = ProviderConfig(displayName = "Binance"),
            networkManager = get<NetworkManager>(),
        )
    }
    single<ProviderRegistry> { ProviderRegistry(getAll<Provider>()) }

    factory { TradingViewModel(providerRegistry = get()) }
}
