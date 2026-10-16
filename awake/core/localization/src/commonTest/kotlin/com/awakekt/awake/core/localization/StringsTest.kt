/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
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

class StringsTest {

    private val records = mutableListOf<LogRecord>()
    private val sink = LogSink { records += it }

    @BeforeTest
    fun installSink() {
        Log.reset()
        Log.install(sink)
    }

    @AfterTest
    fun removeSink() {
        Log.reset()
    }

    private val tables = mapOf(
        LocaleId.English to mapOf(
            "menu.start" to "Start",
            "menu.quit" to "Quit",
            "inventory.count" to "{count, plural, one {# item} other {# items}}",
            "greeting" to "Hello, {name}!",
            "broken" to "Hello, {name",
        ),
        LocaleId.of("ko") to mapOf(
            "menu.start" to "시작",
            "inventory.count" to "아이템 {count}개",
        ),
        LocaleId.of("pt") to mapOf("menu.start" to "Começar"),
    )

    private val warnings get() = records.filter { it.level >= LogLevel.Warn }

    @Test
    fun theActiveLocaleIsReadFirstThenTheDefault() = runTest {
        val strings = Strings.load(StringTableSource.of(tables), locale = LocaleId.of("ko"))
        assertEquals("시작", strings.text("menu.start"))
        assertEquals("Quit", strings.text("menu.quit"))
        assertEquals("아이템 3개", strings.text("inventory.count", "count" to 3))
    }

    @Test
    fun aRegionalLocaleFallsBackToItsLanguage() = runTest {
        val strings = Strings.load(StringTableSource.of(tables), locale = LocaleId.of("pt-BR"))
        assertEquals("Começar", strings.text("menu.start"))
        assertEquals(listOf("pt", "en"), strings.resolvedLocales.map { it.tag })
        assertTrue(warnings.isEmpty(), "a parent table is enough: $warnings")
    }

    @Test
    fun aMissingKeyShowsTheKeyAndLogsOnce() = runTest {
        val strings = Strings.load(StringTableSource.of(tables), locale = LocaleId.English)
        assertEquals("quest.missing.title", strings.text("quest.missing.title"))
        assertEquals("quest.missing.title", strings.text("quest.missing.title"))
        assertEquals(1, warnings.size)
        assertTrue("quest.missing.title" in warnings.single().message)
        assertFalse(strings.contains("quest.missing.title"))
    }

    @Test
    fun aMissingLocaleFallsBackToTheDefaultAndLogsOnce() = runTest {
        val strings = Strings.load(StringTableSource.of(tables), locale = LocaleId.of("de"))
        assertEquals("de", strings.locale.value.tag)
        assertEquals("Start", strings.text("menu.start"))
        assertEquals("Start", strings.text("menu.start"))
        assertEquals(listOf("en"), strings.resolvedLocales.map { it.tag })
        assertEquals(1, warnings.size)
        assertTrue("de" in warnings.single().message)
    }

    @Test
    fun noTableAtAllShowsKeys() = runTest {
        val strings = Strings.load(StringTableSource.of(emptyMap()), locale = LocaleId.of("ko"))
        assertEquals("menu.start", strings.text("menu.start"))
    }

    @Test
    fun pluralsFollowTheLocaleTheTextCameFrom() = runTest {
        val strings = Strings.load(StringTableSource.of(tables), locale = LocaleId.of("ru"))
        // No Russian table: the English text is formatted with English plurals, so 21 is "items".
        assertEquals("21 items", strings.text("inventory.count", "count" to 21))
        assertEquals("1 item", strings.text("inventory.count", "count" to 1))
    }

    @Test
    fun aBrokenMessageShowsTheKeyAndLogsOnce() = runTest {
        val strings = Strings.load(StringTableSource.of(tables), locale = LocaleId.English)
        assertEquals("broken", strings.text("broken", "name" to "x"))
        assertEquals("broken", strings.text("broken", "name" to "x"))
        assertEquals(1, warnings.size)
    }

    @Test
    fun placeholderValuesAreShownAsTheyAre() = runTest {
        val strings = Strings.load(StringTableSource.of(tables), locale = LocaleId.English)
        assertEquals("Hello, {name}!", strings.text("greeting", "name" to "{name}"))
        assertEquals("Hello, {name}!", strings.text("greeting"))
    }

    @Test
    fun switchingLocalePublishesTheNewLocaleAfterItsTablesLoad() = runTest {
        val strings = Strings.load(StringTableSource.of(tables), locale = LocaleId.English)
        assertEquals("Start", strings.text("menu.start"))
        strings.setLocale(LocaleId.of("ko"))
        assertEquals("ko", strings.locale.value.tag)
        assertEquals("시작", strings.text("menu.start"))
        strings.setLocale(LocaleId.English)
        assertEquals("en", strings.locale.value.tag)
        assertEquals("Start", strings.text("menu.start"))
    }

    @Test
    fun onlyTheLocalesInUseAreLoaded() = runTest {
        val asked = mutableListOf<String>()
        val source = StringTableSource { locale ->
            asked += locale.tag
            tables[locale]
        }
        val strings = Strings.load(source, locale = LocaleId.of("ko-KR"))
        assertEquals(listOf("ko-KR", "ko", "en"), asked)
        strings.setLocale(LocaleId.of("ko"))
        assertEquals(listOf("ko-KR", "ko", "en"), asked, "ko and en are already loaded")
    }

    @Test
    fun tablesLoadFromProjectAssets() = runTest {
        val files = mapOf(
            "strings/en.json" to """{ "menu": { "start": "Start" }, "title": "Harbor Town" }""",
            "strings/ko.json" to """{ "menu.start": "시작" }""",
        )
        val assets = AssetSource { path: AssetPath ->
            files[path.value]?.let { Result.success(it.encodeToByteArray()) } ?: Result.failure(NoSuchElementException(path.value))
        }
        val strings = Strings.load(StringTableSource.assets(assets), locale = LocaleId.of("ko"))
        assertEquals("시작", strings.text("menu.start"))
        assertEquals("Harbor Town", strings.text("title"))
    }

    @Test
    fun anUnreadableTableIsLoggedAndSkipped() = runTest {
        val assets = AssetSource { path: AssetPath ->
            when (path.value) {
                "strings/en.json" -> Result.success("""{ "menu.start": "Start" }""".encodeToByteArray())
                "strings/ko.json" -> Result.success("""{ "menu.start": 3 }""".encodeToByteArray())
                else -> Result.failure(NoSuchElementException(path.value))
            }
        }
        val strings = Strings.load(StringTableSource.assets(assets), locale = LocaleId.of("ko"))
        assertEquals("Start", strings.text("menu.start"))
        assertTrue(records.any { it.level == LogLevel.Error && "ko" in it.message })
    }
}
