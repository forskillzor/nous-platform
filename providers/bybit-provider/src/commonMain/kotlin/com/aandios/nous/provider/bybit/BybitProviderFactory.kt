/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit

import com.aandios.nous.api.market.NetworkManager
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.ProviderFactory

class BybitProviderFactory(
    override val providerName: String = "bybit-nous",
    override val providerVersion: String = "0.0.1",
) : ProviderFactory {
    override val providerId: String = "$providerName-$providerVersion"

    override fun createProvider(config: ProviderConfig, networkManager: NetworkManager): Provider =
        BybitProvider(
            providerId = providerId,
            providerName = providerName,
            version = providerVersion,
            config = config,
            networkManager = networkManager,
        )
}
