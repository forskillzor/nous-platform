/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market

open class ProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)

open class AdapterException(message: String, cause: Throwable? = null) : Exception(message, cause)

open class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

open class ConfigValidationException(message: String, details: Map<String, String> = emptyMap()) : Exception(message)