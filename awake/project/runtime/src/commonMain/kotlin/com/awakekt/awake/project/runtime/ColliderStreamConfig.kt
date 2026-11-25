/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

/**
 * Streaming radii and observer movement hysteresis for [StaticColliderStreamSystem].
 *
 * Each static collider kind uses a hysteresis band (`reach <= leave`) so small observer motions
 * across a boundary do not repeatedly create and destroy physics bodies: a parked collider
 * activates once the observer enters `reach`, and an active collider is destroyed and parked only
 * after the observer moves beyond `leave`.
 *
 * @property terrainReach Horizontal distance from a terrain heightmap footprint within which its
 *   static heightfield collider is active, in metres.
 * @property terrainLeave Horizontal distance beyond which an active terrain collider is destroyed
 *   and parked, in metres. Must be greater than or equal to [terrainReach].
 * @property meshReach Horizontal distance from a static mesh collider's world position within
 *   which its collider is active, in metres.
 * @property meshLeave Horizontal distance beyond which an active static mesh collider is
 *   destroyed and parked, in metres. Must be greater than or equal to [meshReach].
 * @property moveStep Minimum horizontal observer displacement since the last streaming pass before
 *   re-evaluating active and parked colliders, in metres. `0f` evaluates on every tick.
 */
data class ColliderStreamConfig(
    val terrainReach: Float = DEFAULT_TERRAIN_REACH,
    val terrainLeave: Float = DEFAULT_TERRAIN_LEAVE,
    val meshReach: Float = DEFAULT_MESH_REACH,
    val meshLeave: Float = DEFAULT_MESH_LEAVE,
    val moveStep: Float = DEFAULT_MOVE_STEP,
) {
    init {
        require(terrainReach.isFinite() && terrainReach >= 0f) {
            "terrainReach must be finite and non-negative: $terrainReach"
        }
        require(terrainLeave.isFinite() && terrainLeave >= terrainReach) {
            "terrainLeave ($terrainLeave) must be finite and >= terrainReach ($terrainReach)"
        }
        require(meshReach.isFinite() && meshReach >= 0f) {
            "meshReach must be finite and non-negative: $meshReach"
        }
        require(meshLeave.isFinite() && meshLeave >= meshReach) {
            "meshLeave ($meshLeave) must be finite and >= meshReach ($meshReach)"
        }
        require(moveStep.isFinite() && moveStep >= 0f) {
            "moveStep must be finite and non-negative: $moveStep"
        }
    }

    /** Default streaming thresholds for [ColliderStreamConfig]. */
    companion object {
        /** Default activation distance for static terrain colliders, in metres. */
        const val DEFAULT_TERRAIN_REACH: Float = 640f

        /** Default deactivation distance for static terrain colliders, in metres. */
        const val DEFAULT_TERRAIN_LEAVE: Float = 768f

        /** Default activation distance for static mesh colliders, in metres. */
        const val DEFAULT_MESH_REACH: Float = 512f

        /** Default deactivation distance for static mesh colliders, in metres. */
        const val DEFAULT_MESH_LEAVE: Float = 640f

        /** Default horizontal observer displacement before re-evaluating colliders, in metres. */
        const val DEFAULT_MOVE_STEP: Float = 32f
    }
}
