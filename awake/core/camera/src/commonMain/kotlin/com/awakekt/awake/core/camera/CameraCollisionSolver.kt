/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.camera

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.damp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Places one follow camera's eye where [CameraCollision] allows it: no farther from the pivot than
 * the first obstacle along the way, and no lower than the ground under it plus its margin.
 *
 * It keeps the distance it placed the eye at, so it is one solver per camera: an obstacle pulls the
 * eye in at once, and once it clears the eye eases back out over [CameraCollision.easeOutSeconds]
 * rather than jumping. [reset] forgets that, for a camera that cuts to a new view.
 *
 * Nothing here allocates, so it runs every frame.
 */
class CameraCollisionSolver {
    /** Distance from the pivot the eye was last placed at, or [Float.NaN] before the first [solve]. */
    var distance: Float = Float.NaN
        private set

    private val direction = Vec3f()

    /** Forgets the eased distance, so the next [solve] places the eye where it is allowed at once. */
    fun reset() {
        distance = Float.NaN
    }

    /**
     * Writes into [out] the eye for a camera that wants to be at [desiredEye] looking at [pivot], and
     * returns [out]. [ground] and [obstruction] answer where the world is; either may be `null`, as for
     * a host with no terrain or no physics, and that check is skipped. [footprint] is how far around
     * the eye the ground is sampled, about the half-size of the near plane, so the view's edge stays
     * out of a slope as well as its centre. [delta] is the frame time in seconds.
     */
    @Suppress("LongParameterList") // Each is one plain input; bundling them would allocate per frame.
    fun solve(
        pivot: Vec3f,
        desiredEye: Vec3f,
        settings: CameraCollision,
        ground: CameraGround?,
        obstruction: CameraObstruction?,
        footprint: Float,
        delta: Float,
        out: Vec3f,
    ): Vec3f {
        direction.set(desiredEye).sub(pivot)
        val desired = sqrt(direction.x * direction.x + direction.y * direction.y + direction.z * direction.z)
        if (desired > EPSILON) direction.scale(1f / desired) else direction.set(0f, 0f, 0f)
        val allowed = if (settings.obstacles && obstruction != null && desired > EPSILON) {
            unblocked(pivot, desiredEye, desired, settings.radius, obstruction)
        } else {
            desired
        }
        distance = eased(allowed, settings.easeOutSeconds, delta)
        out.set(direction).scale(distance).add(pivot)
        if (settings.ground && ground != null) lift(out, settings.groundMargin, ground, footprint)
        return out
    }

    /** How far the eye can be from the pivot before the swept sphere touches something. */
    private fun unblocked(pivot: Vec3f, desiredEye: Vec3f, desired: Float, radius: Float, obstruction: CameraObstruction): Float {
        val fraction = obstruction.clearFraction(pivot, desiredEye, radius)
        // A NaN answer is treated as clear rather than collapsing the camera onto its pivot.
        if (fraction.isNaN() || fraction >= 1f) return desired
        return (fraction.coerceAtLeast(0f) * desired - SKIN).coerceAtLeast(0f)
    }

    /** In at once, out over the half-life: a wall never shows, and a cleared one doesn't snap. */
    private fun eased(allowed: Float, halfLife: Float, delta: Float): Float {
        val current = distance
        val snap = current.isNaN() || allowed <= current || halfLife <= 0f
        return if (snap) allowed else damp(current, allowed, halfLife, delta).coerceAtMost(allowed)
    }

    /** Raises [eye] to [margin] above the highest ground under it and around it. */
    private fun lift(eye: Vec3f, margin: Float, ground: CameraGround, footprint: Float) {
        val reach = footprint.coerceAtLeast(0f)
        var highest = ground.heightAt(eye.x, eye.z)
        highest = higher(highest, ground.heightAt(eye.x + reach, eye.z))
        highest = higher(highest, ground.heightAt(eye.x - reach, eye.z))
        highest = higher(highest, ground.heightAt(eye.x, eye.z + reach))
        highest = higher(highest, ground.heightAt(eye.x, eye.z - reach))
        if (!highest.isNaN() && eye.y < highest + margin) eye.y = highest + margin
    }

    /** The higher of two heights, either of which may be NaN for no ground. */
    private fun higher(a: Float, b: Float): Float = when {
        a.isNaN() -> b
        b.isNaN() -> a
        else -> max(a, b)
    }

    private companion object {
        const val EPSILON = 1e-5f

        // Kept between the swept sphere's contact and the eye, so float error never leaves it touching.
        const val SKIN = 0.01f
    }
}
