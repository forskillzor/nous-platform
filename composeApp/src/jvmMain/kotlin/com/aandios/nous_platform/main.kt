/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.v2.SwingWindow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.v2.rememberWindowStateWithBounds
import com.aandios.nous.core.ui.theme.TradingTerminalTheme
import com.aandios.nous.core.ui.window.applyWindowDarkBackground
import com.aandios.nous.core.ui.window.applyWindowsDarkTitleBar
import com.aandios.nous.core.workspace.*
import com.aandios.nous.core.workspace.viewmodel.TabManager
import com.aandios.nous.core.workspace.viewmodel.WindowManager
import com.aandios.nous_platform.di.initKoin
import com.aandios.nous_platform.ui.main.TerminalWindowContent
import com.aandios.nous_platform.ui.terminalLayout.TerminalStateViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initKoin()
    application {

        val tabManager: TabManager = koinInject()
        val workspaceRepo: WorkspaceRepository = koinInject()
        val templateRepo: TemplateRepository = koinInject()
        val workspaceBus: WorkspaceBus = koinInject()
        val windowManager: WindowManager = koinInject()
        val dragBus: TabDragBus = koinInject()
        val terminalStateViewModel: TerminalStateViewModel = koinInject()

        // Восстановление окон и вкладок
        LaunchedEffect(Unit) { windowManager.restoreSessions() }

        val sessions by windowManager.sessions.collectAsState()

        sessions.forEach { session ->
            key(session.id) {
                val wsState = if (session.x != null && session.y != null) {
                    rememberWindowStateWithBounds(
                        initialPosition = DpOffset(session.x!!.dp, session.y!!.dp),
                        initialSize = DpSize(session.width.dp, session.height.dp),
                    )
                } else {
                    rememberWindowStateWithBounds(
                        initialSize = DpSize(session.width.dp, session.height.dp),
                    )
                }
                SwingWindow(
                    onCloseRequest = {
                        // Закрытие последнего окна завершает приложение
                        if (!windowManager.closeSession(session.id)) exitApplication()
                    },
                    title = "Nous Platform • v 0.1",
                    state = wsState,
                    // Выполняется ДО показа окна: AWT-фрейм получает тёмный фон
                    // до создания нативного peer — при ресайзе заливка тёмная,
                    // без белых вспышек.
                    init = { w -> applyWindowDarkBackground(w) },
                ) {
                    // Тёмный заголовок окна (Windows, DWM); no-op на других ОС.
                    LaunchedEffect(Unit) { applyWindowsDarkTitleBar(window) }
                    TradingTerminalTheme(
                        darkTheme = true,
                        nightMode = false
                    ) {
                        TerminalWindowContent(
                            session = session,
                            windowState = wsState,
                            tabManager = tabManager,
                            workspaceRepo = workspaceRepo,
                            templateRepo = templateRepo,
                            workspaceBus = workspaceBus,
                            windowManager = windowManager,
                            dragBus = dragBus,
                            terminalStateViewModel = terminalStateViewModel,
                        )
                    }
                }
            }
        }
    }
}
