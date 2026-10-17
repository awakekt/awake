/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a player presses for one action, apart from how the action behaves: a [ButtonBinding]
 * (`"type": "button"`) or an [AxisBinding] (`"type": "axis"`). Rebinding swaps this and keeps the
 * action's name and trigger.
 */
@Serializable
sealed interface ActionBinding

/**
 * What triggers a [ButtonAction]: any of [keys], pointer [buttons] or [chords].
 *
 * @property keys Keys that trigger it whatever else is held, unless a chord on the same key is more specific.
 * @property buttons Pointer buttons that trigger it.
 * @property chords Keys or controller buttons with the modifiers held for them.
 */
@Serializable
@SerialName("button")
data class ButtonBinding(
    val keys: Set<Key> = emptySet(),
    val buttons: Set<PointerButton> = emptySet(),
    val chords: Set<InputChord> = emptySet(),
) : ActionBinding {
    /** Every way to trigger it, for a label: [keys], then [buttons], then [chords]. */
    fun describe(): List<BindingDescription> = buildList {
        keys.forEach { add(BindingDescription(emptyList(), InputControl.Keyboard(it))) }
        buttons.forEach { add(BindingDescription(emptyList(), InputControl.Pointer(it))) }
        chords.forEach { add(it.describe()) }
    }
}

/**
 * The keys that push an [AxisAction] each way.
 *
 * @property up Keys that push it up.
 * @property down Keys that push it down.
 * @property left Keys that push it left.
 * @property right Keys that push it right.
 */
@Serializable
@SerialName("axis")
data class AxisBinding(
    val up: Set<Key> = emptySet(),
    val down: Set<Key> = emptySet(),
    val left: Set<Key> = emptySet(),
    val right: Set<Key> = emptySet(),
) : ActionBinding

/**
 * A player's controls as data, action name to binding, for a settings file to save and load.
 * [InputActions.bindings] takes them and [InputActions.rebindAll] puts them back.
 *
 * ```json
 * { "actions": {
 *   "jump": { "type": "button", "keys": ["Space"] },
 *   "slot1": { "type": "button", "chords": [ { "type": "key", "key": "Digit1", "modifiers": ["Shift"] } ] }
 * } }
 * ```
 *
 * @property actions Each action's binding, by name.
 */
@Serializable
data class InputBindings(val actions: Map<String, ActionBinding> = emptyMap())
