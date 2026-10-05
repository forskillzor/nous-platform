/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc

// Торговля поддерживается только на десктопе (JVM)
actual fun hmacSha256Hex(key: String, data: String): String = ""
