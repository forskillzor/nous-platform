/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.aandios.nous.core.ui.theme.TradingTerminalTheme
import com.aandios.nous.core.workspace.*
import com.aandios.nous.core.workspace.viewmodel.TabManager
import com.aandios.nous.core.workspace.viewmodel.WindowManager
import com.aandios.nous_platform.di.initKoin
import com.aandios.nous_platform.ui.main.TerminalWindowContent
import com.aandios.nous_platform.ui.terminalLayout.TerminalStateViewModel
import org.koin.compose.koinInject

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
                val wsState = rememberWindowState(
                    position = if (session.x != null && session.y != null)
                        WindowPosition(x = session.x!!.dp, y = session.y!!.dp)
                    else WindowPosition.PlatformDefault,
                    width = session.width.dp,
                    height = session.height.dp,
                )
                Window(
                    onCloseRequest = {
                        // Закрытие последнего окна завершает приложение
                        if (!windowManager.closeSession(session.id)) exitApplication()
                    },
                    title = "Nous Platform • v 0.1",
                    state = wsState,
                ) {
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
