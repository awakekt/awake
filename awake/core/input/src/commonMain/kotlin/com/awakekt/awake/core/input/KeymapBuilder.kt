/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

/** Marks the [KeymapBuilder] DSL, so a nested block cannot reach an outer one by accident. */
@DslMarker
annotation class KeymapDsl

/**
 * A [Keymap] of the actions [block] declares.
 *
 * ```kotlin
 * val keymap = keymap<Game> {
 *     group("windows") {
 *         press("window.inventory", "input.window.inventory", Key.I) { game -> game.toggleInventory() }
 *     }
 *     hold("attack", "input.attack", Key.F)
 *     axis("move", "input.move", up = setOf(Key.W), down = setOf(Key.S), left = setOf(Key.A), right = setOf(Key.D))
 *     layer(priority = 10, enabledWhen = { game -> game.windowOpen }) {
 *         press("window.close", "input.window.close", Key.Escape) { game -> game.closeWindow() }
 *     }
 * }
 * ```
 */
fun <C> keymap(block: KeymapBuilder<C>.() -> Unit): Keymap<C> = Keymap(keymapEntries(block))

/**
 * The entries [block] declares, for a [Keymap] put together from several places: each feature
 * declares its own and one keymap takes them all with [KeymapBuilder.include] or `Keymap(a + b)`.
 */
fun <C> keymapEntries(block: KeymapBuilder<C>.() -> Unit): List<KeymapEntry<C>> =
    ArrayList<KeymapEntry<C>>().also { KeymapBuilder(it, KeymapScope()).block() }

/**
 * Declares a keymap's actions. A [group] or [layer] block passes its group, priority and condition to
 * every action in it; an action's own `enabledWhen` applies as well as its layer's.
 */
@KeymapDsl
class KeymapBuilder<C> internal constructor(
    private val entries: MutableList<KeymapEntry<C>>,
    private val scope: KeymapScope<C>,
) {
    /** A button active for the frame it is pressed, by any of [keys], [chords] and pointer [buttons]. */
    fun press(
        name: String,
        label: String,
        vararg keys: Key,
        chords: Set<InputChord> = emptySet(),
        buttons: Set<PointerButton> = emptySet(),
        enabledWhen: ((C) -> Boolean)? = null,
        onPress: ((C) -> Unit)? = null,
    ) {
        add(ButtonAction(name, keys.toSet(), buttons, ActionTrigger.Press, chords = chords), label, enabledWhen, onPress)
    }

    /** A button active while any of [keys], [chords] and pointer [buttons] is held. */
    fun hold(
        name: String,
        label: String,
        vararg keys: Key,
        chords: Set<InputChord> = emptySet(),
        buttons: Set<PointerButton> = emptySet(),
        enabledWhen: ((C) -> Boolean)? = null,
        onPress: ((C) -> Unit)? = null,
    ) {
        add(ButtonAction(name, keys.toSet(), buttons, ActionTrigger.Hold, chords = chords), label, enabledWhen, onPress)
    }

    /** A button each press switches on or off, on to begin with when [startsOn]. */
    fun toggle(
        name: String,
        label: String,
        vararg keys: Key,
        startsOn: Boolean = false,
        chords: Set<InputChord> = emptySet(),
        buttons: Set<PointerButton> = emptySet(),
        enabledWhen: ((C) -> Boolean)? = null,
        onPress: ((C) -> Unit)? = null,
    ) {
        add(ButtonAction(name, keys.toSet(), buttons, ActionTrigger.Toggle, startsOn, chords), label, enabledWhen, onPress)
    }

    /** A direction on two axes from four sets of keys, as [AxisAction] says. A system polls it. */
    fun axis(
        name: String,
        label: String,
        up: Set<Key> = emptySet(),
        down: Set<Key> = emptySet(),
        left: Set<Key> = emptySet(),
        right: Set<Key> = emptySet(),
    ) {
        add(AxisAction(name, up, down, left, right), label, enabledWhen = null, onPress = null)
    }

    /** An action made elsewhere, such as one of a module's defaults. Only a [ButtonAction] takes [onPress]. */
    fun action(
        definition: InputActionDefinition,
        label: String,
        enabledWhen: ((C) -> Boolean)? = null,
        onPress: ((C) -> Unit)? = null,
    ) {
        add(definition, label, enabledWhen, onPress)
    }

    /** Lists the actions [block] declares under [name] on a rebinding screen. */
    fun group(name: String, block: KeymapBuilder<C>.() -> Unit) {
        KeymapBuilder(entries, KeymapScope(name, scope.priority, scope.enabledWhen)).block()
    }

    /**
     * Gives the handlers [block] declares [priority] over lower ones on the same key, and lets them
     * run only while [enabledWhen] holds, and an outer layer's condition with it.
     */
    fun layer(priority: Int, enabledWhen: ((C) -> Boolean)? = null, block: KeymapBuilder<C>.() -> Unit) {
        KeymapBuilder(entries, KeymapScope(scope.group, priority, both(scope.enabledWhen, enabledWhen))).block()
    }

    /** Adds entries declared elsewhere with [keymapEntries], as they are. */
    fun include(entries: List<KeymapEntry<C>>) {
        this.entries += entries
    }

    private fun add(definition: InputActionDefinition, label: String, enabledWhen: ((C) -> Boolean)?, onPress: ((C) -> Unit)?) {
        val condition = both(scope.enabledWhen, enabledWhen) ?: { true }
        entries += KeymapEntry(definition, label, scope.group, scope.priority, condition, onPress)
    }
}

/** What a [KeymapBuilder.group] or [KeymapBuilder.layer] block passes to the actions in it. */
internal class KeymapScope<C>(
    val group: String = "",
    val priority: Int = 0,
    val enabledWhen: ((C) -> Boolean)? = null,
)

private fun <C> both(first: ((C) -> Boolean)?, second: ((C) -> Boolean)?): ((C) -> Boolean)? = when {
    first == null -> second
    second == null -> first
    else -> { context -> first(context) && second(context) }
}
