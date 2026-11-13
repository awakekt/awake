/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.bindingsForGroup
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.pipeline.ResourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CelestialShaderTest {

    @Test
    fun declaresAUniformBlockTwoTexturesAndASampler() {
        val derived = assertNotNull(CelestialShader.bindingsForGroup(BindingLayout.Standard.slot(BindingSemantic.Material)))

        assertEquals(listOf(0, 1, 2, 3), derived.entries.map { it.binding })
        assertEquals(ResourceKind.UniformBuffer, assertNotNull(derived.at(0)).kind)
        assertEquals(listOf(ResourceKind.SampledTexture, ResourceKind.SampledTexture, ResourceKind.Sampler), (1..3).map { assertNotNull(derived.at(it)).kind })
        assertFalse(assertNotNull(derived.at(1)).cubemap, "the sun and moon are flat images")
    }

    @Test
    fun emitsTheSunMoonAndPhaseMath() {
        val wgsl = CelestialShader.emitWgsl()

        assertTrue(wgsl.contains("var sunTexture : texture_2d<f32>;"))
        assertTrue(wgsl.contains("var moonTexture : texture_2d<f32>;"))
        assertTrue(wgsl.contains("params : vec4f"))
        assertTrue(wgsl.contains("textureSample(moonTexture, bodySampler,"))
    }

    @Test
    fun layoutPacksElevenFieldsAndTheSkyIsBlendedBeforeGeometry() {
        // 16 floats for the matrix, then ten vec4 fields.
        assertEquals(16 + 10 * 4, CelestialUniformLayout.total)
        assertTrue(PipelineVariant.BackgroundBlended.blendEnabled)
        assertFalse(PipelineVariant.BackgroundBlended.additive)
        assertFalse(PipelineVariant.BackgroundBlended.depthTestEnabled)
        assertFalse(PipelineVariant.BackgroundBlended.depthWriteEnabled)
    }
}
