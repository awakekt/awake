/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.camera

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.damp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Places one follow camera's eye where [CameraCollision] allows it: no farther from the pivot than
 * where the terrain, or a body on one of its obstacle layers, first blocks the way, and no lower than
 * the ground under it plus its margin.
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
        var fraction = 1f
        if (desired > EPSILON && settings.terrain && ground != null) {
            fraction = terrainFraction(pivot, desiredEye, settings.radius, ground)
        }
        if (desired > EPSILON && obstruction != null) {
            fraction = min(fraction, obstacleFraction(pivot, desiredEye, settings, obstruction))
        }
        val allowed = if (fraction >= 1f) desired else (fraction * desired - SKIN).coerceAtLeast(0f)
        distance = eased(allowed, settings.easeOutSeconds, delta)
        out.set(direction).scale(distance).add(pivot)
        if (settings.ground && ground != null) lift(out, settings.groundMargin, ground, footprint)
        return out
    }

    /**
     * How far along the line from [pivot] to [eye] it first passes within [clearance] above the
     * ground: stepped in [TERRAIN_STEPS], then narrowed by halving [REFINE_STEPS] times.
     */
    private fun terrainFraction(pivot: Vec3f, eye: Vec3f, clearance: Float, ground: CameraGround): Float {
        var blocked = -1
        var step = 1
        while (blocked < 0 && step <= TERRAIN_STEPS) {
            if (underground(pivot, eye, step.toFloat() / TERRAIN_STEPS, clearance, ground)) blocked = step
            step++
        }
        if (blocked < 0) return 1f
        var clear = (blocked - 1).toFloat() / TERRAIN_STEPS
        var hit = blocked.toFloat() / TERRAIN_STEPS
        repeat(REFINE_STEPS) {
            val middle = (clear + hit) * HALF
            if (underground(pivot, eye, middle, clearance, ground)) hit = middle else clear = middle
        }
        return clear
    }

    /** Whether the point [t] of the way from [pivot] to [eye] is less than [clearance] above the ground. */
    private fun underground(pivot: Vec3f, eye: Vec3f, t: Float, clearance: Float, ground: CameraGround): Boolean {
        val height = ground.heightAt(pivot.x + (eye.x - pivot.x) * t, pivot.z + (eye.z - pivot.z) * t)
        return !height.isNaN() && pivot.y + (eye.y - pivot.y) * t < height + clearance
    }

    /** The nearest of the sweeps against each of the [settings]' obstacle layers. */
    private fun obstacleFraction(pivot: Vec3f, eye: Vec3f, settings: CameraCollision, obstruction: CameraObstruction): Float {
        var nearest = 1f
        val layers = settings.obstacleLayers
        for (index in layers.indices) {
            val fraction = obstruction.clearFraction(pivot, eye, settings.radius, layers[index])
            // A NaN answer is treated as clear rather than collapsing the camera onto its pivot.
            if (!fraction.isNaN()) nearest = min(nearest, fraction.coerceAtLeast(0f))
        }
        return nearest
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
        const val HALF = 0.5f

        // Sixteen steps find a rise a sixteenth of the follow distance wide; eight halvings place it
        // to within 1/4096 of the way.
        const val TERRAIN_STEPS = 16
        const val REFINE_STEPS = 8

        // Kept between the swept sphere's contact and the eye, so float error never leaves it touching.
        const val SKIN = 0.01f
    }
}
