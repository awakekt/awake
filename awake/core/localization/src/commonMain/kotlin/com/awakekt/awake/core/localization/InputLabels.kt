/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

import com.awakekt.awake.core.input.BindingDescription
import com.awakekt.awake.core.input.ButtonAction
import com.awakekt.awake.core.input.GamepadButton
import com.awakekt.awake.core.input.InputChord
import com.awakekt.awake.core.input.InputControl
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.KeyModifier
import com.awakekt.awake.core.input.PointerButton

/**
 * How a platform names and orders modifier keys in a shortcut: `Ctrl+Shift+K` on Windows,
 * `Control+Shift+Cmd+K` on Apple platforms.
 */
enum class ShortcutStyle {
    /** Windows: Meta is `Win`. */
    Windows,

    /** Linux, Android and other non-Apple systems: Meta is `Super`. */
    Linux,

    /** macOS and iOS: Alt is `Option` and Meta is `Cmd`. */
    Apple,
    ;

    /**
     * The modifier a platform's own shortcuts use, such as copy: [KeyModifier.Meta] (Cmd) on Apple
     * platforms and [KeyModifier.Ctrl] elsewhere. A game that binds editor-style shortcuts binds a
     * `KeyChord` with this one.
     */
    val primaryModifier: KeyModifier get() = if (this == Apple) KeyModifier.Meta else KeyModifier.Ctrl

    /** The modifier keys in the order this platform writes them in a shortcut. */
    internal val modifierOrder: List<Key>
        get() = if (this == Apple) listOf(Key.Ctrl, Key.Alt, Key.Shift, Key.Meta) else listOf(Key.Meta, Key.Ctrl, Key.Alt, Key.Shift)

    /** The suffix of a label key's platform form: `input.key.Meta.apple`. */
    internal val suffix: String get() = name.lowercase()

    companion object {
        /**
         * The style of the platform this runs on: desktop reads the OS name, iOS is [Apple], Android is
         * [Linux], and the browser reads `navigator.platform`.
         *
         * @return The style.
         */
        fun system(): ShortcutStyle = systemShortcutStyle()
    }
}

/** The platform's [ShortcutStyle]. */
internal expect fun systemShortcutStyle(): ShortcutStyle

/**
 * What a controller's buttons are printed with, which decides the name a prompt shows for each
 * [GamepadButton].
 */
enum class ControllerLayout {
    /** Letters A (bottom), B (right), X (left), Y (top); shoulders LB and RB; triggers LT and RT. */
    Letters,

    /** Letters with the face pairs swapped: B (bottom), A (right), Y (left), X (top); L, R, ZL, ZR. */
    SwappedLetters,

    /** Shapes: Cross (bottom), Circle (right), Square (left), Triangle (top); L1, R1, L2, R2. */
    Shapes,
    ;

    /** The suffix of a label key's layout form: `input.gamepad.South.shapes`. */
    internal val suffix: String
        get() = when (this) {
            Letters -> "letters"
            SwappedLetters -> "swapped"
            Shapes -> "shapes"
        }
}

/**
 * The names of keys, mouse buttons and controller buttons, and of the chords and bindings made of
 * them, in the player's language and their platform's style, for button prompts and hotkey labels:
 * `Strg+Umschalt+K` for a German Windows player, `Control+Shift+Cmd+K` on a Mac.
 *
 * It names what `core:input` describes: a [BindingDescription], from `InputChord.describe()` or
 * `ButtonBinding.describe()`, is named by [binding]. [load] gives the labels a [Strings] reads, which
 * change with its locale. A test, or a game that names buttons its own way, can implement this itself.
 */
interface InputLabels {
    /**
     * The name of [key]: `Space`, `Esc`, `A`, `F5`.
     *
     * @param key The key.
     * @return Its name.
     */
    fun key(key: Key): String

    /**
     * The name of mouse or pointer [button]: `Left click`.
     *
     * @param button The button.
     * @return Its name.
     */
    fun pointer(button: PointerButton): String

    /**
     * The name of controller [button] as the layout prints it: `A`, `Cross`, `LB`.
     *
     * @param button The button.
     * @return Its name.
     */
    fun gamepad(button: GamepadButton): String

    /**
     * The name of any one [control].
     *
     * @param control The key, pointer button or controller button.
     * @return Its name.
     */
    fun control(control: InputControl): String = when (control) {
        is InputControl.Keyboard -> key(control.key)
        is InputControl.Pointer -> pointer(control.button)
        is InputControl.Gamepad -> gamepad(control.button)
    }

    /**
     * One way to trigger an action, its modifiers first and joined by the locale's separator:
     * `Ctrl+Shift+K`, `LB+A`. Modifier keys are written in the platform's order.
     *
     * @param description What is held and what is pressed.
     * @return Its name.
     */
    fun binding(description: BindingDescription): String

    /**
     * A chord, such as `KeyChord(Key.K, setOf(KeyModifier.Ctrl))`: `Ctrl+K`.
     *
     * @param chord The chord.
     * @return Its name.
     */
    fun chord(chord: InputChord): String = binding(chord.describe())

    /**
     * Every way to trigger [action], keys then pointer buttons then chords, between the locale's
     * separator: `E / Right click / Shift+E`.
     *
     * @param action The action.
     * @return Its bindings' names, or an empty string when it has none.
     */
    fun bindings(action: ButtonAction): String

    companion object {
        /**
         * The built-in names, at [StringLayer.BuiltIn]: English, German, French and Spanish.
         *
         * A game or plugin overrides any of them, or adds a language, with the same keys in its own
         * tables: `input.key.<Key>`, `input.pointer.<PointerButton>` and `input.gamepad.<GamepadButton>`,
         * each optionally with a platform or layout suffix (`input.key.Meta.apple`,
         * `input.gamepad.South.shapes`) that wins over the plain key in the same locale.
         * `input.chord.separator` joins a chord and `input.bindings.separator` an action's bindings.
         */
        val defaults: StringTableSource = StringTableSource.of(BuiltInInputLabels.tables)

        /**
         * Adds [defaults] to [strings], if they are not there yet, and returns labels that read it.
         *
         * @param strings The text, whose locale the labels follow.
         * @param style The platform style.
         * @param layout How the player's controller is printed.
         * @return The labels.
         */
        suspend fun load(
            strings: Strings,
            style: ShortcutStyle = ShortcutStyle.system(),
            layout: ControllerLayout = ControllerLayout.Letters,
        ): InputLabels {
            strings.addSource(defaults, StringLayer.BuiltIn)
            return StringsInputLabels(strings, style, layout)
        }
    }
}

/** [InputLabels] read from [strings], a key's platform or layout form before its plain one. */
private class StringsInputLabels(
    private val strings: Strings,
    private val style: ShortcutStyle,
    private val layout: ControllerLayout,
) : InputLabels {

    override fun key(key: Key): String = label("input.key.${key.name}", style.suffix) { key.fallbackName() }

    override fun pointer(button: PointerButton): String = label("input.pointer.${button.name}", style.suffix) { button.name }

    override fun gamepad(button: GamepadButton): String = label("input.gamepad.${button.name}", layout.suffix) { button.name }

    override fun binding(description: BindingDescription): String {
        val modifiers = description.modifiers.sortedBy { (it as? InputControl.Keyboard)?.key?.let(style.modifierOrder::indexOf) ?: 0 }
        return (modifiers + description.control).joinToString(label("input.chord.separator", style.suffix) { "+" }, transform = ::control)
    }

    override fun bindings(action: ButtonAction): String =
        action.binding.describe().joinToString(label("input.bindings.separator", style.suffix) { " / " }, transform = ::binding)

    private inline fun label(key: String, suffix: String, fallback: () -> String): String =
        strings.textOfFirst(arrayOf("$key.$suffix", key)) ?: fallback()
}

/** `A`, `5`, `F5`: what a key is called when no table names it. */
private fun Key.fallbackName(): String = if (name.startsWith("Digit")) name.removePrefix("Digit") else name
