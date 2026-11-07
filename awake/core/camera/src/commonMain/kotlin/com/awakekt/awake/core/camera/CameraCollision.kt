/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.camera

import com.awakekt.awake.core.math.Vec3f

/**
 * How a follow camera keeps its eye out of the world: above the ground, and in front of the terrain
 * or the obstacles that stand between it and the point it orbits. [CameraCollisionSolver] applies it.
 *
 * By default only the terrain pulls the eye in. Props, trees and buildings usually shouldn't: a game
 * fades those instead. A game that wants some of them to pull the eye in names their physics layers
 * in [obstacleLayers].
 *
 * The defaults are neutral starting points, not a game's feel; a game authors its own values.
 *
 * @property ground Whether the eye is kept [groundMargin] above the ground under it.
 * @property terrain Whether the eye is pulled in front of the ground when the ground rises between
 *   the pivot and the eye, as over a hill behind the player.
 * @property groundMargin Least height of the eye above the ground, in world units.
 * @property radius Clearance kept between the line from the pivot to the eye and what blocks it, in
 *   world units: the radius of the sphere swept against [obstacleLayers], and how far above the
 *   ground the line must pass for the terrain not to block it.
 * @property easeOutSeconds Half-life of the ease back out once the way clears, in seconds: the eye
 *   covers half of what remains of the way back every [easeOutSeconds]. Zero or less returns at once.
 *   Pulling in is always immediate, so the view never shows the inside of the terrain or a wall.
 * @property obstacleLayers Physics layers whose bodies also pull the eye in, swept through the
 *   host's [CameraObstruction]. Empty, the default, sweeps none.
 */
data class CameraCollision(
    val ground: Boolean = true,
    val terrain: Boolean = true,
    val groundMargin: Float = DEFAULT_GROUND_MARGIN,
    val radius: Float = DEFAULT_RADIUS,
    val easeOutSeconds: Float = DEFAULT_EASE_OUT_SECONDS,
    val obstacleLayers: List<Int> = emptyList(),
) {
    init {
        require(groundMargin >= 0f) { "groundMargin must not be negative: $groundMargin" }
        require(radius >= 0f) { "radius must not be negative: $radius" }
        require(obstacleLayers.all { it >= 0 }) { "obstacleLayers must not be negative: $obstacleLayers" }
    }

    /** Neutral defaults for [CameraCollision]. */
    companion object {
        /** Default [groundMargin], in world units. */
        const val DEFAULT_GROUND_MARGIN = 0.3f

        /** Default [radius], in world units. */
        const val DEFAULT_RADIUS = 0.2f

        /** Default [easeOutSeconds]. */
        const val DEFAULT_EASE_OUT_SECONDS = 0.25f
    }
}

/**
 * The ground a camera keeps above: its height at a world position.
 */
fun interface CameraGround {
    /**
     * World height of the ground at [x], [z], or [Float.NaN] where there is none, such as off the
     * edge of a terrain. Called a few times a frame, so it should not allocate.
     */
    fun heightAt(x: Float, z: Float): Float
}

/**
 * What stands between a camera's pivot and its eye, on one physics layer.
 */
fun interface CameraObstruction {
    /**
     * How far a sphere of [radius] moves from [from] towards [to] before it touches a body on
     * [layer], as a fraction of the way: `1` when nothing is in the way, `0` when it is blocked where
     * it starts. Neither vector may be kept or changed.
     */
    fun clearFraction(from: Vec3f, to: Vec3f, radius: Float, layer: Int): Float
}
