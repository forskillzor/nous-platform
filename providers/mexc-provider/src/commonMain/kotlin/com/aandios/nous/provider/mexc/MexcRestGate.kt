/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc

import io.ktor.client.plugins.ResponseException
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Гейт REST-запросов к MEXC: весовой бюджет (минута), очередь и
 * дедупликация одинаковых запросов в полёте. При 429 ставит cooldown
 * (Retry-After, дефолт 30 с) и ретраит запрос изнутри.
 *
 * Почему: несколько панелей одновременно шлют одинаковые/соседние запросы
 * (kline, contract detail, ticker) — MEXC Contract API лимитирует 20 req/2s
 * на endpoint, суммарно легко пробить лимит при открытии workspace
 * с N панелями.
 */
class MexcRestGate(
    private val maxWeightPerMinute: Int = 2000,
) {
    companion object {
        private const val DEFAULT_COOLDOWN_MS = 30_000L
        private const val MAX_ATTEMPTS = 3
    }

    private val mutex = Mutex()
    private var available = maxWeightPerMinute.toDouble()
    private var lastRefill = currentTimeMillis()

    @Volatile
    private var cooldownUntil = 0L

    private val inFlight = mutableMapOf<String, CompletableDeferred<Any?>>()

    /**
     * Выполняет запрос под контролем гейта.
     * @param key ключ дедупликации (endpoint + параметры)
     * @param weight вес запроса (условно по лимитам MEXC)
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> execute(key: String, weight: Int = 1, block: suspend () -> T): T {
        val existing = mutex.withLock { inFlight[key] }
        if (existing != null) {
            return existing.await() as T
        }

        val deferred = CompletableDeferred<Any?>()
        mutex.withLock { inFlight[key] = deferred }

        var result: Any? = null
        var failure: Throwable? = null
        try {
            var attempts = 0
            while (attempts < MAX_ATTEMPTS) {
                waitCooldown()
                acquire(weight)
                try {
                    result = block()
                    break
                } catch (e: ResponseException) {
                    val status = e.response.status.value
                    if (status == 418 || status == 429) {
                        val retryAfter = e.response.headers["Retry-After"]?.toLongOrNull()
                        markCooldown(retryAfter?.times(1000) ?: DEFAULT_COOLDOWN_MS)
                        println("⏳ MexcRestGate: HTTP $status, cooldown ${cooldownUntil - currentTimeMillis()}ms (попытка ${attempts + 1})")
                        attempts++
                    } else {
                        throw e
                    }
                }
            }
            if (result == null && failure == null) {
                failure = IllegalStateException("MexcRestGate: лимит ретраев исчерпан для $key")
            }
        } catch (t: Throwable) {
            failure = t
        } finally {
            mutex.withLock { inFlight.remove(key) }
        }

        if (failure != null) {
            deferred.completeExceptionally(failure)
            throw failure
        }
        deferred.complete(result)
        return result as T
    }

    /** Берет [weight] весов из минутного бюджета; при нехватке — ждёт пополнения. */
    private suspend fun acquire(weight: Int) = mutex.withLock {
        while (true) {
            val now = currentTimeMillis()
            val elapsedSec = (now - lastRefill) / 1000.0
            if (elapsedSec > 0) {
                available = (available + elapsedSec * maxWeightPerMinute / 60.0)
                    .coerceAtMost(maxWeightPerMinute.toDouble())
                lastRefill = now
            }
            if (available >= weight) {
                available -= weight
                return@withLock
            }
            val needSec = (weight - available) / (maxWeightPerMinute / 60.0)
            delay((needSec * 1000).toLong().coerceAtLeast(50))
        }
    }

    private fun markCooldown(ms: Long) {
        val target = currentTimeMillis() + ms
        if (target > cooldownUntil) cooldownUntil = target
    }

    private suspend fun waitCooldown() {
        while (true) {
            val remaining = cooldownUntil - currentTimeMillis()
            if (remaining <= 0) return
            delay(remaining.coerceAtLeast(50))
        }
    }
}
