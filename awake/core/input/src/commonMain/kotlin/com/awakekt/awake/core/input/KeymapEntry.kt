/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

/**
 * One action in a [Keymap]: its default binding, what a rebinding screen shows for it, and what a
 * press of it does.
 *
 * @param C What [enabledWhen] and [onPress] are given each frame: a game's or a tool's own context, or
 * an ECS world.
 * @property action The action and its default binding, which [Keymap.reset] goes back to.
 * @property label What a rebinding screen calls the action: a localisation key, or the text itself
 * for a tool with one language.
 * @property group What a rebinding screen lists it under, such as `"windows"` or `"combat"`; empty
 * for none.
 * @property priority Which handler a press shared by several actions reaches first: the highest, then
 * the one declared first.
 * @property enabledWhen Whether [onPress] runs this frame. A press it declines goes on to the next
 * handler on the same key. A system that polls the action reads it from [Keymap.actions] whatever
 * this says.
 * @property onPress What a press of the action does, or null for an action a system polls instead.
 */
class KeymapEntry<in C>(
    val action: InputActionDefinition,
    val label: String,
    val group: String = "",
    val priority: Int = 0,
    val enabledWhen: (C) -> Boolean = { true },
    val onPress: ((C) -> Unit)? = null,
) {
    init {
        require(onPress == null || action is ButtonAction) { "\"${action.name}\" is an axis: only a button action is pressed" }
    }

    /** The name systems read the action by. */
    val name: String get() = action.name

    override fun toString(): String = "KeymapEntry($name, label=$label, group=$group, priority=$priority)"
}
