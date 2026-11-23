/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.bindingsForGroup
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.ResourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StylizedWaterAndCloudShaderTest {
    @Test
    fun stylizedWaterShaderEmitsWgslAndUsesZeroTextures() {
        val wgsl = StylizedWaterShader.emitWgsl()
        assertTrue(wgsl.contains("struct Uniforms"), "WGSL contains uniform struct")
        assertTrue(wgsl.contains("perturbedNormal"), "WGSL includes Standard analytic normal path")
        assertTrue(wgsl.contains("flowX"), "WGSL includes directional flow advection")
        assertTrue(wgsl.contains("crestTint"), "WGSL includes clean sunlit wave-crest glow")
        val group = BindingLayout.Standard.slot(BindingSemantic.Material)
        val sampled = StylizedWaterShader.bindingsForGroup(group)
            ?.entries
            .orEmpty()
            .filter { it.kind == ResourceKind.SampledTexture }
        assertEquals(0, sampled.size, "StylizedWaterShader requires zero texture bindings")
    }

    @Test
    fun stylizedWaterDepthShaderBindsSceneDepthAndEmitsContactFoam() {
        for (clipSpace in ClipSpace.entries) {
            val shader = stylizedWaterShader(clipSpace, samplesDepth = true)
            val wgsl = shader.emitWgsl()
            assertTrue(wgsl.contains("texture_depth_2d"), "$clipSpace depth water declares texture_depth_2d")
            assertTrue(wgsl.contains("foamPattern"), "$clipSpace depth water includes cellular foam breakup")
            assertTrue(wgsl.contains("contactFoam"), "$clipSpace depth water computes shore/rock contact foam")
            assertTrue(wgsl.contains("causticsRgb"), "$clipSpace depth water computes shallow underwater caustics")
            val depthGroup = BindingLayout.Standard.slot(BindingSemantic.SceneDepth)
            val depthBindings = shader.bindingsForGroup(depthGroup)?.entries.orEmpty()
            assertEquals(2, depthBindings.size, "$clipSpace depth water binds SceneDepth texture + sampler")
        }
    }

    @Test
    fun cloudLayerShaderEmitsWgslAndUsesZeroTextures() {
        val wgsl = CloudLayerShader.emitWgsl()
        assertTrue(wgsl.contains("fn valueNoise"), "WGSL includes procedural value noise")
        assertTrue(wgsl.contains("uvUpper"), "WGSL includes single-pass dual-layer parallax path")
        val group = BindingLayout.Standard.slot(BindingSemantic.Material)
        val sampled = CloudLayerShader.bindingsForGroup(group)
            ?.entries
            .orEmpty()
            .filter { it.kind == ResourceKind.SampledTexture }
        assertEquals(0, sampled.size, "CloudLayerShader requires zero texture bindings")
    }
}
