/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.camera.CameraOrbitButtons
import com.awakekt.awake.core.input.HostInputState
import com.awakekt.awake.core.input.InputDevice
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.input.PointerButton
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraMode
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.controls.camera.CameraSystem
import com.awakekt.awake.scene.controls.camera.SceneCameraRig
import com.awakekt.awake.scene.controls.movement.registerControls
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** A rig's `orbitButtons`: which held pointer button orbits it, and the touch drag that always does. */
class CameraOrbitButtonsTest {
    private val registry = SceneComponentRegistry().registerControls()

    init {
        DefaultSceneComponentResolvers.install()
    }

    @Test
    fun secondaryOnlyIgnoresAPrimaryDragAndOrbitsOnASecondaryDrag() {
        assertEquals(0f, yawAfterDrag(CameraOrbitButtons.SecondaryOnly, primary = true), "a primary drag walks, it must not orbit")
        assertNotEquals(0f, yawAfterDrag(CameraOrbitButtons.SecondaryOnly, secondary = true), "a secondary drag orbits")
    }

    @Test
    fun secondaryOnlyStillOrbitsFromOneFingerOnTouch() {
        assertNotEquals(0f, yawAfterDrag(CameraOrbitButtons.SecondaryOnly, primary = true, touch = true))
    }

    @Test
    fun theDefaultOrbitsOnEitherButtonAsBefore() {
        assertEquals(CameraOrbitButtons.PrimaryOrSecondary, CameraRig().orbitButtons)
        assertEquals(CameraOrbitButtons.PrimaryOrSecondary, SceneCameraRig().orbitButtons)
        val primary = yawAfterDrag(CameraOrbitButtons.PrimaryOrSecondary, primary = true)
        assertNotEquals(0f, primary)
        assertEquals(primary, yawAfterDrag(CameraOrbitButtons.PrimaryOrSecondary, secondary = true))
        assertEquals(primary, yawAfterDrag(CameraOrbitButtons.PrimaryOrSecondary, primary = true, touch = true))
        assertEquals(0f, yawAfterDrag(CameraOrbitButtons.PrimaryOrSecondary))
    }

    @Test
    fun theSceneDocumentRoundTripsTheChoice() {
        val world = World()
        SceneLoader.decode(SCENE).instantiate(world = world, componentRegistry = registry)
        var loaded: CameraRig? = null
        world.queryEach(CameraRig::class) { _, rig -> loaded = rig }
        assertEquals(CameraOrbitButtons.SecondaryOnly, loaded!!.orbitButtons)

        val exported = SceneLoader.fromWorld(world, name = "x", componentRegistry = registry)
        assertEquals(CameraOrbitButtons.SecondaryOnly, exported.nodes.flatMap { it.components }.filterIsInstance<SceneCameraRig>().single().orbitButtons)
        assertTrue("\"orbitButtons\":\"SecondaryOnly\"" in SceneLoader.encode(exported).replace(" ", ""))
    }

    @Test
    fun aDefaultRigSavesNoOrbitButtons() {
        val world = World()
        world.spawnRig(CameraOrbitButtons.PrimaryOrSecondary)
        val saved = SceneLoader.encode(SceneLoader.fromWorld(world, name = "x", componentRegistry = registry))
        assertTrue("orbitButtons" !in saved, saved)
    }

    /** The rig's yaw after one frame pressing and one dragging 100 pixels right. */
    private fun yawAfterDrag(buttons: CameraOrbitButtons, primary: Boolean = false, secondary: Boolean = false, touch: Boolean = false): Float {
        val world = World()
        val rig = world.spawnRig(buttons)
        val held = if (secondary) setOf(PointerButton.Secondary) else emptySet()
        var snapshot = IDLE.copy(pointerDown = primary, pointerFromTouch = touch, secondaryPointerDown = secondary, buttonsDown = held)
        val host = HostInputState()
        val system = CameraSystem({ GameplayInput(snapshot, InputOwnership(), host) })
        host.update(snapshot, showTouchControls = touch)
        if (touch) assertEquals(InputDevice.Touch, host.lastInputDevice)
        system.update(world, FRAME)
        snapshot = snapshot.copy(pointerX = 200f)
        host.update(snapshot, showTouchControls = touch)
        system.update(world, FRAME)
        return rig.yaw
    }

    private fun World.spawnRig(buttons: CameraOrbitButtons): CameraRig {
        val rig = CameraRig().apply {
            mode = CameraMode.ThirdPerson
            offsetPosition = Vec3f(0f, 0f, 0f)
            orbitButtons = buttons
            needsReset = false
        }
        create().also { entity ->
            add(entity, Camera(Lens.perspective(eye = Vec3f(0f, 0f, 5f), center = Vec3f(0f, 0f, 0f))))
            add(entity, ActiveCamera())
            add(entity, rig)
        }
        return rig
    }

    private companion object {
        const val FRAME = 1f / 60f
        val IDLE = InputSnapshot(
            pointerX = 100f,
            pointerY = 100f,
            pointerDown = false,
            scrollDeltaX = 0f,
            scrollDeltaY = 0f,
            keysDown = emptySet(),
            keysPressed = emptySet(),
            keysReleased = emptySet(),
            typedText = "",
            editActions = emptyList(),
        )
        const val SCENE = """
{ "version": 1, "name": "x", "nodes": [
  { "name": "Camera", "components": [
    { "component": "camera", "primary": true },
    { "component": "camera_rig", "mode": "ThirdPerson", "orbitButtons": "SecondaryOnly" }
  ] }
] }
"""
    }
}
