/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import kotlinx.coroutines.delay

/**
 * Общий каркас всплывающих списков терминала (symbol explorer, indicator
 * dropdown): табы, поиск, фильтры, шапка списка, тело и футер. Владелец
 * передаёт свои слоты, поэтому внешний вид каждого меню не меняется.
 * Ctrl/⌘K переводит фокус в поиск; [autoFocusSearch] делает это при открытии
 * попапа.
 */
@Composable
fun TerminalExplorerPopupContent(
    tabs: @Composable () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    searchPlaceholder: String,
    searchFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    autoFocusSearch: Boolean = false,
    dividerAfterTabs: Boolean = true,
    filters: (@Composable RowScope.() -> Unit)? = null,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    listHeader: (@Composable RowScope.() -> Unit)? = null,
    footer: (@Composable RowScope.() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current

    if (autoFocusSearch) {
        LaunchedEffect(Unit) {
            delay(80)
            runCatching { searchFocusRequester.requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.K &&
                    (event.isCtrlPressed || event.isMetaPressed)
                ) {
                    runCatching { searchFocusRequester.requestFocus() }
                    true
                } else {
                    false
                }
            },
    ) {
        tabs()

        if (dividerAfterTabs) {
            HorizontalDivider(
                color = tokens.colors.panelBorder,
                thickness = tokens.dimensions.borderWidth,
            )
        }

        TerminalSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = searchPlaceholder,
            focusRequester = searchFocusRequester,
            modifier = Modifier.padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
        )

        header?.invoke(this)

        if (filters != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            ) {
                filters.invoke(this)
            }
            HorizontalDivider(
                color = tokens.colors.panelBorder,
                thickness = tokens.dimensions.borderWidth,
            )
        }

        if (listHeader != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listHeader.invoke(this)
            }
        }

        body()

        if (footer != null) {
            HorizontalDivider(
                color = tokens.colors.panelBorder,
                thickness = tokens.dimensions.borderWidth,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                footer.invoke(this)
            }
        }
    }
}
