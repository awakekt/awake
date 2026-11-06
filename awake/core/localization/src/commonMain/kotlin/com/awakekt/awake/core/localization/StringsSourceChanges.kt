/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

import com.awakekt.awake.core.localization.Strings.Chain
import com.awakekt.awake.core.localization.Strings.Table
import com.awakekt.awake.core.logging.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.withLock

// How a [Strings]' sources change: every change runs under its switch lock, and a source
// [Strings.removeSourceSoon] queues is applied by whoever holds the lock next, so removing one needs no
// coroutine. Removing reads no table: the tables the other sources hold for the active locale are
// loaded, so the chain is republished from them.

/** Runs [change] under [Strings.switchLock], with the removals asked for so far applied first and any asked for meanwhile after. */
internal suspend fun <T> Strings.changing(change: suspend () -> T): T {
    val result = switchLock.withLock {
        applyRemovals()
        change()
    }
    applyRemovalsIfFree()
    return result
}

/**
 * Applies the pending removals while nothing else holds [Strings.switchLock]. Whoever holds it calls this
 * once it lets go, after a removal is queued, so none is left behind.
 */
// isEmpty is read only to decide whether to take the lock again; a stale answer costs one more try.
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Strings.applyRemovalsIfFree() {
    while (!removals.isEmpty && switchLock.tryLock()) {
        try {
            applyRemovals()
        } finally {
            switchLock.unlock()
        }
    }
}

/** Takes out every source queued by [Strings.removeSourceSoon] and republishes the active locale. Runs under [Strings.switchLock]. */
private fun Strings.applyRemovals() {
    var removed = false
    while (true) {
        val source = removals.tryReceive().getOrNull() ?: break
        if (drop(source)) removed = true
    }
    if (removed) publish(localeState.value)
}

internal fun Strings.drop(source: StringTableSource): Boolean {
    val kept = sources.filter { it.source !== source }
    val removed = kept.size != sources.size
    sources = kept
    return removed
}

/** Loads [locale]'s chain from every source and publishes it. Runs under [Strings.switchLock]. */
internal suspend fun Strings.switchTo(locale: LocaleId) {
    for (tag in wantedFor(locale)) sources.forEach { it.tableFor(tag) }
    publish(locale)
}

private fun Strings.wantedFor(locale: LocaleId): List<LocaleId> = (locale.fallbackChain + defaultLocale.fallbackChain).distinct()

/**
 * Publishes [locale]'s chain from the tables the sources have loaded, which [switchTo] reads first;
 * the active locale's are loaded already. Runs under [Strings.switchLock].
 */
internal fun Strings.publish(locale: LocaleId) {
    val wanted = wantedFor(locale)
    val tables = wanted.mapNotNull { tag ->
        // Every source's table for exactly this locale, merged key by key with the winning source's last.
        val found = sources.mapNotNull { it.loaded[tag] }
        when (found.size) {
            0 -> null
            1 -> Table(tag, found.single(), pluralRules(tag), ordinalRules(tag))
            else -> {
                val merged = HashMap<String, String>(found.sumOf { it.size } * 2)
                for (i in found.indices.reversed()) merged.putAll(found[i])
                Table(tag, merged, pluralRules(tag), ordinalRules(tag))
            }
        }
    }
    // Defaults a plugin or Core ships say nothing about whether the game itself is translated.
    val gameLocales = wanted.filter { tag -> sources.any { it.layer == StringLayer.Game && it.loaded[tag] != null } }
    // Said once per locale switched to, not again when a source is added or removed.
    if (locale.fallbackChain.none { it in gameLocales } && warnedMissing != locale) {
        log.warn { "No string table for $locale; showing ${gameLocales.firstOrNull() ?: "keys"} instead." }
        warnedMissing = locale
    } else if (locale != warnedMissing) {
        warnedMissing = null
    }
    if (tables.isEmpty()) log.error { "No string table for $locale or the default locale $defaultLocale; text shows as keys." }
    // Only the tables in use stay in memory; switching back reads the file again.
    val inUse = wanted.toSet()
    sources.forEach { it.loaded.keys.retainAll(inUse) }
    chain = Chain(locale, tables)
    localeState.value = locale
}

private val log = Logger("Strings")
