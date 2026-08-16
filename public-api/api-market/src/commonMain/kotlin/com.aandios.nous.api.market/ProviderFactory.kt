/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market

interface ProviderFactory {
    val providerId: String
    val providerName: String
    val providerVersion: String

    fun createProvider(
        config: ProviderConfig,
        networkManager: NetworkManager
    ): Provider
}