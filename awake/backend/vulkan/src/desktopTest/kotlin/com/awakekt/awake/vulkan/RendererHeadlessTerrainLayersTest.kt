/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderpack.terrainContentFeature
import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.kit.terrainlayers.TerrainControlMap
import com.awakekt.awake.kit.terrainlayers.TerrainLayer
import com.awakekt.awake.kit.terrainlayers.TerrainLayerPalette
import com.awakekt.awake.kit.terrainlayers.packLayerArray
import com.awakekt.awake.kit.terrainlayers.terrainLayersSurface
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The layered terrain surface on a real device: a control map split between a red and a green
 * layer renders each half in its layer's colour, and the colour crosses the split without a step.
 */
class RendererHeadlessTerrainLayersTest {

    @Test
    fun eachHalfOfTheControlMapShowsItsLayer() {
        val frame = render(split())

        assertTrue(frame.leftIsRed(), "The left half should be the red layer: ${frame.summary()}")
        assertTrue(frame.rightIsGreen(), "The right half should be the green layer: ${frame.summary()}")
    }

    /** Green's share of each covered pixel along the middle row only ever rises, left to right. */
    @Test
    fun theSplitBlendsWithoutAStep() {
        val row = render(split()).middleRowGreenShare()

        assertTrue(row.size > MIN_ROW_PIXELS, "Only ${row.size} covered pixels in the middle row.")
        assertTrue(row.first() < LOW_SHARE && row.last() > HIGH_SHARE, "The row never crosses from red to green: $row")
        row.zipWithNext().forEach { (left, right) ->
            assertTrue(right >= left - STEP_TOLERANCE, "Green's share falls from $left to $right across the split: $row")
        }
    }

    /** Positive control: the same scene with a control map of only the red layer must fail the split check. */
    @Test
    fun aControlMapOfOneLayerFailsTheSplitCheck() {
        val frame = render(TerrainControlMap.reduce(SAMPLES, SAMPLES, layerCount = 2) { layer, _, _ -> if (layer == RED) 1f else 0f }.controlMap)

        assertTrue(frame.leftIsRed())
        assertFalse(frame.rightIsGreen(), "The right half is green with no green in the control map: ${frame.summary()}")
    }

    private fun split() = TerrainControlMap.reduce(SAMPLES, SAMPLES, layerCount = 2) { layer, x, _ ->
        if ((x < SAMPLES / 2) == (layer == RED)) 1f else 0f
    }.controlMap

    private fun render(control: TerrainControlMap): Frame {
        val surface = terrainLayersSurface(PALETTE, packLayerArray(listOf(solid(255, 0, 0), solid(0, 255, 0))), control)
        val attached = runBlocking {
            shared().attacher.attachContentFeature(
                terrainContentFeature(surface.shaders, FLAT, CONFIG, surfaceTextures = surface.textures),
            )
        }
        try {
            return Frame(shared().render(LENS), shared().size)
        } finally {
            attached.detach()
        }
    }

    private class Frame(private val pixels: ByteArray, private val size: Int) {
        private fun channel(x: Int, y: Int, offset: Int) = pixels[(y * size + x) * 4 + offset].toInt() and 0xFF

        private fun covered(x: Int, y: Int) = channel(x, y, 0) + channel(x, y, 1) + channel(x, y, 2) > CLEAR_TOLERANCE

        /** Mean red and green over covered pixels with x in [from, until). */
        private fun mean(from: Int, until: Int): Pair<Float, Float> {
            var red = 0f
            var green = 0f
            var count = 0
            for (y in 0 until size) {
                for (x in from until until) {
                    if (!covered(x, y)) continue
                    red += channel(x, y, 0)
                    green += channel(x, y, 1)
                    count++
                }
            }
            return if (count == 0) 0f to 0f else red / count to green / count
        }

        fun leftIsRed() = mean(0, size * 2 / 5).let { (r, g) -> r > DOMINANCE * g && r > MIN_CHANNEL }

        fun rightIsGreen() = mean(size * 3 / 5, size).let { (r, g) -> g > DOMINANCE * r && g > MIN_CHANNEL }

        fun middleRowGreenShare(): List<Float> {
            val y = (0 until size).maxBy { row -> (0 until size).count { covered(it, row) } }
            return (0 until size).filter { covered(it, y) }.map { x ->
                channel(x, y, 1).toFloat() / (channel(x, y, 0) + channel(x, y, 1)).coerceAtLeast(1)
            }
        }

        fun summary() = "left mean ${mean(0, size * 2 / 5)}, right mean ${mean(size * 3 / 5, size)}"
    }

    private companion object {
        const val SAMPLES = 16
        const val RED = 0
        const val CLEAR_TOLERANCE = 12
        const val DOMINANCE = 3f
        const val MIN_CHANNEL = 40f
        const val MIN_ROW_PIXELS = 32
        const val LOW_SHARE = 0.2f
        const val HIGH_SHARE = 0.8f
        const val STEP_TOLERANCE = 0.02f

        val CONFIG = TerrainClipmapConfig(ringCount = 2, ringResolution = 16, baseSpacing = 1f)
        val FLAT = Heightmap(FloatArray(SAMPLES * SAMPLES), SAMPLES, SAMPLES, Vec3f(1f, 1f, 1f))
        val LENS = Lens(eye = Vec3f(0f, 10f, 9f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f)

        val PALETTE = TerrainLayerPalette(
            layers = listOf(
                TerrainLayer(id = "red", albedo = "red.png"),
                TerrainLayer(id = "green", albedo = "green.png"),
            ),
        )

        fun solid(r: Int, g: Int, b: Int) = TextureAsset(
            ByteArray(2 * 2 * 4) { index -> byteArrayOf(r.toByte(), g.toByte(), b.toByte(), -1)[index % 4] },
            2,
            2,
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
