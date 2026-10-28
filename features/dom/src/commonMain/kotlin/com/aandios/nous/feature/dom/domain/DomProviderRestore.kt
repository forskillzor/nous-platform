/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.domain

/**
 * Выбирает провайдера DOM-панели при [attachPanel].
 *
 * [preferred] — PanelConfig.providerId (уже проверен в реестре),
 * [stored] — сохранённый `dom_provider_{workspace}_{panel}` (или legacy).
 *
 * Конфиг панели главный, но до исправления гонки в него мог попасть
 * дефолтный Binance раньше, чем завершалось восстановление. Поэтому только
 * на ПЕРВОМ attach: если preferred — это дефолтный провайдер, а в panel-store
 * лежит другой, доверяем store (лечим stale-значение). После первого attach
 * конфиг снова главный — runtime Apply-to-workspace не блокируется.
 */
internal fun resolveRestoredProvider(
    preferred: String?,
    stored: String?,
    defaultProvider: String,
    firstAttach: Boolean,
): String? {
    if (preferred == null) return stored
    if (stored == null || stored == preferred) return preferred
    return if (firstAttach && preferred == defaultProvider) stored else preferred
}
