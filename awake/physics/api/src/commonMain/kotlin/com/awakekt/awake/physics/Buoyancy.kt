/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics

import com.awakekt.awake.core.math.Vec3f

/**
 * How a body behaves in a fluid, for [PhysicsWorld.applyBuoyancy].
 *
 * A type rather than six parameters, because Jolt's own call takes nine and the interesting ones
 * are the two that decide whether something floats and how quickly it stops moving.
 */
data class Buoyancy(
    /**
     * How hard the fluid pushes back, as a multiple of the body's own displaced weight.
     *
     * **1 is neutral**: the body hangs wherever it is put, which looks like a bug. Above 1 it
     * rises and bobs, below 1 it sinks slowly. The default floats.
     */
    val strength: Float = 1.2f,
    /** How quickly the fluid stops the body moving through it; 0 is a frictionless fluid. */
    val linearDrag: Float = 0.5f,
    /** How quickly the fluid stops it tumbling. Much smaller than [linearDrag] in practice. */
    val angularDrag: Float = 0.01f,
    /** A current. Zero is still water; a river is a horizontal velocity here. */
    val fluidVelocity: Vec3f = Vec3f(0f, 0f, 0f),
) {
    init {
        require(strength >= 0f && strength.isFinite()) { "strength must be finite and >= 0: $strength" }
        require(linearDrag >= 0f && linearDrag.isFinite()) { "linearDrag must be finite and >= 0: $linearDrag" }
        require(angularDrag >= 0f && angularDrag.isFinite()) {
            "angularDrag must be finite and >= 0: $angularDrag"
        }
    }

    companion object {
        /** Floats and settles: the one to start from. */
        val Water = Buoyancy()

        /** Sinks, slowly, still slowed by the fluid -- mud, tar, a body heavier than what it is in. */
        val Sinking = Buoyancy(strength = 0.6f, linearDrag = 2f)
    }
}
