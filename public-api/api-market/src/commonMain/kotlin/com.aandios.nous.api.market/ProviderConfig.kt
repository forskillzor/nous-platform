/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market

data class ProviderConfig(
    val apiKey: String? = null,
    val secretKey: String? = null,
    val isTestnet: Boolean = false,
    val customSettings: Map<String, String> = emptyMap(),
    /** Отображаемое имя биржи (например, "Binance"). */
    val displayName: String = "Binance"
)
