/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.PointerButton
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraGesturePolicy
import com.awakekt.awake.scene.controls.camera.CameraMode
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.controls.camera.CameraSystem
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.PlayerInputSystem
import com.awakekt.awake.scene.rendering.Camera
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultitouchAndCaptureTest {
    @Test
    fun draggingUiPanelWhileHoldingWasdContinuesWalking() {
        val input = Input()
        input.setKeyDown(Key.W, true)
        input.setPointer(true, 40f, 50f)
        val snapshot = input.updateSnapshot()

        val ownership = InputOwnership(isCaptured = true, capturedPointerIds = setOf(0L))
        val gameplay = GameplayInput(snapshot, ownership)

        assertFalse(gameplay.pointerDown, "captured mouse pointer must stay hidden from the world")
        assertFalse(gameplay.isDown(PointerButton.Primary))
        assertTrue(gameplay.isDown(Key.W), "UI pointer capture must not stop keyboard movement")

        val world = World()
        val player = world.create()
        world.add(player, MovementControl())
        PlayerInputSystem(inputProvider = { gameplay }).update(world, 1f / 60f)
        assertEquals(1f, world.get(player, MovementControl::class)!!.moveZ)
    }

    @Test
    fun capturedJoystickTouchAllowsSingleFingerCameraOrbitOnViewport() {
        val input = Input()
        val world = World()
        val camera = spawnOrbitCamera(world, distance = 12f)
        val ownership = InputOwnership(isCaptured = true, capturedPointerIds = setOf(1L))
        val system = CameraSystem(
            inputProvider = { GameplayInput(input.currentSnapshot, ownership) },
            viewportBounds = { null },
            gesturePolicy = CameraGesturePolicy.Default,
        )

        // Finger 0 holds the UI joystick; finger 1 drags in the world viewport.
        input.setTouch(0L, 100f, 800f, true)
        input.setTouch(1L, 500f, 400f, true)
        input.updateSnapshot()
        assertFalse(GameplayInput(input.currentSnapshot, ownership).isMultiTouch)
        system.update(world, 1f / 60f)

        val yawBefore = world.get(camera, CameraRig::class)!!.yaw
        input.setTouch(0L, 120f, 760f, true)
        input.setTouch(1L, 620f, 430f, true)
        input.updateSnapshot()

        val gameplay = GameplayInput(input.currentSnapshot, ownership)
        assertTrue(gameplay.pointerDown)
        assertEquals(620f, gameplay.pointerX)
        assertEquals(430f, gameplay.pointerY)
        assertFalse(gameplay.isMultiTouch)

        system.update(world, 1f / 60f)
        val yawAfter = world.get(camera, CameraRig::class)!!.yaw
        assertTrue(yawAfter != yawBefore, "dragging a second finger on the viewport must orbit while holding joystick")
    }

    @Test
    fun capturedJoystickTouchAllowsTwoFingerPinchZoomOnViewport() {
        val input = Input()
        val world = World()
        val camera = spawnOrbitCamera(world, distance = 12f)
        val ownership = InputOwnership(isCaptured = true, capturedPointerIds = setOf(1L))
        val system = CameraSystem(
            inputProvider = { GameplayInput(input.currentSnapshot, ownership) },
            viewportBounds = { null },
            gesturePolicy = CameraGesturePolicy.Default,
        )

        // Finger 0 holds joystick; fingers 1 & 2 start 100px apart, then spread to 200px apart.
        input.setTouch(0L, 100f, 800f, true)
        input.setTouch(1L, 450f, 400f, true)
        input.setTouch(2L, 550f, 400f, true)
        input.updateSnapshot()
        system.update(world, 1f / 60f)
        assertEquals(12f, world.get(camera, CameraRig::class)!!.distance)

        input.setTouch(0L, 110f, 780f, true)
        input.setTouch(1L, 400f, 400f, true)
        input.setTouch(2L, 600f, 400f, true)
        input.updateSnapshot()
        assertTrue(GameplayInput(input.currentSnapshot, ownership).isMultiTouch)

        system.update(world, 1f / 60f)
        val zoomed = world.get(camera, CameraRig::class)!!.distance
        assertTrue(zoomed < 12f, "pinching fingers 1 & 2 apart while holding joystick must zoom in (was $zoomed)")
    }

    private fun spawnOrbitCamera(world: World, distance: Float): Entity {
        val entity = world.create()
        world.add(entity, ActiveCamera())
        world.add(entity, Camera(Lens.perspective(eye = Vec3f(0f, 0f, distance), center = Vec3f(0f, 0f, 0f))))
        world.add(
            entity,
            CameraRig().apply {
                mode = CameraMode.ThirdPerson
                this.distance = distance
                minDistance = 2f
                maxDistance = 30f
                offsetPosition = Vec3f(0f, 0f, 0f)
                needsReset = false
            },
        )
        return entity
    }
}
