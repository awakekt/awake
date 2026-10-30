/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.input.GamepadButton
import com.awakekt.awake.core.input.NumberStepping
import com.awakekt.awake.core.text.unicode.TextLengthUnit
import kotlin.time.TimeMark
import kotlin.time.TimeSource

// The widgets a player works. What the player is doing to one right now, its presses, its stick
// and its focus, is in the element's CanvasPointerState; these hold what is set up and what code
// sets.

/**
 * A button: tapped, or as its [settings] say an on-screen action button with a trigger, a long
 * press, a release, an aim and a cooldown. [action] names what it does for the game, such as `jump`.
 */
class CanvasButtonWidget : CanvasWidget {
    override val kind: CanvasElementKind get() = CanvasElementKind.Button

    /** What the button does for the game when pressed, such as `jump`; the game or runtime decides what it means. */
    var action: String = ""

    /** What the button does when double-clicked: two presses within [CanvasElement.DOUBLE_CLICK_SECONDS]. */
    var doubleAction: String = ""

    /** How the button acts as an action button: when it fires, a long press, a release and a cooldown. Null is a plain button, tapped on release. */
    var settings: CanvasButton? = null

    /**
     * Whether the button can be pressed. A disabled one still takes a press, so it does not reach
     * what is under it, but reports nothing and draws in its style's `disabled` look.
     */
    var enabled: Boolean = true
        set(value) {
            val wasUsable = usable
            field = value
            noteReady(wasUsable)
        }

    /**
     * How much of an action button's cooldown is left, from 1, just used, to 0, ready; drawn as a
     * sweep over it. Set from code, or read each frame from [CanvasBinding.cooldown].
     */
    var cooldown: Float = 0f
        set(value) {
            val wasUsable = usable
            field = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)
            noteReady(wasUsable)
        }

    /**
     * How many uses an action button has left, shown as a badge; below 0, the default, shows none.
     * While it is above 0 the button can be used even as [cooldown] recharges the next one. Set from
     * code, or read each frame from [CanvasBinding.charges].
     */
    var charges: Int = -1
        set(value) {
            val wasUsable = usable
            field = value
            noteReady(wasUsable)
        }

    /**
     * Locks an aimed action button's aim onto a target while [CanvasAim.lockTarget] is set, such as
     * the one nearest the aimed line: the game knows its targets, so it answers. Null keeps the raw aim.
     */
    var aimLock: CanvasAimLock? = null

    /** Whether an action button is cooling down: [cooldown] above 0 and no [charges] left to spend. */
    val coolingDown: Boolean get() = cooldown > 0f && charges <= 0

    /** Whether the button can be pressed now: [enabled], and not held back by [coolingDown]. */
    val usable: Boolean get() = enabled && !(coolingDown && (settings ?: CanvasButton.Default).blockWhileCooling)

    /** What times the button's ready pulse; its element sets it to its own. */
    internal var clock: TimeSource = TimeSource.Monotonic

    /** When the button last became usable again, for its [CanvasControlState.Ready] pulse. */
    internal var readySince: TimeMark? = null

    private var becameReady = false

    private fun noteReady(wasUsable: Boolean) {
        if (!wasUsable && usable) {
            becameReady = true
            readySince = clock.markNow()
        }
    }

    /**
     * True once each time an action button becomes usable again, its cooldown done, a charge back
     * or it is enabled, since the last call: what a "ready" pulse or sound waits for.
     */
    fun consumeReady(): Boolean = becameReady.also { becameReady = false }
}

/** A virtual analog stick: a round pad whose knob is dragged within it, steering what [action] names, such as `move`. */
class CanvasJoystickWidget : CanvasWidget {
    override val kind: CanvasElementKind get() = CanvasElementKind.Joystick

    /** The axis action the stick steers, such as `move`. */
    var action: String = ""

    /**
     * How the stick moves and what it reports: its mode, reach, dead zone, response, directions,
     * sprint and idle fade. Null behaves as [CanvasJoystick.Default].
     */
    var settings: CanvasJoystick? = null
}

/** A region where a swipe looks and a pinch zooms, such as the right half of the screen. */
class CanvasLookAreaWidget : CanvasWidget {
    override val kind: CanvasElementKind get() = CanvasElementKind.LookArea

    /** The axis action the swipe is pushed to, such as `look`. */
    var action: String = ""

    /** How a swipe turns into a look and a pinch into a zoom; null behaves as [CanvasLook.Default]. */
    var settings: CanvasLook? = null
}

/** A single-line field the player types into; what is typed is its element's [CanvasText.text]. */
class CanvasTextFieldWidget : CanvasWidget {
    override val kind: CanvasElementKind get() = CanvasElementKind.TextField

    /**
     * The password mask: one character drawn for each typed one; empty draws the text. A masked
     * field refuses copy and cut, and reads as a password to an accessibility service and the
     * platform keyboard.
     */
    var mask: String = ""

    /**
     * The most the player may type, counted in [maxLengthUnit]; 0, the default, for no limit. Typing,
     * pasting and an IME stop at the limit, and never inside a character. A system writing the text
     * is not limited.
     */
    var maxLength: Int = 0

    /** What [maxLength] counts: the characters a player sees, or code points or UTF-8 bytes to match a server's limit. */
    var maxLengthUnit: TextLengthUnit = TextLengthUnit.Graphemes

    /**
     * The key of the hint shown while the field is empty and does not hold the keyboard, such as
     * `chat.hint`; empty shows none. It draws in the field's text colour at half strength.
     */
    var placeholder: String = ""

    /** The space between the field's edge and its text, in dp; it also keeps the room a glyph's overhang needs. */
    var padding: CanvasPadding = CanvasPadding()
}

/**
 * A number with minus and plus buttons, stepped as its [settings] say by a press, a held button,
 * the keyboard or [stepBy].
 */
class CanvasStepperWidget : CanvasWidget {
    override val kind: CanvasElementKind get() = CanvasElementKind.Stepper

    /** The range, step and repeat, and the number it starts at; null is [CanvasStepper.Default]. Setting it puts [number] at its value. */
    var settings: CanvasStepper? = null
        set(value) {
            field = value
            number = (value ?: CanvasStepper.Default).value
        }

    /** The number right now: where [settings] start it, then moved by the player, by [stepBy], or by a bound `value` field. */
    var number: Float = CanvasStepper.Default.value

    private var numberChanged = false

    /** Moves the number [steps] steps, held to its range, as a press of a button does; returns whether it moved. */
    fun stepBy(steps: Int): Boolean {
        val range = settings ?: CanvasStepper.Default
        val next = NumberStepping.stepped(number, steps, range.min, range.max, range.step)
        if (steps == 0 || next == number) return false
        number = next
        numberChanged = true
        return true
    }

    /** Moves the number as a controller's [button] does: the pad up or right one step, the shoulders ten. */
    fun stepBy(button: GamepadButton): Boolean = stepBy(NumberStepping.stepsOf(button))

    /** Puts the number on [target], held to its range, as Home and End do; returns whether it moved. */
    internal fun stepTo(target: Float): Boolean {
        val range = settings ?: CanvasStepper.Default
        val next = NumberStepping.clamp(target, range.min, range.max)
        if (next == number) return false
        number = next
        numberChanged = true
        return true
    }

    /** True once each time the player, or [stepBy], has changed the number since the last call. */
    fun consumeChange(): Boolean = numberChanged.also { numberChanged = false }
}
