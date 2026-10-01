/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

import kotlinx.serialization.Serializable
import kotlin.time.Clock

/**
 * Состо��ние приложения между сессиями — какие табы открыты, тема, версия.
 */
@Serializable
data class AppConfig(
    val openWorkspaceIds: List<String> = emptyList(),
    val activeWorkspaceId: String? = null,
    val theme: String = "dark",
    val lastKnownVersion: String? = null
)

/** KMP-safe unique ID generator */
fun generateId(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    return (1..12).map { chars.random() }.joinToString("")
}

/** KMP-safe current time in millis */
fun currentTime(): Long = Clock.System.now().toEpochMilliseconds()
