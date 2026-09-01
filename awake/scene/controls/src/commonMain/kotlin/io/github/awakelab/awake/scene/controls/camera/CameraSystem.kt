/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.controls.camera

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.System
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.controls.GameplayInput
import io.github.awakelab.awake.scene.controls.camera.ActiveCamera
import io.github.awakelab.awake.scene.controls.camera.CameraMode
import io.github.awakelab.awake.scene.controls.camera.CameraRig
import io.github.awakelab.awake.scene.core.transform.Transform
import io.github.awakelab.awake.scene.rendering.Camera
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Drives the [ActiveCamera]'s pose from its [CameraRig].
 */
class CameraSystem(
    /** This frame's input, with whatever the UI claimed already taken out. */
    private val inputProvider: () -> GameplayInput,
) : System {
    private var lastPointerX = 0f
    private var lastPointerY = 0f
    private var wasDragging = false

    /** The point being orbited, when it is composed rather than taken straight from a rig. */
    private val scratchPivot = Vec3f(0f, 0f, 0f)

    // Scratch vectors -- this runs every frame, so the pose math must not allocate.
    private val forward = Vec3f()
    private val desiredEye = Vec3f()

    override fun update(world: World, delta: Float) {
        val input = inputProvider()

        val dragging = input.pointerDown
        val dx = if (dragging && wasDragging) input.pointerX - lastPointerX else 0f
        val dy = if (dragging && wasDragging) input.pointerY - lastPointerY else 0f
        lastPointerX = input.pointerX
        lastPointerY = input.pointerY
        wasDragging = dragging

        world.queryEach(Camera::class, CameraRig::class) { entity, camera, config ->
            if (!world.has(entity, ActiveCamera::class)) return@queryEach

            val targetTransform = config.targetEntity?.let { world.get<Transform>(it) }

            if (config.needsReset) {
                resetCameraForMode(config, camera, targetTransform)
                config.needsReset = false
            }

            if (config.mode.usesYaw) {
                config.yaw += dx * LOOK_SENSITIVITY
            }
            if (config.mode.usesPitch) {
                config.pitch = (config.pitch - dy * LOOK_SENSITIVITY)
                    .coerceIn(-PITCH_LIMIT, PITCH_LIMIT)
            }
            if (config.mode.usesZoom) {
                val scroll = input.scrollDeltaY
                config.distance = (config.distance - scroll * ZOOM_SENSITIVITY)
                    .coerceIn(config.minDistance, config.maxDistance)
            }

            updateCameraPose(config, camera, targetTransform, delta)
        }
    }

    private fun resetCameraForMode(config: CameraRig, camera: Camera, target: Transform?) {
        when (config.mode) {
            CameraMode.FirstPerson -> {
                config.pitch = 0f
                config.yaw = 0f
                config.distance = 0f
            }

            CameraMode.ThirdPerson -> {
                config.pitch = THIRD_PERSON_PITCH
                config.yaw = 0f
                config.distance = THIRD_PERSON_DISTANCE
            }

            CameraMode.FreeFly -> {
                config.pitch = 0f
                config.yaw = 0f
                config.distance = 0f
            }

            CameraMode.Cinematic -> {
                if (target != null) {
                    camera.lens.eye.set(
                        target.position.x + CINEMATIC_OFFSET,
                        target.position.y + CINEMATIC_HEIGHT,
                        target.position.z + CINEMATIC_OFFSET,
                    )
                }
            }

            CameraMode.TopDown -> {
                config.distance = TOP_DOWN_DISTANCE
            }
        }
    }

    private fun updateCameraPose(
        config: CameraRig,
        camera: Camera,
        target: Transform?,
        dt: Float,
    ) {
        // A rig with no target used to leave EVERY mode except FreeFly doing nothing at all:
        // no pose written, no error, a camera that silently ignores input. The orbit and top-down
        // modes have an obvious answer -- orbit the world point in `offsetPosition` -- so they
        // now take it, and only the modes that genuinely follow something (first-person's eye,
        // cinematic's look-at) still need one.
        if (target == null && config.mode.needsTarget) return
        val core = camera.lens
        // The point being orbited: the target's position offset, or the offset alone when a rig
        // orbits a place rather than a thing.
        val pivot = if (target == null) config.offsetPosition else scratchPivot.set(target.position).add(config.offsetPosition)
        when (config.mode) {
            CameraMode.FirstPerson -> {
                if (target != null) {
                    core.eye.set(target.position).add(config.offsetPosition)
                }
                forwardFrom(config.yaw, config.pitch, forward)
                core.center.set(core.eye).add(forward)
            }

            CameraMode.ThirdPerson -> {
                core.center.set(pivot)
                forwardFrom(config.yaw, config.pitch, forward)
                desiredEye.set(core.center).sub(forward.scale(config.distance))
                core.eye.lerp(desiredEye, (1f - exp(-SMOOTHING * dt)).coerceIn(0f, 1f))
            }

            CameraMode.FreeFly -> {
                forwardFrom(config.yaw, config.pitch, forward)
                core.center.set(core.eye).add(forward)
            }

            CameraMode.Cinematic -> {
                if (target != null) {
                    core.center.set(target.position)
                }
            }

            CameraMode.TopDown -> {
                run {
                    core.center.set(pivot)
                    forwardFrom(config.yaw, TOP_DOWN_PITCH, forward)
                    core.eye.set(core.center).sub(forward.scale(config.distance))
                }
            }
        }
    }

    private fun forwardFrom(yaw: Float, pitch: Float, out: Vec3f) {
        val cp = cos(pitch)
        out.set(sin(yaw) * cp, sin(pitch), -cos(yaw) * cp)
    }

    private companion object {
        const val LOOK_SENSITIVITY = 0.005f
        const val ZOOM_SENSITIVITY = 0.5f

        const val PITCH_LIMIT = (85.0 * PI / 180.0).toFloat()
        const val TOP_DOWN_PITCH = (-60.0 * PI / 180.0).toFloat()

        const val THIRD_PERSON_PITCH = -0.4f
        const val THIRD_PERSON_DISTANCE = 5f
        const val TOP_DOWN_DISTANCE = 15f
        const val CINEMATIC_OFFSET = 10f
        const val CINEMATIC_HEIGHT = 5f

        const val SMOOTHING = 10f
    }
}
