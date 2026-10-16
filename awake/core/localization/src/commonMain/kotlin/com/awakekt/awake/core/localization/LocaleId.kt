/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

/**
 * A locale named by its BCP 47 language tag, such as `en`, `ko`, `pt-BR` or `zh-Hant-TW`.
 *
 * The tag is normalized when it is made: `_` becomes `-`, the language is lower case, a four-letter
 * script is title case and a region is upper case, so `zh_hant_tw` and `zh-Hant-TW` are the same
 * locale and name the same string table, `strings/zh-Hant-TW.json`.
 *
 * It is not a full BCP 47 parser. Variants and extensions (`-u-…`, `-x-…`) are dropped, because a
 * string table is chosen by language, script and region only.
 *
 * @property tag The normalized tag.
 */
class LocaleId private constructor(val tag: String) {

    /** The language subtag, such as `en` or `zh`. */
    val language: String get() = tag.substringBefore('-')

    /**
     * The locales to try, most specific first: `zh-Hant-TW`, `zh-Hant`, `zh`.
     *
     * A lookup in this locale tries each in turn before it falls back to the default locale.
     */
    val fallbackChain: List<LocaleId>
        get() {
            val parts = tag.split('-')
            return (parts.size downTo 1).map { LocaleId(parts.subList(0, it).joinToString("-")) }
        }

    override fun equals(other: Any?): Boolean = other is LocaleId && other.tag == tag

    override fun hashCode(): Int = tag.hashCode()

    override fun toString(): String = tag

    companion object {
        /** English, the default locale a [Strings] falls back to unless it is given another. */
        val English: LocaleId = LocaleId("en")

        /**
         * Parses and normalizes [tag].
         *
         * @param tag A BCP 47 tag, with `-` or `_` between subtags.
         * @return The locale.
         * @throws IllegalArgumentException when [tag] has no two- or three-letter language subtag.
         */
        fun of(tag: String): LocaleId =
            requireNotNull(parseOrNull(tag)) { "Not a locale tag: \"$tag\". Expected a language such as \"en\" or \"pt-BR\"." }

        /**
         * Parses and normalizes [tag], or returns null when it names no language, as an empty system
         * setting or the POSIX `C` locale does.
         *
         * @param tag A BCP 47 tag, with `-` or `_` between subtags.
         * @return The locale, or null.
         */
        fun parseOrNull(tag: String): LocaleId? {
            // A POSIX locale may carry a codeset or modifier, "en_US.UTF-8@euro"; neither names a table.
            val subtags = tag.trim().substringBefore('.').substringBefore('@').split('-', '_').filter { it.isNotEmpty() }
            val language = subtags.firstOrNull()?.lowercase() ?: return null
            if (language.length !in 2..3 || !language.all { it in 'a'..'z' }) return null
            val normalized = StringBuilder(language)
            var index = 1
            subtags.getOrNull(index)?.takeIf { it.length == 4 && it.all(Char::isLetter) }?.let {
                normalized.append('-').append(it.lowercase().replaceFirstChar(Char::uppercaseChar))
                index++
            }
            subtags.getOrNull(index)?.takeIf { isRegion(it) }?.let { normalized.append('-').append(it.uppercase()) }
            return LocaleId(normalized.toString())
        }

        private fun isRegion(subtag: String): Boolean =
            (subtag.length == 2 && subtag.all(Char::isLetter)) || (subtag.length == 3 && subtag.all(Char::isDigit))
    }
}
