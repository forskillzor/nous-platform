/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.core.market.MarketOverviewRow
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.core.ui.format.plainDecimalFixed
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily
import com.aandios.nous.core.ui.workspace.LocalPanelSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Строка расширенного списка символов: символ + биржа + тип контракта +
 * статистика 24ч. Все числовые поля — «сырые», форматирует UI.
 */
data class SymbolExplorerRow(
    val symbol: String,
    val displaySymbol: String,
    val exchangeId: String,
    val exchangeLabel: String,
    val isPerpetual: Boolean = true,
    val quoteAsset: String? = null,
    val tickSize: Double? = null,
    val lastPrice: Double? = null,
    val changePercent24h: Double? = null,
    val quoteVolume24h: Double? = null,
    val exchangeName: String = exchangeLabel,
)

/** Маппинг строки каталога рынка в строку explorer-дропдауна. */
fun MarketOverviewRow.toSymbolExplorerRow(): SymbolExplorerRow = SymbolExplorerRow(
    symbol = symbol,
    displaySymbol = symbol + if (isPerpetual) ".P" else "",
    exchangeId = exchangeId,
    exchangeLabel = exchangeName.uppercase(),
    isPerpetual = isPerpetual,
    quoteAsset = quoteAsset,
    tickSize = tickSize,
    lastPrice = stats?.lastPrice,
    changePercent24h = stats?.changePercent24h,
    quoteVolume24h = stats?.quoteVolume24h,
    exchangeName = exchangeName,
)

enum class SymbolExplorerSort(val label: String) {
    VOLUME("VOLUME"),
    CHANGE("24H %"),
    PRICE("PRICE"),
    SYMBOL("SYMBOL"),
}

data class SymbolExplorerMatch(
    val row: SymbolExplorerRow,
    val matchRange: IntRange?,
)

data class SymbolExplorerSearchResult(
    val matches: List<SymbolExplorerMatch>,
    val error: String? = null,
)

/** Quote-активы, встречающиеся в списке (USDT первым). */
fun explorerQuoteOptions(rows: List<SymbolExplorerRow>): List<String> =
    rows.mapNotNull { it.quoteAsset }
        .distinct()
        .sortedWith(compareBy({ it != "USDT" }, { it }))

/** Биржи в списке: id + label, отсортированы по label. */
fun explorerExchangeOptions(rows: List<SymbolExplorerRow>): List<Pair<String, String>> =
    rows.map { it.exchangeId to it.exchangeLabel }
        .distinct()
        .sortedBy { it.second }

fun explorerFooterText(count: Int, sort: SymbolExplorerSort): String =
    "$count CONTRACTS · SORTED BY ${sort.label}"

private fun <R : Comparable<R>> nullsLastAsc(selector: (SymbolExplorerRow) -> R?): Comparator<SymbolExplorerRow> =
    Comparator { a, b ->
        val va = selector(a)
        val vb = selector(b)
        when {
            va == null && vb == null -> 0
            va == null -> 1
            vb == null -> -1
            else -> va.compareTo(vb)
        }
    }

private fun <R : Comparable<R>> nullsLastDesc(selector: (SymbolExplorerRow) -> R?): Comparator<SymbolExplorerRow> =
    Comparator { a, b ->
        val va = selector(a)
        val vb = selector(b)
        when {
            va == null && vb == null -> 0
            va == null -> 1
            vb == null -> -1
            else -> vb.compareTo(va)
        }
    }

fun explorerComparator(sort: SymbolExplorerSort, descending: Boolean): Comparator<SymbolExplorerRow> =
    when (sort) {
        SymbolExplorerSort.VOLUME ->
            if (descending) nullsLastDesc { it.quoteVolume24h } else nullsLastAsc { it.quoteVolume24h }
        SymbolExplorerSort.CHANGE ->
            if (descending) nullsLastDesc { it.changePercent24h } else nullsLastAsc { it.changePercent24h }
        SymbolExplorerSort.PRICE ->
            if (descending) nullsLastDesc { it.lastPrice } else nullsLastAsc { it.lastPrice }
        SymbolExplorerSort.SYMBOL ->
            if (descending) compareByDescending { it.displaySymbol } else compareBy { it.displaySymbol }
    }

fun sortExplorerRows(
    rows: List<SymbolExplorerRow>,
    sort: SymbolExplorerSort,
    descending: Boolean,
): List<SymbolExplorerRow> =
    rows.sortedWith(explorerComparator(sort, descending).thenBy { it.displaySymbol })

/**
 * Полный конвейер списка: фильтры (таб watchlist, perpetuals, quote, биржа),
 * поиск (единый матчер [matchSymbol]: символ с подсветкой, затем
 * биржа/PERP/quote без подсветки) и сортировка. С активным чипом SYMBOL и
 * запросом порядок — по релевантности (как Market Watch, направление ↓/↑
 * инвертирует); при остальных сортировках — по выбранному полю.
 */
fun computeExplorerRows(
    rows: List<SymbolExplorerRow>,
    activeListSymbols: Set<String>?,
    perpetualsOnly: Boolean,
    quote: String?,
    exchangeId: String?,
    query: String,
    sort: SymbolExplorerSort,
    descending: Boolean,
): SymbolExplorerSearchResult {
    val filtered = rows.filter { row ->
        (activeListSymbols == null || row.symbol in activeListSymbols) &&
            (!perpetualsOnly || row.isPerpetual) &&
            (quote == null || row.quoteAsset == quote) &&
            (exchangeId == null || row.exchangeId == exchangeId)
    }

    if (query.isBlank()) {
        return SymbolExplorerSearchResult(
            sortExplorerRows(filtered, sort, descending).map { SymbolExplorerMatch(it, null) },
        )
    }

    val regex = when (val compiled = compileSearchRegex(query)) {
        is SearchRegexResult.Ok -> compiled.regex
        is SearchRegexResult.Invalid -> return SymbolExplorerSearchResult(emptyList(), compiled.message)
    }

    val matched = filtered.mapNotNull { row ->
        val match = matchSymbol(
            displaySymbol = row.displaySymbol,
            metaFields = buildList {
                add(row.exchangeLabel)
                if (row.isPerpetual) add("PERP")
                row.quoteAsset?.let { add(it) }
            },
            regex = regex,
        ) ?: return@mapNotNull null
        row to match
    }

    val relevanceOrder = sort == SymbolExplorerSort.SYMBOL
    val comparator = if (relevanceOrder) {
        Comparator<Pair<SymbolExplorerRow, SymbolMatch>> { a, b ->
            symbolMatchComparator.compare(a.second, b.second)
        }.thenBy { it.first.displaySymbol }
    } else {
        Comparator<Pair<SymbolExplorerRow, SymbolMatch>> { a, b ->
            explorerComparator(sort, descending).compare(a.first, b.first)
        }.thenBy { it.first.displaySymbol }
    }
    val ordered = if (relevanceOrder && descending) {
        matched.sortedWith(comparator.reversed())
    } else {
        matched.sortedWith(comparator)
    }

    return SymbolExplorerSearchResult(
        ordered.map { (row, match) -> SymbolExplorerMatch(row, match.matchRange) },
    )
}

/** Компиляция поискового regex (регистронезависимо) для Market Watch и explorer. */
internal sealed interface SearchRegexResult {
    data class Ok(val regex: Regex) : SearchRegexResult
    data class Invalid(val message: String) : SearchRegexResult
}

internal fun compileSearchRegex(query: String): SearchRegexResult = try {
    SearchRegexResult.Ok(Regex(query, RegexOption.IGNORE_CASE))
} catch (e: IllegalArgumentException) {
    SearchRegexResult.Invalid(e.message ?: "Invalid regular expression")
}

/**
 * Единый матчер поиска (Market Watch и explorer): сначала символ — с
 * диапазоном подсветки и score релевантности (0 точное → 1 префикс →
 * 2 подстрока → 3 пустой матч), затем мета-поля (биржа/PERP/quote) без
 * подсветки (score 4). null — кандидат не совпал.
 */
internal fun matchSymbol(
    displaySymbol: String,
    metaFields: List<String>,
    regex: Regex,
): SymbolMatch? {
    val symbolMatch = regex.find(displaySymbol)
    if (symbolMatch != null) {
        val range = symbolMatch.range.takeIf { symbolMatch.value.isNotEmpty() }
        val score = when {
            range == null -> 3
            range.first == 0 && symbolMatch.value.length == displaySymbol.length -> 0
            range.first == 0 -> 1
            else -> 2
        }
        return SymbolMatch(
            matchRange = range,
            score = score,
            position = range?.first ?: Int.MAX_VALUE,
            matchLength = if (range == null) Int.MAX_VALUE else range.last - range.first,
        )
    }
    return metaFields.firstNotNullOfOrNull { regex.find(it) }?.let {
        SymbolMatch(matchRange = null, score = 4, position = Int.MAX_VALUE, matchLength = Int.MAX_VALUE)
    }
}

/**
 * Матч кандидата: [matchRange] — диапазон подсветки в символе (null —
 * совпало мета-поле); [score]/[position]/[matchLength] — ключи релевантности.
 */
internal data class SymbolMatch(
    val matchRange: IntRange?,
    val score: Int,
    val position: Int,
    val matchLength: Int,
)

/** Порядок релевантности матчей: score → позиция → длина (меньше — лучше). */
internal val symbolMatchComparator: Comparator<SymbolMatch> =
    compareBy({ it.score }, { it.position }, { it.matchLength })

/**
 * Расширенный дропдаун выбора символа (тайтл chart, заголовки DOM/Trades):
 * табы (ALL + watchlists), поиск, фильтры (perpetuals/quote/биржа), список со
 * статистикой 24ч и избранным. Выбор строки другой биржи переключает
 * провайдера панели (зовите [onSelect] и обрабатывайте кросс-биржу выше).
 * Содержимое — общий [SymbolExplorerContent] (та же форма, что в Market Watch).
 */
@Composable
fun SymbolExplorerDropdown(
    currentSymbol: String,
    currentExchangeId: String?,
    rows: List<SymbolExplorerRow>,
    onSelect: (SymbolExplorerRow) -> Unit,
    modifier: Modifier = Modifier,
    onManageWatchlist: (() -> Unit)? = null,
    menuWidth: androidx.compose.ui.unit.Dp = 380.dp,
) {
    val tokens = LocalTerminalDesignTokens.current

    var expanded by remember { mutableStateOf(false) }
    var activeListId by remember { mutableStateOf<String?>(null) }
    var perpetualsOnly by remember { mutableStateOf(true) }
    var quoteFilter by remember { mutableStateOf<String?>("USDT") }
    var exchangeFilter by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(SymbolExplorerSort.VOLUME) }
    var descending by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var listHeight by remember { mutableStateOf(EXPLORER_ROW_HEIGHT * 5) }

    val density = LocalDensity.current
    val panelSize = LocalPanelSize.current
    // Верхняя граница высоты списка — высота панели-хозяина (chart/DOM/
    // Trades) за вычетом хрома попапа, чтобы список не вылезал за панель.
    // Вне workspace-панели (standalone окна) размера нет — без границы.
    val maxListHeight = remember(panelSize, density) {
        if (panelSize.height > 0) {
            with(density) {
                (panelSize.height.toDp() - EXPLORER_POPUP_CHROME_HEIGHT)
                    .coerceAtLeast(EXPLORER_MIN_LIST_HEIGHT)
            }
        } else {
            null
        }
    }
    val cappedListHeight = maxListHeight
        ?.let { max -> listHeight.coerceIn(EXPLORER_MIN_LIST_HEIGHT, max) }
        ?: listHeight.coerceAtLeast(EXPLORER_MIN_LIST_HEIGHT)

    val searchFocus = remember { FocusRequester() }

    LaunchedEffect(expanded) {
        if (!expanded) query = ""
    }
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(80)
            runCatching { searchFocus.requestFocus() }
        }
    }

    val currentExchangeLabel = currentExchangeId?.let { id ->
        rows.firstOrNull { it.exchangeId == id }?.exchangeLabel
    }?.takeIf { it.isNotEmpty() }
    val exchangeLeading: (@Composable () -> Unit)? = currentExchangeLabel?.let { label ->
        {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.primary,
                fontSize = tokens.typography.microSize,
                lineHeight = tokens.typography.controlLineHeight,
                fontWeight = FontWeight.Medium,
                fontFamily = TerminalFontFamily,
                maxLines = 1,
            )
        }
    }

    TerminalBoxDropdown(
        text = currentSymbol,
        modifier = modifier,
        menuWidth = menuWidth,
        onExpandedChange = { expanded = it },
        leading = exchangeLeading,
    ) { dismiss ->
        // Fixed width/height are required: M3 DropdownMenu measures its content
        // with IntrinsicSize.Max, and a SubcomposeLayout can only answer
        // intrinsic queries when it has a specified size (SizeNode short-circuit).
        SymbolExplorerContent(
            rows = rows,
            currentSymbol = currentSymbol,
            currentExchangeId = currentExchangeId,
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
            onSelect = { row ->
                onSelect(row)
                dismiss()
            },
            listWidth = menuWidth,
            listHeight = cappedListHeight,
            searchFocusRequester = searchFocus,
            tabsTrailing = {
                Text(
                    text = "✕",
                    color = tokens.colors.label,
                    fontSize = 12.sp,
                    fontFamily = TerminalFontFamily,
                    modifier = Modifier
                        .clickable { dismiss() }
                        .padding(tokens.spacing.xs),
                )
            },
            footerTrailing = {
                if (onManageWatchlist != null) {
                    Text(
                        text = "MANAGE WATCHLIST",
                        color = tokens.colors.accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TerminalFontFamily,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier
                            .clickable {
                                dismiss()
                                onManageWatchlist()
                            }
                            .padding(tokens.spacing.xs),
                    )
                }
                Spacer(Modifier.width(tokens.spacing.xs))
                TerminalResizeGrip(
                    onDrag = { delta ->
                        val updated = listHeight + with(density) { delta.toDp() }
                        listHeight = maxListHeight
                            ?.let { max -> updated.coerceIn(EXPLORER_MIN_LIST_HEIGHT, max) }
                            ?: updated.coerceAtLeast(EXPLORER_MIN_LIST_HEIGHT)
                    },
                    orientation = TerminalResizeOrientation.VERTICAL,
                )
            },
        )
    }
}

/** Высота строки explorer-списка (фиксированная — для попапа). */
private val EXPLORER_ROW_HEIGHT = 46.dp

/** Минимальная высота списка при ресайзе (две строки). */
private val EXPLORER_MIN_LIST_HEIGHT = EXPLORER_ROW_HEIGHT * 2

/**
 * Хром попапа (табы + поиск + фильтры + шапка + футер с отступами), который
 * вычитается из высоты панели при ограничении ресайза списка.
 */
private val EXPLORER_POPUP_CHROME_HEIGHT = 180.dp

/**
 * Содержимое explorer-списка (общее для попапа [SymbolExplorerDropdown] и
 * панели Market Watch): табы (ALL + watchlists), поиск, фильтры
 * (perpetuals/quote/биржа), сортировка, ленивый список со статистикой 24ч и
 * звёздами (активный список или Favourites), футер. Состояние поднимает
 * владелец; [rowMenu] добавляет контекстное меню строки, [onListError] —
 * ошибки избранного.
 */
@Composable
fun SymbolExplorerContent(
    rows: List<SymbolExplorerRow>,
    currentSymbol: String,
    currentExchangeId: String?,
    activeListId: String?,
    onActiveListIdChange: (String?) -> Unit,
    perpetualsOnly: Boolean,
    onPerpetualsOnlyChange: (Boolean) -> Unit,
    quoteFilter: String?,
    onQuoteFilterChange: (String?) -> Unit,
    exchangeFilter: String?,
    onExchangeFilterChange: (String?) -> Unit,
    sort: SymbolExplorerSort,
    descending: Boolean,
    onSortChange: (SymbolExplorerSort, Boolean) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (SymbolExplorerRow) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    listWidth: Dp? = null,
    listHeight: Dp? = null,
    searchFocusRequester: FocusRequester = remember { FocusRequester() },
    tabsTrailing: (@Composable RowScope.() -> Unit)? = null,
    footerTrailing: (@Composable RowScope.() -> Unit)? = null,
    rowMenu: (@Composable (SymbolExplorerRow, dismiss: () -> Unit) -> Unit)? = null,
    onListError: ((String?) -> Unit)? = null,
) {
    val tokens = LocalTerminalDesignTokens.current
    val watchlistRepository = LocalSymbolWatchlistRepository.current
    val watchlists by (
        watchlistRepository?.lists ?: kotlinx.coroutines.flow.flowOf<List<SymbolWatchlist>>(emptyList())
        ).collectAsState(initial = emptyList())

    val activeList = activeListId?.let { id -> watchlists.firstOrNull { it.id == id } }
    val favouritesList = watchlists.favouritesWatchlist()
    val starSymbols: Set<String> = (activeList ?: favouritesList)?.symbols?.toSet().orEmpty()

    val scope = rememberCoroutineScope()
    val toggleStar: (String) -> Unit = { symbol ->
        val repo = watchlistRepository
        if (repo != null) {
            val list = activeList ?: favouritesList
            scope.launch {
                try {
                    val target = list
                        ?: runCatching { repo.create(FAVOURITES_LIST_NAME) }.getOrNull()
                        ?: repo.lists.value.favouritesWatchlist()
                        ?: return@launch
                    repo.toggleSymbol(target.id, symbol)
                    onListError?.invoke(null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onListError?.invoke(e.message)
                }
            }
        }
    }

    val reorderWatchlists: (List<String>) -> Unit = { ids ->
        val repo = watchlistRepository
        if (repo != null) {
            scope.launch {
                try {
                    repo.reorder(ids)
                    onListError?.invoke(null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onListError?.invoke(e.message)
                }
            }
        }
    }

    val quoteOptions = remember(rows) { explorerQuoteOptions(rows) }
    val exchangeOptions = remember(rows) { explorerExchangeOptions(rows) }
    val effectiveQuote = quoteFilter?.takeIf { it in quoteOptions }
    val effectiveExchange = exchangeFilter?.takeIf { id -> exchangeOptions.any { it.first == id } }

    val result = remember(
        rows, activeListId, watchlists, perpetualsOnly,
        effectiveQuote, effectiveExchange, query, sort, descending,
    ) {
        computeExplorerRows(
            rows = rows,
            activeListSymbols = activeList?.symbols?.toSet(),
            perpetualsOnly = perpetualsOnly,
            quote = effectiveQuote,
            exchangeId = effectiveExchange,
            query = query,
            sort = sort,
            descending = descending,
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (listHeight == null) Modifier.fillMaxSize() else Modifier)
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
        // ── Табы (ALL + Favourites + дропдаун списков) + хвост владельца ──
        ExplorerTabsRow(
            watchlists = watchlists,
            activeListId = activeListId,
            onActiveListIdChange = onActiveListIdChange,
            onReorderWatchlists = reorderWatchlists,
            trailing = tabsTrailing,
        )

        HorizontalDivider(color = tokens.colors.panelBorder, thickness = tokens.dimensions.borderWidth)

        // ── Поиск ──
        TerminalSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = "Search symbol, market or contract",
            focusRequester = searchFocusRequester,
            modifier = Modifier.padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
        )

        // ── Фильтры: perpetuals / quote / биржа + сортировка ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            ExplorerFilterChip(
                label = "PERPETUALS",
                active = perpetualsOnly,
                onClick = { onPerpetualsOnlyChange(!perpetualsOnly) },
            )
            ExplorerChipDropdown(
                label = quoteFilter?.takeIf { it in quoteOptions } ?: "ALL",
                selectedValue = quoteFilter?.takeIf { it in quoteOptions },
                options = buildList {
                    add("ALL" to (null as String?))
                    quoteOptions.forEach { add(it to it) }
                },
                onSelect = { onQuoteFilterChange(it) },
            )
            ExplorerChipDropdown(
                label = effectiveExchange?.let { id -> exchangeOptions.first { it.first == id }.second }
                    ?: "ALL EXCHANGES",
                selectedValue = effectiveExchange,
                options = buildList {
                    add("ALL EXCHANGES" to (null as String?))
                    exchangeOptions.forEach { (id, text) -> add(text to id) }
                },
                onSelect = { onExchangeFilterChange(it) },
            )
            Spacer(Modifier.weight(1f))
            ExplorerSortButton(
                sort = sort,
                descending = descending,
                onSortSelected = { selected ->
                    if (selected == sort) {
                        onSortChange(sort, !descending)
                    } else {
                        onSortChange(selected, selected != SymbolExplorerSort.SYMBOL)
                    }
                },
            )
        }

        HorizontalDivider(color = tokens.colors.panelBorder, thickness = tokens.dimensions.borderWidth)

        // ── Шапка списка ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "CONTRACT",
                color = tokens.colors.label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = TerminalFontFamily,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "LAST  24H",
                color = tokens.colors.label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = TerminalFontFamily,
                letterSpacing = 1.sp,
            )
        }

        // ── Список (ленивый; фиксированные ширина/высота нужны попапу) ──
        Box(
            modifier = Modifier
                .then(if (listWidth != null) Modifier.width(listWidth) else Modifier.fillMaxWidth())
                .then(if (listHeight != null) Modifier.height(listHeight) else Modifier.weight(1f)),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (rows.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(tokens.spacing.lg),
                                strokeWidth = tokens.dimensions.borderWidth * 2,
                            )
                        }
                    }
                }
                if (result.matches.isEmpty()) {
                    item {
                        Text(
                            text = result.error ?: "No symbols",
                            color = if (result.error != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            fontSize = 11.sp,
                            fontFamily = TerminalFontFamily,
                            modifier = Modifier.padding(tokens.spacing.lg),
                        )
                    }
                }
                items(
                    items = result.matches,
                    key = { "${it.row.exchangeId}:${it.row.symbol}" },
                ) { match ->
                    val row = match.row
                    val isCurrent = row.symbol == currentSymbol &&
                        (currentExchangeId == null || row.exchangeId == currentExchangeId)
                    ExplorerRowItem(
                        row = row,
                        matchRange = match.matchRange,
                        starred = row.symbol in starSymbols,
                        isCurrent = isCurrent,
                        rowHeight = EXPLORER_ROW_HEIGHT,
                        onToggleStar = { toggleStar(row.symbol) },
                        onClick = { onSelect(row) },
                        menuContent = rowMenu,
                    )
                }
            }

            ExplorerScrollTopFab(
                visible = listState.firstVisibleItemIndex > 0,
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = tokens.spacing.md, bottom = tokens.spacing.sm),
            )
        }

        HorizontalDivider(color = tokens.colors.panelBorder, thickness = tokens.dimensions.borderWidth)

        // ── Футер ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = explorerFooterText(result.matches.size, sort),
                color = tokens.colors.label,
                fontSize = 9.sp,
                fontFamily = TerminalFontFamily,
                letterSpacing = 0.5.sp,
                modifier = Modifier.weight(1f),
            )
            footerTrailing?.invoke(this)
        }
    }
}

/**
 * Кнопка прокрутки списка explorer в начало. Вынесена из
 * [SymbolExplorerContent] в отдельный composable: внутри `Column { Box { … } }`
 * вызов AnimatedVisibility разрешается в ColumnScope-перегрузку и не компилируется.
 */
@Composable
private fun ExplorerScrollTopFab(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalTerminalDesignTokens.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(tokens.colors.panelHeaderSurface)
                .border(tokens.dimensions.borderWidth, tokens.colors.panelBorder, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            ExplorerScrollTopGlyph(color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ExplorerScrollTopGlyph(color: Color) {
    Canvas(modifier = Modifier.size(12.dp)) {
        val stroke = 1.4.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(
            color,
            Offset(w * 0.5f, h),
            Offset(w * 0.5f, h * 0.15f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(w * 0.18f, h * 0.45f),
            Offset(w * 0.5f, h * 0.15f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(w * 0.82f, h * 0.45f),
            Offset(w * 0.5f, h * 0.15f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun ExplorerFilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    val tokens = LocalTerminalDesignTokens.current
    val shape = RoundedCornerShape(tokens.cornerRadii.smallCard)
    Text(
        text = label,
        color = if (active) tokens.colors.accent else tokens.colors.label,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = TerminalFontFamily,
        letterSpacing = 0.5.sp,
        maxLines = 1,
        modifier = Modifier
            .border(
                tokens.dimensions.borderWidth,
                if (active) tokens.colors.accent else tokens.colors.panelBorder,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
    )
}

@Composable
private fun <T> ExplorerChipDropdown(
    label: String,
    options: List<Pair<String, T?>>,
    selectedValue: T?,
    onSelect: (T?) -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    val shape = RoundedCornerShape(tokens.cornerRadii.smallCard)
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .border(tokens.dimensions.borderWidth, tokens.colors.panelBorder, shape)
                .clickable { expanded = true }
                .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
        ) {
            Text(
                text = label,
                color = tokens.colors.label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = TerminalFontFamily,
                letterSpacing = 0.5.sp,
                maxLines = 1,
            )
            Spacer(Modifier.width(tokens.spacing.xxs))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = tokens.colors.label,
                modifier = Modifier.size(12.dp),
            )
        }
        TerminalDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 120.dp),
        ) {
            options.forEach { (text, value) ->
                TerminalMenuItem(
                    text = text,
                    selected = value == selectedValue,
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ExplorerSortButton(
    sort: SymbolExplorerSort,
    descending: Boolean,
    onSortSelected: (SymbolExplorerSort) -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        TerminalSortGlyph(
            color = tokens.colors.label,
            modifier = Modifier.clickable { expanded = true },
        )
        TerminalDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 150.dp),
        ) {
            SymbolExplorerSort.entries.forEach { option ->
                val isActive = option == sort
                TerminalMenuItem(
                    text = option.label + if (isActive && descending) "  ↓" else if (isActive) "  ↑" else "",
                    selected = isActive,
                    onClick = {
                        onSortSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ExplorerRowItem(
    row: SymbolExplorerRow,
    matchRange: IntRange?,
    starred: Boolean,
    isCurrent: Boolean,
    rowHeight: androidx.compose.ui.unit.Dp,
    onToggleStar: () -> Unit,
    onClick: () -> Unit,
    menuContent: (@Composable (SymbolExplorerRow, dismiss: () -> Unit) -> Unit)? = null,
) {
    val tokens = LocalTerminalDesignTokens.current
    val formatter = remember(row.tickSize) {
        row.tickSize?.let { SymbolFormatter(it) } ?: SymbolFormatter.DEFAULT
    }
    var menuExpanded by remember(row.exchangeId, row.symbol) { mutableStateOf(false) }
    val subline = buildList {
        add(row.exchangeLabel)
        if (row.isPerpetual) add("PERP")
        row.quoteVolume24h?.let { add("VOL $" + SymbolFormatter.formatCompactVolume(it)) }
    }.joinToString(" · ")

    val rowModifier = Modifier
        .fillMaxWidth()
        .height(rowHeight)
        .background(if (isCurrent) tokens.colors.selection else Color.Transparent)
        .then(
            if (menuContent != null) {
                Modifier.pointerInput(row.exchangeId, row.symbol) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (
                                event.type == PointerEventType.Press &&
                                event.buttons.isSecondaryPressed
                            ) {
                                menuExpanded = true
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            } else {
                Modifier
            },
        )
        .clickable(onClick = onClick)

    Box {
        Row(
            modifier = rowModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(if (isCurrent) tokens.colors.accent else Color.Transparent),
            )
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .fillMaxHeight()
                    .clickable(onClick = onToggleStar),
                contentAlignment = Alignment.Center,
            ) {
                ExplorerStar(starred = starred)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = highlightedMatch(row.displaySymbol, matchRange, tokens.colors.accent),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFontFamily,
                    maxLines = 1,
                )
                if (subline.isNotEmpty()) {
                    Text(
                        text = subline,
                        color = tokens.colors.label,
                        fontSize = 9.sp,
                        fontFamily = TerminalFontFamily,
                        maxLines = 1,
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(horizontal = tokens.spacing.lg),
            ) {
                Text(
                    text = row.lastPrice?.let { formatter.formatPrice(it) } ?: "—",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontFamily = TerminalFontFamily,
                    maxLines = 1,
                )
                val change = row.changePercent24h
                if (change != null) {
                    Text(
                        text = (if (change >= 0) "+" else "-") + plainDecimalFixed(abs(change), 2) + "%",
                        color = if (change >= 0) tokens.colors.profit else tokens.colors.loss,
                        fontSize = 9.sp,
                        fontFamily = TerminalFontFamily,
                        maxLines = 1,
                    )
                }
            }
        }
        if (menuContent != null) {
            TerminalDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                Column(Modifier.padding(vertical = tokens.spacing.xs)) {
                    menuContent(row) { menuExpanded = false }
                }
            }
        }
    }
    HorizontalDivider(color = tokens.colors.panelBorder, thickness = tokens.dimensions.borderWidth)
}

@Composable
private fun ExplorerStar(starred: Boolean) {
    val tokens = LocalTerminalDesignTokens.current
    Canvas(modifier = Modifier.size(16.dp)) {
        val outer = size.minDimension * 0.5f
        val inner = outer * 0.44f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val path = Path()
        for (i in 0 until 10) {
            val angle = (-PI / 2 + i * PI / 5).toFloat()
            val radius = if (i % 2 == 0) outer else inner
            val x = cx + radius * cos(angle)
            val y = cy + radius * sin(angle)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        if (starred) {
            drawPath(path, color = tokens.colors.accent)
        } else {
            drawPath(path, color = tokens.colors.label, style = Stroke(1.2.dp.toPx()))
        }
    }
}
