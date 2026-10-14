/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout
import com.aandios.nous.core.ui.theme.TerminalFontFamily

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.core.ui.component.ExchangeDropdown
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.component.highlightedSymbol
import com.aandios.nous.core.ui.component.LocalSymbolWatchlistRepository
import com.aandios.nous.core.ui.component.SymbolSearchMatch
import com.aandios.nous.core.ui.component.SymbolWatchlist
import com.aandios.nous.core.ui.component.TerminalDropdown
import com.aandios.nous.core.ui.component.TerminalContextPopup
import com.aandios.nous.core.ui.component.searchSymbols
import com.aandios.nous.core.ui.workspace.WorkspaceLayoutThumbnail
import com.aandios.nous.core.workspace.WorkspaceConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class MarketSort(val label: String) {
    SYMBOL("SYMBOL"),
    CHANGE("24H %"),
    VOLUME("VOL 24H"),
    PRICE("PRICE"),
}

private data class WatchlistChoice(val id: String?, val title: String)

private data class MatchedMarketRow(
    val row: MarketWatchRow,
    val match: SymbolSearchMatch,
    val rank: Int,
)

private enum class WatchlistDialogMode {
    CREATE,
    RENAME,
}

@Composable
fun SymbolsPanel(
    selectedSymbol: String,
    onSymbolSelected: (String) -> Unit,
    workspaceTemplates: List<Pair<String, WorkspaceConfig>>,
    onOpenWorkspaceTemplate: ((WorkspaceConfig, String, String, String, String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val registry: ProviderRegistry = koinInject()
    val marketWatchStore: MarketWatchStore = koinInject()
    val marketState by marketWatchStore.state.collectAsState()
    val watchlistsRepository = LocalSymbolWatchlistRepository.current
    val watchlists by watchlistsRepository?.lists?.collectAsState()
        ?: remember { mutableStateOf(emptyList()) }
    val scope = rememberCoroutineScope()
    var selectedExchange by remember { mutableStateOf(ALL_EXCHANGES) }
    var selectedListId by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var watchlistDialogMode by remember { mutableStateOf<WatchlistDialogMode?>(null) }
    var watchlistDialogName by remember { mutableStateOf("") }
    var listActionError by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(MarketSort.SYMBOL) }
    var descending by remember { mutableStateOf(false) }

    val selectedList = watchlists.firstOrNull { it.id == selectedListId }
    val listChoices = remember(watchlists) {
        listOf(WatchlistChoice(null, MARKET_LIST_LABEL)) +
            watchlists.map { WatchlistChoice(it.id, it.name) }
    }
    val selectedListChoice = listChoices.firstOrNull { it.id == selectedListId }
        ?: listChoices.first()
    val exchangeRows = remember(marketState.rows, selectedExchange) {
        if (selectedExchange == ALL_EXCHANGES) marketState.rows
        else marketState.rows.filter { it.exchangeId == selectedExchange }
    }
    val listRows = remember(exchangeRows, selectedList) {
        selectedList?.let { list -> exchangeRows.filter { it.symbol in list.symbols } } ?: exchangeRows
    }
    val symbolSearch = remember(listRows, search) {
        searchSymbols(listRows.map { it.symbol }, search)
    }
    val matchingRows = remember(listRows, symbolSearch) {
        val rowsBySymbol = listRows.groupBy { it.symbol }
        val usedIndices = mutableMapOf<String, Int>()
        symbolSearch.matches.mapIndexedNotNull { rank, match ->
            val rowIndex = usedIndices[match.symbol] ?: 0
            usedIndices[match.symbol] = rowIndex + 1
            rowsBySymbol[match.symbol]?.getOrNull(rowIndex)?.let { MatchedMarketRow(it, match, rank) }
        }
    }
    val sortedRows = remember(matchingRows, sort, descending) {
        val comparator = when (sort) {
            MarketSort.SYMBOL -> compareBy<MatchedMarketRow> { it.rank }
            MarketSort.CHANGE -> compareBy(nullsLast()) { it.row.stats?.changePercent24h }
            MarketSort.VOLUME -> compareBy(nullsLast()) { it.row.stats?.quoteVolume24h }
            MarketSort.PRICE -> compareBy(nullsLast()) { it.row.stats?.lastPrice }
        }
        matchingRows.sortedWith(if (descending) comparator.reversed() else comparator)
    }
    val tokens = LocalTerminalDesignTokens.current

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = tokens.spacing.sm,
                vertical = tokens.spacing.xs,
            ),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val exchangeOptions = remember(registry) {
                listOf(ALL_EXCHANGES_LABEL) + registry.providers.map { it.config.displayName }
            }
            val currentExchangeLabel = if (selectedExchange == ALL_EXCHANGES) {
                ALL_EXCHANGES_LABEL
            } else {
                registry.get(selectedExchange)?.config?.displayName ?: ALL_EXCHANGES_LABEL
            }
            ExchangeDropdown(
                current = currentExchangeLabel,
                exchanges = exchangeOptions,
                onChange = { label ->
                    selectedExchange = registry.providers.firstOrNull {
                        it.config.displayName == label
                    }?.providerId ?: ALL_EXCHANGES
                },
                modifier = Modifier.weight(1f),
            )
            TerminalDropdown(
                currentValue = selectedListChoice,
                items = listChoices,
                onValueChanged = { selectedListId = it.id },
                displayText = { it.title },
                menuWidth = 180.dp,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = tokens.spacing.sm,
                vertical = tokens.spacing.xxs,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
        ) {
            BasicTextField(
                value = search,
                onValueChange = { search = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = tokens.typography.controlSize,
                    fontFamily = TerminalFontFamily,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        RoundedCornerShape(tokens.cornerRadii.card),
                    )
                    .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
                decorationBox = { inner ->
                    if (search.isEmpty()) {
                        Text(
                            "Regex search…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            fontSize = tokens.typography.compactSize,
                            fontFamily = TerminalFontFamily,
                        )
                    }
                    inner()
                },
            )
            Box {
                val mode = watchlistDialogMode
                Text(
                    text = if (selectedList == null) "+ LIST" else "RENAME",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = tokens.typography.microSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFontFamily,
                    modifier = Modifier.clickable {
                        watchlistDialogName = selectedList?.name.orEmpty()
                        watchlistDialogMode = if (selectedList == null) {
                            WatchlistDialogMode.CREATE
                        } else {
                            WatchlistDialogMode.RENAME
                        }
                    }.padding(4.dp),
                )
                TerminalContextPopup(
                    expanded = watchlistDialogMode != null,
                    onDismiss = { watchlistDialogMode = null },
                    title = if (mode == WatchlistDialogMode.CREATE) "Create watchlist" else "Rename watchlist",
                    value = watchlistDialogName,
                    onValueChange = { watchlistDialogName = it },
                    placeholder = "List name",
                    confirmText = if (mode == WatchlistDialogMode.CREATE) "Create" else "Save",
                    onConfirm = {
                        val repository = watchlistsRepository ?: return@TerminalContextPopup
                        val listToRename = selectedList
                        val name = watchlistDialogName
                        watchlistDialogMode = null
                        scope.launch {
                            try {
                                if (mode == WatchlistDialogMode.CREATE) {
                                    selectedListId = repository.create(name).id
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
            if (selectedList != null) {
                Text(
                    "DELETE",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 9.sp,
                    modifier = Modifier.clickable {
                        val repository = watchlistsRepository ?: return@clickable
                        scope.launch {
                            try {
                                repository.delete(selectedList.id)
                                selectedListId = null
                                listActionError = null
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                listActionError = e.message
                            }
                        }
                    }.padding(horizontal = 5.dp),
                )
            }
        }

        listActionError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = tokens.spacing.sm,
                vertical = tokens.spacing.xs,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (marketState.loading) "SYNC" else "${sortedRows.size} SYMBOLS",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                fontFamily = TerminalFontFamily,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                MarketSort.entries.forEach { option ->
                    val active = sort == option
                    Text(
                        option.label,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = tokens.typography.microSize,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = TerminalFontFamily,
                        modifier = Modifier.clickable {
                            if (sort == option) descending = !descending
                            else {
                                sort = option
                                descending = option != MarketSort.SYMBOL
                            }
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = tokens.spacing.sm,
                vertical = tokens.spacing.xxs,
            ),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("SYMBOL", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp)
            Text("24H %", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp)
        }

        symbolSearch.error?.let {
            Text(
                "REGEX: $it",
                color = MaterialTheme.colorScheme.error,
                fontSize = 9.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }

        if (marketState.error != null) {
            Text(
                marketState.error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                fontSize = 9.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }

        if (marketState.loading && marketState.rows.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(12.dp),
                strokeWidth = 2.dp,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            items(sortedRows, key = { "${it.row.exchangeId}:${it.row.symbol}" }) { item ->
                MarketWatchSymbolRow(
                    row = item.row,
                    matchRange = item.match.matchRange,
                    selected = item.row.symbol == selectedSymbol,
                    inSelectedList = item.row.symbol in (selectedList?.symbols ?: emptyList()),
                    selectedList = selectedList,
                    availableLists = watchlists,
                    workspaceTemplates = workspaceTemplates,
                    canAddToLists = selectedList == null && watchlists.isNotEmpty(),
                    onSelect = { onSymbolSelected(item.row.symbol) },
                    onAddToList = { listId ->
                        val repository = watchlistsRepository ?: return@MarketWatchSymbolRow
                        scope.launch {
                            try {
                                repository.toggleSymbol(listId, item.row.symbol)
                                listActionError = null
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                listActionError = e.message
                            }
                        }
                    },
                    onRemoveFromList = {
                        val list = selectedList ?: return@MarketWatchSymbolRow
                        val repository = watchlistsRepository ?: return@MarketWatchSymbolRow
                        scope.launch {
                            try {
                                repository.toggleSymbol(list.id, item.row.symbol)
                                listActionError = null
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                listActionError = e.message
                            }
                        }
                    },
                    onOpenWithTemplate = { templateName, template ->
                        onOpenWorkspaceTemplate?.invoke(
                            template,
                            templateName,
                            item.row.symbol,
                            item.row.exchangeId,
                            item.row.exchangeName,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun MarketWatchSymbolRow(
    row: MarketWatchRow,
    matchRange: IntRange?,
    selected: Boolean,
    inSelectedList: Boolean,
    selectedList: SymbolWatchlist?,
    availableLists: List<SymbolWatchlist>,
    workspaceTemplates: List<Pair<String, WorkspaceConfig>>,
    canAddToLists: Boolean,
    onSelect: () -> Unit,
    onAddToList: (String) -> Unit,
    onRemoveFromList: () -> Unit,
    onOpenWithTemplate: (String, WorkspaceConfig) -> Unit,
) {
    var addMenuExpanded by remember(row.exchangeId, row.symbol) { mutableStateOf(false) }
    var openWithExpanded by remember(row.exchangeId, row.symbol) { mutableStateOf(false) }
    val positive = row.stats?.changePercent24h?.let { it >= 0.0 } == true
    val changeColor = when {
        row.stats == null -> MaterialTheme.colorScheme.onSurfaceVariant
        positive -> Color(0xFF00C853)
        else -> Color(0xFFEF5350)
    }
    Surface(
        modifier = Modifier.fillMaxWidth()
            .pointerInput(row.exchangeId, row.symbol) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (
                            event.type == PointerEventType.Press &&
                            event.buttons.isSecondaryPressed &&
                            workspaceTemplates.isNotEmpty()
                        ) {
                            openWithExpanded = true
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .clickable(onClick = onSelect),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else Color.Transparent,
    ) {
        Box {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = highlightedSymbol(row.symbol, matchRange),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        row.exchangeName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 8.sp,
                        fontFamily = TerminalFontFamily,
                        maxLines = 1,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        row.stats?.changePercent24h?.let { "%+.2f%%".format(it) } ?: "—",
                        color = changeColor,
                        fontSize = 10.sp,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        row.stats?.quoteVolume24h?.let(::formatMarketVolume) ?: "VOL —",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 8.sp,
                        fontFamily = TerminalFontFamily,
                    )
                }
                if (selectedList != null && inSelectedList) {
                    Text(
                        "−",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable(onClick = onRemoveFromList).padding(start = 8.dp),
                    )
                } else if (canAddToLists) {
                    Box {
                        Text(
                            "+",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { addMenuExpanded = true }
                                .padding(start = 8.dp, end = 4.dp),
                        )
                        DropdownMenu(
                            expanded = addMenuExpanded,
                            onDismissRequest = { addMenuExpanded = false },
                            modifier = Modifier.width(170.dp),
                        ) {
                            availableLists.forEach { list ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Add to ${list.name}",
                                            fontSize = 11.sp,
                                            fontFamily = TerminalFontFamily,
                                        )
                                    },
                                    onClick = {
                                        onAddToList(list.id)
                                        addMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            DropdownMenu(
                expanded = openWithExpanded,
                onDismissRequest = { openWithExpanded = false },
            ) {
                Column(Modifier.width(460.dp).padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text(
                        "OPEN ${row.symbol} WITH",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
                        fontFamily = TerminalFontFamily,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        workspaceTemplates.forEach { (templateName, template) ->
                            Column(
                                modifier = Modifier.width(124.dp)
                                    .clickable {
                                        openWithExpanded = false
                                        onOpenWithTemplate(templateName, template)
                                    }
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        RoundedCornerShape(4.dp),
                                    )
                                    .padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                WorkspaceLayoutThumbnail(
                                    config = template,
                                    modifier = Modifier.fillMaxWidth().height(68.dp),
                                )
                                Text(
                                    templateName,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 9.sp,
                                    fontFamily = TerminalFontFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 5.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMarketVolume(value: Double): String = when {
    value >= 1_000_000_000 -> "VOL ${"%.2f".format(value / 1_000_000_000)}B"
    value >= 1_000_000 -> "VOL ${"%.2f".format(value / 1_000_000)}M"
    value >= 1_000 -> "VOL ${"%.1f".format(value / 1_000)}K"
    else -> "VOL ${"%.0f".format(value)}"
}

private const val ALL_EXCHANGES = "ALL"
private const val ALL_EXCHANGES_LABEL = "ALL"
private const val MARKET_LIST_LABEL = "MARKET"
