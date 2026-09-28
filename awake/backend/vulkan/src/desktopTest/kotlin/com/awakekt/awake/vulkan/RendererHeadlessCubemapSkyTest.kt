/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderpack.skyboxCubemapContentFeature
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.texture.createCubemapAsset
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A cubemap sky shows each face where its axis points, laid out the way the GPU samples a cube face.
 *
 * This pins the convention a baked sky is written in: looking along +X with +Y up, a face's first
 * rows are at the top of the screen, and its first columns on the +Z side, which is screen right.
 */
class RendererHeadlessCubemapSkyTest {

    @Test
    fun eachFaceShowsAlongItsAxis() {
        val seen = FACES.map { (forward, up) -> render(forward, up).colourAt(SIZE / 2, SIZE / 2) }

        assertEquals(COLOURS.map { it.map { channel -> channel > HALF } }, seen.map { it.map { channel -> channel > BRIGHT } })
    }

    @Test
    fun aSideFacesFirstRowsAreUpAndItsFirstColumnsFacePlusZ() {
        val pixels = render(FACES[0].first, FACES[0].second)

        val (top, bottom) = pixels.colourAt(SIZE / 2, SIZE / 4) to pixels.colourAt(SIZE / 2, SIZE * 3 / 4)
        assertTrue(top[0] > bottom[0] + MARGIN, "rows 0-1 are brighter red, so they should be above: top $top, bottom $bottom")
        val (left, right) = pixels.colourAt(SIZE / 4, SIZE / 2) to pixels.colourAt(SIZE * 3 / 4, SIZE / 2)
        assertTrue(left[1] > right[1] + MARGIN, "columns 2-3 carry green and face -Z, screen left: left $left, right $right")
    }

    private fun render(forward: Vec3f, up: Vec3f): ByteArray {
        val attached = runBlocking { shared().attacher.attachContentFeature(skyboxCubemapContentFeature(CUBEMAP)) }
        return try {
            shared().render(
                Lens(eye = Vec3f(0f, 0f, 0f), center = forward, up = up, fovYRadians = 1f, near = 0.1f, far = 10f),
                EnvironmentUniforms.Default.copy(showSky = true),
            )
        } finally {
            attached.detach()
        }
    }

    private fun ByteArray.colourAt(x: Int, y: Int): List<Int> = (0..2).map { this[(y * SIZE + x) * 4 + it].toInt() and 0xFF }

    private companion object {
        val SIZE = HeadlessContentAttachFixture.TARGET_SIZE
        const val FACE_SIZE = 4
        const val HALF = 127
        const val BRIGHT = 100
        const val MARGIN = 20

        /** View direction and up for +X, -X, +Y, -Y, +Z, -Z. */
        val FACES = listOf(
            Vec3f(1f, 0f, 0f) to Vec3f(0f, 1f, 0f),
            Vec3f(-1f, 0f, 0f) to Vec3f(0f, 1f, 0f),
            Vec3f(0f, 1f, 0f) to Vec3f(0f, 0f, -1f),
            Vec3f(0f, -1f, 0f) to Vec3f(0f, 0f, 1f),
            Vec3f(0f, 0f, 1f) to Vec3f(0f, 1f, 0f),
            Vec3f(0f, 0f, -1f) to Vec3f(0f, 1f, 0f),
        )

        /** Red, cyan, green, magenta, blue, yellow. */
        val COLOURS = listOf(
            listOf(255, 0, 0),
            listOf(0, 255, 255),
            listOf(0, 255, 0),
            listOf(255, 0, 255),
            listOf(0, 0, 255),
            listOf(255, 255, 0),
        )

        /** Solid faces, except +X: half red in rows 2-3, and green in columns 2-3. */
        val CUBEMAP = createCubemapAsset(
            COLOURS.mapIndexed { face, colour ->
                ByteArray(FACE_SIZE * FACE_SIZE * 4) { index ->
                    val texel = index / 4
                    val (row, column) = texel / FACE_SIZE to texel % FACE_SIZE
                    val value = when {
                        index % 4 == 3 -> 255
                        face != 0 -> colour[index % 4]
                        index % 4 == 0 -> if (row < 2) 255 else HALF
                        index % 4 == 1 -> if (column < 2) 0 else HALF
                        else -> 0
                    }
                    value.toByte()
                }
            },
            FACE_SIZE,
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
