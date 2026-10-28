/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

/**
 * Поля панели, которыми владеет внешний конфиг workspace (Market Watch
 * «Apply to current workspace»): символ и биржа. Локальное состояние панели
 * (layout, timeframe/chartMode/depth/sizeFilter и т.п.) при внешнем
 * обновлении не трогаем.
 */
fun externalPanelChanged(local: PanelConfig, remote: PanelConfig): Boolean =
    local.symbol != remote.symbol ||
        local.providerRef != remote.providerRef ||
        local.providerId != remote.providerId ||
        local.providerName != remote.providerName

/**
 * Переносит внешние (config-owned) поля панели в локальную копию.
 * Возвращает [local] без изменений, если внешних отличий нет.
 */
fun applyExternalPanelState(local: PanelConfig, remote: PanelConfig): PanelConfig =
    if (!externalPanelChanged(local, remote)) {
        local
    } else {
        local.copy(
            symbol = remote.symbol,
            providerRef = remote.providerRef,
            providerId = remote.providerId,
            providerName = remote.providerName,
        )
    }
