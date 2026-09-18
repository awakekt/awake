/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.bindingsForGroup
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.render.pipeline.SamplerType
import com.awakekt.awake.render.pipeline.ShaderStage
import com.awakekt.awake.render.pipeline.TextureSampleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SkyboxCubemapShaderBindingsTest {

    private val materialGroup = BindingLayout.Standard.slot(BindingSemantic.Material)

    @Test
    fun cubemapSkyboxDeclaresExpectedMaterialBindings() {
        val derived = assertNotNull(SkyboxCubemapShader.bindingsForGroup(materialGroup))

        assertEquals(listOf(0, 1, 2), derived.entries.map { it.binding })

        val uniformBinding = assertNotNull(derived.at(0))
        assertEquals(ResourceKind.UniformBuffer, uniformBinding.kind)
        assertEquals(setOf(ShaderStage.Fragment), uniformBinding.stages)

        val textureBinding = assertNotNull(derived.at(1))
        assertEquals(ResourceKind.SampledTexture, textureBinding.kind)
        assertTrue(textureBinding.cubemap)
        assertEquals(false, textureBinding.arrayed)
        assertEquals(TextureSampleType.Float, textureBinding.textureSampleType)
        assertEquals(setOf(ShaderStage.Fragment), textureBinding.stages)

        val samplerBinding = assertNotNull(derived.at(2))
        assertEquals(ResourceKind.Sampler, samplerBinding.kind)
        assertEquals(SamplerType.Filtering, samplerBinding.samplerType)
        assertEquals(setOf(ShaderStage.Fragment), samplerBinding.stages)
    }

    @Test
    fun cubemapSkyboxEmitsValidWgsl() {
        val wgsl = SkyboxCubemapShader.emitWgsl()
        assertTrue(wgsl.contains("var envMap : texture_cube<f32>;"))
        assertTrue(wgsl.contains("var envSampler : sampler;"))
        assertTrue(wgsl.contains("textureSample(envMap, envSampler, rayDir)"))
        assertTrue(wgsl.contains("exposure : vec4f"))
    }

    @Test
    fun cubemapUniformFloatsWriterPacksCorrectly() {
        val floats = skyboxCubemapUniformFloats(
            inverseViewProjection = Mat4(),
            cameraEye = Vec3f(1f, 2f, 3f),
            exposure = 1.5f,
        )
        // 16 floats for Mat4 + 4 floats for CameraEye (Vec4) + 4 floats for Exposure (Vec4) = 24 floats
        assertEquals(24, floats.size)
        assertEquals(1f, floats[16])
        assertEquals(2f, floats[17])
        assertEquals(3f, floats[18])
        assertEquals(0f, floats[19])
        assertEquals(1.5f, floats[20])
    }
}
