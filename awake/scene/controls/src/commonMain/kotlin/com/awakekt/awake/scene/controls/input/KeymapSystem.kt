/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.input

import com.awakekt.awake.core.input.Keymap
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.GameplayInput

/**
 * Runs a [Keymap] over a world each frame: reads this frame's input into its actions and runs the
 * handlers of what was pressed, each given the context [context] makes of the world. Add it where the
 * handlers should run in the frame, usually with the other input systems before gameplay. Other
 * systems poll the keymap's held actions and axes from [Keymap.actions].
 *
 * The keymap's actions are its own, apart from the scene's `input_actions` that
 * [com.awakekt.awake.scene.controls.movement.PlayerInputSystem] reads, so a canvas control does not
 * press them.
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
        keymap.update(inputProvider(), context(world))
    }
}

/** A [KeymapSystem] whose handlers are given the world itself. */
fun KeymapSystem(keymap: Keymap<World>, inputProvider: () -> GameplayInput): KeymapSystem<World> =
    KeymapSystem(keymap, inputProvider) { it }
