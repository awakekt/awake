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
import com.awakekt.awake.kit.terrainlayers.TerrainLightmap
import com.awakekt.awake.kit.terrainlayers.packLayerArray
import com.awakekt.awake.kit.terrainlayers.terrainLayersSurface
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.RenderDebugView
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
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

    /** Six equal layers at every texel, four red and two blue: blue keeps its third. */
    @Test
    fun aTexelKeepsSixLayers() {
        val share = render(sixLayers { _, _ -> true }, layers = FOUR_RED_TWO_BLUE).middleRowShare(BLUE_CHANNEL)

        assertTrue(share.all { it in 0.28f..0.39f }, "Blue's share should be about 1/3 everywhere: $share")
    }

    /**
     * Four red layers on the left texels and two blue on the right put six layers in the pixels
     * between them, though no texel holds more than four. Blended linearly, they cross exactly as
     * one red and one blue layer split the same way.
     */
    @Test
    fun sixLayersMeetingBetweenTexelsBlendLikeTwo() {
        val six = render(sixLayers { layer, x -> (x < SAMPLES / 2) == (layer < 4) }, layers = FOUR_RED_TWO_BLUE, sharpness = 0f)
            .middleRowShare(BLUE_CHANNEL)
        val two = render(split(), layers = listOf(solid(255, 0, 0), solid(0, 0, 255)), sharpness = 0f).middleRowShare(BLUE_CHANNEL)

        assertTrue(two.first() < LOW_SHARE && two.last() > HIGH_SHARE, "The two-layer row never crosses from red to blue: $two")
        assertEquals(two.size, six.size)
        six.zip(two).forEach { (mine, reference) ->
            assertTrue(abs(mine - reference) <= STEP_TOLERANCE, "Six layers $six\ndiffer from two $two")
        }
    }

    /** Positive control: the same scene with a control map of only the red layer must fail the split check. */
    @Test
    fun aControlMapOfOneLayerFailsTheSplitCheck() {
        val frame = render(allRed())

        assertTrue(frame.leftIsRed())
        assertFalse(frame.rightIsGreen(), "The right half is green with no green in the control map: ${frame.summary()}")
    }

    /** Baked light halves the left half (x0.5) and leaves the right (x1); alpha 255 ignores the sun. */
    @Test
    fun aLightmapScalesTheSurfaceItCovers() {
        val lit = render(allRed(), halvedLeft())
        val neutral = render(allRed())

        val litRatio = lit.redMean(left = true) / lit.redMean(left = false)
        val neutralRatio = neutral.redMean(left = true) / neutral.redMean(left = false)
        assertTrue(litRatio in 0.4f..0.6f, "Left over right red is $litRatio under a lightmap halving the left: ${lit.summary()}")
        assertTrue(neutralRatio > 0.9f, "Without a lightmap both halves should match, but left over right is $neutralRatio.")
    }

    /** Each layer in its own debug colour: the red layer's on the left, the green layer's on the right. */
    @Test
    fun layerWeightsDrawEachLayerInItsOwnColour() {
        val frame = render(split(), view = RenderDebugView.LayerWeights)
        val control = render(allRed(), view = RenderDebugView.LayerWeights)

        assertTrue(frame.mean(left = true).near(layerColour(RED)), "Left ${frame.mean(left = true).toList()} vs ${layerColour(RED).toList()}")
        assertTrue(frame.mean(left = false).near(layerColour(GREEN)), "Right ${frame.mean(left = false).toList()} vs ${layerColour(GREEN).toList()}")
        assertFalse(control.mean(left = false).near(layerColour(GREEN)), "A control map of only red still shows green's colour.")
    }

    /** Across the split the weights view blends; the dominant view only ever shows one layer's colour. */
    @Test
    fun dominantLayerSnapsWhereTheWeightsBlend() {
        val weights = render(split(), view = RenderDebugView.LayerWeights).middleRow()
        val dominant = render(split(), view = RenderDebugView.DominantLayer).middleRow()
        fun FloatArray.isALayer() = near(layerColour(RED)) || near(layerColour(GREEN))

        assertTrue(dominant.all { it.isALayer() }, "Dominant pixels between layers: ${dominant.filterNot { it.isALayer() }.map { it.toList() }}")
        assertTrue(dominant.any { it.near(layerColour(RED)) } && dominant.any { it.near(layerColour(GREEN)) }, "Dominant row lacks a layer.")
        assertTrue(weights.any { !it.isALayer() }, "The weights row never blends, so the dominant check proves nothing.")
    }

    /** The bake as stored: 64 on the halved left, 128 (x1) on the right. */
    @Test
    fun lightmapViewShowsTheBakeAsStored() {
        val halved = render(allRed(), halvedLeft(), RenderDebugView.Lightmap)
        val neutral = render(allRed(), view = RenderDebugView.Lightmap)

        assertTrue(halved.mean(left = true).near(grey(HALF_LIGHT)), "Halved left ${halved.mean(left = true).toList()}")
        assertTrue(halved.mean(left = false).near(grey(NEUTRAL_LIGHT)), "Neutral right ${halved.mean(left = false).toList()}")
        assertFalse(neutral.mean(left = true).near(grey(HALF_LIGHT)), "A neutral lightmap reads as the halved one.")
    }

    /** The generic views reach the layered surface: unlit albedo, and the flat heightmap's up normal. */
    @Test
    fun albedoAndNormalsReachTheLayeredSurface() {
        val albedo = render(split(), halvedLeft(), RenderDebugView.Albedo)
        val lit = render(split(), halvedLeft())
        val normals = render(split(), view = RenderDebugView.WorldNormals)
        val red = floatArrayOf(255f, 0f, 0f)

        assertTrue(albedo.mean(left = true).near(red), "Albedo left ${albedo.mean(left = true).toList()}")
        assertFalse(lit.mean(left = true).near(red), "The lit left half is already unlit red.")
        assertTrue(normals.mean(left = true).near(floatArrayOf(128f, 255f, 128f)), "Normals ${normals.mean(left = true).toList()}")
    }

    private fun halvedLeft() = TerrainLightmap(SAMPLES, SAMPLES, ByteArray(SAMPLES * SAMPLES * 4) { index ->
        val x = (index / 4) % SAMPLES
        if (index % 4 == 3) -1 else if (x < SAMPLES / 2) HALF_LIGHT else NEUTRAL_LIGHT
    })

    private fun allRed() = TerrainControlMap.reduce(SAMPLES, SAMPLES, layerCount = 2) { layer, _, _ -> if (layer == RED) 1f else 0f }.controlMap

    private fun split() = TerrainControlMap.reduce(SAMPLES, SAMPLES, layerCount = 2) { layer, x, _ ->
        if ((x < SAMPLES / 2) == (layer == RED)) 1f else 0f
    }.controlMap

    private fun sixLayers(covers: (layer: Int, x: Int) -> Boolean) =
        TerrainControlMap.reduce(SAMPLES, SAMPLES, layerCount = 6) { layer, x, _ -> if (covers(layer, x)) 1f else 0f }.controlMap

    private fun render(
        control: TerrainControlMap,
        lightmap: TerrainLightmap = TerrainLightmap.Neutral,
        view: RenderDebugView = RenderDebugView.Off,
        layers: List<TextureAsset> = listOf(solid(255, 0, 0), solid(0, 255, 0)),
        sharpness: Float = TerrainLayer(id = "", albedo = "").blendSharpness,
    ): Frame {
        val palette = TerrainLayerPalette(
            layers = layers.indices.map { TerrainLayer(id = "layer$it", albedo = "layer$it.png", blendSharpness = sharpness) },
        )
        val surface = terrainLayersSurface(palette, packLayerArray(layers), control, lightmap)
        val attached = runBlocking {
            shared().attacher.attachContentFeature(
                terrainContentFeature(surface.shaders, FLAT, CONFIG, surfaceTextures = surface.textures),
            )
        }
        try {
            return Frame(shared().render(LENS, EnvironmentUniforms(debugView = view)), shared().size)
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

        fun redMean(left: Boolean) = if (left) mean(0, size * 2 / 5).first else mean(size * 3 / 5, size).first

        fun leftIsRed() = mean(0, size * 2 / 5).let { (r, g) -> r > DOMINANCE * g && r > MIN_CHANNEL }

        fun rightIsGreen() = mean(size * 3 / 5, size).let { (r, g) -> g > DOMINANCE * r && g > MIN_CHANNEL }

        fun middleRowGreenShare(): List<Float> = middleRowShare(1)

        /** [channel]'s share of red plus [channel] across the most-covered row. */
        fun middleRowShare(channel: Int): List<Float> {
            val y = (0 until size).maxBy { row -> (0 until size).count { covered(it, row) } }
            return (0 until size).filter { covered(it, y) }.map { x ->
                channel(x, y, channel).toFloat() / (channel(x, y, 0) + channel(x, y, channel)).coerceAtLeast(1)
            }
        }

        /** Mean RGB over covered pixels of the left or right two fifths. */
        fun mean(left: Boolean): FloatArray {
            val (from, until) = if (left) 0 to size * 2 / 5 else size * 3 / 5 to size
            val sums = FloatArray(3)
            var count = 0
            for (y in 0 until size) {
                for (x in from until until) {
                    if (!covered(x, y)) continue
                    for (c in 0..2) sums[c] += channel(x, y, c).toFloat()
                    count++
                }
            }
            return FloatArray(3) { if (count == 0) 0f else sums[it] / count }
        }

        /** Every covered pixel of the most-covered row, as RGB. */
        fun middleRow(): List<FloatArray> {
            val y = (0 until size).maxBy { row -> (0 until size).count { covered(it, row) } }
            return (0 until size).filter { covered(it, y) }.map { x -> FloatArray(3) { channel(x, y, it).toFloat() } }
        }

        fun summary() = "left mean ${mean(0, size * 2 / 5)}, right mean ${mean(size * 3 / 5, size)}"
    }

    private companion object {
        /** `debugLayerColor` for palette index [layer], in bytes. */
        fun layerColour(layer: Int): FloatArray {
            val hue = (layer * LAYER_HUE_STEP) % 1f
            return FloatArray(3) { (0.5f + 0.5f * cos((hue + it / 3f) * 2f * PI.toFloat())) * 255f }
        }

        fun grey(value: Byte) = FloatArray(3) { (value.toInt() and 0xFF).toFloat() }

        fun FloatArray.near(other: FloatArray) = indices.all { abs(this[it] - other[it]) <= COLOUR_TOLERANCE }

        const val LAYER_HUE_STEP = 0.618034f
        const val COLOUR_TOLERANCE = 4f
        const val GREEN = 1
        const val SAMPLES = 16
        const val RED = 0
        const val CLEAR_TOLERANCE = 12
        const val DOMINANCE = 3f
        const val MIN_CHANNEL = 40f
        const val MIN_ROW_PIXELS = 32
        const val LOW_SHARE = 0.2f
        const val HIGH_SHARE = 0.8f
        const val STEP_TOLERANCE = 0.02f
        const val BLUE_CHANNEL = 2
        const val HALF_LIGHT: Byte = 64
        const val NEUTRAL_LIGHT: Byte = -128

        val CONFIG = TerrainClipmapConfig(ringCount = 2, ringResolution = 16, baseSpacing = 1f)
        val FLAT = Heightmap(FloatArray(SAMPLES * SAMPLES), SAMPLES, SAMPLES, Vec3f(1f, 1f, 1f))
        val LENS = Lens(eye = Vec3f(0f, 10f, 9f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f)

        fun solid(r: Int, g: Int, b: Int) = TextureAsset(
            ByteArray(2 * 2 * 4) { index -> byteArrayOf(r.toByte(), g.toByte(), b.toByte(), -1)[index % 4] },
            2,
            2,
        )

        val FOUR_RED_TWO_BLUE = List(4) { solid(255, 0, 0) } + List(2) { solid(0, 0, 255) }

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
