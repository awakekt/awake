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

    /** What a player presses for it, which [InputActions.rebind] can swap. */
    val binding: ActionBinding
}

/**
 * An action that is on or off, triggered by any of [keys], pointer [buttons] and [chords].
 *
 * @property name The name systems read the action by.
 * @property keys The keys that trigger it, whatever modifiers are held, unless a chord on the same key
 * is more specific; see [InputChord].
 * @property buttons The pointer buttons that trigger it.
 * @property trigger Whether it is active while held, for the frame it is pressed, or switched by each press.
 * @property startsOn Whether a [ActionTrigger.Toggle] starts on; other triggers ignore it.
 * @property chords Keys and controller buttons pressed with modifiers held, such as Shift+1.
 */
data class ButtonAction(
    override val name: String,
    val keys: Set<Key> = emptySet(),
    val buttons: Set<PointerButton> = emptySet(),
    val trigger: ActionTrigger = ActionTrigger.Hold,
    val startsOn: Boolean = false,
    val chords: Set<InputChord> = emptySet(),
) : InputActionDefinition {
    override val binding: ButtonBinding get() = ButtonBinding(keys, buttons, chords)

    /** This action triggered by [binding] instead, with the same name and trigger. */
    fun rebound(binding: ButtonBinding): ButtonAction =
        copy(keys = binding.keys, buttons = binding.buttons, chords = binding.chords)
}

/**
 * A direction on two axes, such as moving: [up] and [down] push it along y, [right] and [left] along
 * x, opposite keys cancel, and two at once give a unit diagonal. A key whose press a more specific
 * chord took, such as S under Ctrl+S, does not push it.
 */
data class AxisAction(
    override val name: String,
    val up: Set<Key> = emptySet(),
    val down: Set<Key> = emptySet(),
    val left: Set<Key> = emptySet(),
    val right: Set<Key> = emptySet(),
) : InputActionDefinition {
    override val binding: AxisBinding get() = AxisBinding(up, down, left, right)

    /** This axis pushed by [binding]'s keys instead, with the same name. */
    fun rebound(binding: AxisBinding): AxisAction =
        copy(up = binding.up, down = binding.down, left = binding.left, right = binding.right)
}

/** The keys and buttons [InputActions.read] reads, with whatever the UI owns already left out. */
interface ActionInputSource {
    /** Whether [key] is held. */
    fun isDown(key: Key): Boolean

    /** Whether [key] was pressed this frame. */
    fun wasPressed(key: Key): Boolean

    /** Whether pointer [button] is held. */
    fun isDown(button: PointerButton): Boolean

    /** Whether pointer [button] was pressed this frame. */
    fun wasPressed(button: PointerButton): Boolean

    /** Whether controller [button] is held. False unless a source reads a controller. */
    fun isDown(button: GamepadButton): Boolean = false

    /** Whether controller [button] was pressed this frame. False unless a source reads a controller. */
    fun wasPressed(button: GamepadButton): Boolean = false

    /**
     * Whether actions bound to keys must read no key at all this frame, though [isDown] and
     * [wasPressed] may still answer for some. A focused text field lets keys it has no use for
     * through to a system that asks for one, but movement bound to such a key must not walk the
     * player while they type. Pointer and controller buttons are read either way. False unless a
     * source says so.
     */
    val keysWithheldFromActions: Boolean get() = false
}

/**
 * Named actions and their state this frame, so systems ask for "jump" rather than for a key, and the
 * keys, pointer buttons and touch controls behind each action are data.
 *
 * Each frame starts with [beginFrame], then each source adds what it holds and presses: [read] for
 * keys, pointer and controller buttons, and [hold], [press] and [push] for anything else, such as an
 * on-screen button. A toggle switches once a frame however many sources press it, and keeps its state
 * across frames, including frames in which no source can be read, such as while the UI has the keys.
 *
 * Bindings on the same key or controller button resolve as [InputChord] says, across every action
 * here: the most specific chord held when the key goes down takes it until it is released. A player's
 * bindings are data too: [bindings] takes them, [rebind] and [rebindAll] change them at run time, and
 * [actionsBoundTo] finds what a new chord would clash with.
 *
 * Asking about a name no definition has gives false and 0, so a system can ask for an action a scene
 * may not declare.
 *
 * @param definitions The actions, each with a different name.
 */
@Suppress("TooManyFunctions") // Reading a frame, asking about an action, and rebinding one: one surface, one owner of the state.
class InputActions(definitions: List<InputActionDefinition>) {
    /** The actions, in the order given, each with the binding it has now. */
    var definitions: List<InputActionDefinition> = definitions.toList()
        private set

    private val buttons: Array<ButtonState>
    private val axes: Array<AxisState>
    private val buttonsByName = HashMap<String, ButtonState>()
    private val axesByName = HashMap<String, AxisState>()
    private val keyChords = ChordTable(KeyboardChords)
    private val gamepadChords = ChordTable(GamepadChords)

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
        buildChordTables()
    }

    /** Whether an action is named [action]. */
    operator fun contains(action: String): Boolean = action in buttonsByName || action in axesByName

    /** Starts a frame: nothing is held or pressed and every axis is centred, and each toggle stays as it was. */
    fun beginFrame() {
        for (state in buttons) state.clear()
        for (state in axes) state.set(0f, 0f)
    }

    /**
     * Holds and presses the actions bound to what [source] holds and presses, and sets each axis from
     * its keys. While [ActionInputSource.keysWithheldFromActions], only pointer and controller buttons
     * are read.
     */
    fun read(source: ActionInputSource) {
        val readKeys = !source.keysWithheldFromActions
        if (readKeys) {
            keyChords.read(source)
        } else {
            // Releases go unseen while the keys are withheld, so each key is resolved afresh once they are not.
            keyChords.release()
        }
        gamepadChords.read(source)
        for (state in buttons) state.read(source)
        if (readKeys) for (state in axes) state.read(source, keyChords)
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

    /** What a player presses for [action] now, or null when no action has that name. */
    fun binding(action: String): ActionBinding? = definitions.firstOrNull { it.name == action }?.binding

    /** Every action's binding, by name, for a settings file to save. */
    fun bindings(): InputBindings = InputBindings(definitions.associate { it.name to it.binding })

    /**
     * Makes [action] triggered by [binding] from the next [read], keeping its name, trigger and toggle
     * state. A key held through the change is resolved afresh, held but not pressed.
     *
     * @throws IllegalArgumentException when no action is named [action], or [binding] is for the
     * other kind of action: a [ButtonBinding] for an [AxisAction] or an [AxisBinding] for a [ButtonAction].
     */
    fun rebind(action: String, binding: ActionBinding) {
        require(rebindOne(action, binding)) { rebindRefusal(action, binding) }
        buildChordTables()
    }

    /**
     * Rebinds every action [bindings] names, as a settings file saved by [bindings] is loaded, and
     * leaves the rest as they are. A saved file may be older than the actions, so this skips, rather
     * than fails on, a name no action has or a binding for the other kind of action.
     *
     * @return The names it skipped, for a caller to report or drop from the file.
     */
    fun rebindAll(bindings: InputBindings): List<String> {
        val skipped = bindings.actions.mapNotNull { (action, binding) -> action.takeUnless { rebindOne(action, binding) } }
        buildChordTables()
        return skipped
    }

    /**
     * The actions a player pressing [chord] would also trigger: those bound to the same key or
     * controller button with the same modifiers, whatever their [InputChord.extraModifiers]. A plain
     * key in [ButtonAction.keys] or an [AxisAction] is the chord of that key with no modifiers. A
     * rebinding screen asks this before it takes a new chord, to say what the chord is already bound to.
     */
    fun actionsBoundTo(chord: InputChord): List<String> = definitions.filter { it.isBoundTo(chord) }.map { it.name }

    private fun rebindOne(action: String, binding: ActionBinding): Boolean {
        val index = definitions.indexOfFirst { it.name == action }
        val rebound = definitions.getOrNull(index)?.reboundTo(binding) ?: return false
        when (rebound) {
            is ButtonAction -> buttonsByName.getValue(action).bind(rebound)
            is AxisAction -> axesByName.getValue(action).bind(rebound)
        }
        definitions = definitions.toMutableList().also { it[index] = rebound }
        return true
    }

    private fun rebindRefusal(action: String, binding: ActionBinding): String =
        if (action !in this) "No action is named \"$action\"" else "\"$action\" cannot take a ${binding::class.simpleName}"

    private fun buildChordTables() {
        val keys = ChordTable.Builder(KeyboardChords.size)
        val pads = ChordTable.Builder(GamepadChords.size)
        for (definition in definitions) {
            when (definition) {
                is ButtonAction -> {
                    val state = buttonsByName.getValue(definition.name)
                    definition.keys.forEach { keys.add(it.ordinal, Participant(state, mask = 0, allowExtra = true)) }
                    definition.chords.forEach { chord -> addChord(chord, state, keys, pads) }
                }
                is AxisAction -> {
                    // An axis key is a bare key that allows extra modifiers, so a more specific chord takes it.
                    val directions = definition.up + definition.down + definition.left + definition.right
                    directions.forEach { keys.add(it.ordinal, Participant(state = null, mask = 0, allowExtra = true)) }
                }
            }
        }
        keyChords.load(keys)
        gamepadChords.load(pads)
    }

    /** Action name rules. */
    companion object {
        private val NAME = Regex("[A-Za-z0-9_][A-Za-z0-9_.-]*")

        /** What [isValidName] accepts, worded for a message. */
        const val NAME_RULE = "use letters, digits, '_', '.' and '-', starting with a letter, digit or '_'"

        /** Whether [name] can name an action. */
        fun isValidName(name: String): Boolean = NAME.matches(name)
    }
}

/** How one device's controls and modifiers are read, for a [ChordTable]. */
private sealed interface ChordDevice {
    /** How many controls it has, each numbered by its enum ordinal. */
    val size: Int

    /** The held modifiers that a chord which blocks extra modifiers counts against itself. */
    val extraMask: Int

    /** What is held, as bits a [Participant.mask] can be compared with. */
    fun held(source: ActionInputSource): Int

    fun isDown(source: ActionInputSource, control: Int): Boolean

    fun wasPressed(source: ActionInputSource, control: Int): Boolean

    /** The modifier bit [control] itself sets while held, which never counts as extra for its own chord. */
    fun selfBit(control: Int): Int
}

/** Keys, with the four [KeyModifier]s as bits by their ordinal. */
private data object KeyboardChords : ChordDevice {
    private val keys = Key.entries
    private val modifiers = KeyModifier.entries
    override val size = keys.size
    override val extraMask = (1 shl modifiers.size) - 1

    override fun held(source: ActionInputSource): Int {
        var held = 0
        for (modifier in modifiers) if (source.isDown(modifier.key)) held = held or (1 shl modifier.ordinal)
        return held
    }

    override fun isDown(source: ActionInputSource, control: Int) = source.isDown(keys[control])

    override fun wasPressed(source: ActionInputSource, control: Int) = source.wasPressed(keys[control])

    override fun selfBit(control: Int): Int {
        for (modifier in modifiers) if (modifier.key.ordinal == control) return 1 shl modifier.ordinal
        return 0
    }
}

/** Controller buttons, every one a bit by its ordinal; shoulders and triggers count as extra. */
private data object GamepadChords : ChordDevice {
    private val buttons = GamepadButton.entries
    override val size = buttons.size
    override val extraMask = buttons.filter { it.isModifier }.maskOf { it.ordinal }

    override fun held(source: ActionInputSource): Int {
        var held = 0
        for (button in buttons) if (source.isDown(button)) held = held or (1 shl button.ordinal)
        return held
    }

    override fun isDown(source: ActionInputSource, control: Int) = source.isDown(buttons[control])

    override fun wasPressed(source: ActionInputSource, control: Int) = source.wasPressed(buttons[control])

    override fun selfBit(control: Int): Int = 1 shl control
}

private inline fun <T> Iterable<T>.maskOf(bit: (T) -> Int): Int = fold(0) { mask, item -> mask or (1 shl bit(item)) }

private fun addChord(chord: InputChord, state: ButtonState, keys: ChordTable.Builder, pads: ChordTable.Builder) {
    val allowExtra = chord.extraModifiers == ExtraModifiers.Allow
    when (chord) {
        is KeyChord -> keys.add(chord.key.ordinal, Participant(state, chord.modifiers.maskOf { it.ordinal }, allowExtra))
        is GamepadChord -> pads.add(chord.button.ordinal, Participant(state, chord.modifiers.maskOf { it.ordinal }, allowExtra))
    }
}

private fun InputActionDefinition.reboundTo(binding: ActionBinding): InputActionDefinition? = when (this) {
    is ButtonAction -> (binding as? ButtonBinding)?.let(::rebound)
    is AxisAction -> (binding as? AxisBinding)?.let(::rebound)
}

private fun InputActionDefinition.isBoundTo(chord: InputChord): Boolean = when (this) {
    is ButtonAction -> when (chord) {
        is KeyChord -> (chord.modifiers.isEmpty() && chord.key in keys) ||
            chords.any { it is KeyChord && it.key == chord.key && it.modifiers == chord.modifiers }
        is GamepadChord -> chords.any { it is GamepadChord && it.button == chord.button && it.modifiers == chord.modifiers }
    }
    is AxisAction -> chord is KeyChord && chord.modifiers.isEmpty() && chord.key in (up + down + left + right)
}

/**
 * One binding on one key or controller button. [state] is null for an axis direction, which only
 * needs to know whether the key is its own.
 */
private class Participant(val state: ButtonState?, val mask: Int, val allowExtra: Boolean) {
    val specificity = mask.countOneBits()

    /** Whether its modifiers are all in [held], and nothing else from [extraMask] is unless it allows that. */
    fun matches(held: Int, extraMask: Int, self: Int): Boolean =
        held and mask == mask && (allowExtra || held and mask.inv() and self.inv() and extraMask == 0)
}

/**
 * The bindings on each control of one device, and which specificity took each control when it went
 * down. A control nobody binds costs nothing a frame.
 */
private class ChordTable(private val device: ChordDevice) {
    private var participants: Array<Array<Participant>?> = arrayOfNulls(device.size)
    private var bound = IntArray(0)
    private val claims = IntArray(device.size) { UNCLAIMED }

    /** The specificity that took [control], [UNCLAIMED] while it is up, or [NOTHING] when no binding matched. */
    fun claim(control: Int): Int = claims[control]

    fun load(builder: Builder) {
        participants = builder.build()
        bound = participants.indices.filter { participants[it] != null }.toIntArray()
        release()
    }

    fun release() {
        claims.fill(UNCLAIMED)
    }

    fun read(source: ActionInputSource) {
        if (bound.isEmpty()) return
        val held = device.held(source)
        val extraMask = device.extraMask
        for (control in bound) {
            val down = device.isDown(source, control)
            val pressed = device.wasPressed(source, control)
            if (down || pressed) resolve(control, down, pressed, held, extraMask)
            if (!down) claims[control] = UNCLAIMED
        }
    }

    private fun resolve(control: Int, down: Boolean, pressed: Boolean, held: Int, extraMask: Int) {
        val bindings = participants[control]!!
        val self = device.selfBit(control)
        // Claimed when it goes down, or when first seen held, and kept until it is released.
        if (pressed || claims[control] == UNCLAIMED) claims[control] = best(bindings, held, extraMask, self)
        val claim = claims[control]
        for (binding in bindings) {
            val state = binding.state
            if (state == null || binding.specificity != claim || !binding.matches(held, extraMask, self)) continue
            if (down) state.held = true
            if (pressed) state.press()
        }
    }

    private fun best(bindings: Array<Participant>, held: Int, extraMask: Int, self: Int): Int {
        var best = NOTHING
        for (binding in bindings) {
            if (binding.specificity > best && binding.matches(held, extraMask, self)) best = binding.specificity
        }
        return best
    }

    class Builder(size: Int) {
        private val lists = arrayOfNulls<MutableList<Participant>>(size)

        fun add(control: Int, participant: Participant) {
            (lists[control] ?: ArrayList<Participant>().also { lists[control] = it }).add(participant)
        }

        fun build(): Array<Array<Participant>?> = Array(lists.size) { lists[it]?.toTypedArray() }
    }

    companion object {
        const val UNCLAIMED = -1
        const val NOTHING = -2
    }
}

private class ButtonState(definition: ButtonAction) {
    private var buttons = definition.buttons.toTypedArray()
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

    fun bind(definition: ButtonAction) {
        buttons = definition.buttons.toTypedArray()
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

    /** Reads the pointer buttons; keys and controller buttons come through the action's chord tables. */
    fun read(source: ActionInputSource) {
        for (button in buttons) {
            if (source.isDown(button)) held = true
            if (source.wasPressed(button)) press()
        }
    }
}

private class AxisState(definition: AxisAction) {
    private var up = definition.up.toTypedArray()
    private var down = definition.down.toTypedArray()
    private var left = definition.left.toTypedArray()
    private var right = definition.right.toTypedArray()
    var x = 0f
        private set
    var y = 0f
        private set

    fun bind(definition: AxisAction) {
        up = definition.up.toTypedArray()
        down = definition.down.toTypedArray()
        left = definition.left.toTypedArray()
        right = definition.right.toTypedArray()
    }

    fun set(x: Float, y: Float) {
        this.x = x
        this.y = y
    }

    fun read(source: ActionInputSource, chords: ChordTable) {
        val towardX = source.anyDown(right, chords) - source.anyDown(left, chords)
        val towardY = source.anyDown(up, chords) - source.anyDown(down, chords)
        if (towardX == 0f && towardY == 0f) return
        val length = sqrt(towardX * towardX + towardY * towardY)
        set(towardX / length, towardY / length)
    }

    /** 1 when one of [keys] is held and no chord more specific than a bare key took it. */
    private fun ActionInputSource.anyDown(keys: Array<Key>, chords: ChordTable): Float {
        for (key in keys) if (isDown(key) && chords.claim(key.ordinal) == 0) return 1f
        return 0f
    }
}
