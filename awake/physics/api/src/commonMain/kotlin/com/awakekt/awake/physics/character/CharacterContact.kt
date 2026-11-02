/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.character

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.ShapeCastHit

/**
 * What the last move of a [KinematicCharacterController] ran into, read back after
 * [KinematicCharacterController.move] or [KinematicCharacterController.moveFree].
 *
 * Reported rather than acted on, like [KinematicCharacterController.onContact]: a caller that bounces,
 * damps or lands a free-moving character decides how from this, and the controller stays the same
 * slide for everyone. Updated in place each move, so reading it allocates nothing.
 */
class CharacterContact internal constructor() {
    /** Whether the move was stopped by something: slid along it, or pushed out of it. */
    var hit: Boolean = false
        private set

    /**
     * The normal of the move's first contact, pointing out of the surface back toward the character,
     * exactly as the sweep reported it: before the slide turned the rest of the motion along it. A
     * caller that would rather reflect than slide reflects its own velocity about this. `(0, 1, 0)`
     * when nothing was [hit].
     */
    val normal: Vec3f = Vec3f(0f, 1f, 0f)

    /** The body of the move's first contact, or null when nothing was [hit]. */
    var body: BodyHandle? = null
        private set

    /**
     * Whether something lay under the capsule within [CharacterConfig.groundProbeDistance] after the
     * move, walkable or not. A free-moving character reads this to find the floor without becoming
     * grounded; a grounded one sees it whenever it stands.
     */
    var supportBelow: Boolean = false
        private set

    internal fun clear() {
        hit = false
        normal.set(0f, 1f, 0f)
        body = null
        supportBelow = false
    }

    /** Keeps [contact] when it is the move's first. */
    internal fun record(contact: ShapeCastHit) {
        if (hit) return
        hit = true
        normal.set(contact.normal)
        body = contact.handle
    }

    internal fun below(found: Boolean) {
        supportBelow = found
    }
}
