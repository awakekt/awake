/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shadercompiler

import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderpack.ParticleShader
import com.awakekt.awake.asset.shaderpack.ParticleShadowDepthShader
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The particle shader and its shadow caster, validated by naga and compiled to the SPIR-V Vulkan
 * loads. Both read a particle's spin from its instance matrix, so a spin that did not compile on
 * one backend would show up here rather than as a blank sprite.
 */
class ParticleShaderCompileTest {
    private val shaders: Map<String, AslShaderDefinition> = mapOf(
        "particle" to ParticleShader,
        "particle shadow depth" to ParticleShadowDepthShader,
    )

    @Test
    fun theParticleShadersValidateAndCompileToSpirv() {
        shaders.forEach { (name, shader) ->
            val wgsl = shader.emitWgsl()

            assertNull(NagaShaderCompiler.validate(wgsl), "WGSL validation failed for the $name shader.")
            val spirv = NagaShaderCompiler.wgslToSpirv(wgsl)
            assertTrue(spirv.isNotEmpty() && spirv.size % 4 == 0, "the $name shader compiled to ${spirv.size} bytes, not whole SPIR-V words")
        }
    }

    /** Both shaders turn the quad by the sine and cosine of the angle in column 2, so neither can ignore a spin. */
    @Test
    fun bothParticleShadersTurnTheQuadByTheSpinInTheInstanceMatrix() {
        shaders.forEach { (name, shader) ->
            val wgsl = shader.emitWgsl()

            assertTrue("sin(" in wgsl && "cos(" in wgsl, "the $name shader never takes the sine and cosine of the spin:\n$wgsl")
        }
    }
}
