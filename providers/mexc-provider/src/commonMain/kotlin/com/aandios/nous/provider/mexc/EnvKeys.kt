/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc

/**
 * Чтение переменных окружения (API-ключи и т.п.).
 * На десктопе (JVM) — системное окружение; на js/wasmJs — не поддерживается.
 */
expect fun getEnv(name: String): String?
