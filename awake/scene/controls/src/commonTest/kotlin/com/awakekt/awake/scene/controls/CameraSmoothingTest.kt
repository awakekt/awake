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
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraMode
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.controls.camera.CameraSystem
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.Camera
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CameraSmoothingTest {

    @Test
    fun thirdPersonEyeEasesTowardItsDesiredPoseWithoutOvershooting() {
        val (world, cameraEntity) = thirdPersonCamera()
        val camera = requireNotNull(world.get(cameraEntity, Camera::class))
        val start = copy(camera.lens.eye)
        val desired = Vec3f(-5f, 0f, 0f)
        val delta = 1f / 60f
        val system = CameraSystem { GameplayInput(IDLE, InputOwnership()) }

        system.update(world, delta)

        val amount = 1f - exp(-SMOOTHING_RATE * delta)
        assertEquals(start.x + (desired.x - start.x) * amount, camera.lens.eye.x, TOLERANCE)
        assertEquals(start.y + (desired.y - start.y) * amount, camera.lens.eye.y, TOLERANCE)
        assertEquals(start.z + (desired.z - start.z) * amount, camera.lens.eye.z, TOLERANCE)
        assertTrue(distance(start, camera.lens.eye) > 0f, "The eye should begin moving immediately.")
        assertTrue(
            distance(camera.lens.eye, desired) < distance(start, desired),
            "The eye should move toward its desired pose.",
        )

        var previousDistance = distance(camera.lens.eye, desired)
        repeat(120) {
            system.update(world, delta)
            val eye = camera.lens.eye
            val remainingDistance = distance(eye, desired)
            assertTrue(
                remainingDistance <= previousDistance + TOLERANCE,
                "The eye should converge monotonically without overshooting.",
            )
            assertTrue(eye.x in (desired.x - TOLERANCE)..(start.x + TOLERANCE))
            previousDistance = remainingDistance
        }
        assertTrue(previousDistance < TOLERANCE, "The eye should converge to its desired pose.")
    }

    @Test
    fun thirdPersonEasingIsConsistentAcrossFrameRates() {
        val atSixtyHz = runThirdPersonFor(List(60) { 1f / 60f })
        val atOneTwentyHz = runThirdPersonFor(List(120) { 1f / 120f })

        assertEquals(atSixtyHz.x, atOneTwentyHz.x, RATE_TOLERANCE)
        assertEquals(atSixtyHz.y, atOneTwentyHz.y, RATE_TOLERANCE)
        assertEquals(atSixtyHz.z, atOneTwentyHz.z, RATE_TOLERANCE)
    }

    @Test
    fun firstPersonStillPlacesItsEyeDirectlyAtItsTarget() {
        val world = World()
        val target = world.create()
        world.add(target, Transform())
        val cameraEntity = world.create()
        val camera = Camera(Lens.perspective(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO))
        world.add(cameraEntity, camera)
        world.add(cameraEntity, ActiveCamera())
        world.add(
            cameraEntity,
            CameraRig().apply {
                mode = CameraMode.FirstPerson
                targetEntity = target
                offsetPosition = Vec3f(0f, 1f, 0f)
                needsReset = false
            },
        )

        CameraSystem { GameplayInput(IDLE, InputOwnership()) }.update(world, 1f / 60f)

        assertEquals(0f, camera.lens.eye.x, TOLERANCE)
        assertEquals(1f, camera.lens.eye.y, TOLERANCE)
        assertEquals(0f, camera.lens.eye.z, TOLERANCE)
    }

    private fun runThirdPersonFor(deltas: List<Float>): Vec3f {
        val (world, cameraEntity) = thirdPersonCamera()
        val system = CameraSystem { GameplayInput(IDLE, InputOwnership()) }
        deltas.forEach { system.update(world, it) }
        return copy(requireNotNull(world.get(cameraEntity, Camera::class)).lens.eye)
    }

    private fun thirdPersonCamera(): Pair<World, Entity> {
        val world = World()
        val target = world.create()
        world.add(target, Transform())
        val cameraEntity = world.create()
        world.add(cameraEntity, Camera(Lens.perspective(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO)))
        world.add(cameraEntity, ActiveCamera())
        world.add(
            cameraEntity,
            CameraRig().apply {
                mode = CameraMode.ThirdPerson
                targetEntity = target
                yaw = (PI / 2.0).toFloat()
                pitch = 0f
                distance = 5f
                offsetPosition = Vec3f.ZERO
                needsReset = false
            },
        )
        return world to cameraEntity
    }

    private fun copy(vector: Vec3f) = Vec3f(vector.x, vector.y, vector.z)

    private fun distance(first: Vec3f, second: Vec3f): Float {
        val dx = first.x - second.x
        val dy = first.y - second.y
        val dz = first.z - second.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

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
        const val TOLERANCE = 0.00001f
        const val RATE_TOLERANCE = 0.0001f
        const val SMOOTHING_RATE = 10f
    }
}
