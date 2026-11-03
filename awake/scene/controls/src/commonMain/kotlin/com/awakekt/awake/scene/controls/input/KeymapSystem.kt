/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.input

import com.awakekt.awake.core.input.InputActions
import com.awakekt.awake.core.input.InputBindings
import com.awakekt.awake.core.input.Keymap
import com.awakekt.awake.core.input.KeymapEntry
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.controls.movement.MovementActions

/**
 * Runs a [Keymap] over a world each frame, each handler and condition given the context [context]
 * makes of the world. Other systems poll the keymap's held actions and axes from [Keymap.actions].
 *
 * A keymap made by [shareKeymap] shares the scene's input actions:
 * [com.awakekt.awake.scene.controls.movement.PlayerInputSystem] reads the keys into them and a played
 * project's canvas controls push and press them, so this system only runs the handlers. Add it after
 * both. Any other keymap's actions are its own, and this system reads this frame's input into them
 * first.
 */
class KeymapSystem<C>(
    /** The actions, their handlers and their bindings. */
    private val keymap: Keymap<C>,
    /** This frame's input, with whatever the UI claimed already taken out. */
    private val inputProvider: () -> GameplayInput,
    /** What each handler and condition is given, made from the world each frame. */
    private val context: (World) -> C,
) : System {
    override fun update(world: World, delta: Float) {
        val input = inputProvider()
        if (world.inputActions() === keymap.actions) {
            keymap.dispatch(context(world), input)
        } else {
            keymap.update(input, context(world))
        }
    }
}

/** A [KeymapSystem] whose handlers are given the world itself. */
fun KeymapSystem(keymap: Keymap<World>, inputProvider: () -> GameplayInput): KeymapSystem<World> =
    KeymapSystem(keymap, inputProvider) { it }

/**
 * Makes [entries] and the scene's input actions one set, and returns a [Keymap] over it. Its actions
 * become the world's ([inputActions]), so keys, a played project's canvas controls and the keymap's
 * handlers all work on the same actions: a joystick that pushes `move` and the keys a keymap binds to
 * `move` steer the same player, on every platform. Run it with [KeymapSystem] after the systems that
 * feed the actions.
 *
 * The scene's own actions, or [MovementActions.defaults] when it has none, stay as they are. An entry
 * named like one of them replaces it, and where the scene declared that action itself its binding is
 * kept, as a scene rebinds a default. The keymap's defaults are the bindings that result. A saved
 * scene leaves out the actions only the keymap added, which are the player's settings, not the
 * scene's.
 *
 * Call it once the scene is in the world; a scene loaded afterwards brings its own actions.
 */
fun <C> World.shareKeymap(entries: List<KeymapEntry<C>>): Keymap<C> {
    val holder = query(InputActions::class).firstOrNull() ?: create()
    val scene = get(holder, InputActions::class)?.definitions ?: MovementActions.defaults
    val names = entries.mapTo(HashSet()) { it.name }
    val shared = InputActions(entries.map { it.action } + scene.filterNot { it.name in names })
    val declared = scene.filter { it.name in names && it !in MovementActions.defaults }
    shared.rebindAll(InputBindings(declared.associate { it.name to it.binding }))
    val added = names - declared.mapTo(HashSet()) { it.name } + get(holder, KeymapActions::class)?.names.orEmpty()
    add(holder, InputActions::class, shared)
    add(holder, KeymapActions::class, KeymapActions(added))
    return Keymap(entries, shared)
}

/** The actions [shareKeymap] added to a scene's input actions, which [InputActionsBinding] does not save into the scene. */
internal class KeymapActions(val names: Set<String>)
