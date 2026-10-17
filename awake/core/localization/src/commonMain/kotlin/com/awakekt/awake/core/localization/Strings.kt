/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.core.text.unicode.CodePoints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * A project's text, by key, in the active locale.
 *
 * Game data, UI and plugins store keys, such as `quest.harbor.title`, and ask for the text here:
 *
 * ```kotlin
 * val strings = Strings.load(StringTableSource.assets(project.assets), locale = settings.locale ?: systemLocale())
 * strings.text("inventory.count", "count" to 3)   // "3 items"
 * strings.setLocale(LocaleId.of("ko"))              // later, from a settings screen
 * ```
 *
 * A key is looked up in the active locale's table, then in its parents' (`pt-BR`, then `pt`), then
 * in the default locale's. A key no table has shows as the key itself and is logged once; nothing
 * here throws for missing or broken text. Text is a [MessageFormat] pattern, formatted with the
 * plural rules, ordinal rules and [NumberFormat] of the locale the text came from.
 *
 * Text comes in [StringLayer]s: the game's tables, given to [load], over defaults a plugin or library
 * adds with [addSource], over Core's built-in text. Each locale's table is the layers merged key by
 * key, the higher layer winning, so a game overrides one plugin key and keeps the rest. The locale
 * comes first: a plugin's Korean text beats the game's English for a Korean player, and the game's
 * English beats the plugin's English.
 *
 * Tables load per locale when that locale is used, so a project with many languages reads only the
 * active one and the default. A message is parsed the first time it is shown and kept.
 *
 * [text] is meant for the frame loop's thread, like [com.awakekt.awake.core.logging.Log];
 * [setLocale] may run on any coroutine and publishes the new tables in one step.
 *
 * @property defaultLocale The locale every lookup falls back to.
 */
class Strings internal constructor(
    source: StringTableSource,
    val defaultLocale: LocaleId,
    private val pluralRules: (LocaleId) -> PluralRule,
    private val ordinalRules: (LocaleId) -> PluralRule,
    initialLocale: LocaleId,
) {
    private val switchLock = Mutex()

    /** Every source, the one that wins first. Replaced whole, under [switchLock]. */
    private var sources: List<Source> = listOf(Source(source, StringLayer.Game))

    /** The locale whose missing table was last logged, so a reload of the same locale says it once. */
    private var warnedMissing: LocaleId? = null

    @Volatile
    private var chain: Chain = Chain(initialLocale, emptyList())

    private val localeState = MutableStateFlow(initialLocale)

    /**
     * The active locale. It changes once the new locale's tables are loaded, so UI that reads text
     * when it changes gets the new text, never keys or a mix.
     */
    val locale: StateFlow<LocaleId> = localeState.asStateFlow()

    /** The locales whose tables a lookup reads, in order: the active locale's chain, then the default's. */
    val resolvedLocales: List<LocaleId> get() = chain.tables.map { it.locale }

    /**
     * Switches to [locale], loading its tables first. Text read after this returns is in [locale].
     *
     * A locale with no table of its own is not an error: lookups fall back to its parents and the
     * default locale, and the missing table is logged once.
     *
     * @param locale The locale to show text in.
     */
    suspend fun setLocale(locale: LocaleId) {
        switchLock.withLock { switchTo(locale) }
    }

    /**
     * Adds [source] as a layer of text under or over the others, and reloads the active locale's
     * tables, so text read after this returns has it.
     *
     * A plugin or library adds its own default text this way, usually its English and any
     * translations it ships, at [StringLayer.Plugin]; the game's own tables, at [StringLayer.Game],
     * override any of its keys. Within one layer, a source added later wins. Adding a source that is
     * already here does nothing.
     *
     * @param source The tables.
     * @param layer Which layer the tables belong to.
     */
    suspend fun addSource(source: StringTableSource, layer: StringLayer = StringLayer.Plugin) {
        switchLock.withLock {
            if (sources.none { it.source === source }) {
                // A stable sort, so within a layer the newest source stays first.
                sources = (listOf(Source(source, layer)) + sources).sortedBy { it.layer.ordinal }
                switchTo(localeState.value)
            }
        }
    }

    /**
     * Removes a source [addSource] added, or the one [load] was given, and reloads the active
     * locale's tables without it.
     *
     * @param source The tables, the same instance that was added.
     * @return Whether it was here.
     */
    suspend fun removeSource(source: StringTableSource): Boolean = switchLock.withLock {
        val kept = sources.filter { it.source !== source }
        val removed = kept.size != sources.size
        if (removed) {
            sources = kept
            switchTo(localeState.value)
        }
        removed
    }

    /** Loads [locale]'s chain from every source and publishes it. Runs under [switchLock]. */
    private suspend fun switchTo(locale: LocaleId) {
        val wanted = (locale.fallbackChain + defaultLocale.fallbackChain).distinct()
        val tables = wanted.mapNotNull { tag ->
            // Every source's table for exactly this locale, merged key by key with the winning source's last.
            val found = sources.mapNotNull { it.tableFor(tag) }
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

    /**
     * The text for [key] in the active locale, with [arguments] put into its placeholders.
     *
     * @param key The key, as the string tables and game data name it.
     * @param arguments Values for the text's `{name}`, `plural` and `select` placeholders.
     * @return The text, or [key] itself when no table in the chain has it or it cannot be formatted.
     */
    fun text(key: String, arguments: Map<String, Any?> = emptyMap()): String {
        val current = chain
        var found: Table? = null
        var raw: String? = null
        for (table in current.tables) {
            raw = table.text[key]
            if (raw != null) {
                found = table
                break
            }
        }
        if (found == null || raw == null) {
            current.report(key) { "No text for \"$key\" in ${current.locale} or the default locale $defaultLocale." }
            return key
        }
        return current.format(found, key, raw, arguments)
    }

    /**
     * The text for [key] with [arguments] given as pairs: `text("greeting", "name" to player.name)`.
     *
     * @see text
     */
    fun text(key: String, vararg arguments: Pair<String, Any?>): String = text(key, arguments.toMap())

    /**
     * Whether any table in the active chain has [key].
     *
     * @param key The key.
     * @return True when [text] would find it.
     */
    fun contains(key: String): Boolean = chain.tables.any { key in it.text }

    /**
     * The text of the first of [keys] found, trying each key in a locale's table before the next
     * locale's, or null, unlogged, when no table has any. [InputLabels] reads a key's platform form
     * before its plain form this way, so a parent locale's plain form never beats the player's own
     * locale's platform form.
     */
    internal fun textOfFirst(keys: Array<String>): String? {
        val current = chain
        for (table in current.tables) {
            for (key in keys) {
                val raw = table.text[key] ?: continue
                return current.format(table, key, raw, emptyMap())
            }
        }
        return null
    }

    /**
     * Every char the text of the active locale's tables uses, the default locale's included, so a
     * font can be baked with just the glyphs this text needs.
     *
     * @return The chars.
     */
    fun characters(): Set<Char> {
        val chars = HashSet<Char>()
        for (table in chain.tables) {
            for (text in table.text.values) text.forEach { chars += it }
        }
        return chars
    }

    /**
     * Every Unicode code point the text of the active locale's tables uses, the default locale's
     * included. Unlike [characters], an emoji or a CJK Extension B ideograph is one code point here
     * rather than two halves of a surrogate pair, so a font can be found and baked for it.
     *
     * @return The code points.
     */
    fun codePoints(): Set<Int> {
        val codePoints = HashSet<Int>()
        for (table in chain.tables) {
            for (text in table.text.values) CodePoints.forEach(text) { _, codePoint -> codePoints += codePoint }
        }
        return codePoints
    }

    /** One layer's tables, with those loaded so far that the chain still uses; null marks a locale with none. */
    private class Source(val source: StringTableSource, val layer: StringLayer) {
        val loaded = HashMap<LocaleId, Map<String, String>?>()

        suspend fun tableFor(locale: LocaleId): Map<String, String>? {
            if (locale in loaded) return loaded[locale]
            // A table that can't be read is a StringTableException, an IllegalArgumentException, as is a
            // kotlinx.serialization failure in a custom source.
            val table = try {
                source.load(locale)
            } catch (e: IllegalArgumentException) {
                log.error(e) { "String table for $locale could not be read: ${e.message}" }
                null
            }
            loaded[locale] = table
            return table
        }
    }

    /** One locale's tables, swapped in whole by [setLocale] and [addSource]. Each logs a problem once while it is active. */
    private class Chain(val locale: LocaleId, val tables: List<Table>) {
        private val reported = HashSet<String>()

        inline fun report(id: String, message: () -> String) {
            if (reported.add(id)) log.warn(message)
        }

        /** [raw], the text of [key] in [table], formatted with [arguments]; [key] when it can't be. */
        fun format(table: Table, key: String, raw: String, arguments: Map<String, Any?>): String {
            val message = if (MessageFormat.isPlain(raw)) null else table.message(key, raw)
            return when {
                MessageFormat.isPlain(raw) -> raw
                message == null -> {
                    report("$key@${table.locale}") { "Text \"$key\" in ${table.locale} is not a valid message: ${table.errors[key]}" }
                    key
                }
                else -> message.format(arguments, table.plurals, table.ordinals, table.numbers) { problem ->
                    report("$key@${table.locale}:$problem") { "Text \"$key\" in ${table.locale}: $problem." }
                }
            }
        }
    }

    private class Table(val locale: LocaleId, val text: Map<String, String>, val plurals: PluralRule, val ordinals: PluralRule) {
        val numbers: NumberFormat = NumberFormat.forLocale(locale)
        private val parsed = HashMap<String, MessageFormat>()
        val errors = HashMap<String, String>()

        fun message(key: String, raw: String): MessageFormat? =
            parsed[key] ?: if (key in errors) null else parse(key, raw)

        private fun parse(key: String, raw: String): MessageFormat? = try {
            MessageFormat.parse(raw).also { parsed[key] = it }
        } catch (e: MessageSyntaxException) {
            errors[key] = e.message.orEmpty()
            null
        }
    }

    companion object {
        private val log = Logger("Strings")

        /**
         * Loads the tables for [locale] and the default locale, and returns the strings.
         *
         * @param source Where the tables come from, usually [StringTableSource.assets].
         * @param locale The locale to start in: the player's setting if there is one, else [systemLocale].
         * @param defaultLocale The locale every lookup falls back to; the tables' source language.
         * @param pluralRules The plural rule of each locale; [PluralRules.forLocale] unless a language
         *   needs one it does not cover.
         * @param ordinalRules The ordinal rule of each locale, for `selectordinal`; [OrdinalRules.forLocale]
         *   unless a language needs one it does not cover.
         * @return The strings, in [locale].
         */
        suspend fun load(
            source: StringTableSource,
            locale: LocaleId = systemLocale(),
            defaultLocale: LocaleId = LocaleId.English,
            pluralRules: (LocaleId) -> PluralRule = PluralRules::forLocale,
            ordinalRules: (LocaleId) -> PluralRule = OrdinalRules::forLocale,
        ): Strings = Strings(source, defaultLocale, pluralRules, ordinalRules, locale).also { it.setLocale(locale) }
    }
}
