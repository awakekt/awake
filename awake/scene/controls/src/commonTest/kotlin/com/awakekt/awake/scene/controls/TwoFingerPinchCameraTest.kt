/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.math.CameraPoseState
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Two fingers on a touch screen zoom the camera; the first finger moving with them does not orbit it. */
class TwoFingerPinchCameraTest {
    private val input = Input()
    private var snapshot: InputSnapshot = input.snapshot()

    private fun frame(block: Input.() -> Unit) {
        input.block()
        snapshot = input.snapshot()
    }

    @Test
    fun aPinchZoomsTheRigWithoutOrbitingIt() {
        val world = World()
        val rig = world.spawnRig()
        val system = CameraSystem({ GameplayInput(snapshot, InputOwnership()) })

        frame { setTouch(0, 400f, 500f, down = true) }
        system.update(world, FRAME)
        frame { setTouch(1, 600f, 500f, down = true) }
        system.update(world, FRAME)
        // The fingers spread: the first, which drives the pointer, moves 100px left.
        frame {
            setTouch(0, 300f, 500f, down = true)
            setTouch(1, 700f, 500f, down = true)
            addPinch(2f, 500f, 500f)
        }
        system.update(world, FRAME)

        assertEquals(0f, rig.yaw, "a pinch must not orbit")
        assertEquals(50f, rig.distance, TOLERANCE, "spreading the fingers 2x halves the distance")
    }

    @Test
    fun oneFingerLeftAfterAPinchOrbitsFromWhereItIsWithoutAJump() {
        val world = World()
        val rig = world.spawnRig()
        val system = CameraSystem({ GameplayInput(snapshot, InputOwnership()) })

        frame {
            setTouch(0, 400f, 500f, down = true)
            setTouch(1, 600f, 500f, down = true)
        }
        system.update(world, FRAME)
        frame { setTouch(0, 300f, 500f, down = true) }
        system.update(world, FRAME)
        frame { setTouch(1, 600f, 500f, down = false) }
        system.update(world, FRAME)
        assertEquals(0f, rig.yaw, "lifting the second finger is no drag")

        frame { setTouch(0, 310f, 500f, down = true) }
        system.update(world, FRAME)
        assertNotEquals(0f, rig.yaw, "the finger left down orbits again")
    }

    @Test
    fun theEditorProcessorZoomsAPinchWithoutOrbiting() {
        val processor = CameraInputProcessor()
        val state = CameraPoseState(yaw = 0f, pitch = 0f, distance = 100f)
        val config = CameraControlConfig(orbitSensitivity = 0.01f)

        frame { setTouch(0, 400f, 500f, down = true) }
        processor.process(state, config, snapshot, FRAME)
        frame { setTouch(1, 600f, 500f, down = true) }
        processor.process(state, config, snapshot, FRAME)
        frame {
            setTouch(0, 300f, 500f, down = true)
            setTouch(1, 700f, 500f, down = true)
            addPinch(2f, 500f, 500f)
        }
        assertTrue(processor.process(state, config, snapshot, FRAME))

        assertEquals(0f, state.yaw, "a pinch must not orbit")
        assertEquals(50f, state.distance, TOLERANCE)
    }

    private fun World.spawnRig(): CameraRig {
        val rig = CameraRig().apply {
            mode = CameraMode.ThirdPerson
            offsetPosition = Vec3f(0f, 0f, 0f)
            maxDistance = 1000f
            distance = 100f
            needsReset = false
        }
        create().also { entity ->
            add(entity, Camera(Lens.perspective(eye = Vec3f(0f, 0f, 100f), center = Vec3f(0f, 0f, -1f))))
            add(entity, ActiveCamera())
            add(entity, rig)
        }
        return rig
    }

    private companion object {
        const val FRAME = 0.016f
        const val TOLERANCE = 1e-3f
    }
}
