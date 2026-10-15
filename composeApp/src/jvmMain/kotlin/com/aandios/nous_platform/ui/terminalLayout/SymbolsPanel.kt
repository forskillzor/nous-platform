/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout
import com.aandios.nous.core.ui.theme.TerminalFontFamily

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.core.ui.component.LocalSymbolWatchlistRepository
import com.aandios.nous.core.ui.component.SymbolSearchMatch
import com.aandios.nous.core.ui.component.SymbolWatchlist
import com.aandios.nous.core.ui.component.TerminalContextPopup
import com.aandios.nous.core.ui.component.TerminalGripDots
import com.aandios.nous.core.ui.component.TerminalGripOrientation
import com.aandios.nous.core.ui.component.TerminalPanelHeader
import com.aandios.nous.core.ui.component.highlightedSymbol
import com.aandios.nous.core.ui.component.searchSymbols
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
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
    onApplyToCurrentWorkspace: ((String, String, String) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
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
    var listMenuExpanded by remember { mutableStateOf(false) }
    var moreMenuExpanded by remember { mutableStateOf(false) }

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
    val favoriteEnabled = selectedList != null && selectedSymbol.isNotBlank()

    fun toggleFavorite() {
        val list = selectedList ?: return
        val repository = watchlistsRepository ?: return
        scope.launch {
            try {
                repository.toggleSymbol(list.id, selectedSymbol)
                listActionError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                listActionError = e.message
            }
        }
    }

    fun deleteSelectedList() {
        val list = selectedList ?: return
        val repository = watchlistsRepository ?: return
        scope.launch {
            try {
                repository.delete(list.id)
                selectedListId = null
                listActionError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                listActionError = e.message
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(tokens.colors.panelSurface)) {
        // ── Заголовок: grip, star, title, new list, overflow ──
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
                MiniAction(
                    contentDescription = "Toggle in list",
                    enabled = favoriteEnabled,
                    onClick = { toggleFavorite() },
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (favoriteEnabled) tokens.colors.accent
                        else tokens.colors.label.copy(alpha = 0.35f),
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = "Market Watch",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = tokens.typography.controlSize,
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    MiniAction(
                        contentDescription = "Panel settings",
                        onClick = { moreMenuExpanded = true },
                    ) {
                        SlidersGlyph(color = tokens.colors.label)
                    }
                    DropdownMenu(
                        expanded = moreMenuExpanded,
                        onDismissRequest = { moreMenuExpanded = false },
                        modifier = Modifier.width(tokens.dimensions.tradeFilterMenuWidth),
                    ) {
                        MenuCaption("SORT")
                        MarketSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { MenuCheckRow(text = option.label, checked = sort == option) },
                                onClick = {
                                    if (sort == option) descending = !descending
                                    else {
                                        sort = option
                                        descending = option != MarketSort.SYMBOL
                                    }
                                    moreMenuExpanded = false
                                },
                            )
                        }
                        HorizontalDivider(
                            color = tokens.colors.panelBorder,
                            thickness = tokens.dimensions.dividerThickness,
                        )
                        MenuCaption("EXCHANGE")
                        DropdownMenuItem(
                            text = { MenuCheckRow(text = ALL_EXCHANGES_LABEL, checked = selectedExchange == ALL_EXCHANGES) },
                            onClick = {
                                selectedExchange = ALL_EXCHANGES
                                moreMenuExpanded = false
                            },
                        )
                        registry.providers.forEach { provider ->
                            DropdownMenuItem(
                                text = {
                                    MenuCheckRow(
                                        text = provider.config.displayName,
                                        checked = selectedExchange == provider.providerId,
                                    )
                                },
                                onClick = {
                                    selectedExchange = provider.providerId
                                    moreMenuExpanded = false
                                },
                            )
                        }
                        HorizontalDivider(
                            color = tokens.colors.panelBorder,
                            thickness = tokens.dimensions.dividerThickness,
                        )
                        DropdownMenuItem(
                            text = { MenuRowText("Delete list") },
                            enabled = selectedList != null,
                            onClick = {
                                moreMenuExpanded = false
                                deleteSelectedList()
                            },
                        )
                        if (onClose != null) {
                            DropdownMenuItem(
                                text = { MenuRowText("Close panel") },
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

        // ── Строка LIST: подпись, выпадающий список, rename, new ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = tokens.spacing.lg,
                vertical = tokens.spacing.sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
        ) {
            Text(
                text = "LIST",
                color = tokens.colors.label,
                fontSize = tokens.typography.microSize,
                fontFamily = TerminalFontFamily,
                letterSpacing = tokens.typography.compactLetterSpacing,
            )
            Box(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(tokens.cornerRadii.smallCard),
                    color = tokens.colors.panelHeaderSurface.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            tokens.dimensions.borderWidth,
                            tokens.colors.panelBorder,
                            RoundedCornerShape(tokens.cornerRadii.smallCard),
                        )
                        .clickable { listMenuExpanded = true },
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = tokens.spacing.sm,
                            vertical = tokens.spacing.xs,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = selectedListChoice.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = tokens.typography.controlSize,
                            fontFamily = TerminalFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = tokens.colors.label,
                            modifier = Modifier.size(tokens.dimensions.dropdownIconSize),
                        )
                    }
                }
                DropdownMenu(
                    expanded = listMenuExpanded,
                    onDismissRequest = { listMenuExpanded = false },
                    modifier = Modifier.width(tokens.dimensions.marketWatchListMenuWidth),
                ) {
                    listChoices.forEach { choice ->
                        DropdownMenuItem(
                            text = {
                                MenuCheckRow(
                                    text = choice.title,
                                    checked = choice.id == selectedListChoice.id,
                                )
                            },
                            onClick = {
                                selectedListId = choice.id
                                listMenuExpanded = false
                            },
                        )
                    }
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
            MiniAction(
                contentDescription = "Rename list",
                enabled = selectedList != null,
                onClick = {
                    watchlistDialogName = selectedList?.name.orEmpty()
                    watchlistDialogMode = WatchlistDialogMode.RENAME
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = if (selectedList != null) tokens.colors.label
                    else tokens.colors.label.copy(alpha = 0.35f),
                    modifier = Modifier.size(14.dp),
                )
            }
            MiniAction(
                contentDescription = "New list",
                onClick = {
                    watchlistDialogName = ""
                    watchlistDialogMode = WatchlistDialogMode.CREATE
                },
                bordered = true,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = tokens.colors.accent,
                    modifier = Modifier.size(13.dp),
                )
            }
        }

        // ── Поиск ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = tokens.spacing.lg,
                vertical = tokens.spacing.sm,
            ),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        tokens.colors.panelHeaderSurface.copy(alpha = 0.45f),
                        RoundedCornerShape(tokens.cornerRadii.smallCard),
                    )
                    .border(
                        tokens.dimensions.borderWidth,
                        tokens.colors.panelBorder,
                        RoundedCornerShape(tokens.cornerRadii.smallCard),
                    )
                    .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = tokens.colors.label,
                    modifier = Modifier.size(14.dp),
                )
                BasicTextField(
                    value = search,
                    onValueChange = { search = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = tokens.typography.controlSize,
                        fontFamily = TerminalFontFamily,
                    ),
                    cursorBrush = SolidColor(tokens.colors.accent),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (search.isEmpty()) {
                            Text(
                                "Filter contracts",
                                color = tokens.colors.label.copy(alpha = 0.65f),
                                fontSize = tokens.typography.compactSize,
                                fontFamily = TerminalFontFamily,
                            )
                        }
                        inner()
                    },
                )
            }
        }

        // ── Подписи колонок ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = tokens.spacing.lg,
                vertical = tokens.spacing.xs,
            ),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "INSTRUMENT",
                color = tokens.colors.label,
                fontSize = tokens.typography.microSize,
                fontFamily = TerminalFontFamily,
                letterSpacing = tokens.typography.compactLetterSpacing,
            )
            Text(
                "LAST / CHG",
                color = tokens.colors.label,
                fontSize = tokens.typography.microSize,
                fontFamily = TerminalFontFamily,
                letterSpacing = tokens.typography.compactLetterSpacing,
            )
        }

        listActionError?.let {
            Text(
                it,
                color = tokens.colors.loss,
                fontSize = tokens.typography.smallSize,
                modifier = Modifier.padding(horizontal = tokens.spacing.lg),
            )
        }

        symbolSearch.error?.let {
            Text(
                "REGEX: $it",
                color = tokens.colors.loss,
                fontSize = tokens.typography.microSize,
                modifier = Modifier.padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.xs),
            )
        }

        if (marketState.error != null) {
            Text(
                marketState.error.orEmpty(),
                color = tokens.colors.loss,
                fontSize = tokens.typography.microSize,
                modifier = Modifier.padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.xs),
            )
        }

        if (marketState.loading && marketState.rows.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(tokens.spacing.lg),
                strokeWidth = tokens.dimensions.borderWidth * 2,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
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
                    onApplyToCurrentWorkspace = onApplyToCurrentWorkspace?.let { apply ->
                        { apply(item.row.symbol, item.row.exchangeId, item.row.exchangeName) }
                    },
                )
            }
        }
    }
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

@Composable
private fun MenuCaption(text: String) {
    val tokens = LocalTerminalDesignTokens.current
    Text(
        text = text,
        color = tokens.colors.label,
        fontSize = tokens.typography.nanoSize,
        fontFamily = TerminalFontFamily,
        letterSpacing = tokens.typography.compactLetterSpacing,
        modifier = Modifier.padding(
            start = tokens.spacing.lg,
            end = tokens.spacing.lg,
            top = tokens.spacing.xs,
            bottom = tokens.spacing.xxs,
        ),
    )
}

@Composable
private fun MenuCheckRow(text: String, checked: Boolean) {
    val tokens = LocalTerminalDesignTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
    ) {
        Box(modifier = Modifier.size(12.dp), contentAlignment = Alignment.Center) {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = tokens.colors.accent,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = tokens.typography.controlSize,
            fontFamily = TerminalFontFamily,
        )
    }
}

@Composable
private fun MenuRowText(text: String) {
    val tokens = LocalTerminalDesignTokens.current
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = tokens.typography.controlSize,
        fontFamily = TerminalFontFamily,
    )
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
    onApplyToCurrentWorkspace: (() -> Unit)? = null,
) {
    val tokens = LocalTerminalDesignTokens.current
    var addMenuExpanded by remember(row.exchangeId, row.symbol) { mutableStateOf(false) }
    var openWithExpanded by remember(row.exchangeId, row.symbol) { mutableStateOf(false) }
    val positive = row.stats?.changePercent24h?.let { it >= 0.0 } == true
    val changeColor = when {
        row.stats == null -> tokens.colors.label
        positive -> tokens.colors.profit
        else -> tokens.colors.loss
    }
    Surface(
        modifier = Modifier.fillMaxWidth()
            .drawWithContent {
                drawContent()
                if (selected) {
                    drawRect(
                        color = tokens.colors.accent,
                        size = Size(tokens.spacing.xxs.toPx(), size.height),
                    )
                }
            }
            .pointerInput(row.exchangeId, row.symbol) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (
                            event.type == PointerEventType.Press &&
                            event.buttons.isSecondaryPressed &&
                            (workspaceTemplates.isNotEmpty() || onApplyToCurrentWorkspace != null)
                        ) {
                            openWithExpanded = true
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .clickable(onClick = onSelect),
        color = if (selected) tokens.colors.selection
        else Color.Transparent,
    ) {
        Box {
            Row(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = tokens.spacing.lg,
                    vertical = tokens.spacing.md,
                ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = highlightedSymbol(row.symbol, matchRange),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = tokens.typography.controlSize,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        row.exchangeName,
                        color = tokens.colors.label,
                        fontSize = tokens.typography.smallSize,
                        fontFamily = TerminalFontFamily,
                        maxLines = 1,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        row.stats?.changePercent24h?.let { "%+.2f%%".format(it) } ?: "—",
                        color = changeColor,
                        fontSize = tokens.typography.smallSize,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        row.stats?.lastPrice?.let { SymbolFormatter.DEFAULT.formatVolumeFull(it) } ?: "—",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = tokens.typography.compactSize,
                        fontFamily = TerminalFontFamily,
                    )
                }
                if (selectedList != null && inSelectedList) {
                    Text(
                        "−",
                        color = tokens.colors.accent,
                        fontSize = tokens.typography.compactActionSize,
                        modifier = Modifier.clickable(onClick = onRemoveFromList)
                            .padding(start = tokens.spacing.md),
                    )
                } else if (canAddToLists) {
                    Box {
                        Text(
                            "+",
                            color = tokens.colors.accent,
                            fontSize = tokens.typography.compactActionSize,
                            modifier = Modifier.clickable { addMenuExpanded = true }
                                .padding(start = tokens.spacing.md, end = tokens.spacing.xs),
                        )
                        DropdownMenu(
                            expanded = addMenuExpanded,
                            onDismissRequest = { addMenuExpanded = false },
                            modifier = Modifier.width(tokens.dimensions.marketWatchAddListMenuWidth),
                        ) {
                            availableLists.forEach { list ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Add to ${list.name}",
                                            fontSize = tokens.typography.compactSize,
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
                Column(Modifier.padding(vertical = tokens.spacing.xs)) {
                    onApplyToCurrentWorkspace?.let { apply ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Apply to current workspace",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = tokens.typography.compactSize,
                                    fontFamily = TerminalFontFamily,
                                )
                            },
                            onClick = {
                                openWithExpanded = false
                                apply()
                            },
                        )
                    }
                    WorkspaceTemplatePicker(
                        title = "OPEN ${row.symbol} WITH",
                        templates = workspaceTemplates,
                        onPick = { name, template ->
                            openWithExpanded = false
                            onOpenWithTemplate(name, template)
                        },
                    )
                }
            }
        }
    }
}

private const val ALL_EXCHANGES = "ALL"
private const val ALL_EXCHANGES_LABEL = "ALL"
private const val MARKET_LIST_LABEL = "MARKET"
