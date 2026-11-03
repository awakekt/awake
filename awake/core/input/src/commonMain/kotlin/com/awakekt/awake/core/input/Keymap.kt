/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

/**
 * Named actions, what a rebinding screen shows for each, and what a press of each does, over one
 * [InputActions]. It depends on no ECS, UI or window, so a game loop, an ECS system or an editor
 * drives it the same way.
 *
 * Each frame, [update] reads a source into [actions] and runs the handlers of the actions pressed.
 * A caller that feeds [actions] itself, such as on-screen controls pushing an axis or pressing a
 * button, calls [InputActions.beginFrame], reads and pushes, then [dispatch]. Held actions and axes
 * stay polled: a system asks [actions] for them, as it does without a keymap. A joystick that pushes
 * `move` and keys bound to `move` drive the same action, so the systems reading it never ask which
 * device moved the player.
 *
 * A press shared by several handled actions, such as Escape closing a window or else opening a menu,
 * goes to the highest [KeymapEntry.priority] first, then in the order declared. An action whose
 * [KeymapEntry.enabledWhen] is false lets the press go on. A handler that runs takes the keys and
 * buttons it was pressed with, so a handler further down on the same key does not run as well. A
 * press with nothing to take, such as an on-screen button's, always reaches its handler.
 *
 * A player's bindings are the [actions]' own: [InputActions.rebind] changes one, [unbind] and [reset]
 * clear one or put back its default, [changedBindings] is what a settings file saves and [load] reads
 * it back. [usePreset] swaps the defaults themselves, such as for a left-handed layout.
 *
 * @param C What handlers are given each frame.
 * @param entries The actions with labels and handlers, each with a different name.
 * @property actions The actions' state this frame and their bindings now, read by [update]. By default
 * the entries' own; a larger set, such as a scene's actions with the entries added, shares one set
 * with other readers. It must have every entry's action, of the same kind, and its bindings as given
 * become the [defaults].
 * @throws IllegalArgumentException when two entries have the same name, a name is not an action name,
 * or [actions] lacks an entry's action or has it as the other kind.
 */
class Keymap<C>(
    entries: List<KeymapEntry<C>>,
    val actions: InputActions = InputActions(entries.map { it.action }),
) {
    /** The actions with labels and handlers, in the order given. */
    val entries: List<KeymapEntry<C>> = entries.toList()

    /** Every action's default binding, by name: what [reset] puts back and [changedBindings] measures from. */
    var defaults: InputBindings = actions.bindings()
        private set

    private val byName = this.entries.associateBy { it.name }
    private val handled = this.entries.filter { it.onPress != null }.sortedByDescending { it.priority }
    private val claims = PressClaims()

    init {
        require(byName.size == this.entries.size) { "Two entries are named \"${this.entries.duplicateName()}\"" }
        for (entry in this.entries) require(actions.binding(entry.name).isFor(entry.action)) { actions.refusal(entry) }
    }

    /** The entry named [name], or null when none is, such as for an action only [actions] has. */
    fun entry(name: String): KeymapEntry<C>? = byName[name]

    /** Starts a frame, reads [source] into [actions], and runs the handlers of what it pressed. */
    fun update(source: ActionInputSource, context: C) {
        actions.beginFrame()
        actions.read(source)
        dispatch(context, source)
    }

    /**
     * Runs the handler of each action pressed this frame, in priority order, once [actions] has been
     * read. With [source], what each handler was pressed with is taken from the handlers after it;
     * without, every enabled handler of a pressed action runs.
     */
    fun dispatch(context: C, source: ActionInputSource? = null) {
        claims.clear()
        for (entry in handled) {
            if (actions.reaches(entry, context, source, claims)) entry.onPress?.invoke(context)
        }
    }

    /** Leaves [name] bound to nothing, so only an on-screen control or code triggers it. */
    fun unbind(name: String) {
        val binding = requireNotNull(actions.binding(name)) { "No action is named \"$name\"" }
        actions.rebind(name, binding.unbound())
    }

    /** Puts back [name]'s default binding. */
    fun reset(name: String) {
        actions.rebind(name, requireNotNull(defaults.actions[name]) { "No action is named \"$name\"" })
    }

    /** Puts back every action's default binding. */
    fun resetAll() {
        actions.rebindAll(defaults)
    }

    /**
     * Rebinds every action [preset] names, and makes every action's binding then its default, so a
     * layout picked in settings, such as arrows or a left-handed set, is what [reset] puts back and
     * [changedBindings] leaves out. Load a player's own changes after it.
     *
     * @return The names it skipped: no action has them, or the binding is for the other kind of action.
     */
    fun usePreset(preset: InputBindings): List<String> {
        resetAll()
        val skipped = actions.rebindAll(preset)
        defaults = actions.bindings()
        return skipped
    }

    /** The bindings that differ from the defaults, for a settings file to save; [load] reads them back. */
    fun changedBindings(): InputBindings =
        InputBindings(actions.bindings().actions.filter { (name, binding) -> binding != defaults.actions[name] })

    /**
     * Rebinds every action [bindings] names, as a file saved from [changedBindings] is loaded, and
     * leaves the rest as they are.
     *
     * @return The names it skipped: no action has them, or the binding is for the other kind of action.
     */
    fun load(bindings: InputBindings): List<String> = actions.rebindAll(bindings)

    /**
     * The entries a player pressing [chord] would also trigger, for a rebinding screen to warn about.
     * An action only [actions] has is left out; [InputActions.actionsBoundTo] names every one.
     */
    fun entriesBoundTo(chord: InputChord): List<KeymapEntry<C>> = actions.actionsBoundTo(chord).mapNotNull(byName::get)
}

/** Whether this frame's press of [entry] reaches its handler: pressed, enabled, and not all taken already. */
private fun <C> InputActions.reaches(entry: KeymapEntry<C>, context: C, source: ActionInputSource?, claims: PressClaims): Boolean =
    wasPressed(entry.name) &&
        entry.enabledWhen(context) &&
        (binding(entry.name) as? ButtonBinding)?.let { source == null || claims.take(it, source) } == true

private fun ActionBinding?.isFor(action: InputActionDefinition): Boolean = when (action) {
    is ButtonAction -> this is ButtonBinding
    is AxisAction -> this is AxisBinding
}

private fun InputActions.refusal(entry: KeymapEntry<*>): String =
    if (entry.name in this) "\"${entry.name}\" is the other kind of action in the actions given" else "The actions given have no \"${entry.name}\""

private fun List<KeymapEntry<*>>.duplicateName(): String? = groupingBy { it.name }.eachCount().entries.firstOrNull { it.value > 1 }?.key

private fun ActionBinding.unbound(): ActionBinding = when (this) {
    is ButtonBinding -> ButtonBinding()
    is AxisBinding -> AxisBinding()
}

/** The keys, controller buttons and pointer buttons the handlers run so far this frame were pressed with. */
private class PressClaims {
    private val keys = BooleanArray(Key.entries.size)
    private val pads = BooleanArray(GamepadButton.entries.size)
    private val pointer = BooleanArray(PointerButton.entries.size)

    fun clear() {
        keys.fill(false)
        pads.fill(false)
        pointer.fill(false)
    }

    /**
     * Takes what [source] pressed this frame of [binding]'s keys and buttons, unless an earlier
     * handler took all of it. A press with none of them pressed, such as an on-screen button's, goes
     * through.
     */
    fun take(binding: ButtonBinding, source: ActionInputSource): Boolean {
        var pressed = 0
        var taken = 0
        forEachPressed(binding, source) { claims, index ->
            pressed++
            if (claims[index]) taken++
        }
        if (pressed > 0 && taken == pressed) return false
        forEachPressed(binding, source) { claims, index -> claims[index] = true }
        return true
    }

    private inline fun forEachPressed(binding: ButtonBinding, source: ActionInputSource, visit: (BooleanArray, Int) -> Unit) {
        for (key in binding.keys) if (source.wasPressed(key)) visit(keys, key.ordinal)
        for (button in binding.buttons) if (source.wasPressed(button)) visit(pointer, button.ordinal)
        for (chord in binding.chords) {
            when (chord) {
                is KeyChord -> if (source.wasPressed(chord.key)) visit(keys, chord.key.ordinal)
                is GamepadChord -> if (source.wasPressed(chord.button)) visit(pads, chord.button.ordinal)
            }
        }
    }
}
