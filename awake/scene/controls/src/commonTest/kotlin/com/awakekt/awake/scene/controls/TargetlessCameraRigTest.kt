/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraMode
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.controls.camera.CameraSystem
import com.awakekt.awake.scene.rendering.Camera
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A rig with no target entity orbits a POINT rather than doing nothing.
 *
 * Every mode except free-fly used to be skipped outright when `targetEntity` was null -- no pose
 * written, no complaint. To whoever was holding the mouse that is not "no target", it is a camera
 * that ignores input, and the two look identical from the outside. Orbiting and top-down have an
 * obvious answer (the point in `offsetPosition`), so they take it; the modes that genuinely
 * follow something still opt out through `CameraMode.needsTarget`.
 */
class TargetlessCameraRigTest {

    @Test
    fun anOrbitRigWithNoTargetStillFramesItsPivot() {
        val world = World()
        val camera = world.spawnOrbitCamera(pivot = Vec3f(4f, 1f, -2f), distance = 6f)

        CameraSystem { GameplayInput(IDLE, InputOwnership()) }.settle(world)

        val lens = requireNotNull(world.get(camera, Camera::class)).lens
        assertEquals(4f, lens.center.x, TOLERANCE, "The pivot is what an orbit looks at.")
        assertEquals(1f, lens.center.y, TOLERANCE)
        assertEquals(-2f, lens.center.z, TOLERANCE)
        assertEquals(
            6f,
            (lens.eye - lens.center).length3(),
            DISTANCE_TOLERANCE,
            "The eye should settle at the rig's distance from that pivot.",
        )
    }

    @Test
    fun draggingATargetlessOrbitMovesTheView() {
        val world = World()
        val camera = world.spawnOrbitCamera(pivot = Vec3f.ZERO, distance = 5f)
        val lens = requireNotNull(world.get(camera, Camera::class)).lens
        var snapshot = IDLE.copy(pointerDown = true)
        val system = CameraSystem { GameplayInput(snapshot, InputOwnership()) }

        system.update(world, 1f)
        val before = Vec3f(lens.eye.x, lens.eye.y, lens.eye.z)
        snapshot = IDLE.copy(pointerX = 60f, pointerDown = true)
        repeat(8) { system.update(world, 1f) }

        assertTrue(
            (lens.eye - before).length3() > MOVED,
            "The eye did not move for a held drag: ${lens.eye}. That is the exact symptom the " +
                "null-target skip produced -- a camera that silently ignores the mouse.",
        )
    }

    private fun World.spawnOrbitCamera(pivot: Vec3f, distance: Float) = create().also { entity ->
        add(entity, Camera(Lens.perspective(eye = Vec3f(0f, 0f, distance), center = Vec3f.ZERO)))
        add(entity, ActiveCamera())
        add(
            entity,
            CameraRig().apply {
                mode = CameraMode.ThirdPerson
                // No targetEntity on purpose: this is the case that used to do nothing.
                offsetPosition = pivot
                this.distance = distance
                needsReset = false
            },
        )
    }

    /** Enough frames for the eye's exponential smoothing to arrive. */
    private fun CameraSystem.settle(world: World) = repeat(SETTLE_FRAMES) { update(world, 1f) }

    private companion object {
        val IDLE = InputSnapshot(
            pointerX = 0f,
            pointerY = 0f,
            pointerDown = false,
            scrollDeltaX = 0f,
            scrollDeltaY = 0f,
            keysDown = emptySet(),
            keysPressed = emptySet(),
            keysReleased = emptySet(),
            typedText = "",
            editActions = emptyList(),
        )
        const val TOLERANCE = 0.001f
        const val DISTANCE_TOLERANCE = 0.05f
        const val MOVED = 0.1f
        const val SETTLE_FRAMES = 12
    }
}
