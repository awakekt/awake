/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.camera.CameraCollision
import com.awakekt.awake.core.camera.CameraGround
import com.awakekt.awake.core.camera.CameraObstruction
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraCollisionProbes
import com.awakekt.awake.scene.controls.camera.CameraMode
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.controls.camera.CameraSystem
import com.awakekt.awake.scene.controls.camera.SceneCameraCollision
import com.awakekt.awake.scene.controls.camera.SceneCameraRig
import com.awakekt.awake.scene.controls.movement.registerControls
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A third-person rig orbiting a target at the origin from 5 units behind it, along +z, at head height 1.5. */
class CameraCollisionTest {
    private val idle = Input()

    /** Probes whose wall across z, at [wallZ], the test moves; null for no wall. */
    private class Probes(var wallZ: Float? = null, override val ground: CameraGround? = null) : CameraCollisionProbes {
        override val obstruction = CameraObstruction { from, to, radius ->
            val wall = wallZ
            val reach = if (wall == null) 1f else (wall - radius - from.z) / (to.z - from.z)
            if (reach in 0f..1f) reach else 1f
        }
    }

    private fun rig(world: World, collision: CameraCollision?): Camera {
        val target = world.create()
        world.add(target, Transform())
        val entity = world.create()
        val camera = Camera(Lens.perspective(eye = Vec3f(0f, 1.5f, 5f), center = Vec3f(0f, 1.5f, 0f)))
        world.add(entity, camera)
        world.add(entity, ActiveCamera())
        world.add(
            entity,
            CameraRig().apply {
                mode = CameraMode.ThirdPerson
                targetEntity = target
                distance = 5f
                offsetPosition = Vec3f(0f, 1.5f, 0f)
                this.collision = collision
                needsReset = false
            },
        )
        return camera
    }

    private fun system(probes: CameraCollisionProbes) =
        CameraSystem({ GameplayInput(idle.currentSnapshot, InputOwnership()) }, { null }, collisionProbes = probes)

    @Test
    fun anEyeUnderASlopeIsLifted() {
        val world = World()
        val camera = rig(world, CameraCollision(obstacles = false, groundMargin = 0.5f))
        // The ground climbs toward the eye: 2.5 high under it at z = 5, above the 1.5 it wants.
        val slope = CameraGround { _, z -> z * 0.5f }

        system(Probes(ground = slope)).update(world, 1f / 60f)

        assertTrue(camera.lens.eye.y >= 3f - 1e-4f, "the eye stands groundMargin above the slope, was ${camera.lens.eye.y}")
    }

    @Test
    fun aWallBetweenTheTargetAndTheEyePullsItIn() {
        val world = World()
        val camera = rig(world, CameraCollision(ground = false, radius = 0.2f))

        system(Probes(wallZ = 2f)).update(world, 1f / 60f)

        assertTrue(camera.lens.eye.z < 1.8f, "the eye is in front of the wall at z = 2, was ${camera.lens.eye.z}")
    }

    @Test
    fun theEaseOutReturnsTheEyeToTheDesiredDistance() {
        val world = World()
        val camera = rig(world, CameraCollision(ground = false, easeOutSeconds = 0.2f))
        val probes = Probes(wallZ = 2f)
        val system = system(probes)
        system.update(world, 1f / 60f)

        probes.wallZ = null
        system.update(world, 1f / 60f)
        assertTrue(camera.lens.eye.z < 4f, "a cleared wall eases the eye out, not snaps it: ${camera.lens.eye.z}")
        repeat(180) { system.update(world, 1f / 60f) }

        assertEquals(5f, camera.lens.eye.z, 1e-2f)
    }

    @Test
    fun collisionOffLeavesTheEyeWhereItWas() {
        val withProbes = World()
        val plain = World()
        val collided = rig(withProbes, collision = null)
        val free = rig(plain, collision = null)
        val blocking = system(Probes(wallZ = 2f, ground = { _, _ -> 100f }))
        val none = CameraSystem { GameplayInput(idle.currentSnapshot, InputOwnership()) }

        repeat(10) {
            blocking.update(withProbes, 1f / 60f)
            none.update(plain, 1f / 60f)
        }

        assertEquals(free.lens.eye, collided.lens.eye)
        assertEquals(Vec3f(0f, 1.5f, 5f), collided.lens.eye)
    }

    @Test
    fun anAuthoredCollisionAttachesAndRoundTrips() {
        DefaultSceneComponentResolvers.install()
        val registry = SceneComponentRegistry().registerControls()
        val world = World()
        SceneLoader.decode(SCENE).instantiate(world = world, componentRegistry = registry)

        var rig: CameraRig? = null
        world.queryEach(CameraRig::class) { _, found -> rig = found }
        assertEquals(CameraCollision(groundMargin = 0.5f, radius = 0.25f, easeOutSeconds = 0.5f), rig!!.collision)
        val exported = SceneLoader.fromWorld(world, name = "x", componentRegistry = registry)
            .nodes.flatMap { it.components }.filterIsInstance<SceneCameraRig>().single()
        assertEquals(SceneCameraCollision(groundMargin = 0.5f, radius = 0.25f, easeOutSeconds = 0.5f), exported.collision)
        assertNull(SceneCameraRig().collision, "collision is off unless a scene asks for it")
    }

    @Test
    fun aNegativeCollisionValueIsReported() {
        val issues = SceneCameraRig(collision = SceneCameraCollision(radius = -1f)).validate("nodes[0]")
        assertTrue(issues.any { "radius" in it.message }, issues.toString())
    }

    private companion object {
        const val SCENE = """
{ "version": 1, "name": "x", "nodes": [
  { "name": "Camera", "components": [
    { "component": "camera", "primary": true },
    { "component": "camera_rig", "mode": "ThirdPerson",
      "collision": { "groundMargin": 0.5, "radius": 0.25, "easeOutSeconds": 0.5 } }
  ] }
] }
"""
    }
}
