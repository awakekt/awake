/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlin.math.sqrt

/** How a [ButtonAction] turns its bindings into whether it is active. */
enum class ActionTrigger {
    /** Active while a binding is held. */
    Hold,

    /** Active for the frame a binding is pressed. */
    Press,

    /** Each press switches it on or off. */
    Toggle,
}

/** One named action and what triggers it: a [ButtonAction] or an [AxisAction]. */
sealed interface InputActionDefinition {
    /** The name systems read the action by, one [InputActions.isValidName] accepts. */
    val name: String
}

/**
 * An action that is on or off, triggered by any of [keys] and pointer [buttons].
 *
 * @property name The name systems read the action by.
 * @property keys The keys that trigger it.
 * @property buttons The pointer buttons that trigger it.
 * @property trigger Whether it is active while held, for the frame it is pressed, or switched by each press.
 * @property startsOn Whether a [ActionTrigger.Toggle] starts on; other triggers ignore it.
 */
data class ButtonAction(
    override val name: String,
    val keys: Set<Key> = emptySet(),
    val buttons: Set<PointerButton> = emptySet(),
    val trigger: ActionTrigger = ActionTrigger.Hold,
    val startsOn: Boolean = false,
) : InputActionDefinition

/**
 * A direction on two axes, such as moving: [up] and [down] push it along y, [right] and [left] along
 * x, opposite keys cancel, and two at once give a unit diagonal.
 */
data class AxisAction(
    override val name: String,
    val up: Set<Key> = emptySet(),
    val down: Set<Key> = emptySet(),
    val left: Set<Key> = emptySet(),
    val right: Set<Key> = emptySet(),
) : InputActionDefinition

/** The keys and pointer buttons [InputActions.read] reads, with whatever the UI owns already left out. */
interface ActionInputSource {
    /** Whether [key] is held. */
    fun isDown(key: Key): Boolean

    /** Whether [key] was pressed this frame. */
    fun wasPressed(key: Key): Boolean

    /** Whether pointer [button] is held. */
    fun isDown(button: PointerButton): Boolean

    /** Whether pointer [button] was pressed this frame. */
    fun wasPressed(button: PointerButton): Boolean
}

/**
 * Named actions and their state this frame, so systems ask for "jump" rather than for a key, and the
 * keys, pointer buttons and touch controls behind each action are data.
 *
 * Each frame starts with [beginFrame], then each source adds what it holds and presses: [read] for
 * keys and pointer buttons, and [hold], [press] and [push] for anything else, such as an on-screen
 * button. A toggle switches once a frame however many sources press it, and keeps its state across
 * frames, including frames in which no source can be read, such as while the UI has the keys.
 *
 * Asking about a name no definition has gives false and 0, so a system can ask for an action a scene
 * may not declare.
 *
 * @param definitions The actions, each with a different name.
 */
class InputActions(definitions: List<InputActionDefinition>) {
    /** The actions, in the order given. */
    val definitions: List<InputActionDefinition> = definitions.toList()

    private val buttons: Array<ButtonState>
    private val axes: Array<AxisState>
    private val buttonsByName = HashMap<String, ButtonState>()
    private val axesByName = HashMap<String, AxisState>()

    init {
        this.definitions.forEach { definition ->
            require(isValidName(definition.name)) { "\"${definition.name}\" is not an action name: $NAME_RULE" }
            require(definition.name !in this) { "Two actions are named \"${definition.name}\"" }
            when (definition) {
                is ButtonAction -> buttonsByName[definition.name] = ButtonState(definition)
                is AxisAction -> axesByName[definition.name] = AxisState(definition)
            }
        }
        buttons = buttonsByName.values.toTypedArray()
        axes = axesByName.values.toTypedArray()
    }

    /** Whether an action is named [action]. */
    operator fun contains(action: String): Boolean = action in buttonsByName || action in axesByName

    /** Starts a frame: nothing is held or pressed and every axis is centred, and each toggle stays as it was. */
    fun beginFrame() {
        for (state in buttons) state.clear()
        for (state in axes) state.set(0f, 0f)
    }

    /** Holds and presses the actions bound to what [source] holds and presses, and sets each axis from its keys. */
    fun read(source: ActionInputSource) {
        for (state in buttons) state.read(source)
        for (state in axes) state.read(source)
    }

    /** Holds button [action] this frame, as a held on-screen button does. */
    fun hold(action: String) {
        buttonsByName[action]?.held = true
    }

    /** Presses button [action] this frame, as a tapped on-screen button does. */
    fun press(action: String) {
        buttonsByName[action]?.press()
    }

    /** Sets axis [action] to ([x], [y]) this frame in place of what its keys gave it, as a deflected joystick does. */
    fun push(action: String, x: Float, y: Float) {
        axesByName[action]?.set(x, y)
    }

    /** Whether button [action] is active: held, pressed this frame, or switched on, as its trigger says. */
    fun isActive(action: String): Boolean = buttonsByName[action]?.isActive ?: false

    /** Whether button [action] was pressed this frame, whatever its trigger. */
    fun wasPressed(action: String): Boolean = buttonsByName[action]?.pressed ?: false

    /** Axis [action]'s x this frame, right being positive. */
    fun axisX(action: String): Float = axesByName[action]?.x ?: 0f

    /** Axis [action]'s y this frame, up being positive. */
    fun axisY(action: String): Float = axesByName[action]?.y ?: 0f

    /** Action name rules. */
    companion object {
        private val NAME = Regex("[A-Za-z0-9_][A-Za-z0-9_.-]*")

        /** What [isValidName] accepts, worded for a message. */
        const val NAME_RULE = "use letters, digits, '_', '.' and '-', starting with a letter, digit or '_'"

        /** Whether [name] can name an action. */
        fun isValidName(name: String): Boolean = NAME.matches(name)
    }
}

private class ButtonState(definition: ButtonAction) {
    private val keys = definition.keys.toTypedArray()
    private val buttons = definition.buttons.toTypedArray()
    private val trigger = definition.trigger
    private var on = definition.trigger == ActionTrigger.Toggle && definition.startsOn
    var held = false
    var pressed = false
        private set

    val isActive: Boolean get() = when (trigger) {
        ActionTrigger.Hold -> held
        ActionTrigger.Press -> pressed
        ActionTrigger.Toggle -> on
    }

    fun clear() {
        held = false
        pressed = false
    }

    fun press() {
        if (pressed) return
        pressed = true
        if (trigger == ActionTrigger.Toggle) on = !on
    }

    fun read(source: ActionInputSource) {
        for (key in keys) {
            if (source.isDown(key)) held = true
            if (source.wasPressed(key)) press()
        }
        for (button in buttons) {
            if (source.isDown(button)) held = true
            if (source.wasPressed(button)) press()
        }
    }
}

private class AxisState(definition: AxisAction) {
    private val up = definition.up.toTypedArray()
    private val down = definition.down.toTypedArray()
    private val left = definition.left.toTypedArray()
    private val right = definition.right.toTypedArray()
    var x = 0f
        private set
    var y = 0f
        private set

    fun set(x: Float, y: Float) {
        this.x = x
        this.y = y
    }

    fun read(source: ActionInputSource) {
        val towardX = source.anyDown(right) - source.anyDown(left)
        val towardY = source.anyDown(up) - source.anyDown(down)
        if (towardX == 0f && towardY == 0f) return
        val length = sqrt(towardX * towardX + towardY * towardY)
        set(towardX / length, towardY / length)
    }

    private fun ActionInputSource.anyDown(keys: Array<Key>): Float {
        for (key in keys) if (isDown(key)) return 1f
        return 0f
    }
}
