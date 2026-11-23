/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.bindingsForGroup
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
        val group = BindingLayout.Standard.slot(BindingSemantic.Material)
        val sampled = StylizedWaterShader.bindingsForGroup(group)
            ?.entries
            .orEmpty()
            .filter { it.kind == ResourceKind.SampledTexture }
        assertEquals(0, sampled.size, "StylizedWaterShader requires zero texture bindings")
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
