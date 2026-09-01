/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.physics

import io.github.awakelab.awake.physics.PhysicsWorld
import io.github.awakelab.awake.scene.world.OriginShiftListener

/**
 * Moves the simulation with the scene when the floating origin shifts.
 *
 * `FloatingOriginSystem` rebases `Transform`s, and for anything with a `PhysicsBody` that is not
 * the last word: `PhysicsSystem` writes the simulation's answer back into the transform on the
 * next frame, so a shifted body snaps straight back to where physics still thinks it is. The
 * result is a world that moves and a set of props that do not.
 *
 * Register it with the system that does the shifting:
 *
 * ```
 * val origin = FloatingOriginSystem()
 * origin.addListener(PhysicsOriginShiftListener(physicsWorld))
 * ```
 *
 * Here rather than inside `FloatingOriginSystem`, because `:awake:scene:scene-core` cannot see
 * the physics module and should not: a scene with no physics pays nothing for this, and the
 * dependency points the way the layers already do.
 */
class PhysicsOriginShiftListener(
    private val physicsWorld: PhysicsWorld,
) : OriginShiftListener {
    override fun onOriginShift(
        shift: io.github.awakelab.awake.core.math.Vec3f,
        origin: io.github.awakelab.awake.scene.world.WorldOrigin,
    ) {
        physicsWorld.shiftOrigin(shift)
    }
}
