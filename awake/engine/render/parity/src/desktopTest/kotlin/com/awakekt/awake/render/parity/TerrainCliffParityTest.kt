/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.TERRAIN_SURFACE_FIRST_BINDING
import com.awakekt.awake.render.texture.TextureAsset
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Terrain casts from the rings it draws on both backends, the same amount.
 *
 * The cliff scene with and without cascades: what turns dark only with them on is what the terrain
 * cast. Its surface declares a texture past the clipmap stage's bindings, so the depth pipeline,
 * whose shader declares only those, binds the surface's group only through the visible pipeline's
 * own group-0 layout -- on WebGPU a layout built from the depth shader alone is incompatible.
 * Positive control: building it that way stops the WebGPU frame with a bind-group layout mismatch
 * at binding 3.
 */
class TerrainCliffParityTest {

    @Test
    fun theTerrainCastsTheSameShadowOnBothBackends() {
        val cast = BACKEND_ORDER.associateWith { backend ->
            openHeadlessScene(backend).use { session ->
                val shadowed = session.renderer.renderTerrainCliffScene(shaders = TintedTerrainSurface, surfaceTextures = TINT)
                val unshadowed = session.renderer.renderTerrainCliffScene(cascades = false, shaders = TintedTerrainSurface, surfaceTextures = TINT)
                shadowed.shadedPixels() - unshadowed.shadedPixels()
            }
        }
        println("TERRAIN CAST $cast")

        val vulkan = cast.getValue(HeadlessUiBackend.Vulkan)
        val webGpu = cast.getValue(HeadlessUiBackend.WebGpu)
        assertTrue(vulkan > MIN_CAST_PIXELS, "Vulkan's terrain cast $vulkan pixels of shadow; it should cast the plateau's.")
        assertTrue(
            kotlin.math.abs(webGpu - vulkan) <= vulkan * CAST_TOLERANCE,
            "The terrain cast $webGpu pixels of shadow on WebGPU against $vulkan on Vulkan.",
        )
    }

    /**
     * Pixels clearly below the lit level and above the clear colour. The lit level is the commonest
     * brightness, measured per capture, so the count holds whatever shade the ground is lit to.
     */
    private fun ByteArray.shadedPixels(): Int {
        val pixels = (0 until SCENE_SIZE * SCENE_SIZE).map { luminanceAt(it % SCENE_SIZE, it / SCENE_SIZE) }
        val lit = pixels.filter { it > 0 }.groupingBy { it }.eachCount().maxBy { it.value }.key
        return pixels.count { it in 1 until lit * SHADED_PERCENT / 100 }
    }

    private companion object {
        val TINT = mapOf(TERRAIN_SURFACE_FIRST_BINDING to TextureAsset(ByteArray(2 * 2 * 4) { -1 }, 2, 2))

        /** Vulkan first, for the loader reason [UiBackendParityTest] documents. */
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)
        const val SHADED_PERCENT = 85
        const val MIN_CAST_PIXELS = 200
        const val CAST_TOLERANCE = 0.25
    }
}
