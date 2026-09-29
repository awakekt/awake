/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.movement

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.rendering.camera.Camera
import kotlin.math.sqrt

/**
 * The active camera's horizontal forward and right, which turn [MovementControl] intent into world
 * motion: forward intent moves where the camera looks. Without an active camera, forward is -Z.
 *
 * Reuses its two vectors, so a system can call [update] every frame without allocating.
 */
class CameraRelativeBasis {
    private val forward = Vec3f(0f, 0f, -1f)
    private val right = Vec3f(1f, 0f, 0f)

    fun update(world: World) {
        forward.set(0f, 0f, -1f)
        right.set(1f, 0f, 0f)
        world.queryEach(Camera::class, ActiveCamera::class) { _, camera, _ ->
            val lens = camera.lens
            // Must end up unit length, or movement speed scales with the camera's distance.
            val dirX = lens.center.x - lens.eye.x
            val dirZ = lens.center.z - lens.eye.z
            val length = sqrt(dirX * dirX + dirZ * dirZ)
            if (length > MIN_DIRECTION_LENGTH) {
                forward.set(dirX / length, 0f, dirZ / length)
                // forward x up, for up = +Y.
                right.set(-forward.z, 0f, forward.x)
            }
        }
    }

    /** World X for [moveX] to the right and [moveZ] forward. */
    fun worldX(moveX: Float, moveZ: Float): Float = right.x * moveX + forward.x * moveZ

    /** World Z for [moveX] to the right and [moveZ] forward. */
    fun worldZ(moveX: Float, moveZ: Float): Float = right.z * moveX + forward.z * moveZ

    private companion object {
        /** Below this the camera looks straight down and has no usable horizontal heading. */
        const val MIN_DIRECTION_LENGTH = 1e-4f
    }
}
