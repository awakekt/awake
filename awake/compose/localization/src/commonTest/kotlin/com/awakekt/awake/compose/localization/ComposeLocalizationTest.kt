/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.localization

import com.awakekt.awake.compose.runtime.Applier
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.RememberHolder
import com.awakekt.awake.compose.runtime.Slot
import com.awakekt.awake.compose.runtime.reconcile
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.KeyChord
import com.awakekt.awake.core.input.KeyModifier
import com.awakekt.awake.core.localization.InputLabels
import com.awakekt.awake.core.localization.LocaleId
import com.awakekt.awake.core.localization.ShortcutStyle
import com.awakekt.awake.core.localization.StringLayer
import com.awakekt.awake.core.localization.StringTableSource
import com.awakekt.awake.core.localization.Strings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ComposeLocalizationTest {

    private class TestNode : RememberHolder {
        private val slots = mutableListOf<Any?>()
        override val rememberSlots: List<Any?> get() = slots
    }

    private class TestApplier : Applier {
        override fun childCount(slot: Slot) = 0
        override fun typeAt(slot: Slot, index: Int): Any? = null
        override fun keyAt(slot: Slot, index: Int): Any? = null
        override fun nodeAt(slot: Slot, index: Int): Any = Unit
        override fun createAt(slot: Slot, index: Int, type: Any, key: Any?): Any = Unit
        override fun moveTo(slot: Slot, from: Int, to: Int) = Unit
        override fun truncateFrom(slot: Slot, index: Int) = Unit
        override fun down(slot: Slot, index: Int) = Unit
        override fun up() = Unit
    }

    private val root = TestNode()

    /** One frame: Awake Compose composes the whole tree each one. */
    private fun frame(content: context(Composer) () -> Unit) = reconcile(TestApplier(), root, content)

    private val fil = LocaleId.of("fil")
    private val game = StringTableSource.of(
        mapOf(
            LocaleId.English to mapOf("menu.start" to "Start", "hud.items" to "{n, plural, one {# item} other {# items}}"),
            fil to mapOf("menu.start" to "Simulan", "hud.items" to "{n} gamit"),
        ),
    )

    @Test
    fun textChangesOnTheFrameAfterALocaleSwitch() = runTest {
        val strings = Strings.load(game, locale = LocaleId.English)
        val shown = mutableListOf<String>()
        val hud: context(Composer)
        () -> Unit = {
            ProvideStrings(strings) {
                shown += stringResource("menu.start")
                shown += stringResource("hud.items", "n" to 2)
                shown += stringResource("hud.items", mapOf("n" to 1))
            }
        }
        frame(hud)
        assertEquals(listOf("Start", "2 items", "1 item"), shown)

        strings.setLocale(fil)
        shown.clear()
        frame(hud)
        assertEquals(listOf("Simulan", "2 gamit", "1 gamit"), shown)
    }

    @Test
    fun aPluginsDefaultsShowUntilTheGameOverridesThem() = runTest {
        val plugin = StringTableSource.of(mapOf(LocaleId.English to mapOf("hud.hp" to "HP", "menu.start" to "Begin")))
        val strings = Strings.load(game, locale = LocaleId.English).apply { addSource(plugin, StringLayer.Plugin) }
        var hp = ""
        var start = ""
        frame {
            ProvideStrings(strings) {
                hp = stringResource("hud.hp")
                start = stringResource("menu.start")
            }
        }
        assertEquals("HP", hp)
        assertEquals("Start", start)
    }

    @Test
    fun inputLabelsFollowTheLocaleToo() = runTest {
        val strings = Strings.load(game, locale = LocaleId.English)
        val labels = InputLabels.load(strings, ShortcutStyle.Windows)
        var save = ""
        val prompt: context(Composer)
        () -> Unit = {
            ProvideStrings(strings, labels) { save = inputLabels().chord(KeyChord(Key.S, setOf(KeyModifier.Ctrl, KeyModifier.Shift))) }
        }
        frame(prompt)
        assertEquals("Ctrl+Shift+S", save)
        strings.setLocale(LocaleId.of("de"))
        frame(prompt)
        assertEquals("Strg+Umschalt+S", save)
    }

    @Test
    fun withNoStringsTheKeyShows() {
        var text = ""
        frame { text = stringResource("menu.start") }
        assertEquals("menu.start", text)
    }

    @Test
    fun inputLabelsWithoutAProviderSayWhatIsMissing() {
        assertFailsWith<IllegalStateException> { frame { inputLabels() } }
    }
}
