/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.AxisAction
import com.awakekt.awake.core.input.ButtonAction
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.PointerButton
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraFlyAction
import com.awakekt.awake.scene.controls.camera.CameraFlyActions
import com.awakekt.awake.scene.controls.camera.CameraGesturePolicy
import com.awakekt.awake.scene.controls.camera.CameraMode
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.controls.camera.CameraSystem
import com.awakekt.awake.scene.controls.input.keybindingProfile
import com.awakekt.awake.scene.rendering.Camera
import kotlin.test.Test
import kotlin.test.assertEquals

/** Free-fly movement, pan, proportional zoom, and top-down keeping its distance. */
class CameraNavigationTest {

    @Test
    fun freeFlyMovesAlongTheViewAndShiftGoesFaster() {
        val world = World()
        val camera = world.spawnCamera(CameraMode.FreeFly)
        var snapshot = IDLE.copy(keysDown = setOf(Key.W))
        val system = CameraSystem({ GameplayInput(snapshot, InputOwnership()) })
        val eye = world.lens(camera).eye

        system.update(world, HALF_SECOND)
        assertEquals(-5f, eye.z, TOLERANCE, "W at 10 units a second for half a second.")

        snapshot = IDLE.copy(keysDown = setOf(Key.W, Key.Shift))
        system.update(world, HALF_SECOND)
        assertEquals(-25f, eye.z, TOLERANCE, "Shift flies four times as fast.")
    }

    @Test
    fun aHostRebindsTheFlyActions() {
        val world = World()
        val camera = world.spawnCamera(CameraMode.FreeFly)
        var snapshot = IDLE.copy(keysDown = setOf(Key.W))
        val policy = CameraGesturePolicy(
            flyActions = listOf(
                AxisAction(CameraFlyActions.FLY, up = setOf(Key.I), down = setOf(Key.K)),
                ButtonAction(CameraFlyActions.FAST, keys = setOf(Key.Ctrl)),
            ),
        )
        val system = CameraSystem({ GameplayInput(snapshot, InputOwnership()) }, { null }, policy)
        val eye = world.lens(camera).eye

        system.update(world, HALF_SECOND)
        assertEquals(0f, eye.z, TOLERANCE, "W no longer flies")
        snapshot = IDLE.copy(keysDown = setOf(Key.I, Key.Ctrl))
        system.update(world, HALF_SECOND)
        assertEquals(-20f, eye.z, TOLERANCE, "I flies, four times as fast with Ctrl")
    }

    @Suppress("DEPRECATION")
    @Test
    fun flyKeysGivenTheOldWayStillFly() {
        val world = World()
        val camera = world.spawnCamera(CameraMode.FreeFly)
        val policy = CameraGesturePolicy(flyKeys = keybindingProfile { bind(CameraFlyAction.Forward, Key.I) })
        val system = CameraSystem({ GameplayInput(IDLE.copy(keysDown = setOf(Key.I)), InputOwnership()) }, { null }, policy)

        system.update(world, HALF_SECOND)

        assertEquals(-5f, world.lens(camera).eye.z, TOLERANCE)
    }

    @Test
    fun theEditorFliesOnlyWhileRightIsHeld() {
        val world = World()
        val camera = world.spawnCamera(CameraMode.FreeFly)
        var snapshot = IDLE.copy(keysDown = setOf(Key.E))
        val system = editor { snapshot }
        val eye = world.lens(camera).eye

        system.update(world, 1f)
        assertEquals(0f, eye.y, TOLERANCE, "E alone is an editor shortcut, not flight.")

        snapshot = snapshot.copy(buttonsDown = setOf(PointerButton.Secondary))
        system.update(world, 1f)
        assertEquals(10f, eye.y, TOLERANCE, "Holding Right, E rises.")
    }

    @Test
    fun middleDragPansTheOrbitPointWithoutTurning() {
        val world = World()
        val camera = world.spawnCamera(CameraMode.ThirdPerson, distance = 10f)
        val rig = requireNotNull(world.get(camera, CameraRig::class))
        var snapshot = IDLE.copy(buttonsDown = setOf(PointerButton.Middle))
        val system = editor { snapshot }

        system.update(world, 1f)
        snapshot = snapshot.copy(pointerX = 100f)
        system.update(world, 1f)

        assertEquals(-1.5f, rig.offsetPosition.x, TOLERANCE, "100 px at distance 10 drags the pivot 1.5 units left.")
        assertEquals(0f, rig.yaw, TOLERANCE, "A pan must not orbit.")
    }

    @Test
    fun editorZoomKeepsPaceWithDistance() {
        val world = World()
        val camera = world.spawnCamera(CameraMode.ThirdPerson, distance = 100f)
        val rig = requireNotNull(world.get(camera, CameraRig::class))

        editor { IDLE.copy(scrollDeltaY = 1f) }.update(world, 1f)

        assertEquals(89.5f, rig.distance, TOLERANCE, "One notch zooms 0.5 plus a tenth of the distance.")
    }

    @Test
    fun topDownKeepsTheDistanceItWasGiven() {
        val world = World()
        val camera = world.spawnCamera(CameraMode.ThirdPerson, distance = 40f)
        val rig = requireNotNull(world.get(camera, CameraRig::class))

        rig.mode = CameraMode.TopDown
        CameraSystem({ GameplayInput(IDLE, InputOwnership()) }).update(world, 1f)

        assertEquals(40f, rig.distance, TOLERANCE)
    }

    private fun editor(snapshot: () -> InputSnapshot) = CameraSystem(
        inputProvider = { GameplayInput(snapshot(), InputOwnership()) },
        viewportBounds = { null },
        gesturePolicy = CameraGesturePolicy.Editor,
    )

    private fun World.lens(camera: Entity) = requireNotNull(get(camera, Camera::class)).lens

    private fun World.spawnCamera(mode: CameraMode, distance: Float = 5f) = create().also { entity ->
        val eye = if (mode == CameraMode.FreeFly) Vec3f(0f, 0f, 0f) else Vec3f(0f, 0f, distance)
        add(entity, Camera(Lens.perspective(eye = eye, center = Vec3f(0f, 0f, -1f))))
        add(entity, ActiveCamera())
        add(
            entity,
            CameraRig().apply {
                this.mode = mode
                offsetPosition = Vec3f(0f, 0f, 0f)
                maxDistance = 1000f
                this.distance = distance
                needsReset = false
            },
        )
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
        const val HALF_SECOND = 0.5f
        const val TOLERANCE = 0.001f
    }
}
