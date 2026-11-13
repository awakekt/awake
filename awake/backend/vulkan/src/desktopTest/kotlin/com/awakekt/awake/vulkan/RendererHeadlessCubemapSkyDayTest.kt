/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderpack.SkyboxCubemapLook
import com.awakekt.awake.asset.shaderpack.skyboxCubemapContentFeature
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.createCubemapAsset
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A cubemap sky follows the day: at noon it is exactly the baked panorama, at sunset it is tinted,
 * dimmed and part way to the evening panorama, and at midnight it is the night panorama.
 */
class RendererHeadlessCubemapSkyDayTest {

    @Test
    fun noonIsExactlyTheBakedPanorama() {
        val baked = render(DAY, look = null)
        val noon = render(DAY, SkyboxCubemapLook(), blendTo = NIGHT)

        assertContentEquals(baked, noon, "a white tint, exposure 1 and blend 0 change nothing")
        assertEquals(DAY_COLOUR, noon.colourAt(), "the sky shows the panorama's colour")
    }

    @Test
    fun sunsetTintsDimsAndBlendsTowardTheNextPanorama() {
        val noon = render(DAY, SkyboxCubemapLook(), blendTo = NIGHT).colourAt()

        val tinted = render(DAY, SkyboxCubemapLook(tintG = 0.5f, tintB = 0.25f), blendTo = NIGHT).colourAt()
        assertEquals(noon[0], tinted[0], "the red a tint keeps is left as it was")
        assertTrue(tinted[1] < noon[1] && tinted[2] < tinted[1], "the tint cuts green and then blue: $noon -> $tinted")

        val dimmed = render(DAY, SkyboxCubemapLook(exposure = 0.5f), blendTo = NIGHT).colourAt()
        assertTrue(dimmed.indices.all { dimmed[it] < noon[it] }, "half exposure is darker: $noon -> $dimmed")

        val halfway = render(DAY, SkyboxCubemapLook(blend = 0.5f), blendTo = NIGHT).colourAt()
        val night = render(NIGHT, look = null).colourAt()
        assertTrue(halfway[0] < noon[0] && halfway[0] > night[0], "red is between day and night: $noon, $halfway, $night")
        assertTrue(halfway[2] < noon[2] && halfway[2] > night[2], "blue is between day and night: $noon, $halfway, $night")
    }

    @Test
    fun midnightIsTheNightPanorama() {
        val midnight = render(DAY, SkyboxCubemapLook(blend = 1f), blendTo = NIGHT)

        assertContentEquals(render(NIGHT, look = null), midnight, "blend 1 shows only the second panorama")
        assertEquals(NIGHT_COLOUR, midnight.colourAt())
    }

    private fun render(cubemap: TextureAsset, look: SkyboxCubemapLook?, blendTo: TextureAsset = cubemap): ByteArray {
        val source = if (look == null) {
            skyboxCubemapContentFeature(cubemap)
        } else {
            skyboxCubemapContentFeature(cubemap, blendTo = blendTo, look = look)
        }
        val attached = runBlocking { shared().attacher.attachContentFeature(source) }
        return try {
            shared().render(
                Lens(eye = Vec3f(0f, 0f, 0f), center = Vec3f(1f, 0f, 0f), up = Vec3f(0f, 1f, 0f), fovYRadians = 1f, near = 0.1f, far = 10f),
                EnvironmentUniforms.Default.copy(showSky = true),
            )
        } finally {
            attached.detach()
        }
    }

    private fun ByteArray.colourAt(): List<Int> =
        (0..2).map { this[((SIZE / 2) * SIZE + SIZE / 2) * 4 + it].toInt() and 0xFF }

    private companion object {
        val SIZE = HeadlessContentAttachFixture.TARGET_SIZE
        const val FACE_SIZE = 4
        val DAY_COLOUR = listOf(200, 180, 120)
        val NIGHT_COLOUR = listOf(20, 30, 90)

        fun solid(colour: List<Int>): TextureAsset = createCubemapAsset(
            List(6) { ByteArray(FACE_SIZE * FACE_SIZE * 4) { index -> (if (index % 4 == 3) 255 else colour[index % 4]).toByte() } },
            FACE_SIZE,
        )

        val DAY = solid(DAY_COLOUR)
        val NIGHT = solid(NIGHT_COLOUR)

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
