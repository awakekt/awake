/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.camera

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.contains
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraMode
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.camera.Camera
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Drives the [ActiveCamera]'s pose from its [CameraRig].
 */
class CameraSystem(
    /** This frame's input, with whatever the UI claimed already taken out. */
    private val inputProvider: () -> GameplayInput,
    /** Optional screen boundaries the 3D scene is confined to. */
    private val viewportBounds: () -> Rectangle?,
    /** Configurable gesture policy for camera navigation. */
    private val gesturePolicy: CameraGesturePolicy = CameraGesturePolicy.Default,
) : System {
    constructor(inputProvider: () -> GameplayInput) : this(inputProvider, { null }, CameraGesturePolicy.Default)
    constructor(inputProvider: () -> GameplayInput, viewportBounds: () -> Rectangle?) : this(
        inputProvider,
        viewportBounds,
        CameraGesturePolicy.Default,
    )

    private var lastPointerX = 0f
    private var lastPointerY = 0f
    private var wasDragging = false

    /** The point being orbited, when it is composed rather than taken straight from a rig. */
    private val scratchPivot = Vec3f(0f, 0f, 0f)

    // Scratch vectors -- this runs every frame, so the pose math must not allocate.
    private val forward = Vec3f()

    @Suppress("UnusedParameter") // System.update supplies frame delta; this rig applies its pose directly.
    override fun update(world: World, delta: Float) {
        val input = inputProvider()

        val bounds = viewportBounds()
        val inViewport = bounds?.contains(input.pointerX, input.pointerY) ?: true
        val isDragActive = gesturePolicy.isOrbitDragging.isDragging(input)
        val dragging = if (wasDragging) isDragActive else (isDragActive && inViewport)
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

            applyCameraRigInput(config, input, dx, dy, inViewport)
            updateCameraPose(config, camera, targetTransform)
        }
    }

    private fun applyCameraRigInput(
        config: CameraRig,
        input: GameplayInput,
        dx: Float,
        dy: Float,
        inViewport: Boolean,
    ) {
        val yawSign = if (gesturePolicy.invertYaw) -1f else 1f
        val pitchSign = if (gesturePolicy.invertPitch) -1f else 1f
        if (config.mode.usesYaw) {
            config.yaw += dx * gesturePolicy.lookSensitivity * yawSign
        }
        if (config.mode.usesPitch) {
            config.pitch = (config.pitch - dy * gesturePolicy.lookSensitivity * pitchSign)
                .coerceIn(-PITCH_LIMIT, PITCH_LIMIT)
        }
        if (config.mode.usesZoom && inViewport) {
            val scroll = input.scrollDeltaY
            config.distance = (config.distance - scroll * gesturePolicy.zoomSensitivity)
                .coerceIn(config.minDistance, config.maxDistance)
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
                if (config.distance <= 0f) {
                    config.distance = THIRD_PERSON_DISTANCE
                }
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
                core.eye.set(core.center).sub(forward.scale(config.distance))
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

        // Retained in the public API dump for binary compatibility; third-person poses are now direct.
        const val SMOOTHING = 10f

    }
}
