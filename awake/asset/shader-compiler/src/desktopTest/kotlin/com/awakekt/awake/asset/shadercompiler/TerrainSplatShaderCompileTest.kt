/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shadercompiler

import com.awakekt.awake.asset.shaderdsl.bindingsForGroup
import com.awakekt.awake.asset.shaderpack.TerrainSplatShader
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.render.pipeline.ShaderStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerrainSplatShaderCompileTest {

    private val wgsl = TerrainSplatShader.emitWgsl()

    @Test
    fun theTerrainSplatShaderValidatesAndCompilesToSpirv() {
        assertNull(NagaShaderCompiler.validate(wgsl), "WGSL validation failed for the terrain splat shader.")

        val spirv = NagaShaderCompiler.wgslToSpirv(wgsl)
        assertTrue(spirv.isNotEmpty(), "Compiled SPIR-V byte array must not be empty.")
        assertTrue(spirv.size % 4 == 0, "SPIR-V words must be 4-byte aligned.")
    }

    @Test
    fun theSplatTexturesAreBoundCorrectly() {
        val bindings = assertNotNull(TerrainSplatShader.bindingsForGroup(0))

        // Binding 1 & 2: Heightmap (Vertex)
        assertEquals(ResourceKind.SampledTexture, bindings.at(1)?.kind)
        assertEquals(setOf(ShaderStage.Vertex), bindings.at(1)?.stages)
        assertEquals(ResourceKind.Sampler, bindings.at(2)?.kind)

        // Binding 3 & 4: Splat weightmap (Fragment)
        assertEquals(ResourceKind.SampledTexture, bindings.at(3)?.kind)
        assertEquals(setOf(ShaderStage.Fragment), bindings.at(3)?.stages)
        assertEquals(ResourceKind.Sampler, bindings.at(4)?.kind)

        // Binding 5 & 6: Texture 2D Array & Sampler (Fragment)
        assertEquals(ResourceKind.SampledTexture, bindings.at(5)?.kind)
        assertEquals(setOf(ShaderStage.Fragment), bindings.at(5)?.stages)
        assertEquals(ResourceKind.Sampler, bindings.at(6)?.kind)
    }
}
