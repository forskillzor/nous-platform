/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

/** Компиляция поискового regex (регистронезависимо) для explorer, indicators и navigator. */
sealed interface SearchRegexResult {
    data class Ok(val regex: Regex) : SearchRegexResult
    data class Invalid(val message: String) : SearchRegexResult
}

fun compileSearchRegex(query: String): SearchRegexResult = try {
    SearchRegexResult.Ok(Regex(query, RegexOption.IGNORE_CASE))
} catch (e: IllegalArgumentException) {
    SearchRegexResult.Invalid(e.message ?: "Invalid regular expression")
}

/** Поля кандидата: [primary] подсвечивается, [meta] участвует в поиске без подсветки. */
data class SearchFields(
    val primary: String,
    val meta: List<String> = emptyList(),
)

enum class MatchQuality(val rank: Int) {
    EXACT(0),
    PREFIX(1),
    SUBSTRING(2),
    EMPTY(3),
    META(4),
}

/**
 * Матч кандидата: [range] — диапазон подсветки в primary (null — совпало
 * meta-поле или regex совпал с пустой строкой); [quality]/[position]/[matchLength]
 * — ключи релевантности.
 */
data class SearchMatch(
    val range: IntRange?,
    val quality: MatchQuality,
    val position: Int,
    val matchLength: Int,
)

data class SearchHit<T>(
    val item: T,
    val match: SearchMatch?,
)

data class SearchOutcome<T>(
    val hits: List<SearchHit<T>>,
    val error: String? = null,
)

/**
 * Единый matcher поиска: сначала primary — с диапазоном подсветки и качеством
 * (точное → префикс → подстрока → пустой матч), затем meta-поля без подсветки.
 * null — кандидат не совпал.
 */
fun matchSearchFields(
    primary: String,
    regex: Regex,
    metaFields: List<String> = emptyList(),
): SearchMatch? {
    val primaryMatch = regex.find(primary)
    if (primaryMatch != null) {
        val range = primaryMatch.range.takeIf { primaryMatch.value.isNotEmpty() }
        val quality = when {
            range == null -> MatchQuality.EMPTY
            range.first == 0 && primaryMatch.value.length == primary.length -> MatchQuality.EXACT
            range.first == 0 -> MatchQuality.PREFIX
            else -> MatchQuality.SUBSTRING
        }
        return SearchMatch(
            range = range,
            quality = quality,
            position = range?.first ?: Int.MAX_VALUE,
            matchLength = if (range == null) Int.MAX_VALUE else range.last - range.first,
        )
    }
    return metaFields.firstNotNullOfOrNull { regex.find(it) }?.let {
        SearchMatch(
            range = null,
            quality = MatchQuality.META,
            position = Int.MAX_VALUE,
            matchLength = Int.MAX_VALUE,
        )
    }
}

/** Порядок релевантности: качество → позиция → длина (меньше — лучше). */
val searchMatchComparator: Comparator<SearchMatch> =
    compareBy({ it.quality.rank }, { it.position }, { it.matchLength })

/** Компаратор совпадений; элементы без матча идут последними. */
fun <T> searchHitComparator(): Comparator<SearchHit<T>> =
    Comparator { a, b ->
        val left = a.match
        val right = b.match
        when {
            left == null && right == null -> 0
            left == null -> 1
            right == null -> -1
            else -> searchMatchComparator.compare(left, right)
        }
    }

/**
 * Ищет [items] по [query]. Пустой запрос возвращает все элементы без матчей,
 * невалидный regex — пустой результат с [SearchOutcome.error].
 */
fun <T> searchItems(
    items: List<T>,
    query: String,
    fields: (T) -> SearchFields,
): SearchOutcome<T> {
    if (query.isBlank()) {
        return SearchOutcome(items.map { SearchHit(it, null) })
    }
    val regex = when (val compiled = compileSearchRegex(query)) {
        is SearchRegexResult.Ok -> compiled.regex
        is SearchRegexResult.Invalid -> return SearchOutcome(emptyList(), compiled.message)
    }
    return SearchOutcome(searchItems(items, regex, fields))
}

/** Вариант с уже скомпилированным regex — для поиска по частям дерева. */
fun <T> searchItems(
    items: List<T>,
    regex: Regex,
    fields: (T) -> SearchFields,
): List<SearchHit<T>> = items.mapNotNull { item ->
    val values = fields(item)
    val match = matchSearchFields(values.primary, regex, values.meta) ?: return@mapNotNull null
    SearchHit(item, match)
}
