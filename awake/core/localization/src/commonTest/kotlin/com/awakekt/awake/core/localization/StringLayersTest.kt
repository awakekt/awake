/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.LogRecord
import com.awakekt.awake.core.logging.LogSink
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StringLayersTest {

    private val records = mutableListOf<LogRecord>()
    private val sink = LogSink { records += it }
    private val warnings get() = records.filter { it.level >= LogLevel.Warn }

    @BeforeTest
    fun installSink() {
        Log.reset()
        Log.install(sink)
    }

    @AfterTest
    fun removeSink() {
        Log.reset()
    }

    private val ko = LocaleId.of("ko")

    private val game = StringTableSource.of(
        mapOf(
            LocaleId.English to mapOf("menu.start" to "Set Sail", "hud.hp" to "Vigor"),
            ko to mapOf("menu.start" to "출항"),
        ),
    )

    private val plugin = StringTableSource.of(
        mapOf(
            LocaleId.English to mapOf("hud.hp" to "HP", "hud.mp" to "MP", "hud.level" to "Level {n}"),
            ko to mapOf("hud.mp" to "마나", "hud.level" to "레벨 {n}"),
        ),
    )

    private val builtIn = StringTableSource.of(
        mapOf(LocaleId.English to mapOf("hud.mp" to "Mana", "hud.xp" to "XP", "menu.start" to "Start")),
    )

    private suspend fun layered(locale: LocaleId): Strings =
        Strings.load(game, locale = locale).apply {
            addSource(builtIn, StringLayer.BuiltIn)
            addSource(plugin, StringLayer.Plugin)
        }

    @Test
    fun theGameOverridesAPluginWhichOverridesBuiltIns() = runTest {
        val strings = layered(LocaleId.English)
        assertEquals("Vigor", strings.text("hud.hp"), "the game's key wins over the plugin's")
        assertEquals("MP", strings.text("hud.mp"), "the plugin's key wins over the built-in")
        assertEquals("XP", strings.text("hud.xp"), "a key only the built-ins have still shows")
        assertEquals("Set Sail", strings.text("menu.start"), "the game's key wins over the built-in")
        assertEquals("Level 3", strings.text("hud.level", "n" to 3))
    }

    @Test
    fun theOrderTheSourcesAreAddedInDoesNotMatterAcrossLayers() = runTest {
        val strings = Strings.load(game, locale = LocaleId.English).apply {
            addSource(plugin, StringLayer.Plugin)
            addSource(builtIn, StringLayer.BuiltIn)
        }
        assertEquals("Vigor", strings.text("hud.hp"))
        assertEquals("MP", strings.text("hud.mp"))
    }

    @Test
    fun theLocaleComesBeforeTheLayer() = runTest {
        val strings = layered(ko)
        assertEquals("출항", strings.text("menu.start"))
        assertEquals("마나", strings.text("hud.mp"), "the plugin's Korean beats every layer's English")
        assertEquals("레벨 3", strings.text("hud.level", "n" to 3))
        assertEquals("Vigor", strings.text("hud.hp"), "no layer has it in Korean: the game's English")
        assertEquals("XP", strings.text("hud.xp"), "only the built-ins have it, in English")
        assertEquals(listOf("ko", "en"), strings.resolvedLocales.map { it.tag })
    }

    @Test
    fun aPluginTranslationAloneServesItsLocale() = runTest {
        val onlyEnglishGame = StringTableSource.of(mapOf(LocaleId.English to mapOf("menu.start" to "Set Sail")))
        val strings = Strings.load(onlyEnglishGame, locale = ko).apply { addSource(plugin) }
        assertEquals("마나", strings.text("hud.mp"))
        assertEquals("Set Sail", strings.text("menu.start"))
        // The game itself has no Korean, which is still worth saying once, but not again per source added.
        assertEquals(1, warnings.count { "No string table for ko" in it.message })
    }

    @Test
    fun withinALayerTheSourceAddedLaterWins() = runTest {
        val first = StringTableSource.of(mapOf(LocaleId.English to mapOf("hud.hp" to "Health", "hud.ap" to "AP")))
        val second = StringTableSource.of(mapOf(LocaleId.English to mapOf("hud.hp" to "Hit points")))
        val strings = Strings.load(StringTableSource.of(emptyMap()), locale = LocaleId.English).apply {
            addSource(first)
            addSource(second)
        }
        assertEquals("Hit points", strings.text("hud.hp"))
        assertEquals("AP", strings.text("hud.ap"))
    }

    @Test
    fun removingASourceTakesItsKeysAway() = runTest {
        val strings = layered(LocaleId.English)
        assertTrue(strings.removeSource(plugin))
        assertFalse(strings.removeSource(plugin), "already gone")
        assertEquals("Mana", strings.text("hud.mp"), "the built-in shows through")
        assertFalse(strings.contains("hud.level"))
    }

    @Test
    fun addingASourceTwiceReadsItOnce() = runTest {
        val asked = mutableListOf<String>()
        val counting = StringTableSource { locale ->
            asked += locale.tag
            if (locale == LocaleId.English) mapOf("hud.hp" to "HP") else null
        }
        val strings = Strings.load(game, locale = LocaleId.English)
        strings.addSource(counting)
        strings.addSource(counting)
        assertEquals(listOf("en"), asked)
        strings.setLocale(ko)
        assertEquals(listOf("en", "ko"), asked, "en is still loaded; only ko is read")
    }

    @Test
    fun aLocaleSwitchKeepsEveryLayer() = runTest {
        val strings = layered(LocaleId.English)
        assertEquals("MP", strings.text("hud.mp"))
        strings.setLocale(ko)
        assertEquals("마나", strings.text("hud.mp"))
        strings.setLocale(LocaleId.English)
        assertEquals("MP", strings.text("hud.mp"))
    }

    @Test
    fun pluralsFollowTheLocaleOfTheMergedTable() = runTest {
        val ru = LocaleId.of("ru")
        val counts = StringTableSource.of(
            mapOf(ru to mapOf("items" to "{n, plural, one {# предмет} few {# предмета} many {# предметов} other {# предмета}}")),
        )
        val strings = Strings.load(game, locale = ru).apply { addSource(counts) }
        assertEquals("21 предмет", strings.text("items", "n" to 21))
        assertEquals("5 предметов", strings.text("items", "n" to 5))
    }
}
