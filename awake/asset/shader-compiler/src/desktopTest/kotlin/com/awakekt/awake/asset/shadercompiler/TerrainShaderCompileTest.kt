/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shadercompiler

import com.awakekt.awake.asset.shaderdsl.bindingsForGroup
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.normalize
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.texture2dArray
import com.awakekt.awake.asset.shaderdsl.textureSampleArray
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xyz
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.z
import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaderpack.TERRAIN_SURFACE_FIRST_BINDING
import com.awakekt.awake.asset.shaderpack.terrainClipmapDiscardUnderFinerRing
import com.awakekt.awake.asset.shaderpack.terrainClipmapVertexStage
import com.awakekt.awake.asset.shaderpack.terrainShader
import com.awakekt.awake.asset.shaders.RenderBackend
import com.awakekt.awake.asset.shaders.stagesFor
import com.awakekt.awake.asset.shaders.ShaderStage as ProgramStage
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.passes.uniforms.SHADOW_CASCADE_PASS_GROUP
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.render.pipeline.ShaderSource
import com.awakekt.awake.render.pipeline.ShaderStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The base terrain shader and a surface shader on the shared clipmap stage, validated by naga. */
class TerrainShaderCompileTest {

    private val terrain = terrainShader(ClipSpace.Vulkan)
    private val wgsl = terrain.emitWgsl()

    @Test
    fun theTerrainShaderValidatesAndCompilesToSpirvForEitherClipSpace() {
        for (clipSpace in ClipSpace.entries) {
            val source = terrainShader(clipSpace).emitWgsl()
            assertNull(NagaShaderCompiler.validate(source), "WGSL validation failed for the $clipSpace terrain shader.")

            val spirv = NagaShaderCompiler.wgslToSpirv(source)
            assertTrue(spirv.isNotEmpty(), "Compiled SPIR-V byte array must not be empty.")
            assertTrue(spirv.size % 4 == 0, "SPIR-V words must be 4-byte aligned.")
        }
    }

    /** The engine's shadow map, in the group every lit shader reads it from, sampled per fragment. */
    @Test
    fun theTerrainShaderReadsTheShadowMapGroup() {
        val bindings = assertNotNull(terrain.bindingsForGroup(BindingLayout.Standard.slot(BindingSemantic.ShadowDepth)))

        assertEquals(listOf(0, 1), bindings.entries.map { it.binding })
        assertTrue(assertNotNull(bindings.at(0)).arrayed)
        assertEquals(setOf(ShaderStage.Fragment), bindings.at(0)?.stages)
        assertEquals(ResourceKind.Sampler, bindings.at(1)?.kind)
    }

    /**
     * The reason this test matters more than a compile check. Every other shader in the pack
     * samples in the fragment stage, where the mip comes from implicit derivatives. A vertex
     * stage has none, so a plain `textureSample` there is a validation error rather than a wrong
     * picture -- and displacing a heightfield is the first thing in this engine that needs it.
     */
    @Test
    fun theHeightmapIsSampledFromTheVertexStage() {
        val bindings = assertNotNull(terrain.bindingsForGroup(0))

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
        val bindings = assertNotNull(terrain.bindingsForGroup(0))

        assertEquals(listOf(0, 1, 2), bindings.entries.map { it.binding })
        assertEquals(ResourceKind.UniformBuffer, bindings.at(0)?.kind)
    }

    /**
     * The rings' cascade depth, from either backend's set: it validates, discards coarse rings
     * under finer ones as the drawn surface does, and declares only the clipmap stage's own
     * group-0 bindings, so the depth pass can bind any surface's group through it.
     */
    @Test
    fun theTerrainShadowDepthShaderCompilesOverTheStagesOwnBindingsForEitherBackend() {
        for (backend in RenderBackend.entries) {
            val stages = PackShaderSets.TerrainShadowDepth.stagesFor(backend)
            val source = (stages[ProgramStage.VERTEX] as ShaderSource.InlineText).sourceCode
            assertNull(NagaShaderCompiler.validate(source), "WGSL validation failed for the $backend terrain depth shader.")
            assertTrue(NagaShaderCompiler.wgslToSpirv(source).isNotEmpty())
            assertTrue(source.contains("discard"), "The $backend terrain depth shader keeps coarse rings under finer ones.")

            assertEquals(listOf(0, 1, 2), stages.bindingsByGroup.getValue(0).entries.map { it.binding })
            assertEquals(ResourceKind.UniformBuffer, stages.bindingsByGroup.getValue(SHADOW_CASCADE_PASS_GROUP).at(0)?.kind)
        }
    }

    /** A surface writes only a fragment stage; the clipmap stage it shares must still compile. */
    @Test
    fun aSurfaceShaderOnTheSharedStageCompiles() {
        val surface = surfaceProbe.emitWgsl()

        assertNull(NagaShaderCompiler.validate(surface), "WGSL validation failed for the surface probe.")
        assertTrue(NagaShaderCompiler.wgslToSpirv(surface).isNotEmpty())
    }

    /** The surface's bindings follow the stage's, which `terrainContentFeature` reads from the set. */
    @Test
    fun aSurfaceShaderDeclaresItsBindingsAfterTheStages() {
        val bindings = assertNotNull(surfaceProbe.bindingsForGroup(0))

        assertEquals(listOf(0, 1, 2, 3, 4), bindings.entries.map { it.binding })
        val layers = assertNotNull(bindings.at(TERRAIN_SURFACE_FIRST_BINDING))
        assertEquals(ResourceKind.SampledTexture, layers.kind)
        assertTrue(layers.arrayed)
        assertEquals(setOf(ShaderStage.Fragment), layers.stages)
    }

    private companion object {
        /** Samples one array layer at the terrain's world position. */
        val surfaceProbe = shader("terrain_surface_probe") {
            val terrain = terrainClipmapVertexStage()
            val group = BindingLayout.Standard.slot(BindingSemantic.Material)
            val layers by texture2dArray(group = group, binding = TERRAIN_SURFACE_FIRST_BINDING)
            val layerSampler by sampler(group = group, binding = TERRAIN_SURFACE_FIRST_BINDING + 1)
            fragment {
                terrainClipmapDiscardUnderFinerRing(terrain)
                val uv = let(
                    "uv",
                    vec2(
                        terrain.worldPosition.x / terrain.terrainSampling.x,
                        terrain.worldPosition.z / terrain.terrainSampling.y,
                    ),
                )
                val albedo = let("albedo", textureSampleArray(layers, layerSampler, uv, 0.lit))
                val light = let(
                    "light",
                    max(dot(normalize(terrain.worldNormal), normalize(terrain.sunDirection.xyz)), 0f.lit),
                )
                colorOutput(vec4(albedo.xyz * light, 1f.lit))
            }
        }
    }
}
