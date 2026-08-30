/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.controls.components

import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.Poolable

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
    var mode: CameraMode = CameraMode.FirstPerson
        set(value) {
            if (field != value) {
                field = value
                needsReset = true
            }
        }

    var needsReset: Boolean = false

    var targetEntity: Entity? = null

    var distance: Float = DEFAULT_DISTANCE
    var minDistance: Float = DEFAULT_MIN_DISTANCE
    var maxDistance: Float = DEFAULT_MAX_DISTANCE

    var pitch: Float = 0f
    var yaw: Float = 0f

    var offsetPosition: Vec3f = Vec3f(0f, DEFAULT_EYE_HEIGHT, 0f)

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
    }

    private companion object {
        const val DEFAULT_DISTANCE = 5f
        const val DEFAULT_MIN_DISTANCE = 2f
        const val DEFAULT_MAX_DISTANCE = 20f
        const val DEFAULT_EYE_HEIGHT = 1.8f
    }
}

/**
 * Modes defining how camera orientation and position respond to input and targets.
 */
enum class CameraMode(
    val usesYaw: Boolean,
    val usesPitch: Boolean,
    val usesZoom: Boolean,
) {
    FirstPerson(usesYaw = true, usesPitch = true, usesZoom = false),
    ThirdPerson(usesYaw = true, usesPitch = true, usesZoom = true),
    FreeFly(usesYaw = true, usesPitch = true, usesZoom = false),
    Cinematic(usesYaw = false, usesPitch = false, usesZoom = false),
    TopDown(usesYaw = true, usesPitch = false, usesZoom = true),
}
