/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.CollisionLayers
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.rendering.terrain.TerrainComponent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneCameraCollisionProbesTest {
    private val pivot = Vec3f(0f, 1.5f, 0f)
    private val eye = Vec3f(0f, 1.5f, 10f)

    @Test
    fun aBodyOnTheNamedLayerStopsTheSweepAndOthersDoNot() = runTest {
        val physics = createJoltPhysicsWorld()
        try {
            val probes = SceneCameraCollisionProbes(physics, pagedHeights = null)
            physics.createBody(BoxShape(Vec3f(0.5f, 0.5f, 0.5f)), Vec3f(0f, 1.5f, 3f), Quat(0f, 0f, 0f, 1f), MotionType.DYNAMIC)
            physics.step(1f / 60f)
            assertEquals(1f, probes.obstruction!!.clearFraction(pivot, eye, 0.2f, WORLD), "a moving body is not on the static layer")

            physics.createBody(BoxShape(Vec3f(5f, 5f, 0.25f)), Vec3f(0f, 1.5f, 6f), Quat(0f, 0f, 0f, 1f), MotionType.STATIC)
            physics.step(1f / 60f)
            val swept = probes.obstruction!!.clearFraction(pivot, eye, 0.2f, WORLD)
            val cast = probes.obstruction!!.clearFraction(pivot, eye, 0f, WORLD)

            assertTrue(swept in 0.5f..0.56f, "the sphere stops a radius short of the wall's face at z = 5.75: $swept")
            assertEquals(0.575f, cast, 1e-3f, "a ray with no radius stops at the face")
        } finally {
            physics.destroy()
        }
    }

    @Test
    fun theGroundIsTheTerrainUnderThePosition() {
        // A 3 x 3 terrain 4 units across, centred on the origin, 2 high everywhere.
        val terrain = TerrainComponent(Heightmap(FloatArray(9) { 2f }, 3, 3, Vec3f(2f, 1f, 2f)))
        val world = World()
        world.add(world.create(), terrain)
        val probes = SceneCameraCollisionProbes(physics = null, pagedHeights = null)
        probes.prepare(world)

        assertEquals(2f, probes.ground.heightAt(0.5f, -0.5f), 1e-5f)
        assertTrue(probes.ground.heightAt(50f, 0f).isNaN(), "off the terrain there is no ground")
        assertEquals(null, probes.obstruction, "with no physics world only the ground is kept")
    }

    @Test
    fun anOffsetTerrainAnswersWhereItsTransformPlacesIt() {
        val world = World()
        val home = TerrainComponent(Heightmap(FloatArray(9) { 2f }, 3, 3, Vec3f(2f, 1f, 2f)))
        world.add(world.create(), home)

        // A high adjoining tile placed under a parent node, as a linked area prefab is instantiated.
        val areaRoot = world.create()
        world.add(areaRoot, Transform(position = Vec3f(100f, 3f, 0f)))
        val eastEntity = world.create()
        world.add(eastEntity, Transform(position = Vec3f(20f, 1f, -40f), parent = areaRoot))
        world.add(eastEntity, TerrainComponent(Heightmap(FloatArray(9) { 25f }, 3, 3, Vec3f(2f, 1f, 2f))))

        val probes = SceneCameraCollisionProbes(physics = null, pagedHeights = null)
        probes.prepare(world)

        // Before TransformSystem runs, local hierarchy translations still place the adjoining tile away from the origin.
        assertEquals(2f, probes.ground.heightAt(0f, 0f), 1e-5f, "an offset terrain does not project its heights onto the origin")
        assertEquals(29f, probes.ground.heightAt(120f, -40f), 1e-5f, "height includes both local samples and world Y translation")

        val transforms = TransformSystem()
        transforms.update(world, 0f)
        assertEquals(2f, probes.ground.heightAt(0f, 0f), 1e-5f)
        assertEquals(29f, probes.ground.heightAt(120f, -40f), 1e-5f)

        world.get<Transform>(eastEntity)!!.position.set(50f, 4f, 10f)
        transforms.update(world, 0f)
        assertTrue(probes.ground.heightAt(120f, -40f).isNaN(), "moving the terrain moves its ground samples")
        assertEquals(32f, probes.ground.heightAt(150f, 10f), 1e-5f)
    }

    private companion object {
        val WORLD = CollisionLayers.World.index
    }
}
