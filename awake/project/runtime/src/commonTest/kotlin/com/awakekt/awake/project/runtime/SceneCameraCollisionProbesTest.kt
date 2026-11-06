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
import com.awakekt.awake.physics.CollisionLayer
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
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

    private companion object {
        val WORLD = CollisionLayer.World.index
    }
}
