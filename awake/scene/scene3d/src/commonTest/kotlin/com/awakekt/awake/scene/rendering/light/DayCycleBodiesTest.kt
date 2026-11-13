/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.light

import com.awakekt.awake.asset.shaderpack.CelestialLook
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.sky.Skybox
import com.awakekt.awake.scene.rendering.sky.sync
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DayCycleBodiesTest {

    private val stops = listOf(
        SceneDayStop(0.25f, sunVisibility = 0f, moonVisibility = 1f),
        SceneDayStop(0.5f, sunVisibility = 1f, moonVisibility = 0f),
        SceneDayStop(0.75f, sunVisibility = 0f, moonVisibility = 0f),
        SceneDayStop(0.9f, sunVisibility = 0f, moonVisibility = 1f),
    )

    @Test
    fun theSunPathFollowsTheTimeAndMatchesAnUnrotatedLight() {
        val scene = Scene(SceneDayCycle())

        scene.at(0.5f)
        val noon = scene.cycle.sunDirection.copy()
        assertEquals(scene.light.direction, noon, "an unrotated light shines along the sun path")
        scene.at(0.75f)
        assertNotEquals(noon, scene.cycle.sunDirection, "and the path moves with the time")
        assertTrue(scene.cycle.sunDirection.y < noon.y)
    }

    @Test
    fun aRotatedLightHoldsTheGroundLightStillWhileTheSunAndMoonMoveWithTheDay() {
        val scene = Scene(SceneDayCycle(), Transform(rotation = Vec3f(0.6f, 0.3f, 0f)))

        val morning = scene.compiledLight(0.3f)
        val morningSun = scene.cycle.sunDirection.copy()
        val evening = scene.compiledLight(0.7f)
        val eveningSun = scene.cycle.sunDirection.copy()

        assertEquals(morning, evening, "the rotated node fixes the compiled light")
        assertNotEquals(morningSun, eveningSun, "the sun path still moves")

        val look = CelestialLook()
        scene.at(0.3f)
        look.sync(scene.world, Skybox())
        assertTrue(look.followsDirection)
        assertEquals(morningSun, look.sunDirection, "the bodies take the day cycle's sun, not the light's direction")
    }

    @Test
    fun withoutADayCycleTheBodiesTakeTheLightsDirection() {
        val look = CelestialLook(followsDirection = true, sunVisibility = 0f)

        look.sync(World(), null)

        assertEquals(false, look.followsDirection)
        assertEquals(1f, look.sunVisibility)
    }

    @Test
    fun visibilityWindowsBlendByTimeOfDayAndAMissingOneStaysShown() {
        val scene = Scene(SceneDayCycle(stops = stops))

        scene.at(0.375f)
        assertEquals(0.5f, scene.cycle.sunVisibility, 1e-5f, "the sun fades in from sunrise to noon")
        assertEquals(0.5f, scene.cycle.moonVisibility, 1e-5f, "as the moon fades out")
        scene.at(0.75f)
        assertEquals(0f, scene.cycle.sunVisibility, 1e-5f, "neither shows for a while at dusk")
        assertEquals(0f, scene.cycle.moonVisibility, 1e-5f)
        scene.at(0.9f)
        assertEquals(1f, scene.cycle.moonVisibility, 1e-5f, "the moon is up at night")

        val plain = Scene(SceneDayCycle(stops = listOf(SceneDayStop(0.5f, ambient = 0.5f))))
        plain.at(0.1f)
        assertEquals(1f, plain.cycle.sunVisibility)
        assertEquals(1f, plain.cycle.moonVisibility)
        assertEquals(1, SceneDayCycle(stops = listOf(SceneDayStop(0.5f, sunVisibility = 2f))).validate("day_cycle").size)
    }

    private fun Vec3f.copy() = Vec3f(x, y, z)

    private class Scene(day: SceneDayCycle, rotation: Transform? = null) {
        val world = World()
        val system = DayCycleSystem()
        val light = Light(type = Light.Type.Directional)
        val cycle = DayCycle(day)

        init {
            val sun = world.create()
            world.add(sun, light)
            world.add(sun, cycle)
            if (rotation != null) world.add(sun, rotation)
        }

        fun at(time: Float) {
            cycle.time = time
            system.update(world, 0f)
        }

        /** The direction the frame's light shines from, after applying [time] of day. */
        fun compiledLight(time: Float): Vec3f {
            at(time)
            val camera = Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f))
            val direction = SceneLightingCompiler(ClipSpace.WebGpu).sceneLight(world, camera, viewportAspect = 1f).direction
            return Vec3f(direction.x, direction.y, direction.z)
        }
    }
}
