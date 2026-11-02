/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

actual fun hyperliquidCurrentTimeMillis(): Long = System.currentTimeMillis()

actual fun hyperliquidEnvironmentVariable(name: String): String? = System.getenv(name)
