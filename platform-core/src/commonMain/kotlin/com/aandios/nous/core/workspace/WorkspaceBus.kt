/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Шина событий workspace-системы: любое изменение workspace'ов или шаблонов
 * (создание, удаление, переименование, сохранение шаблона) увеличивает
 * версию. Подписчики (TerminalLayout, WelcomeScreen) перечитывают репозитории.
 * Решает рассинхронизацию между боковой панелью ProjectTree и экраном Welcome.
 */
class WorkspaceBus {
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    fun workspaceChanged() {
        _version.value++
    }
}
