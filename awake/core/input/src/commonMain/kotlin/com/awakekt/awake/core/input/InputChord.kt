/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A button on a game controller, named by where it sits rather than by what any one maker prints on
 * it: [South] is the bottom face button, [East] the right one.
 *
 * [isModifier] marks the shoulders and triggers, the buttons a player holds to shift what the face
 * buttons do. They are what a [GamepadChord] counts as extra modifiers.
 */
enum class GamepadButton(
    /** Whether this is a shoulder or trigger, which [GamepadChord.extraModifiers] counts when it is held. */
    val isModifier: Boolean = false,
) {
    /** The bottom face button. */
    South,

    /** The right face button. */
    East,

    /** The left face button. */
    West,

    /** The top face button. */
    North,

    /** The left shoulder button. */
    LeftShoulder(isModifier = true),

    /** The right shoulder button. */
    RightShoulder(isModifier = true),

    /** The left trigger, pulled past the point a host counts as pressed. */
    LeftTrigger(isModifier = true),

    /** The right trigger, pulled past the point a host counts as pressed. */
    RightTrigger(isModifier = true),

    /** The left of the two small centre buttons. */
    Select,

    /** The right of the two small centre buttons. */
    Start,

    /** The centre system button, where a host lets it through. */
    Home,

    /** The left stick, clicked in. */
    LeftStick,

    /** The right stick, clicked in. */
    RightStick,

    /** Up on the directional pad. */
    DpadUp,

    /** Down on the directional pad. */
    DpadDown,

    /** Left on the directional pad. */
    DpadLeft,

    /** Right on the directional pad. */
    DpadRight,
}

/**
 * A keyboard modifier a [KeyChord] can require. Left and right are the same modifier, because [Key]
 * has one entry for both: a host reports either Shift as [Key.Shift].
 *
 * Declared in the order labels usually list them, Ctrl+Alt+Shift+Meta, which is the order
 * [BindingDescription.modifiers] uses.
 *
 * @property key The key a host reports while it is held.
 */
enum class KeyModifier(val key: Key) {
    /** Control. */
    Ctrl(Key.Ctrl),

    /** Alt, or Option on macOS. */
    Alt(Key.Alt),

    /** Shift. */
    Shift(Key.Shift),

    /** Meta: Command on macOS, the Windows key on Windows. */
    Meta(Key.Meta),
}

/** Whether a chord still matches while modifiers it does not name are held as well. */
enum class ExtraModifiers {
    /** Any other modifier held stops the match: Ctrl+S does not fire while Ctrl+Shift+S is held. */
    Block,

    /** Other modifiers may be held too: Ctrl+S fires for Ctrl+Shift+S, unless a binding for that is more specific. */
    Allow,
}

/**
 * A control pressed while other controls are held: Shift+1 on a keyboard, or a shoulder held with a
 * face button on a controller. Every chord, from either device, matches and resolves conflicts by
 * the same rules:
 *
 * - **Modifiers first.** A chord fires when its control goes down while its modifiers are already
 *   held, or go down in the same frame. Pressing 1 and then Shift is 1, not Shift+1.
 * - **The most specific chord wins.** Of the bindings whose modifiers are all held when a control
 *   goes down, only those naming the most modifiers fire; Shift+1 does not also fire 1. Bindings
 *   that tie all fire.
 * - **The winner keeps the control until it is released.** Letting go of Shift while 1 stays down
 *   ends Shift+1 without starting 1, and holding Shift after 1 went down does not turn 1 into Shift+1.
 *
 * A plain key in [ButtonAction.keys] or an [AxisAction] direction is a chord with no modifiers that
 * allows extra ones, so it still loses to a more specific chord on the same key.
 */
@Serializable
sealed interface InputChord {
    /** How many modifiers it names: the more, the more specific. */
    val specificity: Int

    /** Whether modifiers it does not name stop it matching. */
    val extraModifiers: ExtraModifiers

    /** What it looks like to a label formatter: its modifiers in a fixed order, then its control. */
    fun describe(): BindingDescription
}

/**
 * [key] pressed while every one of [modifiers] is held, such as Ctrl+Alt+K.
 *
 * @property key The key that fires it. It cannot be [Key.Unknown] or one of its own [modifiers].
 * @property modifiers The modifiers held with it; none makes a chord of the bare key.
 * @property extraModifiers Whether modifiers it does not name stop it matching. [Key.Shift] held for
 * Ctrl+K is extra; [key] itself never is, so Ctrl+Shift where Shift is the key matches.
 */
@Serializable
@SerialName("key")
data class KeyChord(
    val key: Key,
    val modifiers: Set<KeyModifier> = emptySet(),
    override val extraModifiers: ExtraModifiers = ExtraModifiers.Block,
) : InputChord {
    init {
        require(key != Key.Unknown) { "Unknown is not a key that can be pressed" }
        require(modifiers.none { it.key == key }) { "$key cannot be its own modifier" }
    }

    override val specificity: Int get() = modifiers.size

    override fun describe(): BindingDescription = BindingDescription(
        KeyModifier.entries.filter { it in modifiers }.map { InputControl.Keyboard(it.key) },
        InputControl.Keyboard(key),
    )
}

/**
 * [button] pressed while every one of [modifiers] is held, such as a shoulder held with a face button.
 *
 * @property button The button that fires it. It cannot be one of its own [modifiers].
 * @property modifiers The buttons held with it, usually shoulders or triggers; none makes a chord of
 * the bare button.
 * @property extraModifiers Whether a held shoulder or trigger it does not name ([GamepadButton.isModifier])
 * stops it matching.
 */
@Serializable
@SerialName("gamepad")
data class GamepadChord(
    val button: GamepadButton,
    val modifiers: Set<GamepadButton> = emptySet(),
    override val extraModifiers: ExtraModifiers = ExtraModifiers.Block,
) : InputChord {
    init {
        require(button !in modifiers) { "$button cannot be its own modifier" }
    }

    override val specificity: Int get() = modifiers.size

    override fun describe(): BindingDescription = BindingDescription(
        GamepadButton.entries.filter { it in modifiers }.map { InputControl.Gamepad(it) },
        InputControl.Gamepad(button),
    )
}

/** One thing a player presses: a keyboard key, a pointer button or a controller button. */
sealed interface InputControl {
    /** A keyboard [key]. */
    data class Keyboard(val key: Key) : InputControl

    /** A pointer [button]. */
    data class Pointer(val button: PointerButton) : InputControl

    /** A controller [button]. */
    data class Gamepad(val button: GamepadButton) : InputControl
}

/**
 * One way to trigger an action, in the parts a label shows: Shift+1 is [modifiers] `[Shift]` and
 * [control] `1`. It holds no text; a formatter names each part, in the player's language, and joins them.
 *
 * @property modifiers What is held, in [KeyModifier] or [GamepadButton] declaration order.
 * @property control What is pressed.
 */
data class BindingDescription(
    val modifiers: List<InputControl>,
    val control: InputControl,
)
