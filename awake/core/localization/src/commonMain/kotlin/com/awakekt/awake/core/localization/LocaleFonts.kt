/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The font files each locale draws with when the UI font lacks its script: a Hangul font for `ko`,
 * a Cyrillic one for `ru`, a Thai one for `th`.
 *
 * A locale uses the fonts of the most specific locale in its [LocaleId.fallbackChain] that names
 * any, so `zh-Hant` can name a Traditional Chinese font while `zh` names a Simplified one.
 *
 * The file form, kept beside the string tables as `strings/fonts.json`, maps tags to project paths:
 *
 * ```json
 * { "ko": ["fonts/NotoSansKR-Regular.otf"], "ru": ["fonts/NotoSans-Regular.ttf"] }
 * ```
 */
class LocaleFonts(fonts: Map<LocaleId, List<String>>) {
    private val fonts: Map<LocaleId, List<String>> = fonts.filterValues { it.isNotEmpty() }

    /**
     * The font files [locale] draws with, in the order to try them.
     *
     * @param locale The locale.
     * @return The paths, or an empty list when no locale in its chain names fonts.
     */
    fun pathsFor(locale: LocaleId): List<String> =
        locale.fallbackChain.firstNotNullOfOrNull { fonts[it] }.orEmpty()

    companion object {
        /** No locale names fonts. */
        val None: LocaleFonts = LocaleFonts(emptyMap())

        /** Where a project keeps its locale fonts file, beside its string tables. */
        const val DEFAULT_PATH: String = "strings/fonts.json"

        private val json = Json { isLenient = false }

        /**
         * Decodes the file form: a JSON object of locale tags to arrays of font paths.
         *
         * @param bytes The file, UTF-8.
         * @return The fonts.
         * @throws StringTableException when the file is not that shape.
         */
        fun decode(bytes: ByteArray): LocaleFonts {
            val root = try {
                json.parseToJsonElement(bytes.decodeToString())
            } catch (e: IllegalArgumentException) {
                throw StringTableException("Locale fonts are not valid JSON: ${e.message}", e)
            }
            if (root !is JsonObject) throw StringTableException("Locale fonts must be a JSON object of locale tags to font paths.")
            return LocaleFonts(
                root.entries.associate { (tag, value) ->
                    val locale = LocaleId.parseOrNull(tag) ?: throw StringTableException("Locale fonts: \"$tag\" is not a locale tag.")
                    val paths = (value as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                    if (paths == null || paths.any { it == null }) throw StringTableException("Locale fonts: \"$tag\" must be an array of font paths.")
                    locale to paths.filterNotNull()
                },
            )
        }

        /**
         * Reads the file form from project assets.
         *
         * @param assets The project's assets.
         * @param path The file, [DEFAULT_PATH] unless the project keeps it elsewhere.
         * @return The fonts, or [None] when the project has no such file.
         * @throws StringTableException when the file exists but is not that shape.
         */
        suspend fun load(assets: AssetSource, path: String = DEFAULT_PATH): LocaleFonts =
            assets.read(AssetPath(path)).getOrNull()?.let(::decode) ?: None
    }
}

private val fontLog = Logger("Strings")

/**
 * The UI font for the active locale: [primary], followed by the locale's fonts from [fonts] for every
 * char of its text that [primary] lacks.
 *
 * Each fallback font is baked with only the chars the loaded tables use and [primary] cannot draw, so
 * a Korean table of a few thousand distinct syllables bakes a few thousand glyphs, not the font's
 * eleven thousand. Text from elsewhere, such as a player's name, can draw chars no table holds; pass
 * them as [extraChars].
 *
 * Baking takes real time, tens of milliseconds for a hundred glyphs and seconds for thousands, so call
 * this off the frame thread when the locale changes, keep the result, and hand it to the UI, for
 * example as Compose's `LocalFont`. A font file that cannot be read or baked is logged and skipped.
 *
 * Text is drawn a `Char` at a time, without shaping: a script whose letters join or stack, such as
 * Thai's combining marks or Arabic, draws its letters but not their joined forms.
 *
 * @param assets Where the font files are read from.
 * @param fonts Each locale's font files.
 * @param primary The font to try first; its metrics lay out the text.
 * @param extraChars Chars to bake beyond those in the string tables.
 * @return [primary] itself when it already draws every char, else the fallback chain.
 */
suspend fun Strings.loadFont(
    assets: AssetSource,
    fonts: LocaleFonts,
    primary: UiFont = UiFonts.default(),
    extraChars: String = "",
): UiFont {
    val paths = fonts.pathsFor(locale.value)
    if (paths.isEmpty()) return primary
    var missing = (characters() + extraChars.asIterable())
        .filter { !it.isWhitespace() && !it.isISOControl() && !primary.hasGlyph(it) }
        .toSet()
    val fallbacks = mutableListOf<UiFont>()
    for (path in paths) {
        if (missing.isEmpty()) break
        val font = try {
            val bytes = assets.read(AssetPath(path)).getOrThrow()
            UiFonts.load(bytes, path, chars = missing.joinToString(""), cellSize = primary.cellSize)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fontLog.error(e) { "Font $path for ${locale.value} could not be loaded: ${e.message}" }
            continue
        }
        if (missing.none(font::hasGlyph)) {
            fontLog.warn { "Font $path for ${locale.value} draws none of the chars its text needs; it is not used." }
            continue
        }
        fallbacks += font
        missing = missing.filterTo(HashSet()) { !font.hasGlyph(it) }
    }
    if (missing.isNotEmpty()) {
        fontLog.warn { "No font for ${locale.value} draws ${missing.size} chars of its text, such as '${missing.first()}'." }
    }
    return UiFonts.fallback(primary, fallbacks)
}
