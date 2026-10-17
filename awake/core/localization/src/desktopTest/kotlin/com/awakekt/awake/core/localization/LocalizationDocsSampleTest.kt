/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

import com.awakekt.awake.core.input.GamepadButton
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.KeyChord
import com.awakekt.awake.core.input.KeyModifier
import com.awakekt.awake.core.input.PointerButton
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** The samples in `website/docs/guides/localization.md`, run against the snippet tables it shows. */
class LocalizationDocsSampleTest {

    /** The snippet folder stands in for a project root holding `strings/`. */
    private val project = AssetSource { path ->
        runCatching { File("../../../website/docs/snippets/localization", path.value).readBytes() }
    }

    @Test
    fun loadAndLookUp() = runTest {
        val assets = project
        // --8<-- [start:load]
        val strings = Strings.load(
            StringTableSource.assets(assets), // reads strings/<locale>.json
            locale = LocaleId.of("ko"), // the player's setting, or systemLocale()
        )
        // --8<-- [end:load]

        // --8<-- [start:text]
        strings.text("menu.start") // "시작"
        strings.text("greeting", "name" to "Mira") // "다시 오신 것을 환영합니다, Mira님!"
        strings.text("party", "count" to 3, "friend" to "Ren") // not in ko.json: the English text
        strings.text("quest.harbor.reward") // no table has it: "quest.harbor.reward", logged once
        // --8<-- [end:text]

        assertEquals("시작", strings.text("menu.start"))
        assertEquals("다시 오신 것을 환영합니다, Mira님!", strings.text("greeting", "name" to "Mira"))
        assertEquals("You travel with Ren and 2 others", strings.text("party", "count" to 3, "friend" to "Ren"))
        assertEquals("quest.harbor.reward", strings.text("quest.harbor.reward"))
        assertEquals("등불 5개 중 2개 찾음", strings.text("quest.harbor.objective", "found" to 2, "total" to 5))
    }

    @Test
    fun pluralsAndSwitching() = runTest {
        val strings = Strings.load(StringTableSource.assets(project), locale = LocaleId.English)
        // --8<-- [start:plural]
        strings.text("quest.harbor.objective", "found" to 0, "total" to 5) // "Find the lanterns"
        strings.text("quest.harbor.objective", "found" to 1, "total" to 5) // "1 of 5 lanterns found"
        strings.text("party", "count" to 1, "friend" to "Ren") // "You travel with Ren"
        strings.text("party", "count" to 2, "friend" to "Ren") // "You travel with Ren and 1 other"
        // --8<-- [end:plural]
        assertEquals("Find the lanterns", strings.text("quest.harbor.objective", "found" to 0, "total" to 5))
        assertEquals("1 of 5 lanterns found", strings.text("quest.harbor.objective", "found" to 1, "total" to 5))
        assertEquals("You travel with Ren", strings.text("party", "count" to 1, "friend" to "Ren"))
        assertEquals("You travel with Ren and 1 other", strings.text("party", "count" to 2, "friend" to "Ren"))

        // --8<-- [start:switch]
        strings.setLocale(LocaleId.of("ko")) // loads ko.json, then switches
        strings.locale.value // ko
        strings.text("menu.settings") // "설정"
        // --8<-- [end:switch]
        assertEquals("ko", strings.locale.value.tag)
        assertEquals("설정", strings.text("menu.settings"))
    }

    @Test
    fun localeFonts() = runTest {
        val assets = project
        val strings = Strings.load(StringTableSource.assets(assets), locale = LocaleId.English)
        // --8<-- [start:fonts]
        val fonts = LocaleFonts.load(assets) // strings/fonts.json
        val uiFont: UiFont = strings.loadFont(assets, fonts, primary = UiFonts.default())
        // --8<-- [end:fonts]
        assertEquals(listOf("fonts/NotoSansKR-Regular.otf"), fonts.pathsFor(LocaleId.of("ko-KR")))
        assertSame(UiFonts.default(), uiFont, "English needs no fallback font")
    }

    @Test
    fun pluginDefaultsAndInputLabels() = runTest {
        val strings = Strings.load(StringTableSource.assets(project), locale = LocaleId.English)
        // --8<-- [start:layers]
        // A plugin's own text: its English, and any translations it ships.
        val hudDefaults = StringTableSource.of(
            mapOf(
                LocaleId.English to mapOf("hud.hp" to "HP", "menu.start" to "Begin"),
                LocaleId.of("ko") to mapOf("hud.hp" to "체력"),
            ),
        )
        strings.addSource(hudDefaults, StringLayer.Plugin)
        strings.text("hud.hp") // "HP": only the plugin has it
        strings.text("menu.start") // "Start": the game's strings/en.json overrides the plugin
        // --8<-- [end:layers]
        assertEquals("HP", strings.text("hud.hp"))
        assertEquals("Start", strings.text("menu.start"))
        strings.setLocale(LocaleId.of("ko"))
        assertEquals("체력", strings.text("hud.hp"))
        strings.setLocale(LocaleId.English)

        // --8<-- [start:input]
        val labels = InputLabels.load(strings, style = ShortcutStyle.Windows)
        val save = KeyChord(Key.S, setOf(KeyModifier.Ctrl, KeyModifier.Shift))
        labels.key(Key.Space) // "Space"
        labels.chord(save) // "Ctrl+Shift+S"
        labels.pointer(PointerButton.Secondary) // "Right click"
        labels.gamepad(GamepadButton.South) // "A"
        strings.setLocale(LocaleId.of("de"))
        labels.chord(save) // "Strg+Umschalt+S"
        // --8<-- [end:input]
        assertEquals("Strg+Umschalt+S", labels.chord(save))
        strings.setLocale(LocaleId.English)
        assertEquals("Space", labels.key(Key.Space))
        assertEquals("Ctrl+Shift+S", labels.chord(save))
        assertEquals("Right click", labels.pointer(PointerButton.Secondary))
        assertEquals("A", labels.gamepad(GamepadButton.South))
    }
}
