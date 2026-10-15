/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.aandios.nous.core.storage.StateStore
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalThemeDefinition
import com.aandios.nous.core.ui.theme.TerminalThemes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.compose.koinInject

data class ThemeSettingsState(
    val theme: TerminalThemeDefinition = TerminalThemes.Nous,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
)

class ThemeSettingsController(
    private val stateStore: StateStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Mutex()
    private val _state = MutableStateFlow(ThemeSettingsState())
    val state: StateFlow<ThemeSettingsState> = _state.asStateFlow()

    private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            try {
                val savedName = stateStore.getString(THEME_STATE_KEY)
                val theme = savedName?.let { name ->
                    TerminalThemes.available.firstOrNull { it.name == name }
                        ?: throw IllegalStateException("Saved theme '$name' is not available")
                } ?: TerminalThemes.Nous
                _state.value = ThemeSettingsState(theme = theme, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Unable to load saved theme",
                )
            }
        }
    }

    fun select(theme: TerminalThemeDefinition) {
        if (_state.value.loading) {
            _state.value = _state.value.copy(error = "Theme settings are still loading")
            return
        }
        scope.launch {
            writes.withLock {
                _state.value = _state.value.copy(saving = true, error = null)
                try {
                    stateStore.putString(THEME_STATE_KEY, theme.name)
                    _state.value = _state.value.copy(theme = theme, saving = false)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.value = _state.value.copy(
                        saving = false,
                        error = e.message ?: "Unable to save theme",
                    )
                }
            }
        }
    }

    private companion object {
        const val THEME_STATE_KEY = "terminal_theme"
    }
}

@Composable
fun ThemeSettingsOverlay(
    onClose: () -> Unit,
) {
    val controller: ThemeSettingsController = koinInject()
    val state by controller.state.collectAsState()
    val tokens = LocalTerminalDesignTokens.current
    val shape = RoundedCornerShape(tokens.cornerRadii.workspacePanel)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(tokens.dimensions.themeSettingsDialogWidth)
                .clip(shape)
                .background(tokens.colors.panelSurface)
                .border(tokens.dimensions.borderWidth, tokens.colors.panelBorder, shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(tokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Theme settings",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = tokens.typography.bodySize,
                )
                Text(
                    "CLOSE",
                    color = tokens.colors.label,
                    fontSize = tokens.typography.microSize,
                    modifier = Modifier.clickable(onClick = onClose).padding(tokens.spacing.xs),
                )
            }

            TerminalThemes.available.forEach { theme ->
                val selected = theme == state.theme
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(tokens.cornerRadii.card))
                        .background(if (selected) tokens.colors.selection else Color.Transparent)
                        .clickable(enabled = !state.loading && !state.saving) {
                            controller.select(theme)
                        }
                        .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(theme.name, color = MaterialTheme.colorScheme.onSurface)
                    if (selected) {
                        Text(
                            "ACTIVE",
                            color = tokens.colors.accent,
                            fontSize = tokens.typography.microSize,
                        )
                    }
                }
            }

            when {
                state.loading -> Text(
                    "Loading saved theme…",
                    color = tokens.colors.label,
                    fontSize = tokens.typography.compactSize,
                )
                state.saving -> Text(
                    "Saving…",
                    color = tokens.colors.label,
                    fontSize = tokens.typography.compactSize,
                )
            }
            state.error?.let {
                Text(it, color = tokens.colors.loss, fontSize = tokens.typography.compactSize)
            }
        }
    }
}
