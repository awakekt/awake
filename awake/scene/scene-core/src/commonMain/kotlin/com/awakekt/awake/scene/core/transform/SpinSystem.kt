/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.core.transform

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World

/**
 * Drives [Transform.rotation]'s Y angle from [SpinControl.radians], for the rotating-in-place
 * object -- a spinning cube, a coin, a portal -- that every demo otherwise reimplements.
 *
 * It writes an angle and nothing else. [TransformSystem] composes the matrix afterwards, exactly as
 * it does for an entity with no `SpinControl`, so a spun entity is an ordinary transform whose Y
 * rotation happens to be written each frame. That is worth stating because this doc used to claim
 * the system composed `worldMatrix` itself as `translate * rotateY`; it does not, and has not since
 * the body became a single assignment.
 *
 * Doesn't read `delta`: [SpinControl.radians] is expected to already be current, advanced by
 * whoever owns the spin rate -- an auto-play clock, a UI scrub slider, gameplay code -- before this
 * runs. Control carries the state, system only applies it.
 */
class SpinSystem : System {
    override fun update(world: World, delta: Float) {
        world.queryEach(Transform::class, SpinControl::class) { _, transform, spin ->
            transform.rotation.y = spin.radians
        }
    }
}
