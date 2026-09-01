/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.world

import io.github.awakelab.awake.core.math.Vec3f

/** Whole units one origin step covers. See [WorldOrigin.quantum]. */
const val DEFAULT_ORIGIN_QUANTUM = 1024f

/**
 * Where the scene's local origin sits in absolute world space.
 *
 * Everything in the ECS -- every `Transform.position`, every matrix built from one -- is stated
 * relative to this. A scene that never shifts leaves it at zero, and local space and absolute
 * space are then the same thing, which is what every existing scene already assumes.
 *
 * Held in whole [quantum] steps rather than as a float offset, and that is the whole point: a
 * float offset would accumulate exactly the rounding error the shift exists to avoid, so an
 * observer 40km out would carry its imprecision in the origin instead of in its position. An
 * `Int` step count is exact until it overflows, which at the default quantum is past two million
 * kilometres.
 *
 * One instance per scene, on its own entity -- [FloatingOriginSystem] creates it when it first
 * needs one. Anything that stores an absolute coordinate (a streamed cell's contents, a saved
 * position, a map marker) converts through [toLocal]/[toAbsolute] rather than assuming the two
 * agree.
 *
 * @property stepX Steps east of absolute zero.
 * @property stepY Steps above it. Shifted like the others rather than pinned, since nothing here
 * knows which axis a given game calls up.
 * @property stepZ Steps south of it.
 * @property quantum World units per step. Fixed for the life of a scene: changing it would
 * reinterpret every step already taken.
 */
data class WorldOrigin(
    var stepX: Int = 0,
    var stepY: Int = 0,
    var stepZ: Int = 0,
    val quantum: Float = DEFAULT_ORIGIN_QUANTUM,
) {
    /** True while local and absolute space still agree -- nothing has shifted yet. */
    val isZero: Boolean get() = stepX == 0 && stepY == 0 && stepZ == 0

    /** [local] as an absolute world coordinate. Allocates; not for a per-frame loop. */
    fun toAbsolute(local: Vec3f): Vec3f = Vec3f(
        local.x + stepX * quantum,
        local.y + stepY * quantum,
        local.z + stepZ * quantum,
    )

    /** The inverse of [toAbsolute]: an absolute coordinate in this scene's current local space. */
    fun toLocal(absolute: Vec3f): Vec3f = Vec3f(
        absolute.x - stepX * quantum,
        absolute.y - stepY * quantum,
        absolute.z - stepZ * quantum,
    )

    /** [toAbsolute] without the allocation, for a system that already owns a destination. */
    fun toAbsolute(local: Vec3f, target: Vec3f): Vec3f {
        target.x = local.x + stepX * quantum
        target.y = local.y + stepY * quantum
        target.z = local.z + stepZ * quantum
        return target
    }
}

/**
 * Told when the scene's origin moves, so state the ECS does not own can follow it.
 *
 * `Transform.position` is rebased by [FloatingOriginSystem] itself. Anything else holding a
 * world-space coordinate -- a physics body, a navigation grid, a cached path, a spatial index --
 * is invisible to it and has to be told. A listener that ignores the shift leaves its own state
 * pointing at where the world used to be.
 */
fun interface OriginShiftListener {
    /**
     * @param shift Added to every rebased position: the negation of how far the observer moved.
     * @param origin The new origin, already updated when this is called.
     */
    fun onOriginShift(shift: Vec3f, origin: WorldOrigin)
}
