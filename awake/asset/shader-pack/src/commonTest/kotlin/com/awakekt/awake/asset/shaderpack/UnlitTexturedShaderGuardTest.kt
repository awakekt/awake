/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.core.math.ClipSpace
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards mobile GPU Rule 1 & Rule 5 across pack shaders:
 * - Unlit/unlit-additive textured and skinned draws bypass PBR texture fetches, TBN, GGX, shadows, and point lights.
 * - Matte dielectric surfaces bypass environment-reflection and unlit-chrome trig/pow math.
 * - Deep water pixels bypass shoreline 3-octave cellular noise, caustics, and lap-wave trig.
 * - Steady cubemap sky frames (blend == 0) bypass the second cubemap lookup.
 */
class UnlitTexturedShaderGuardTest {
    @Test
    fun unlitAndAdditiveDrawsBypassPbrLightingAndShadows() {
        val wgsl = texturedShader(ClipSpace.Vulkan).emitWgsl()
        val fastPathIndex = wgsl.indexOf("var fastRgb")
        val metallicIndex = wgsl.indexOf("let metallicRoughness")
        val shadowIndex = wgsl.indexOf("let shadowFactor")
        assertTrue(fastPathIndex > 0, "emits unlit/additive fast path block")
        assertTrue(metallicIndex > fastPathIndex, "guards metallicRoughness fetch behind lit branch")
        assertTrue(shadowIndex > fastPathIndex, "guards shadow sampling behind lit branch")
    }

    @Test
    fun unlitSkinnedTexturedDrawsBypassPbrMapsAndPointLights() {
        val frag = SkinnedTexturedShader.emitWgsl().substringAfter("fn fragmentMain")
        val fastPathIndex = frag.indexOf("var unlitRgb")
        val metallicIndex = frag.indexOf("let metallicRoughness")
        val perturbIndex = frag.indexOf("perturbNormal(")
        assertTrue(fastPathIndex > 0, "skinned_textured emits unlit fast path block")
        assertTrue(metallicIndex > fastPathIndex, "skinned_textured guards metallicRoughness fetch behind lit branch")
        assertTrue(perturbIndex > fastPathIndex, "skinned_textured guards perturbNormal behind lit branch")
        assertTrue(frag.contains("textureSampleGrad("), "skinned_textured uses explicit gradients inside uniform branch")
    }

    @Test
    fun environmentReflectionAndChromeAreGuardedForMatteMaterials() {
        val frag = texturedShader(ClipSpace.Vulkan).emitWgsl().substringAfter("fn fragmentMain")
        val envGuardIndex = frag.indexOf("if (envWeight > ")
        val envBandIndex = frag.indexOf("let envBandA")
        val chromeGuardIndex = frag.indexOf("if (unlitReflStrength > ")
        val chromeWaveIndex = frag.indexOf("let unlitWave1")
        assertTrue(envGuardIndex > 0, "guards environmentReflection for matte dielectrics")
        assertTrue(envBandIndex > envGuardIndex, "guards chrome bands inside reflectance branch")
        assertTrue(chromeGuardIndex > 0, "guards unlitSignedChrome when reflectance is zero")
        assertTrue(chromeWaveIndex > chromeGuardIndex, "guards unlit chrome trig waves behind reflectance check")
    }

    @Test
    fun stylizedWaterGuardsShorelineNoiseAndCausticsBehindDepthCutoff() {
        val frag = stylizedWaterShader(ClipSpace.Vulkan, samplesDepth = true).emitWgsl().substringAfter("fn fragmentMain")
        val deepFastIndex = frag.indexOf("let shoreCutoff")
        val foamNoiseIndex = frag.indexOf("let foamUv1")
        val causticIndex = frag.indexOf("let causticNet")
        val lapWaveIndex = frag.indexOf("let lapWave")
        assertTrue(deepFastIndex > 0, "stylized_water_depth computes shoreCutoff before shoreline noise")
        assertTrue(foamNoiseIndex > deepFastIndex, "guards 3-octave shoreline cellular noise behind shoreCutoff")
        assertTrue(causticIndex > deepFastIndex, "guards shallow caustics pow() behind shoreCutoff")
        assertTrue(lapWaveIndex > deepFastIndex, "guards shoreline lapWave sin() behind shoreCutoff")
    }

    @Test
    fun skyboxCubemapGuardsSecondLookupWhenBlendIsZero() {
        val frag = SkyboxCubemapShader.emitWgsl().substringAfter("fn fragmentMain")
        val guardIndex = frag.indexOf("if (uniforms.exposure.y > ")
        val secondLookupIndex = frag.indexOf("let blendedColor")
        assertTrue(guardIndex > 0, "guards second cubemap lookup when blend is zero")
        assertTrue(secondLookupIndex > guardIndex, "second cubemap sample stays inside blend guard")
    }
}
