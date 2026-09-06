/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shadercompiler

import com.awakekt.awake.asset.shaderdsl.bindingsForGroup
import com.awakekt.awake.asset.shaderpack.TerrainShader
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.render.pipeline.ShaderStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Replaces `TerrainSplatShaderCompileTest`, which compiled a hand-written WGSL string that no
 * pipeline ever used. [TerrainShader] is ASL, so what is validated here is what would actually
 * be drawn.
 */
class TerrainShaderCompileTest {

    private val wgsl = TerrainShader.emitWgsl()

    @Test
    fun theTerrainShaderValidatesAndCompilesToSpirv() {
        assertNull(NagaShaderCompiler.validate(wgsl), "WGSL validation failed for the terrain shader.")

        val spirv = NagaShaderCompiler.wgslToSpirv(wgsl)
        assertTrue(spirv.isNotEmpty(), "Compiled SPIR-V byte array must not be empty.")
        assertTrue(spirv.size % 4 == 0, "SPIR-V words must be 4-byte aligned.")
    }

    /**
     * The reason this test matters more than a compile check. Every other shader in the pack
     * samples in the fragment stage, where the mip comes from implicit derivatives. A vertex
     * stage has none, so a plain `textureSample` there is a validation error rather than a wrong
     * picture -- and displacing a heightfield is the first thing in this engine that needs it.
     */
    @Test
    fun theHeightmapIsSampledFromTheVertexStage() {
        val bindings = assertNotNull(TerrainShader.bindingsForGroup(0))

        assertEquals(ResourceKind.SampledTexture, bindings.at(1)?.kind)
        assertEquals(setOf(ShaderStage.Vertex), bindings.at(1)?.stages)
        assertEquals(ResourceKind.Sampler, bindings.at(2)?.kind)
        assertTrue(
            wgsl.contains("textureSampleLevel"),
            "A vertex stage has no implicit derivatives, so the heightmap read must be an " +
                "explicit-level sample.",
        )
    }

    /** One block for every ring -- see `TerrainUniformLayout`'s own note on why. */
    @Test
    fun theUniformBlockIsTheOnlyBufferBinding() {
        val bindings = assertNotNull(TerrainShader.bindingsForGroup(0))

        assertEquals(listOf(0, 1, 2), bindings.entries.map { it.binding })
        assertEquals(ResourceKind.UniformBuffer, bindings.at(0)?.kind)
    }
}
