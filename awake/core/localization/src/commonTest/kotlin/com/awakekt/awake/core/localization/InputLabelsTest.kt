/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

import com.awakekt.awake.core.input.BindingDescription
import com.awakekt.awake.core.input.ButtonAction
import com.awakekt.awake.core.input.GamepadButton
import com.awakekt.awake.core.input.GamepadChord
import com.awakekt.awake.core.input.InputControl
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.KeyChord
import com.awakekt.awake.core.input.KeyModifier
import com.awakekt.awake.core.input.KeyModifier.Alt
import com.awakekt.awake.core.input.KeyModifier.Ctrl
import com.awakekt.awake.core.input.KeyModifier.Meta
import com.awakekt.awake.core.input.KeyModifier.Shift
import com.awakekt.awake.core.input.PointerButton
import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.LogRecord
import com.awakekt.awake.core.logging.LogSink
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InputLabelsTest {

    private val records = mutableListOf<LogRecord>()

    @BeforeTest
    fun installSink() {
        Log.reset()
        Log.install(LogSink { records += it })
    }

    @AfterTest
    fun removeSink() {
        Log.reset()
    }

    private val de = LocaleId.of("de")
    private val noGameText = StringTableSource.of(mapOf(LocaleId.English to mapOf("menu.start" to "Start")))

    private suspend fun labels(
        locale: LocaleId = LocaleId.English,
        style: ShortcutStyle = ShortcutStyle.Windows,
        layout: ControllerLayout = ControllerLayout.Letters,
        game: StringTableSource = noGameText,
    ): Pair<Strings, InputLabels> {
        val strings = Strings.load(game, locale = locale)
        return strings to InputLabels.load(strings, style, layout)
    }

    private fun keys(key: Key, vararg modifiers: KeyModifier) = KeyChord(key, modifiers.toSet())

    @Test
    fun keysHaveNamesAndLettersAndDigitsAreThemselves() = runTest {
        val (_, labels) = labels()
        assertEquals("Space", labels.key(Key.Space))
        assertEquals("Esc", labels.key(Key.Escape))
        assertEquals("Page Up", labels.key(Key.PageUp))
        assertEquals("K", labels.key(Key.K))
        assertEquals("7", labels.key(Key.Digit7))
        assertEquals("F5", labels.key(Key.F5))
        assertTrue(records.none { it.level >= LogLevel.Warn }, "a key no table names is not a missing key: $records")
    }

    @Test
    fun everyKeyAndButtonHasAName() = runTest {
        val (_, labels) = labels()
        Key.entries.forEach { assertTrue(labels.key(it).isNotBlank(), "$it") }
        PointerButton.entries.forEach { assertTrue(labels.pointer(it).isNotBlank(), "$it") }
        ControllerLayout.entries.forEach { layout ->
            val (_, l) = labels(layout = layout)
            GamepadButton.entries.forEach { assertTrue(l.gamepad(it).isNotBlank(), "$it in $layout") }
        }
    }

    @Test
    fun chordsFollowThePlatformsOrderAndNames() = runTest {
        val windows = labels(style = ShortcutStyle.Windows).second
        val apple = labels(style = ShortcutStyle.Apple).second
        val linux = labels(style = ShortcutStyle.Linux).second
        assertEquals("Ctrl+Shift+K", windows.chord(keys(Key.K, Shift, Ctrl)))
        assertEquals("Control+Shift+K", apple.chord(keys(Key.K, Shift, Ctrl)))
        assertEquals("Shift+Cmd+K", apple.chord(keys(Key.K, Meta, Shift)))
        assertEquals("Win+Ctrl+Alt+Delete", windows.chord(keys(Key.Delete, Alt, Ctrl, Meta)))
        assertEquals("Super+Space", linux.chord(keys(Key.Space, Meta)))
        assertEquals("Option+Forward Delete", apple.chord(keys(Key.Delete, Alt)))
        val click = BindingDescription(listOf(InputControl.Keyboard(Key.Shift)), InputControl.Pointer(PointerButton.Primary))
        assertEquals("Shift+Left click", windows.binding(click))
    }

    @Test
    fun thePrimaryModifierIsCmdOnApplePlatformsOnly() = runTest {
        assertEquals(Meta, ShortcutStyle.Apple.primaryModifier)
        assertEquals(Ctrl, ShortcutStyle.Windows.primaryModifier)
        assertEquals(Ctrl, ShortcutStyle.Linux.primaryModifier)
        val save = KeyChord(Key.S, setOf(ShortcutStyle.Apple.primaryModifier))
        assertEquals("Cmd+S", labels(style = ShortcutStyle.Apple).second.chord(save))
    }

    @Test
    fun namesFollowThePlayersLanguage() = runTest {
        val (strings, labels) = labels(locale = de)
        val delete = keys(Key.Delete, Ctrl, Shift)
        assertEquals("Strg+Umschalt+Entf", labels.chord(delete))
        assertEquals("Leertaste", labels.key(Key.Space))
        assertEquals("Linksklick", labels.pointer(PointerButton.Primary))
        strings.setLocale(LocaleId.of("fr"))
        assertEquals("Ctrl+Maj+Suppr", labels.chord(delete), "the labels follow a switch")
        strings.setLocale(LocaleId.of("es-MX"))
        assertEquals("Clic izquierdo", labels.pointer(PointerButton.Primary), "a region uses its language")
        strings.setLocale(LocaleId.of("ja"))
        assertEquals("Ctrl+Shift+Delete", labels.chord(delete), "no Japanese names: English")
    }

    @Test
    fun aPlatformFormInThePlayersLanguageBeatsAPlainOneAndViceVersa() = runTest {
        val apple = labels(locale = de, style = ShortcutStyle.Apple).second
        assertEquals("Control", apple.key(Key.Ctrl), "German's own Apple form, not its Windows Strg")
        assertEquals("Option", apple.key(Key.Alt), "German names Alt nothing: English's Apple form")
        assertEquals("Eingabe", apple.key(Key.Enter), "German's Apple form beats English's Return")
    }

    @Test
    fun controllerButtonsAreNamedByLayout() = runTest {
        val letters = labels(layout = ControllerLayout.Letters).second
        val swapped = labels(layout = ControllerLayout.SwappedLetters).second
        val shapes = labels(layout = ControllerLayout.Shapes).second
        assertEquals("A", letters.gamepad(GamepadButton.South))
        assertEquals("B", swapped.gamepad(GamepadButton.South))
        assertEquals("Cross", shapes.gamepad(GamepadButton.South))
        val shift = GamepadChord(GamepadButton.South, setOf(GamepadButton.RightTrigger, GamepadButton.LeftShoulder))
        assertEquals("LB+RT+A", letters.chord(shift), "held buttons in GamepadButton order")
        assertEquals("L1+R2+Cross", shapes.chord(shift))
        assertEquals("ZL", swapped.gamepad(GamepadButton.LeftTrigger))
        val germanShapes = labels(locale = de, layout = ControllerLayout.Shapes).second
        assertEquals("Kreuz", germanShapes.gamepad(GamepadButton.South))
        assertEquals("L1", germanShapes.gamepad(GamepadButton.LeftShoulder))
    }

    @Test
    fun anActionListsEveryBinding() = runTest {
        val (_, labels) = labels()
        val interact = ButtonAction(
            "interact",
            keys = linkedSetOf(Key.E, Key.Enter),
            buttons = setOf(PointerButton.Secondary),
            chords = linkedSetOf(keys(Key.E, Shift), GamepadChord(GamepadButton.West)),
        )
        assertEquals("E / Enter / Right click / Shift+E / X", labels.bindings(interact))
        assertEquals("", labels.bindings(ButtonAction("unbound")))
    }

    @Test
    fun theGameOverridesABuiltInName() = runTest {
        val game = StringTableSource.of(
            mapOf(
                LocaleId.English to mapOf("input.key.Space" to "Spacebar", "input.chord.separator.apple" to " "),
                de to mapOf("input.pointer.Primary" to "Linke Maustaste"),
            ),
        )
        val (strings, labels) = labels(game = game)
        assertEquals("Spacebar", labels.key(Key.Space))
        assertEquals("Esc", labels.key(Key.Escape), "the rest stay built in")
        strings.setLocale(de)
        assertEquals("Linke Maustaste", labels.pointer(PointerButton.Primary))
        assertEquals("Leertaste", labels.key(Key.Space), "the built-in German beats the game's English")
        val apple = InputLabels.load(Strings.load(game, locale = LocaleId.English), ShortcutStyle.Apple)
        assertEquals("Shift Cmd K", apple.chord(keys(Key.K, Meta, Shift)))
    }
}
