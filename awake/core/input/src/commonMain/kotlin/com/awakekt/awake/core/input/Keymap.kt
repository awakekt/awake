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
 * A caller that feeds [actions] itself, such as on-screen buttons pressing them, calls
 * [InputActions.beginFrame], reads and presses, then [dispatch]. Held actions and axes stay polled:
 * a system asks [actions] for them, as it does without a keymap.
 *
 * A press shared by several handled actions, such as Escape closing a window or else opening a menu,
 * goes to the highest [KeymapEntry.priority] first, then in the order declared. An action whose
 * [KeymapEntry.enabledWhen] is false lets the press go on. A handler that runs takes the keys and
 * buttons it was pressed with, so a handler further down on the same key does not run as well. A
 * press with nothing to take, such as an on-screen button's, always reaches its handler.
 *
 * A player's bindings are the [actions]' own: [InputActions.rebind] changes one, [unbind] and [reset]
 * clear one or put back its default, [changedBindings] is what a settings file saves and [load] reads
 * it back.
 *
 * @param C What handlers are given each frame.
 * @param entries The actions, each with a different name.
 * @throws IllegalArgumentException when two entries have the same name or a name is not an action name.
 */
class Keymap<C>(entries: List<KeymapEntry<C>>) {
    /** The actions in the order given, with their defaults and handlers. */
    val entries: List<KeymapEntry<C>> = entries.toList()

    /** The actions' state this frame and their bindings now, read by [update]. */
    val actions: InputActions = InputActions(this.entries.map { it.action })

    /** Every action's default binding, by name. */
    val defaults: InputBindings = InputBindings(this.entries.associate { it.name to it.action.binding })

    private val byName = this.entries.associateBy { it.name }
    private val handled = this.entries.filter { it.onPress != null }.sortedByDescending { it.priority }
    private val claims = PressClaims()

    /** The entry named [name], or null when none is. */
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
            if (reaches(entry, context, source)) entry.onPress?.invoke(context)
        }
    }

    /** Leaves [name] bound to nothing, so only an on-screen control or code triggers it. */
    fun unbind(name: String) {
        actions.rebind(name, byName.named(name).action.unbound())
    }

    /** Puts back [name]'s default binding. */
    fun reset(name: String) {
        actions.rebind(name, byName.named(name).action.binding)
    }

    /** Puts back every action's default binding. */
    fun resetAll() {
        actions.rebindAll(defaults)
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

    /** The entries a player pressing [chord] would also trigger, for a rebinding screen to warn about. */
    fun entriesBoundTo(chord: InputChord): List<KeymapEntry<C>> = actions.actionsBoundTo(chord).map(byName::getValue)

    /** Whether this frame's press of [entry] reaches its handler: pressed, enabled, and not all taken already. */
    private fun reaches(entry: KeymapEntry<C>, context: C, source: ActionInputSource?): Boolean =
        actions.wasPressed(entry.name) &&
            entry.enabledWhen(context) &&
            (actions.binding(entry.name) as? ButtonBinding)?.let { source == null || claims.take(it, source) } == true
}

private fun <C> Map<String, KeymapEntry<C>>.named(name: String): KeymapEntry<C> =
    requireNotNull(this[name]) { "No action is named \"$name\"" }

private fun InputActionDefinition.unbound(): ActionBinding = when (this) {
    is ButtonAction -> ButtonBinding()
    is AxisAction -> AxisBinding()
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
