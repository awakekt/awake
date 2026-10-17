package com.awakekt.awake.template

import com.awakekt.awake.core.localization.LocaleId
import com.awakekt.awake.core.localization.StringTableSource
import com.awakekt.awake.core.localization.Strings
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The bundled project's string tables: each locale has every key, and the overlay's text switches. */
class StringsTest {
    private val english = LocaleId.English
    private val filipino = LocaleId.of("fil")

    private suspend fun load(locale: LocaleId) = Strings.load(StringTableSource.assets(BundledProject), locale = locale)

    @Test
    fun everyLocaleHasEveryKey() = runBlocking {
        val keys = listOf("hud.title", "hud.hint", "hud.items", "hud.addItem", "hud.language", "hud.switchTo")
        for ((locale, _) in Locales) {
            val strings = load(locale)
            assertEquals(listOf(locale), strings.resolvedLocales.take(1), "no table for $locale")
            for (key in keys) assertTrue(strings.contains(key), "$locale has no $key")
        }
    }

    @Test
    fun theTextSwitchesLocaleAtRuntime() = runBlocking {
        val strings = load(english)
        assertEquals("1 item", strings.text("hud.items", "count" to 1))
        assertEquals("3 items", strings.text("hud.items", "count" to 3))
        assertEquals("Switch to Filipino", strings.text("hud.switchTo", "language" to "Filipino"))

        strings.setLocale(filipino)
        assertEquals("1 aytem", strings.text("hud.items", "count" to 1))
        assertEquals("3 aytem", strings.text("hud.items", "count" to 3))
        assertEquals("Lumipat sa English", strings.text("hud.switchTo", "language" to "English"))
    }
}
