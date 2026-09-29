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
import kotlin.math.exp
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

    private enum class Drag { Orbit, Pan }

    private var lastPointerX = 0f
    private var lastPointerY = 0f
    private var activeDrag: Drag? = null

    /** The point being orbited, when it is composed rather than taken straight from a rig. */
    private val scratchPivot = Vec3f(0f, 0f, 0f)

    // Scratch vectors -- this runs every frame, so the pose math must not allocate.
    private val forward = Vec3f()
    private val desiredEye = Vec3f()
    private val right = Vec3f()
    private val up = Vec3f()

    override fun update(world: World, delta: Float) {
        val input = inputProvider()

        val bounds = viewportBounds()
        val inViewport = bounds?.contains(input.pointerX, input.pointerY) ?: true
        val drag = currentDrag(input, inViewport)
        val moved = drag != null && drag == activeDrag
        val dx = if (moved) input.pointerX - lastPointerX else 0f
        val dy = if (moved) input.pointerY - lastPointerY else 0f
        lastPointerX = input.pointerX
        lastPointerY = input.pointerY
        activeDrag = drag

        world.queryEach(Camera::class, CameraRig::class) { entity, camera, config ->
            if (!world.has(entity, ActiveCamera::class)) return@queryEach

            val targetTransform = config.targetEntity?.let { world.get<Transform>(it) }

            if (config.needsReset) {
                resetCameraForMode(config, camera, targetTransform)
                config.needsReset = false
            }

            if (drag == Drag.Pan) {
                pan(config, camera, dx, dy)
            } else {
                applyCameraRigInput(config, input, dx, dy, inViewport)
            }
            if (config.mode == CameraMode.FreeFly && gesturePolicy.canFly.isDragging(input)) {
                fly(config, camera, input, delta)
            }
            updateCameraPose(config, camera, targetTransform, delta)
        }
    }

    /** The drag held this frame. A drag must start over the scene, but may leave it once started. */
    private fun currentDrag(input: GameplayInput, inViewport: Boolean): Drag? {
        val held = when {
            gesturePolicy.isPanDragging.isDragging(input) -> Drag.Pan
            gesturePolicy.isOrbitDragging.isDragging(input) -> Drag.Orbit
            else -> null
        }
        return if (held == activeDrag || inViewport) held else null
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
            val step = gesturePolicy.zoomSensitivity + config.distance * gesturePolicy.zoomProportion
            config.distance = (config.distance - input.scrollDeltaY * step)
                .coerceIn(config.minDistance, config.maxDistance)
        }
    }

    /** Drags the scene with the pointer: moves the orbit point, or a free-fly eye, across the view. */
    private fun pan(config: CameraRig, camera: Camera, dx: Float, dy: Float) {
        val moving = when (config.mode) {
            CameraMode.ThirdPerson, CameraMode.TopDown -> config.offsetPosition
            CameraMode.FreeFly -> camera.lens.eye
            CameraMode.FirstPerson, CameraMode.Cinematic -> return
        }
        viewAxes(camera)
        val scale = gesturePolicy.panSensitivity * config.distance.coerceAtLeast(1f)
        val shift = up.scale(dy * scale).add(right.scale(-dx * scale))
        moving.add(shift)
        // The third-person eye eases toward its pose; a pan carries it along instead.
        if (moving !== camera.lens.eye) camera.lens.eye.add(shift)
    }

    /** Moves a free-fly eye along the view with [CameraGesturePolicy.flyKeys]. */
    private fun fly(config: CameraRig, camera: Camera, input: GameplayInput, delta: Float) {
        fun axis(positive: CameraFlyAction, negative: CameraFlyAction) =
            (if (input.holds(positive)) 1f else 0f) - (if (input.holds(negative)) 1f else 0f)
        val ahead = axis(CameraFlyAction.Forward, CameraFlyAction.Back)
        val across = axis(CameraFlyAction.Right, CameraFlyAction.Left)
        val rise = axis(CameraFlyAction.Up, CameraFlyAction.Down)
        if (ahead == 0f && across == 0f && rise == 0f) return
        forwardFrom(config.yaw, config.pitch, forward)
        right.set(cos(config.yaw), 0f, sin(config.yaw))
        desiredEye.set(forward.scale(ahead)).add(right.scale(across)).add(up.set(0f, rise, 0f)).normalize()
        val speed = config.flySpeed * if (input.holds(CameraFlyAction.Fast)) FAST_FLY else 1f
        camera.lens.eye.add(desiredEye.scale(speed * delta))
    }

    private fun GameplayInput.holds(action: CameraFlyAction): Boolean {
        val binding = gesturePolicy.flyKeys.getBinding(action) ?: return false
        return isDown(binding.primary) || binding.secondary?.let(::isDown) == true
    }

    /** Fills [right] and [up] from the lens, so every mode pans in the plane it shows. */
    private fun viewAxes(camera: Camera) {
        forward.set(camera.lens.center).sub(camera.lens.eye).normalize()
        right.set(-forward.z, 0f, forward.x).normalize()
        up.set(
            right.y * forward.z - right.z * forward.y,
            right.z * forward.x - right.x * forward.z,
            right.x * forward.y - right.y * forward.x,
        )
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

            // Keeps the eye, yaw and pitch, so free-fly starts from the view it replaces.
            CameraMode.FreeFly -> Unit

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
                if (config.distance <= 0f) {
                    config.distance = TOP_DOWN_DISTANCE
                }
            }
        }
    }

    private fun updateCameraPose(
        config: CameraRig,
        camera: Camera,
        target: Transform?,
        delta: Float,
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
                core.eye.lerp(desiredEye, (1f - exp(-SMOOTHING * delta)).coerceIn(0f, 1f))
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

        // Exponential easing rate for the third-person eye pose.
        const val SMOOTHING = 10f

        const val FAST_FLY = 4f
    }
}
