/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.market.MarketOverviewStore
import com.aandios.nous.core.ui.component.LocalSymbolWatchlistRepository
import com.aandios.nous.core.ui.component.SymbolExplorerContent
import com.aandios.nous.core.ui.component.SymbolExplorerSort
import com.aandios.nous.core.ui.component.TerminalContextPopup
import com.aandios.nous.core.ui.component.TerminalDropdownMenu
import com.aandios.nous.core.ui.component.TerminalGripDots
import com.aandios.nous.core.ui.component.TerminalGripOrientation
import com.aandios.nous.core.ui.component.TerminalMenuItem
import com.aandios.nous.core.ui.component.TerminalPanelHeader
import com.aandios.nous.core.ui.component.toSymbolExplorerRow
import com.aandios.nous.core.ui.panel.MarketWatchPrefs
import com.aandios.nous.core.ui.panel.ToolPanelPrefsRepository
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily
import com.aandios.nous.core.workspace.WorkspaceConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class WatchlistDialogMode {
    CREATE,
    RENAME,
}

@Composable
fun SymbolsPanel(
    selectedSymbol: String,
    selectedProviderId: String? = null,
    onSymbolSelected: (String, String) -> Unit,
    workspaceTemplates: List<Pair<String, WorkspaceConfig>>,
    onOpenWorkspaceTemplate: ((WorkspaceConfig, String, String, String, String) -> Unit)?,
    onApplyToCurrentWorkspace: ((String, String, String) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val marketOverview: MarketOverviewStore = koinInject()
    val toolPanelPrefs: ToolPanelPrefsRepository = koinInject()
    val marketState by marketOverview.state.collectAsState()
    val watchlistsRepository = LocalSymbolWatchlistRepository.current
    val watchlists by watchlistsRepository?.lists?.collectAsState()
        ?: remember { mutableStateOf(emptyList()) }
    val scope = rememberCoroutineScope()
    // Список панели — общий explorer-контент: фильтры/сортировка/поиск как в дропдауне.
    var activeListId by remember { mutableStateOf<String?>(null) }
    var perpetualsOnly by remember { mutableStateOf(true) }
    var quoteFilter by remember { mutableStateOf<String?>("USDT") }
    var exchangeFilter by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(SymbolExplorerSort.VOLUME) }
    var descending by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var watchlistDialogMode by remember { mutableStateOf<WatchlistDialogMode?>(null) }
    var watchlistDialogName by remember { mutableStateOf("") }
    var listActionError by remember { mutableStateOf<String?>(null) }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var errorPopupExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val explorerRows = remember(marketState.rows) {
        marketState.rows.map { it.toSymbolExplorerRow() }
    }
    val activeList = watchlists.firstOrNull { it.id == activeListId }

    // Персистенс настроек Market Watch (список/фильтры/сортировка) и позиции скролла.
    var prefsLoaded by remember { mutableStateOf(false) }
    var pendingScroll by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(toolPanelPrefs) {
        val prefs = toolPanelPrefs.loadMarketWatch()
        activeListId = prefs.listId
        perpetualsOnly = prefs.perpetualsOnly
        quoteFilter = prefs.quoteAsset
        exchangeFilter = prefs.exchangeId?.takeIf { it != "ALL" }
        sort = SymbolExplorerSort.entries.firstOrNull { it.name == prefs.sort }
            ?: SymbolExplorerSort.VOLUME
        descending = prefs.descending
        pendingScroll = prefs.scrollIndex to prefs.scrollOffset
        prefsLoaded = true
    }
    // Скролл восстанавливаем, когда строки уже есть (каталог грузится асинхронно).
    LaunchedEffect(pendingScroll, explorerRows.isNotEmpty()) {
        val target = pendingScroll ?: return@LaunchedEffect
        if (explorerRows.isNotEmpty()) {
            listState.scrollToItem(target.first, target.second)
            pendingScroll = null
        }
    }
    LaunchedEffect(prefsLoaded) {
        if (!prefsLoaded) return@LaunchedEffect
        snapshotFlow {
            MarketWatchPrefs(
                exchangeId = exchangeFilter,
                listId = activeListId,
                sort = sort.name,
                descending = descending,
                perpetualsOnly = perpetualsOnly,
                quoteAsset = quoteFilter,
                scrollIndex = listState.firstVisibleItemIndex,
                scrollOffset = listState.firstVisibleItemScrollOffset,
            )
        }
            .distinctUntilChanged()
            .collectLatest { prefs ->
                delay(500)
                toolPanelPrefs.saveMarketWatch(prefs)
            }
    }

    val tokens = LocalTerminalDesignTokens.current

    fun toggleSymbolInList(listId: String, symbol: String) {
        val repository = watchlistsRepository ?: return
        scope.launch {
            try {
                repository.toggleSymbol(listId, symbol)
                listActionError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                listActionError = e.message
            }
        }
    }

    fun deleteActiveList() {
        val list = activeList ?: return
        val repository = watchlistsRepository ?: return
        scope.launch {
            try {
                repository.delete(list.id)
                activeListId = null
                listActionError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                listActionError = e.message
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(tokens.colors.panelSurface)) {
        // ── Заголовок: grip, title, настройки списков и панели ──
        TerminalPanelHeader {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            ) {
                TerminalGripDots(
                    orientation = TerminalGripOrientation.VERTICAL,
                    color = tokens.colors.label.copy(alpha = 0.55f),
                    modifier = Modifier.width(8.dp).height(14.dp),
                )
                Text(
                    text = "Market Watch",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = tokens.typography.controlSize,
                    lineHeight = tokens.typography.controlLineHeight,
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                marketState.error?.let {
                    // Ошибка каталога: бейдж в тайтле, текст — в контекстном окне по клику
                    ErrorBadgeGlyph(
                        color = tokens.colors.loss,
                        modifier = Modifier.clickable { errorPopupExpanded = true },
                    )
                }
                Box {
                    MiniAction(
                        contentDescription = "Panel settings",
                        onClick = { moreMenuExpanded = true },
                    ) {
                        SlidersGlyph(color = tokens.colors.label)
                    }
                    TerminalDropdownMenu(
                        expanded = moreMenuExpanded,
                        onDismissRequest = { moreMenuExpanded = false },
                        modifier = Modifier.width(tokens.dimensions.tradeFilterMenuWidth),
                    ) {
                        TerminalMenuItem(
                            text = "New list",
                            onClick = {
                                moreMenuExpanded = false
                                watchlistDialogName = ""
                                watchlistDialogMode = WatchlistDialogMode.CREATE
                            },
                        )
                        TerminalMenuItem(
                            text = "Rename list",
                            enabled = activeList != null,
                            onClick = {
                                moreMenuExpanded = false
                                watchlistDialogName = activeList?.name.orEmpty()
                                watchlistDialogMode = WatchlistDialogMode.RENAME
                            },
                        )
                        TerminalMenuItem(
                            text = "Delete list",
                            enabled = activeList != null,
                            onClick = {
                                moreMenuExpanded = false
                                deleteActiveList()
                            },
                        )
                        if (onClose != null) {
                            TerminalMenuItem(
                                text = "Close panel",
                                onClick = {
                                    moreMenuExpanded = false
                                    onClose()
                                },
                            )
                        }
                    }
                }
            }
        }

        listActionError?.let {
            Text(
                it,
                color = tokens.colors.loss,
                fontSize = tokens.typography.smallSize,
                modifier = Modifier.padding(horizontal = tokens.spacing.lg),
            )
        }

        // Текст ошибки каталога — в контекстном окне по клику на бейдж в тайтле
        if (errorPopupExpanded) {
            TerminalContextPopup(
                expanded = true,
                onDismiss = { errorPopupExpanded = false },
                title = "Market data error",
                message = marketState.error.orEmpty(),
                confirmText = "OK",
                onConfirm = { errorPopupExpanded = false },
            )
        }

        if (marketState.loading && explorerRows.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(tokens.spacing.lg),
                strokeWidth = tokens.dimensions.borderWidth * 2,
            )
        }

        // ── Список: тот же explorer, что в тайтлах chart/DOM/Trades ──
        SymbolExplorerContent(
            rows = explorerRows,
            currentSymbol = selectedSymbol,
            currentExchangeId = selectedProviderId,
            activeListId = activeListId,
            onActiveListIdChange = { activeListId = it },
            perpetualsOnly = perpetualsOnly,
            onPerpetualsOnlyChange = { perpetualsOnly = it },
            quoteFilter = quoteFilter,
            onQuoteFilterChange = { quoteFilter = it },
            exchangeFilter = exchangeFilter,
            onExchangeFilterChange = { exchangeFilter = it },
            sort = sort,
            descending = descending,
            onSortChange = { selected, desc ->
                sort = selected
                descending = desc
            },
            query = query,
            onQueryChange = { query = it },
            onSelect = { row -> onSymbolSelected(row.symbol, row.exchangeId) },
            modifier = Modifier.weight(1f),
            listState = listState,
            rowMenu = { row, dismiss ->
                onApplyToCurrentWorkspace?.let { apply ->
                    TerminalMenuItem(
                        text = "Apply to current workspace",
                        onClick = {
                            dismiss()
                            apply(row.symbol, row.exchangeId, row.exchangeName)
                        },
                    )
                }
                if (workspaceTemplates.isNotEmpty()) {
                    WorkspaceTemplatePicker(
                        title = "OPEN ${row.symbol} WITH",
                        templates = workspaceTemplates,
                        onPick = { name, template ->
                            dismiss()
                            onOpenWorkspaceTemplate?.invoke(
                                template,
                                name,
                                row.symbol,
                                row.exchangeId,
                                row.exchangeName,
                            )
                        },
                    )
                }
                if (watchlists.isNotEmpty()) {
                    if (onApplyToCurrentWorkspace != null || workspaceTemplates.isNotEmpty()) {
                        HorizontalDivider(
                            color = tokens.colors.panelBorder,
                            thickness = tokens.dimensions.dividerThickness,
                        )
                    }
                    watchlists.forEach { list ->
                        TerminalMenuItem(
                            text = "Add to ${list.name}",
                            onClick = {
                                dismiss()
                                toggleSymbolInList(list.id, row.symbol)
                            },
                        )
                    }
                }
            },
            onListError = { listActionError = it },
        )
    }

    TerminalContextPopup(
        expanded = watchlistDialogMode != null,
        onDismiss = { watchlistDialogMode = null },
        title = if (watchlistDialogMode == WatchlistDialogMode.CREATE) "Create watchlist" else "Rename watchlist",
        value = watchlistDialogName,
        onValueChange = { watchlistDialogName = it },
        placeholder = "List name",
        confirmText = if (watchlistDialogMode == WatchlistDialogMode.CREATE) "Create" else "Save",
        onConfirm = {
            val repository = watchlistsRepository ?: return@TerminalContextPopup
            val mode = watchlistDialogMode
            val listToRename = activeList
            val name = watchlistDialogName
            watchlistDialogMode = null
            scope.launch {
                try {
                    if (mode == WatchlistDialogMode.CREATE) {
                        activeListId = repository.create(name).id
                    } else if (listToRename != null) {
                        repository.rename(listToRename.id, name)
                    }
                    listActionError = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    listActionError = e.message
                }
            }
        },
    )
}

@Composable
private fun MiniAction(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bordered: Boolean = false,
    content: @Composable () -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    val shape = RoundedCornerShape(tokens.cornerRadii.smallCard)
    Box(
        modifier = modifier
            .then(
                if (bordered) {
                    Modifier
                        .size(20.dp)
                        .clip(shape)
                        .border(tokens.dimensions.borderWidth, tokens.colors.panelBorder, shape)
                } else {
                    Modifier.size(18.dp)
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun SlidersGlyph(color: Color) {
    Canvas(modifier = Modifier.size(14.dp)) {
        val stroke = 1.2.dp.toPx()
        val knobRadius = size.minDimension * 0.155f
        val topY = size.height * 0.27f
        val bottomY = size.height * 0.73f
        val topKnobX = size.width * 0.36f
        val bottomKnobX = size.width * 0.64f
        val edge = size.width * 0.04f

        drawLine(color, Offset(edge, topY), Offset(topKnobX - knobRadius, topY), strokeWidth = stroke)
        drawLine(color, Offset(topKnobX + knobRadius, topY), Offset(size.width - edge, topY), strokeWidth = stroke)
        drawCircle(color, radius = knobRadius, center = Offset(topKnobX, topY), style = Stroke(stroke))

        drawLine(color, Offset(edge, bottomY), Offset(bottomKnobX - knobRadius, bottomY), strokeWidth = stroke)
        drawLine(color, Offset(bottomKnobX + knobRadius, bottomY), Offset(size.width - edge, bottomY), strokeWidth = stroke)
        drawCircle(color, radius = knobRadius, center = Offset(bottomKnobX, bottomY), style = Stroke(stroke))
    }
}

/** Бейдж ошибки: восклицательный знак в красном круге (клик — текст ошибки). */
@Composable
private fun ErrorBadgeGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        drawCircle(color = color)
        val stroke = 1.5.dp.toPx()
        val cx = size.width / 2f
        drawLine(
            color = Color.White,
            start = Offset(cx, size.height * 0.26f),
            end = Offset(cx, size.height * 0.58f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = Color.White,
            radius = stroke / 2f,
            center = Offset(cx, size.height * 0.72f),
        )
    }
}
