/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

import kotlinx.serialization.Serializable

@Serializable
data class ProviderRef(
    val id: String,
    val name: String,
    val isTestnet: Boolean = false,
    val symbols: List<String> = emptyList()
)
