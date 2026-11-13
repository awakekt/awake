/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.light

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.rendering.sky.ScenePanorama
import com.awakekt.awake.scene.rendering.sky.Skybox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DayCycleCubemapTest {

    private val stops = listOf(
        SceneDayStop(0.5f, skyTint = SceneColor(1f, 1f, 1f), skyExposure = 1f),
        SceneDayStop(0.75f, skyTint = SceneColor(1f, 0.5f, 0.25f), skyExposure = 0.5f),
        SceneDayStop(0f, skyTint = SceneColor(0f, 0f, 0.5f), skyExposure = 0.1f),
    ).sortedBy { it.time }

    @Test
    fun aCubemapSkyTakesTheBlendedTintAndExposureWithoutChangingItsAuthoredExposure() {
        val sky = Skybox.Mode.Cubemap(assetPath = "sky.png", exposure = 2f)
        val scene = Scene(SceneDayCycle(stops = stops), sky)

        scene.at(0.625f)

        assertEquals(1f, sky.tint.r, 1e-5f)
        assertEquals(0.75f, sky.tint.g, 1e-5f)
        assertEquals(0.625f, sky.tint.b, 1e-5f)
        assertEquals(0.75f, sky.dayExposure, 1e-5f)
        assertEquals(2f, sky.exposure, "the authored exposure is left for saving")
    }

    @Test
    fun aSceneWithNoSkyStopsLeavesTheCubemapAsBaked() {
        val sky = Skybox.Mode.Cubemap(assetPath = "sky.png")
        val scene = Scene(SceneDayCycle(stops = listOf(SceneDayStop(0.5f, ambient = 0.5f))), sky)
        val tint = sky.tint

        scene.at(0.9f)

        assertSame(tint, sky.tint)
        assertEquals(1f, sky.dayExposure)
        assertEquals(0f, sky.blend)
    }

    @Test
    fun twoPanoramasBlendBetweenTheNearestTwoAcrossMidnight() {
        val sky = Skybox.Mode.Cubemap(
            panoramas = listOf(ScenePanorama(0.25f, "day.png"), ScenePanorama(0.5f, "noon.png"), ScenePanorama(0.75f, "night.png")),
        )
        val scene = Scene(SceneDayCycle(), sky)

        scene.at(0.375f)
        assertEquals(Triple(0, 1, 0.5f), Triple(sky.blendFrom, sky.blendTo, sky.blend))

        scene.at(0.875f)
        assertEquals(2, sky.blendFrom, "after the last panorama, it leads")
        assertEquals(0, sky.blendTo, "and the first follows across midnight")
        assertEquals(0.25f, sky.blend, 1e-5f)

        scene.at(0.125f)
        assertEquals(2, sky.blendFrom, "before the first panorama, yesterday's last leads in")
        assertEquals(0.75f, sky.blend, 1e-5f)
    }

    @Test
    fun aSinglePanoramaNeverBlends() {
        val sky = Skybox.Mode.Cubemap(panoramas = listOf(ScenePanorama(0.5f, "only.png")))
        val scene = Scene(SceneDayCycle(), sky)

        scene.at(0.9f)

        assertEquals(Triple(0, 0, 0f), Triple(sky.blendFrom, sky.blendTo, sky.blend))
        assertTrue(sky.panoramas.size == 1)
    }

    private class Scene(day: SceneDayCycle, mode: Skybox.Mode.Cubemap) {
        private val world = World()
        private val system = DayCycleSystem()
        private val cycle = DayCycle(day)

        init {
            val sun = world.create()
            world.add(sun, Light(color = Vec3f(1f, 1f, 1f), type = Light.Type.Directional))
            world.add(sun, cycle)
            world.add(world.create(), Skybox(mode = mode))
        }

        fun at(time: Float) {
            cycle.time = time
            system.update(world, 0f)
        }
    }
}
