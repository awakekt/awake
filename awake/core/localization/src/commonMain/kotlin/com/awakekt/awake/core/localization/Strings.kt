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
 * Tables load per locale when that locale is used, so a project with many languages reads only the
 * active one and the default. A message is parsed the first time it is shown and kept.
 *
 * [text] is meant for the frame loop's thread, like [com.awakekt.awake.core.logging.Log];
 * [setLocale] may run on any coroutine and publishes the new tables in one step.
 *
 * @property defaultLocale The locale every lookup falls back to.
 */
class Strings internal constructor(
    private val source: StringTableSource,
    val defaultLocale: LocaleId,
    private val pluralRules: (LocaleId) -> PluralRule,
    private val ordinalRules: (LocaleId) -> PluralRule,
    initialLocale: LocaleId,
) {
    private val switchLock = Mutex()

    /** Every table loaded so far that the current chain still uses; null marks a locale with none. */
    private val loaded = HashMap<LocaleId, Table?>()

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
        switchLock.withLock {
            val wanted = (locale.fallbackChain + defaultLocale.fallbackChain).distinct()
            val tables = wanted.mapNotNull { tableFor(it) }
            if (locale.fallbackChain.none { loaded[it] != null }) {
                log.warn { "No string table for $locale; showing ${tables.firstOrNull()?.locale ?: "keys"} instead." }
            }
            if (tables.isEmpty()) log.error { "No string table for $locale or the default locale $defaultLocale; text shows as keys." }
            // Only the tables in use stay in memory; switching back reads the file again.
            loaded.keys.retainAll(wanted.toSet())
            chain = Chain(locale, tables)
            localeState.value = locale
        }
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
        return format(current, found, key, raw, arguments)
    }

    private fun format(current: Chain, table: Table, key: String, raw: String, arguments: Map<String, Any?>): String {
        val message = if (MessageFormat.isPlain(raw)) null else table.message(key, raw)
        return when {
            MessageFormat.isPlain(raw) -> raw
            message == null -> {
                current.report("$key@${table.locale}") { "Text \"$key\" in ${table.locale} is not a valid message: ${table.errors[key]}" }
                key
            }
            else -> message.format(arguments, table.plurals, table.ordinals, table.numbers) { problem ->
                current.report("$key@${table.locale}:$problem") { "Text \"$key\" in ${table.locale}: $problem." }
            }
        }
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

    private suspend fun tableFor(locale: LocaleId): Table? {
        if (locale in loaded) return loaded[locale]
        // A table that can't be read is a StringTableException, an IllegalArgumentException, as is a
        // kotlinx.serialization failure in a custom source.
        val table = try {
            source.load(locale)?.let { Table(locale, it, pluralRules(locale), ordinalRules(locale)) }
        } catch (e: IllegalArgumentException) {
            log.error(e) { "String table for $locale could not be read: ${e.message}" }
            null
        }
        loaded[locale] = table
        return table
    }

    /** One locale's tables, swapped in whole by [setLocale]. Each logs a problem once while it is active. */
    private class Chain(val locale: LocaleId, val tables: List<Table>) {
        private val reported = HashSet<String>()

        inline fun report(id: String, message: () -> String) {
            if (reported.add(id)) log.warn(message)
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
