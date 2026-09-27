/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaderpack.terrainContentFeature
import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.math.abs
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A terrain ends at its heightmap's footprint. The clipmap rings reach well past it and the
 * heightmap sampler repeats, so without the edge clamp the terrain tiles across the whole view.
 */
class RendererHeadlessTerrainFootprintTest {

    @Test
    fun theTerrainCoversOnlyItsFootprint() {
        val attached = runBlocking {
            shared().attacher.attachContentFeature(terrainContentFeature(PackShaderSets.Terrain, FLAT, CONFIG))
        }
        val pixels = try {
            shared().render(LENS)
        } finally {
            attached.detach()
        }
        val size = shared().size
        val covered = (0 until size * size).filter { index ->
            (0 until 3).sumOf { pixels[index * 4 + it].toInt() and 0xFF } > CLEAR_TOLERANCE
        }
        val columns = covered.map { it % size }
        val rows = covered.map { it / size }
        val width = columns.max() - columns.min() + 1
        val depth = rows.max() - rows.min() + 1

        assertTrue(abs(width - EXPECTED_PIXELS) <= TOLERANCE_PIXELS, "Covered $width px wide; the footprint is about $EXPECTED_PIXELS.")
        assertTrue(abs(depth - EXPECTED_PIXELS) <= TOLERANCE_PIXELS, "Covered $depth px deep; the footprint is about $EXPECTED_PIXELS.")
    }

    private companion object {
        const val SAMPLES = 16
        const val CLEAR_TOLERANCE = 12
        const val EYE_HEIGHT = 40f
        const val FOV = 1f
        const val TOLERANCE_PIXELS = 3

        /** Rings of 16 cells at spacing 1 and 2 reach 16 units from the centre; the footprint is 7.5. */
        val CONFIG = TerrainClipmapConfig(ringCount = 2, ringResolution = 16, baseSpacing = 1f)
        val FLAT = Heightmap(FloatArray(SAMPLES * SAMPLES), SAMPLES, SAMPLES, Vec3f(1f, 1f, 1f))
        val LENS = Lens(eye = Vec3f(0f, EYE_HEIGHT, 0.01f), center = Vec3f(0f, 0f, 0f), fovYRadians = FOV, near = 0.1f, far = 100f)

        /** Footprint of (SAMPLES - 1) units over the view's width at the ground. */
        val EXPECTED_PIXELS = ((SAMPLES - 1) / (2f * EYE_HEIGHT * tan(FOV / 2f)) * HeadlessContentAttachFixture.TARGET_SIZE).toInt()

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
