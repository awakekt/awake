/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.light

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.rendering.fog.Fog
import com.awakekt.awake.scene.rendering.sky.Skybox
import kotlinx.serialization.json.Json
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DayCycleTest {

    @Test
    fun theSunRisesAtItsAzimuthPeaksAtNoonSetsOppositeAndIsBelowAtMidnight() {
        val scene = Scene(SceneDayCycle(sunriseAzimuthDegrees = 90f, noonElevationDegrees = 60f))
        val noonY = sin(60f.radians)
        val noonZ = cos(60f.radians)

        assertNear(Vec3f(1f, 0f, 0f), scene.at(0.25f).direction, "rises at azimuth 90, +X, on the horizon")
        assertNear(Vec3f(0f, noonY, noonZ), scene.at(0.5f).direction, "noon is a quarter turn clockwise, +Z")
        assertNear(Vec3f(-1f, 0f, 0f), scene.at(0.75f).direction, "sets opposite its rise")
        assertNear(Vec3f(0f, -noonY, -noonZ), scene.at(0f).direction, "midnight is as far below the horizon")
        assertEquals(60f, asin(scene.at(0.5f).direction.y).degrees, 1e-3f, "noon elevation")
        val highest = (0 until 100).maxBy { scene.at(it / 100f).direction.y }
        assertEquals(50, highest, "no time of day is higher than noon")
    }

    @Test
    fun anotherAzimuthTurnsTheWholePath() {
        val scene = Scene(SceneDayCycle(sunriseAzimuthDegrees = 0f))

        assertNear(Vec3f(0f, 0f, -1f), scene.at(0.25f).direction, "azimuth 0 is -Z")
        assertTrue(scene.at(0.5f).direction.x > 0f, "noon swings to +X, a quarter turn clockwise")
    }

    @Test
    fun midwayBetweenTwoStopsIsTheMidpoint() {
        val scene = Scene(
            SceneDayCycle(
                stops = listOf(
                    SceneDayStop(0.25f, horizonColor = SceneColor(0f, 0f, 0f), lightIntensity = 0.2f, ambient = 0.2f),
                    SceneDayStop(0.75f, horizonColor = SceneColor(1f, 0.5f, 0f), lightIntensity = 1f, ambient = 0.6f),
                ),
            ),
        )

        val light = scene.at(0.5f)

        assertEquals(0.6f, light.intensity, 1e-5f)
        assertEquals(0.4f, light.ambient!!, 1e-5f)
        assertNearColor(Color(0.5f, 0.25f, 0f, 1f), scene.sky.horizonColor)
    }

    @Test
    fun blendingWrapsAcrossMidnight() {
        val scene = Scene(
            SceneDayCycle(stops = listOf(SceneDayStop(0.1f, lightIntensity = 0f), SceneDayStop(0.9f, lightIntensity = 1f))),
        )

        assertEquals(0.5f, scene.at(0f).intensity, 1e-5f, "midnight is halfway from 0.9 to 0.1 of the next day")
        assertEquals(0.75f, scene.at(0.95f).intensity, 1e-5f)
        assertEquals(0.5f, scene.at(0.5f).intensity, 1e-5f, "halfway from 0.1 to 0.9")
    }

    @Test
    fun aFieldNoStopSetsStaysAsAuthored() {
        val scene = Scene(SceneDayCycle(stops = listOf(SceneDayStop(0.5f, lightIntensity = 0.3f))))
        val horizon = scene.sky.horizonColor
        val fogColor = scene.fog.color

        val light = scene.at(0.2f)

        assertEquals(0.3f, light.intensity, "a single stop holds all day")
        assertEquals(Vec3f(1f, 0.9f, 0.8f), light.color)
        assertEquals(null, light.ambient)
        assertSame(horizon, scene.sky.horizonColor)
        assertSame(fogColor, scene.fog.color)
    }

    @Test
    fun fogAndACubemapSkyFollowTheirOwnRules() {
        val stops = listOf(SceneDayStop(0f, horizonColor = SceneColor(1f, 0f, 0f), fogColor = SceneColor(0f, 0f, 1f)))
        val scene = Scene(SceneDayCycle(stops = stops), Skybox(mode = Skybox.Mode.Cubemap(assetPath = "sky.png")))

        scene.at(0.5f)

        assertEquals(Color(0f, 0f, 1f, 1f), scene.fog.color)
        assertIs<Skybox.Mode.Cubemap>(scene.sky.mode, "a cubemap sky keeps its image")
    }

    @Test
    fun timeAdvancesByTheDayLengthAndWraps() {
        val scene = Scene(SceneDayCycle(dayLengthSeconds = 10f, time = 0.9f))

        scene.system.update(scene.world, 2f)

        assertEquals(0.1f, scene.cycle.time, 1e-5f)
        scene.system.update(scene.world, 5f)
        assertEquals(0.6f, scene.cycle.time, 1e-5f)
    }

    @Test
    fun aDayLengthOfZeroHoldsTheTime() {
        val scene = Scene(SceneDayCycle(dayLengthSeconds = 0f, time = 0.25f))

        scene.system.update(scene.world, 100f)

        assertEquals(0.25f, scene.cycle.time)
        assertNear(Vec3f(1f, 0f, 0f), scene.light.direction, "still applied at the held time")
    }

    @Test
    fun invalidValuesAreReported() {
        val issues = SceneDayCycle(
            dayLengthSeconds = -1f,
            time = 1f,
            noonElevationDegrees = 0f,
            stops = listOf(SceneDayStop(0.6f, lightIntensity = -1f), SceneDayStop(0.4f, ambient = 0f)),
        ).validate("sun").map { it.message }

        assertEquals(
            listOf(
                "day_cycle.dayLengthSeconds must be finite and at least 0; was -1.0",
                "day_cycle.time must be from 0 up to 1; was 1.0",
                "day_cycle.noonElevationDegrees must be above 0 and at most 90; was 0.0",
                "day_cycle.stops must be in time order from 0 up to 1",
                "day_cycle stop lightIntensity must be finite and at least 0; was -1.0",
                "day_cycle stop ambient must be above 0 and at most 1; was 0.0",
            ),
            issues,
        )
        assertEquals(emptyList(), SceneDayCycle().validate("sun"))
    }

    @Test
    fun stopsReadFromJsonWithOnlyTheFieldsTheySet() {
        val decoded = Json.decodeFromString(
            SceneDayCycle.serializer(),
            """{"dayLengthSeconds": 120, "stops": [{"time": 0.25, "lightIntensity": 0.5}, {"time": 0.5, "fogColor": "#FF0000"}]}""",
        )

        assertEquals(
            SceneDayCycle(
                dayLengthSeconds = 120f,
                stops = listOf(SceneDayStop(0.25f, lightIntensity = 0.5f), SceneDayStop(0.5f, fogColor = SceneColor(1f, 0f, 0f))),
            ),
            decoded,
        )
    }

    private class Scene(day: SceneDayCycle, val sky: Skybox = Skybox()) {
        val world = World()
        val system = DayCycleSystem()
        val light = Light(color = Vec3f(1f, 0.9f, 0.8f), type = Light.Type.Directional)
        val cycle = DayCycle(day)
        val fog = Fog()

        init {
            val sun = world.create()
            world.add(sun, light)
            world.add(sun, cycle)
            val environment = world.create()
            world.add(environment, sky)
            world.add(environment, fog)
        }

        /** The light after applying [time] of day without advancing it. */
        fun at(time: Float): Light {
            cycle.time = time
            system.update(world, 0f)
            return light
        }
    }

    private fun assertNear(expected: Vec3f, actual: Vec3f, message: String) {
        val close = kotlin.math.abs(expected.x - actual.x) < 1e-5f &&
            kotlin.math.abs(expected.y - actual.y) < 1e-5f &&
            kotlin.math.abs(expected.z - actual.z) < 1e-5f
        assertTrue(close, "$message: expected $expected, was $actual")
    }

    private fun assertNearColor(expected: Color, actual: Color) {
        listOf(expected.r - actual.r, expected.g - actual.g, expected.b - actual.b, expected.a - actual.a)
            .forEach { assertTrue(kotlin.math.abs(it) < 1e-5f, "expected $expected, was $actual") }
    }

    private val Float.radians get() = this * kotlin.math.PI.toFloat() / 180f
    private val Float.degrees get() = this * 180f / kotlin.math.PI.toFloat()
}
