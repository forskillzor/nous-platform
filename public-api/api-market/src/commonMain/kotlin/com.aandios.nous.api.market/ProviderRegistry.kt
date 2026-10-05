/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market

/**
 * Реестр всех зарегистрированных (реально реализованных) провайдеров.
 *
 * Единый источник списка бирж для UI (chart/DOM/trades): дропдауны показывают
 * ТОЛЬКО провайдеров из реестра, а панель резолвит адаптеры выбранного
 * провайдера по его [Provider.providerId].
 */
class ProviderRegistry(
    providers: List<Provider>,
) {
    private val byId: Map<String, Provider> = providers.associateBy { it.providerId }

    /** Все зарегистрированные провайдеры. */
    val providers: List<Provider> get() = byId.values.toList()

    /** Провайдер по id (null — не зарегистрирован). */
    fun get(providerId: String): Provider? = byId[providerId]

    /** Отображаемое имя провайдера по id. */
    fun displayName(providerId: String): String =
        byId[providerId]?.config?.displayName ?: providerId

    /** id провайдера по отображаемому имени (для UI-дропдаунов). */
    fun idByDisplayName(displayName: String): String? =
        byId.values.firstOrNull { it.config.displayName == displayName }?.providerId

    /** Провайдер по умолчанию (первый зарегистрированный). */
    fun first(): Provider? = byId.values.firstOrNull()
}
