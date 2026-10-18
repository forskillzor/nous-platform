/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.core.market.MarketOverviewRow
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.core.ui.format.plainDecimalFixed
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily
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
 * Одиночное совпадение поиска по списку тикеров: символ + диапазон
 * подсветки в нём (используется Market Watch).
 */
data class SymbolSearchMatch(
    val symbol: String,
    val matchRange: IntRange?,
)

data class SymbolSearchResult(
    val matches: List<SymbolSearchMatch>,
    val error: String? = null,
)

/** Поиск по списку тикеров (Market Watch): релевантность → код символа. */
fun searchSymbols(symbols: List<String>, query: String): SymbolSearchResult {
    if (query.isBlank()) return SymbolSearchResult(symbols.map { SymbolSearchMatch(it, null) })
    val regex = when (val compiled = compileSearchRegex(query)) {
        is SearchRegexResult.Ok -> compiled.regex
        is SearchRegexResult.Invalid -> return SymbolSearchResult(emptyList(), compiled.message)
    }

    val matches = symbols.mapNotNull { symbol ->
        matchSymbol(displaySymbol = symbol, metaFields = emptyList(), regex = regex)
            ?.let { symbol to it }
    }.sortedWith(
        Comparator<Pair<String, SymbolMatch>> { a, b -> symbolMatchComparator.compare(a.second, b.second) }
            .thenBy { it.first },
    )
    return SymbolSearchResult(matches.map { SymbolSearchMatch(it.first, it.second.matchRange) })
}

/**
 * Расширенный дропдаун выбора символа (тайтл chart, заголовки DOM/Trades):
 * табы (ALL + watchlists), поиск, фильтры (perpetuals/quote/биржа), список со
 * статистикой 24ч и избранным. Выбор строки другой биржи переключает
 * провайдера панели (зовите [onSelect] и обрабатывайте кросс-биржу выше).
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
    val watchlistRepository = LocalSymbolWatchlistRepository.current
    val watchlists by (
        watchlistRepository?.lists ?: kotlinx.coroutines.flow.flowOf<List<SymbolWatchlist>>(emptyList())
        ).collectAsState(initial = emptyList())

    var expanded by remember { mutableStateOf(false) }
    var activeListId by remember { mutableStateOf<String?>(null) }
    var perpetualsOnly by remember { mutableStateOf(true) }
    var quoteFilter by remember { mutableStateOf<String?>("USDT") }
    var exchangeFilter by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(SymbolExplorerSort.VOLUME) }
    var descending by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }

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

    val activeList = activeListId?.let { id -> watchlists.firstOrNull { it.id == id } }
    val favouritesList = watchlists.firstOrNull { it.name.equals("Favourites", ignoreCase = true) }
    val starSymbols: Set<String> = (activeList ?: favouritesList)?.symbols?.toSet().orEmpty()

    val scope = rememberCoroutineScope()
    val toggleStar: (String) -> Unit = { symbol ->
        val repo = watchlistRepository
        if (repo != null) {
            val list = activeList ?: favouritesList
            scope.launch {
                val target = list ?: runCatching { repo.create("Favourites") }.getOrNull()
                    ?: repo.lists.value.firstOrNull { it.name.equals("Favourites", ignoreCase = true) }
                    ?: return@launch
                runCatching { repo.toggleSymbol(target.id, symbol) }
            }
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
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.K &&
                        (event.isCtrlPressed || event.isMetaPressed)
                    ) {
                        runCatching { searchFocus.requestFocus() }
                        true
                    } else {
                        false
                    }
                },
        ) {
            // ── Табы (ALL + watchlists) + закрыть ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            ) {
                ExplorerTab("ALL", active = activeListId == null) { activeListId = null }
                watchlists.forEach { list ->
                    ExplorerTab(
                        label = list.name.uppercase(),
                        active = activeListId == list.id,
                    ) { activeListId = list.id }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "✕",
                    color = tokens.colors.label,
                    fontSize = 12.sp,
                    fontFamily = TerminalFontFamily,
                    modifier = Modifier
                        .clickable { dismiss() }
                        .padding(tokens.spacing.xs),
                )
            }

            HorizontalDivider(color = tokens.colors.panelBorder, thickness = tokens.dimensions.borderWidth)

            // ── Поиск ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm)
                    .height(26.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(tokens.cornerRadii.smallCard),
                    )
                    .border(
                        tokens.dimensions.borderWidth,
                        tokens.colors.panelBorder,
                        RoundedCornerShape(tokens.cornerRadii.smallCard),
                    )
                    .padding(horizontal = tokens.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(modifier = Modifier.size(12.dp)) {
                    val stroke = 1.2.dp.toPx()
                    val radius = size.minDimension * 0.32f
                    val center = Offset(size.width * 0.4f, size.height * 0.4f)
                    val color = tokens.colors.label
                    drawCircle(
                        color = color,
                        radius = radius,
                        center = center,
                        style = Stroke(stroke),
                    )
                    drawLine(
                        color = color,
                        start = Offset(center.x + radius * 0.7f, center.y + radius * 0.7f),
                        end = Offset(size.width * 0.92f, size.height * 0.92f),
                        strokeWidth = stroke,
                    )
                }
                Spacer(Modifier.width(tokens.spacing.sm))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search symbol, market or contract",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            fontSize = 11.sp,
                            fontFamily = TerminalFontFamily,
                            maxLines = 1,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontFamily = TerminalFontFamily,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFocus),
                    )
                }
                Spacer(Modifier.width(tokens.spacing.sm))
                Text(
                    text = "⌘ K",
                    color = tokens.colors.label,
                    fontSize = 9.sp,
                    fontFamily = TerminalFontFamily,
                    modifier = Modifier
                        .background(
                            tokens.colors.panelHeaderSurface,
                            RoundedCornerShape(3.dp),
                        )
                        .border(
                            tokens.dimensions.borderWidth,
                            tokens.colors.panelBorder,
                            RoundedCornerShape(3.dp),
                        )
                        .padding(horizontal = tokens.spacing.xs, vertical = 1.dp),
                )
            }

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
                    onClick = { perpetualsOnly = !perpetualsOnly },
                )
                ExplorerChipDropdown(
                    label = quoteFilter?.takeIf { it in quoteOptions } ?: "ALL",
                    options = buildList {
                        add("ALL" to (null as String?))
                        quoteOptions.forEach { add(it to it) }
                    },
                    onSelect = { quoteFilter = it },
                )
                ExplorerChipDropdown(
                    label = effectiveExchange?.let { id -> exchangeOptions.first { it.first == id }.second }
                        ?: "ALL EXCHANGES",
                    options = buildList {
                        add("ALL EXCHANGES" to (null as String?))
                        exchangeOptions.forEach { (id, text) -> add(text to id) }
                    },
                    onSelect = { exchangeFilter = it },
                )
                Spacer(Modifier.weight(1f))
                ExplorerSortButton(
                    sort = sort,
                    descending = descending,
                    onSortSelected = { selected ->
                        if (selected == sort) descending = !descending else {
                            sort = selected
                            descending = selected != SymbolExplorerSort.SYMBOL
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

            // ── Список (5 строк со скроллом, ленивый) ──
            // Fixed width is required: M3 DropdownMenu measures its content with
            // IntrinsicSize.Max, and a SubcomposeLayout can only answer intrinsic
            // queries when it has a specified size (SizeNode short-circuit).
            val rowHeight = 46.dp
            LazyColumn(
                modifier = Modifier
                    .width(menuWidth)
                    .height(rowHeight * 5),
            ) {
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
                        rowHeight = rowHeight,
                        onToggleStar = { toggleStar(row.symbol) },
                        onClick = {
                            onSelect(row)
                            dismiss()
                        },
                    )
                }
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
            }
        }
    }
}

@Composable
private fun ExplorerTab(label: String, active: Boolean, onClick: () -> Unit) {
    val tokens = LocalTerminalDesignTokens.current
    Text(
        text = label,
        color = if (active) MaterialTheme.colorScheme.surface else tokens.colors.label,
        fontSize = 10.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        fontFamily = TerminalFontFamily,
        letterSpacing = 0.5.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.cornerRadii.smallCard))
            .background(if (active) tokens.colors.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
    )
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
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 120.dp),
            shape = RoundedCornerShape(tokens.cornerRadii.workspacePanel),
            containerColor = tokens.colors.panelSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(tokens.dimensions.borderWidth, tokens.colors.panelBorder),
        ) {
            options.forEach { (text, value) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = text,
                            color = if (value == null && label == text) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontFamily = TerminalFontFamily,
                            maxLines = 1,
                        )
                    },
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
        Canvas(
            modifier = Modifier
                .size(20.dp)
                .clickable { expanded = true },
        ) {
            val stroke = 1.2.dp.toPx()
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * 0.08f, h * 0.12f)
                lineTo(w * 0.92f, h * 0.12f)
                lineTo(w * 0.58f, h * 0.55f)
                lineTo(w * 0.58f, h * 0.9f)
                lineTo(w * 0.42f, h * 0.9f)
                lineTo(w * 0.42f, h * 0.55f)
                close()
            }
            drawPath(path, color = tokens.colors.label, style = Stroke(stroke))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 150.dp),
            shape = RoundedCornerShape(tokens.cornerRadii.workspacePanel),
            containerColor = tokens.colors.panelSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(tokens.dimensions.borderWidth, tokens.colors.panelBorder),
        ) {
            SymbolExplorerSort.entries.forEach { option ->
                val isActive = option == sort
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label + if (isActive && descending) "  ↓" else if (isActive) "  ↑" else "",
                            color = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontFamily = TerminalFontFamily,
                            maxLines = 1,
                        )
                    },
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
) {
    val tokens = LocalTerminalDesignTokens.current
    val formatter = remember(row.tickSize) {
        row.tickSize?.let { SymbolFormatter(it) } ?: SymbolFormatter.DEFAULT
    }
    val subline = buildList {
        add(row.exchangeLabel)
        if (row.isPerpetual) add("PERP")
        row.quoteVolume24h?.let { add("VOL $" + SymbolFormatter.formatCompactVolume(it)) }
    }.joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .background(if (isCurrent) tokens.colors.selection else Color.Transparent)
            .clickable(onClick = onClick),
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
                text = highlightedSymbol(row.displaySymbol, matchRange, tokens.colors.accent),
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
    HorizontalDivider(color = tokens.colors.panelBorder, thickness = tokens.dimensions.borderWidth)
}

/** Подсветка совпавшего фрагмента символа цветом поиска (как в Market Watch). */
fun highlightedSymbol(symbol: String, matchRange: IntRange?, highlightColor: Color) = buildAnnotatedString {
    val range = matchRange
    if (range == null || range.isEmpty() || range.first !in symbol.indices) {
        append(symbol)
        return@buildAnnotatedString
    }
    val start = range.first.coerceIn(0, symbol.length)
    val endExclusive = (range.last + 1).coerceIn(start, symbol.length)
    append(symbol.substring(0, start))
    pushStyle(
        SpanStyle(
            background = highlightColor.copy(alpha = 0.24f),
            color = highlightColor,
            fontWeight = FontWeight.Bold,
        )
    )
    append(symbol.substring(start, endExclusive))
    pop()
    append(symbol.substring(endExclusive))
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
