/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.ecs.Entity
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
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CameraRigSceneTest {
    private val registry = SceneComponentRegistry().registerControls()

    init {
        DefaultSceneComponentResolvers.install()
    }

    @Test
    fun anAuthoredRigFollowsItsTargetAndRoundTrips() {
        val world = World()

        SceneLoader.decode(SCENE).instantiate(world = world, componentRegistry = registry)

        var rig: CameraRig? = null
        world.queryEach(CameraRig::class) { _, found -> rig = found }
        val loaded = rig!!
        var player: Entity? = null
        world.queryEach(Name::class) { entity, name -> if (name.value == "Player") player = entity }
        assertEquals(player, loaded.targetEntity, "the rig must follow the node it names")
        assertEquals(CameraMode.ThirdPerson, loaded.mode)
        assertEquals(7f, loaded.distance)
        assertEquals(-0.25f, loaded.pitch)
        val exported = SceneLoader.fromWorld(world, name = "x", componentRegistry = registry)
            .nodes.flatMap { it.components }.filterIsInstance<SceneCameraRig>().single()
        assertEquals(AUTHORED, exported)
    }

    @Test
    fun theFirstCameraUpdateKeepsTheAuthoredAngles() {
        val world = World()
        SceneLoader.decode(SCENE).instantiate(world = world, componentRegistry = registry)
        world.queryEach(CameraRig::class) { entity, _ -> world.add(entity, ActiveCamera()) }
        val idle = Input()

        CameraSystem(inputProvider = { GameplayInput(idle.currentSnapshot, InputOwnership()) }).update(world, 1f / 60f)

        world.queryEach(CameraRig::class) { _, rig ->
            assertEquals(-0.25f, rig.pitch, "a mode reset must not replace the authored pitch")
            assertEquals(7f, rig.distance)
        }
    }

    @Test
    fun aDistanceOutsideItsLimitsIsReported() {
        val issues = SceneCameraRig(distance = 50f, maxDistance = 20f).validate("nodes[0]")
        assertTrue(issues.any { "distance" in it.message }, issues.toString())
    }

    private companion object {
        val AUTHORED = SceneCameraRig(
            mode = CameraMode.ThirdPerson,
            target = "Player",
            distance = 7f,
            pitch = -0.25f,
            offset = SceneVec3(0f, 1.5f, 0f),
        )
        const val SCENE = """
{ "version": 1, "name": "x", "nodes": [
  { "name": "Player" },
  { "name": "Camera", "components": [
    { "component": "camera", "primary": true },
    { "component": "camera_rig", "mode": "ThirdPerson", "target": "Player", "distance": 7.0, "pitch": -0.25,
      "offset": { "x": 0.0, "y": 1.5, "z": 0.0 } }
  ] }
] }
"""
    }
}
