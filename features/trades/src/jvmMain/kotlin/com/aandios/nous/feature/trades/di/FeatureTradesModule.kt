/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trades.di

import com.aandios.nous.api.market.NetworkManager
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.core.di.coreModule
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

    // 1. Провайдер Binance (превью живут на одном провайдере) + реестр
    single<Provider> {
        BinanceProviderFactory().createProvider(
            config = ProviderConfig(displayName = "Binance"),
            networkManager = get<NetworkManager>(),
        )
    }
    single<ProviderRegistry> { ProviderRegistry(getAll<Provider>()) }

    // 2. ViewModel — адаптеры резолвятся из реестра по выбранному провайдеру
    factory {
        TradesViewModel(providerRegistry = get())
    }
}
