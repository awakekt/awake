/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.text.font.UiFonts
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LocaleFontsTest {

    private val tables = StringTableSource.of(
        mapOf(
            LocaleId.English to mapOf("hello" to "Hello"),
            LocaleId.of("ru") to mapOf("hello" to "Привет"),
            LocaleId.of("ko") to mapOf("hello" to "안녕하세요"),
        ),
    )

    private suspend fun assets(): AssetSource {
        val roboto = readResourceBytes("Roboto-Regular.ttf")
        return AssetSource { path: AssetPath ->
            if (path.value == "fonts/Roboto-Regular.ttf") Result.success(roboto) else Result.failure(NoSuchElementException(path.value))
        }
    }

    private val fonts = LocaleFonts(
        mapOf(
            LocaleId.of("ru") to listOf("fonts/Missing.ttf", "fonts/Roboto-Regular.ttf"),
            LocaleId.of("ko") to listOf("fonts/Roboto-Regular.ttf"),
        ),
    )

    @Test
    fun aLocaleWhoseScriptThePrimaryLacksGetsAFallback() = runTest {
        val primary = UiFonts.trueSans()
        val strings = Strings.load(tables, locale = LocaleId.of("ru"))
        val font = strings.loadFont(assets(), fonts, primary, extraChars = "Ж")
        assertFalse(primary.hasGlyph('П'))
        assertTrue(font.hasGlyph('П'))
        assertTrue(font.hasGlyph('Ж'), "extra chars are baked too")
        assertFalse(font.hasGlyph('Щ'), "only the chars the text needs are baked")
        assertTrue(font.hasGlyph('H'))
    }

    @Test
    fun aLocaleWithNoFontsOrNoMissingCharsKeepsThePrimary() = runTest {
        val primary = UiFonts.trueSans()
        assertSame(primary, Strings.load(tables, locale = LocaleId.English).loadFont(assets(), fonts, primary))
        assertSame(primary, Strings.load(tables, locale = LocaleId.of("de")).loadFont(assets(), fonts, primary))
    }

    @Test
    fun aFontThatDrawsNoneOfTheTextIsNotUsed() = runTest {
        val primary = UiFonts.trueSans()
        // Roboto has no Hangul.
        val font = Strings.load(tables, locale = LocaleId.of("ko")).loadFont(assets(), fonts, primary)
        assertSame(primary, font)
    }

    @Test
    fun theMostSpecificLocaleThatNamesFontsWins() {
        val byLocale = LocaleFonts(
            mapOf(
                LocaleId.of("zh") to listOf("fonts/sc.otf"),
                LocaleId.of("zh-Hant") to listOf("fonts/tc.otf"),
            ),
        )
        assertEquals(listOf("fonts/tc.otf"), byLocale.pathsFor(LocaleId.of("zh-Hant-TW")))
        assertEquals(listOf("fonts/sc.otf"), byLocale.pathsFor(LocaleId.of("zh-CN")))
        assertEquals(emptyList(), byLocale.pathsFor(LocaleId.of("ja")))
    }

    @Test
    fun theFileFormDecodes() {
        val decoded = LocaleFonts.decode("""{ "ko": ["fonts/kr.otf"], "zh_Hant": ["fonts/tc.otf", "fonts/sc.otf"] }""".encodeToByteArray())
        assertEquals(listOf("fonts/kr.otf"), decoded.pathsFor(LocaleId.of("ko-KR")))
        assertEquals(listOf("fonts/tc.otf", "fonts/sc.otf"), decoded.pathsFor(LocaleId.of("zh-Hant")))
        assertFailsWith<StringTableException> { LocaleFonts.decode("""{ "ko": "fonts/kr.otf" }""".encodeToByteArray()) }
        assertFailsWith<StringTableException> { LocaleFonts.decode("""{ "1": ["a.otf"] }""".encodeToByteArray()) }
    }
}
