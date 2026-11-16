/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.passes.uniforms.MAX_POINT_LIGHTS
import com.awakekt.awake.render.passes.uniforms.MAX_SHADOW_TARGET_LAYERS
import com.awakekt.awake.render.passes.uniforms.POINT_SHADOW_FACE_COUNT
import com.awakekt.awake.render.renderer.MAX_SHADOW_CASCADES
import com.awakekt.awake.render.renderer.ShadowDepthFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** How many shadow array layers a frame needs, and how long they stay resident (#1698). */
class ShadowArrayResidencyTest {
    @Test
    fun noCastingLightNeedsNoLayers() {
        assertEquals(0, shadowLayersNeeded(emptyList()))
    }

    @Test
    fun aDirectionalLightNeedsTheCascadesInUse() {
        assertEquals(3, shadowLayersNeeded(cascades(3)))
        assertEquals(1, shadowLayersNeeded(cascades(1)))
    }

    @Test
    fun eachCastingPointLightAddsSixFacesAfterTheCascadeBlock() {
        assertEquals(MAX_SHADOW_CASCADES + POINT_SHADOW_FACE_COUNT, shadowLayersNeeded(cascades(3) + pointFaces(lights = 1)))
        assertEquals(MAX_SHADOW_CASCADES + 2 * POINT_SHADOW_FACE_COUNT, shadowLayersNeeded(pointFaces(lights = 2)))
        assertEquals(MAX_SHADOW_TARGET_LAYERS, shadowLayersNeeded(cascades(MAX_SHADOW_CASCADES) + pointFaces(MAX_POINT_LIGHTS)))
    }

    @Test
    fun theLayersALightNeedsAreAllocatedTheFrameItFirstCasts() {
        val residency = ShadowArrayResidency(releaseAfterFrames = 4)
        assertEquals(0, residency.frame(needed = 0, resident = 0))
        assertEquals(3, residency.frame(needed = 3, resident = 0))
        assertEquals(10, residency.frame(needed = 10, resident = 3))
    }

    @Test
    fun aWindowWithNoCastingLightReleasesTheArray() {
        val residency = ShadowArrayResidency(releaseAfterFrames = 4)
        var resident = residency.frame(needed = 3, resident = 0)
        val held = (1..8).map { resident = residency.frame(needed = 0, resident = resident); resident }
        // The window that closes on frame 4 saw the cascades; the next one, all quiet, releases
        // them on frame 8, and nothing allocates again until a light casts.
        assertEquals(listOf(3, 3, 3, 3, 3, 3, 0, 0), held)
    }

    @Test
    fun aWindowThatUsedFewerLayersShrinksToItsPeak() {
        val residency = ShadowArrayResidency(releaseAfterFrames = 2)
        var resident = residency.frame(needed = 10, resident = 0)
        resident = residency.frame(needed = 3, resident = resident)
        assertEquals(10, resident, "the first window saw the point light's faces")
        resident = residency.frame(needed = 3, resident = resident)
        resident = residency.frame(needed = 0, resident = resident)
        assertEquals(3, resident, "the cascades were used in the window that closed")
    }

    @Test
    fun anOffscreenRenderGrowsTheArrayAndKeepsItResident() {
        val residency = ShadowArrayResidency(releaseAfterFrames = 2)
        assertEquals(3, residency.layersFor(needed = 3, resident = 0))
        assertEquals(3, residency.frame(needed = 0, resident = 3))
        assertEquals(3, residency.frame(needed = 0, resident = 3), "the window saw the offscreen render's cascades")
        assertEquals(3, residency.frame(needed = 0, resident = 3))
        assertEquals(0, residency.frame(needed = 0, resident = 3))
    }

    @Test
    fun aWindowIsAtLeastOneFrame() {
        assertFailsWith<IllegalArgumentException> { ShadowArrayResidency(releaseAfterFrames = 0) }
    }

    @Test
    fun aMobileTierLayerIsAnEighthOfTheDefault() {
        val default = ShadowDepthFormat.Depth32Float.layerBytes(mapSize = 2048, layers = 1)
        assertEquals(16L * 1024 * 1024, default)
        assertEquals(2L * 1024 * 1024, ShadowDepthFormat.Depth16Unorm.layerBytes(mapSize = 1024, layers = 1))
        assertEquals(448L * 1024 * 1024, ShadowDepthFormat.Depth32Float.layerBytes(2048, MAX_SHADOW_TARGET_LAYERS))
    }

    private fun cascades(count: Int) = List(count) { GpuSubPass(target = null, targetLayer = it, viewProjection = Mat4()) }

    private fun pointFaces(lights: Int) = List(lights * POINT_SHADOW_FACE_COUNT) { face ->
        GpuSubPass(target = null, targetLayer = MAX_SHADOW_CASCADES + face, viewProjection = Mat4())
    }
}
