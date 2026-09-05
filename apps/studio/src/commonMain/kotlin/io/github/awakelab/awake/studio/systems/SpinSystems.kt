/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.systems

import io.github.awakelab.awake.ecs.System
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.editor.EditorMode
import io.github.awakelab.awake.editor.EditorStore
import io.github.awakelab.awake.scene.core.transform.SpinControl
import io.github.awakelab.awake.scene.core.transform.SpinSystem

/** Advances every [SpinControl] by its own rate, which is the half [SpinSystem] deliberately
 * does not do. Runs before it, so the composed transform is this frame's angle. */
internal class SpinClockSystem : System {
    override fun update(world: World, delta: Float) {
        world.queryEach(SpinControl::class) { _, spin -> spin.radians += spin.speed * delta }
    }
}

/** Ticks [delegate] only in [EditorMode.Play]. A wrapper rather than a mode check
 * inside each system: the systems are engine-owned, and an editor's mode is not their concern. */
internal class PlayModeSystem(private val delegate: System, private val store: EditorStore) : System {
    override fun update(world: World, delta: Float) {
        if (store.state.mode == EditorMode.Play) delegate.update(world, delta)
    }
}
