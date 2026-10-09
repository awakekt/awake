/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.kit.terrainlayers.TerrainControlMap
import com.awakekt.awake.kit.terrainlayers.TerrainLayer
import com.awakekt.awake.kit.terrainlayers.TerrainLayerPalette
import com.awakekt.awake.kit.terrainlayers.TerrainLightmap
import com.awakekt.awake.kit.terrainlayers.packLayerArray
import com.awakekt.awake.kit.terrainlayers.terrainLayersSurface
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.TextureAsset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A layered terrain lit only by its bake shows the shadow range by day and not at night.
 *
 * Each case renders the cliff scene with cascades and without, and counts the pixels that differ.
 * By day the plateau's shadow on the baked ground is that difference. With the sun below the
 * horizon there is no sun for a shadow to block, so cascades must change nothing: otherwise the
 * bake is dark inside the shadow range and lit beyond it, split at the range's far end.
 */
class TerrainBakeParityTest {

    @Test
    fun aBakedTerrainShowsItsShadowsByDayAndNoShadowRangeAtNight() {
        BACKEND_ORDER.forEach { backend ->
            openHeadlessScene(backend).use { session ->
                val day = session.renderer.pixelsCascadesChange(DAY_SUN)
                val night = session.renderer.pixelsCascadesChange(NIGHT_SUN)
                println("TERRAIN BAKE $backend day=$day night=$night")

                assertTrue(day > MIN_CAST_PIXELS, "$backend: by day the plateau shaded $day pixels of baked ground; it should shade its shadow.")
                assertEquals(0, night, "$backend: at night cascades changed $night pixels of baked ground.")
            }
        }
    }

    private fun Renderer.pixelsCascadesChange(sun: Vec3f): Int {
        val shadowed = renderTerrainCliffScene(shaders = BAKED.shaders, surfaceTextures = BAKED.textures, sun = sun)
        val unshadowed = renderTerrainCliffScene(cascades = false, shaders = BAKED.shaders, surfaceTextures = BAKED.textures, sun = sun)
        return (0 until SCENE_SIZE * SCENE_SIZE).count { index ->
            val x = index % SCENE_SIZE
            val y = index / SCENE_SIZE
            abs(shadowed.luminanceAt(x, y) - unshadowed.luminanceAt(x, y)) > LUMINANCE_TOLERANCE
        }
    }

    private companion object {
        /** One grey layer everywhere, under a bake that takes the light over entirely. */
        val BAKED = terrainLayersSurface(
            TerrainLayerPalette(layers = listOf(TerrainLayer(id = "ground", albedo = "ground.png"))),
            packLayerArray(listOf(TextureAsset(ByteArray(2 * 2 * 4) { if (it % 4 == 3) -1 else GREY }, 2, 2))),
            TerrainControlMap(1, 1, ByteArray(4), byteArrayOf(-1, 0, 0, 0)),
            TerrainLightmap(1, 1, byteArrayOf(NEUTRAL, NEUTRAL, NEUTRAL, -1)),
        )
        val DAY_SUN = Vec3f(1f, 1f, 0f)

        /** Where a day cycle points the light at night: at the sun, below the horizon. */
        val NIGHT_SUN = Vec3f(1f, -1f, 0f)

        /** Vulkan first, for the loader reason [UiBackendParityTest] documents. */
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)
        const val GREY: Byte = 0xA0.toByte()
        const val NEUTRAL: Byte = 0x80.toByte()
        const val LUMINANCE_TOLERANCE = 2
        const val MIN_CAST_PIXELS = 200
    }
}
