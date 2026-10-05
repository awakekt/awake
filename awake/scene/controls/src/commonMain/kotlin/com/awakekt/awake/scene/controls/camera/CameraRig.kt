/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.camera

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Poolable
import kotlinx.serialization.Serializable

/**
 * Tag component to mark an entity as the active camera.
 * Delta inputs are consumed exclusively by the active camera entity.
 */
class ActiveCamera : Poolable {
    override fun reset() = Unit
}

/**
 * How a camera entity is driven -- orbit/follow mode, target tracking, distance, pitch, yaw.
 */
class CameraRig : Poolable {
    /** Active camera mode driving position and orientation updates. */
    var mode: CameraMode = CameraMode.FirstPerson
        set(value) {
            if (field != value) {
                field = value
                needsReset = true
            }
        }

    /** Flag indicating that mode-dependent state needs re-initialization. */
    var needsReset: Boolean = false

    /** Optional target entity that this camera tracks or focuses on. */
    var targetEntity: Entity? = null

    /** Current distance from the target entity or pivot point in world units. */
    var distance: Float = DEFAULT_DISTANCE

    /** Minimum allowable zoom distance from the target in world units. */
    var minDistance: Float = DEFAULT_MIN_DISTANCE

    /** Maximum allowable zoom distance from the target in world units. */
    var maxDistance: Float = DEFAULT_MAX_DISTANCE

    /** Current pitch rotation angle in radians. */
    var pitch: Float = 0f

    /** Current yaw rotation angle in radians. */
    var yaw: Float = 0f

    /** Offset position relative to the target entity or origin in world units. */
    var offsetPosition: Vec3f = Vec3f(0f, DEFAULT_EYE_HEIGHT, 0f)

    /** Units per second a [CameraMode.FreeFly] camera moves. */
    var flySpeed: Float = DEFAULT_FLY_SPEED

    override fun reset() {
        mode = CameraMode.FirstPerson
        needsReset = false
        targetEntity = null
        distance = DEFAULT_DISTANCE
        minDistance = DEFAULT_MIN_DISTANCE
        maxDistance = DEFAULT_MAX_DISTANCE
        pitch = 0f
        yaw = 0f
        offsetPosition.set(0f, DEFAULT_EYE_HEIGHT, 0f)
        flySpeed = DEFAULT_FLY_SPEED
    }

    /**
     * Default constants for [CameraRig].
     */
    companion object {
        /** Default target distance in world units. */
        const val DEFAULT_DISTANCE = 5f

        /** Default minimum target distance in world units. */
        const val DEFAULT_MIN_DISTANCE = 2f

        /** Default maximum target distance in world units. */
        const val DEFAULT_MAX_DISTANCE = 20f

        /** Default eye height offset in world units. */
        const val DEFAULT_EYE_HEIGHT = 1.8f

        /** Default translation speed for free-fly mode in units per second. */
        const val DEFAULT_FLY_SPEED = 10f
    }
}

/**
 * Modes defining how camera orientation and position respond to input and targets.
 *
 * @property usesYaw Whether horizontal yaw rotation input is accepted in this mode.
 * @property usesPitch Whether vertical pitch rotation input is accepted in this mode.
 * @property usesZoom Whether distance zoom input is accepted in this mode.
 * @property needsTarget Whether this mode requires an explicit [CameraRig.targetEntity] to function.
 */
@Serializable
enum class CameraMode(
    val usesYaw: Boolean,
    val usesPitch: Boolean,
    val usesZoom: Boolean,
    /**
     * Whether this mode is meaningless without a [CameraRig.targetEntity].
     *
     * First-person puts the eye AT the target and cinematic looks AT it, so neither has an answer
     * without one. Orbiting and top-down only need a point, which [CameraRig.offsetPosition]
     * supplies -- and a rig with no target used to make those two silently do nothing, which
     * reads as a camera that ignores input rather than as a missing target.
     */
    val needsTarget: Boolean,
) {
    /** Eye placed directly at the target entity with yaw and pitch control. */
    FirstPerson(usesYaw = true, usesPitch = true, usesZoom = false, needsTarget = true),

    /** Orbiting follow camera around a target entity or pivot point with yaw, pitch, and zoom control. */
    ThirdPerson(usesYaw = true, usesPitch = true, usesZoom = true, needsTarget = false),

    /** Unconstrained six-degrees-of-freedom flying camera with yaw and pitch orientation control. */
    FreeFly(usesYaw = true, usesPitch = true, usesZoom = false, needsTarget = false),

    /** Fixed cinematic camera looking at the target entity without manual orientation control. */
    Cinematic(usesYaw = false, usesPitch = false, usesZoom = false, needsTarget = true),

    /** Overhead orthographic-like camera with yaw orientation and zoom control. */
    TopDown(usesYaw = true, usesPitch = false, usesZoom = true, needsTarget = false),
}
