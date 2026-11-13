/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderpack.CelestialLook
import com.awakekt.awake.asset.shaderpack.celestialContentFeature
import com.awakekt.awake.asset.shaderpack.skyboxCubemapContentFeature
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.createCubemapAsset
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The sun and moon are drawn over a cubemap sky, following the light: the sun where it shines from
 * and the moon opposite, at noon, at sunset and at midnight, with the moon's phase cutting its disc.
 */
class RendererHeadlessCelestialSkyTest {

    @Test
    fun atNoonTheSunIsHighAndTheMoonIsOppositeUnderTheGround() {
        val sun = NOON
        assertBright(look(sun), "the sun is where the light shines from")
        assertSky(look(-sun), "the moon is opposite, below the horizon, but a sky pixel nonetheless: full moon is bluish-white")
    }

    @Test
    fun atSunsetTheSunSitsOnTheHorizonAndNothingIsAtItsSide() {
        assertBright(look(SUNSET), "the sun on the horizon")
        assertEquals(SKY, look(SUNSET, yawDegrees = 40f), "40 degrees away is the sky, untouched")
    }

    @Test
    fun atMidnightTheMoonIsUpAndTheSunIsBelow() {
        val sun = Vec3f(0f, -NOON.y, -NOON.z)
        val moon = look(-sun, light = sun)
        assertTrue(moon.all { it > SKY.max() + 40 }, "the full moon is bright over the sky: $moon")
        assertTrue(moon[2] >= moon[0], "the moon is cooler than the sun: $moon")
    }

    @Test
    fun aMoonPhaseCutsTheDiscAlongItsTerminator() {
        val moon = -NOON
        val third = CelestialLook(moonPhase = 0.25f)
        val lookRight = look(moon, light = NOON, phase = third, pixelOffsetX = 3)
        val lookLeft = look(moon, light = NOON, phase = third, pixelOffsetX = -3)
        assertTrue(lookRight.all { it > SKY.max() + 40 }, "a first quarter is lit on the right: $lookRight")
        assertEquals(SKY, lookLeft, "and dark on the left")
        assertEquals(SKY, look(moon, phase = CelestialLook(moonPhase = 0f)), "a new moon shows nothing")
        assertBright(look(moon, phase = CelestialLook(moonPhase = 0.75f), pixelOffsetX = -3), "a last quarter is lit on the left")
        assertEquals(look(moon), look(moon, phase = CelestialLook(moonPhase = 0.5f)), "the default phase is the whole disc")
    }

    @Test
    fun theBodiesFollowTheDayCyclesSunNotAFixedLight() {
        // The scene light is held at noon, as a rotated light node does, while the day cycle's sun is at sunset.
        val followed = CelestialLook(followsDirection = true).also { it.sunDirection.set(SUNSET.x, SUNSET.y, SUNSET.z) }

        assertBright(look(SUNSET, light = NOON, phase = followed), "the sun is where the day cycle puts it")
        assertEquals(SKY, look(NOON, light = NOON, phase = followed), "not where the fixed light shines from")
        assertBright(look(-SUNSET, light = NOON, phase = followed), "and the moon is opposite the day cycle's sun")
    }

    @Test
    fun aVisibilityOfZeroHidesABodyAndAHalfFadesIt() {
        fun visible(sun: Float, moon: Float) = CelestialLook(sunVisibility = sun, moonVisibility = moon)

        assertEquals(SKY, look(SUNSET, phase = visible(0f, 1f)), "no sun at night")
        assertBright(look(-SUNSET, light = SUNSET, phase = visible(0f, 1f)), "the moon is still up")
        assertEquals(SKY, look(-SUNSET, light = SUNSET, phase = visible(1f, 0f)), "no moon by day")
        val half = look(SUNSET, phase = visible(0.5f, 1f))
        assertTrue(half[0] in (SKY[0] + 40)..250, "half visible is between sky and sun: $half")
    }

    @Test
    fun anAdditiveSunAddsToTheSkyInsteadOfCoveringIt() {
        val covering = look(SUNSET)
        val adding = look(SUNSET, additive = true)

        assertEquals(listOf(255, 235, 184), covering, "covering is the sun's own colour")
        assertEquals(listOf(255, 249, 224), adding, "adding is the sky plus the sun, clamped")
    }

    @Test
    fun aTextureFillsItsSquareAndLeavesTransparentPixelsToTheSky() {
        val red = square(listOf(255, 0, 0, 255))
        val corner = look(SUNSET, sun = red, pixelOffsetX = 7, pixelOffsetY = 7)
        assertEquals(listOf(255, 0, 0), corner.map { it }, "the texture reaches the square's corner, outside the default disc")
        assertEquals(SKY, look(SUNSET, sun = red, pixelOffsetX = 14, pixelOffsetY = 14), "and stops at its edge")
        assertEquals(SKY, look(SUNSET, sun = square(listOf(255, 0, 0, 0))), "a transparent texture shows the sky")
    }

    private fun assertBright(colour: List<Int>, message: String) = assertTrue(colour.all { it > SKY.max() + 40 }, "$message: $colour")

    private fun assertSky(colour: List<Int>, message: String) = assertTrue(colour.all { it >= SKY.min() }, "$message: $colour")

    /** The centre pixel (plus an offset) looking along [direction] with the light at [light], default the sun at [direction]. */
    private fun look(
        direction: Vec3f,
        light: Vec3f = if (direction == -NOON) NOON else direction,
        phase: CelestialLook = CelestialLook(),
        sun: TextureAsset? = null,
        yawDegrees: Float = 0f,
        additive: Boolean = false,
        pixelOffsetX: Int = 0,
        pixelOffsetY: Int = 0,
    ): List<Int> {
        val sky = runBlocking { shared().attacher.attachContentFeature(skyboxCubemapContentFeature(SKY_CUBEMAP)) }
        val bodies = runBlocking { shared().attacher.attachContentFeature(celestialContentFeature(sun, null, phase, additive = additive)) }
        try {
            val rotated = rotateYaw(direction, yawDegrees)
            val pixels = shared().render(
                Lens(eye = Vec3f(0f, 0f, 0f), center = rotated, up = Vec3f(0f, 1f, 0f), fovYRadians = 1f, near = 0.1f, far = 10f),
                EnvironmentUniforms.Default.copy(showSky = true),
                SceneLight(direction = light, color = Vec3f(1f, 1f, 1f)),
            )
            return (0..2).map { pixels[((SIZE / 2 + pixelOffsetY) * SIZE + SIZE / 2 + pixelOffsetX) * 4 + it].toInt() and 0xFF }
        } finally {
            bodies.detach()
            sky.detach()
        }
    }

    private fun rotateYaw(v: Vec3f, degrees: Float): Vec3f {
        val a = Math.toRadians(degrees.toDouble())
        return Vec3f((v.x * Math.cos(a) + v.z * Math.sin(a)).toFloat(), v.y, (-v.x * Math.sin(a) + v.z * Math.cos(a)).toFloat())
    }

    private operator fun Vec3f.unaryMinus() = Vec3f(-x, -y, -z)

    private fun square(rgba: List<Int>) = TextureAsset(ByteArray(4 * 4 * 4) { rgba[it % 4].toByte() }, 4, 4)

    private companion object {
        val SIZE = HeadlessContentAttachFixture.TARGET_SIZE
        val SKY = listOf(10, 14, 40)
        val NOON = Vec3f(0f, 0.866f, 0.5f)
        val SUNSET = Vec3f(-1f, 0f, 0f)

        val SKY_CUBEMAP: TextureAsset = createCubemapAsset(
            List(6) { ByteArray(4 * 4 * 4) { index -> (if (index % 4 == 3) 255 else SKY[index % 4]).toByte() } },
            4,
        )

        private var fixture: HeadlessContentAttachFixture? = null

        fun shared(): HeadlessContentAttachFixture = fixture ?: HeadlessContentAttachFixture.create().also { fixture = it }

        @AfterClass
        @JvmStatic
        fun releaseSharedRenderer() {
            fixture?.release()
            fixture = null
        }
    }
}
