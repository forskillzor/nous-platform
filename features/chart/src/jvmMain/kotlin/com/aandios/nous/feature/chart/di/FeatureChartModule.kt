/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.di

import com.aandios.nous.api.market.NetworkManager
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.ChartAdapter
import com.aandios.nous.api.market.adapters.LiquidationAdapter
import com.aandios.nous.api.market.adapters.SymbolInfoAdapter
import com.aandios.nous.api.market.adapters.TradesAdapter
import com.aandios.nous.core.data.repository.ChartRepositoryImpl
import com.aandios.nous.core.di.coreModule
import com.aandios.nous.core.domain.repository.ChartRepository
import com.aandios.nous.feature.chart.footprint.FootprintApiClient
import com.aandios.nous.feature.chart.indicator.LiquidationViewModel
import com.aandios.nous.feature.chart.ui.ChartViewModel
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

    // 1. Конфигурация для превью (без ключей, основная сеть)
    single<ProviderConfig> {
        ProviderConfig(
            apiKey = null,
            secretKey = null,
            isTestnet = false,
            customSettings = emptyMap()
        )
    }

    // 2. Создаём Provider напрямую через фабрику
    single<Provider> {
        val config = get<ProviderConfig>()
        val networkManager = get<NetworkManager>()

        BinanceProviderFactory().createProvider(
            config = config,
            networkManager = networkManager
        )
    }

    // 3. Адаптер Chart из провайдера
    single<ChartAdapter> {
        get<Provider>().chart ?: error("Chart adapter not available")
    }

    // 4. Репозиторий Chart (используем готовый из platform-core)
    single<ChartRepository> {
        ChartRepositoryImpl(chartAdapter = get())
    }

    // 5. SymbolInfo adapter из провайдера
    single<SymbolInfoAdapter> {
        get<Provider>().symbolInfo ?: error("SymbolInfo adapter not available")
    }

    // 6. Footprint API client (подключается к market-data-server)
    single<FootprintApiClient> {
        FootprintApiClient(httpClient = get<NetworkManager>().httpClient)
    }

    // 6.5 Trades adapter from provider (for live footprint)
    single<TradesAdapter> {
        get<Provider>().trades ?: error("Trades adapter not available")
    }

    // 6.6 Liquidation adapter from provider
    single<LiquidationAdapter?> {
        get<Provider>().liquidation
    }

    // 7. ViewModel
    factory {
        ChartViewModel(
            chartRepository = get(),
            symbolInfoAdapter = get(),
            footprintApiClient = get(),
            tradesAdapter = get(),
        )
    }

    // 8. Indicator ViewModels
    factory {
        LiquidationViewModel(liquidationAdapter = get())
    }
}
