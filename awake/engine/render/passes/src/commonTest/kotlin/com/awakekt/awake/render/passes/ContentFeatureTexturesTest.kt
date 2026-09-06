/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineSpec
import com.awakekt.awake.render.pipeline.ResourceBinding
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.render.pipeline.ShaderSource
import com.awakekt.awake.render.pipeline.ShaderStage
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.texture.TextureAsset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A declared-but-unwritten descriptor reads as undefined on Vulkan and is rejected outright by
 * WebGPU, and a supplied-but-undeclared texture is silently written nowhere. Neither is visible
 * at the call site, so both are rejected where the feature is declared.
 */
class ContentFeatureTexturesTest {

    private val uniforms = UniformLayout(UniformFields.Mvp)
    private val pixel = TextureAsset(byteArrayOf(-1, -1, -1, -1), width = 1, height = 1)

    private fun spec(bindings: GroupBindings?) = PipelineSpec(
        vertexFormat = VertexFormat.None,
        vertexShader = ShaderSource.ResourcePath("v.wgsl", "vertexMain"),
        fragmentShader = ShaderSource.ResourcePath("f.wgsl", "fragmentMain"),
        materialBindings = bindings,
        uniforms = uniforms,
    )

    private fun splatBindings(vararg textureBindings: Int) = GroupBindings(
        listOf(ResourceBinding(0, ResourceKind.UniformBuffer, setOf(ShaderStage.Vertex))) +
            textureBindings.map {
                ResourceBinding(it, ResourceKind.SampledTexture, setOf(ShaderStage.Fragment))
            },
    )

    @Test
    fun aFeatureDeclaringNoTexturesStillBuilds() {
        val feature = ContentFeature(name = "skyLike", spec = spec(null)) { _, _, _ -> error("not built here") }

        assertTrue(feature.textures.isEmpty())
    }

    @Test
    fun declaredTexturesMatchedBySuppliedOnesAreAccepted() {
        val feature = ContentFeature(
            name = "terrainSplat",
            spec = spec(splatBindings(1, 2)),
            textures = mapOf(1 to pixel, 2 to pixel),
        ) { _, _, _ -> error("not built here") }

        assertEquals(setOf(1, 2), feature.textures.keys)
    }

    @Test
    fun aDeclaredBindingWithNoTextureFails() {
        val failure = assertFailsWith<IllegalArgumentException> {
            ContentFeature(
                name = "terrainSplat",
                spec = spec(splatBindings(1, 2)),
                textures = mapOf(1 to pixel),
            ) { _, _, _ -> error("not built here") }
        }

        assertTrue(failure.message.orEmpty().contains("terrainSplat"))
    }

    @Test
    fun aSuppliedTextureWithNoDeclaredBindingFails() {
        assertFailsWith<IllegalArgumentException> {
            ContentFeature(
                name = "terrainSplat",
                spec = spec(splatBindings(1)),
                textures = mapOf(1 to pixel, 9 to pixel),
            ) { _, _, _ -> error("not built here") }
        }
    }

    /** A sampler is created by the backend, not supplied as pixel data, so it is not a texture
     * the feature owes. */
    @Test
    fun aDeclaredSamplerNeedsNoSuppliedTexture() {
        val bindings = GroupBindings(
            listOf(
                ResourceBinding(0, ResourceKind.UniformBuffer, setOf(ShaderStage.Vertex)),
                ResourceBinding(1, ResourceKind.SampledTexture, setOf(ShaderStage.Fragment)),
                ResourceBinding(2, ResourceKind.Sampler, setOf(ShaderStage.Fragment)),
            ),
        )

        val feature = ContentFeature(
            name = "terrainSplat",
            spec = spec(bindings),
            textures = mapOf(1 to pixel),
        ) { _, _, _ -> error("not built here") }

        assertEquals(setOf(1), feature.textures.keys)
    }

    @Test
    fun texturesWithoutAnyDeclarationAtAllFail() {
        assertFailsWith<IllegalArgumentException> {
            ContentFeature(
                name = "terrainSplat",
                spec = spec(null),
                textures = mapOf(1 to pixel),
            ) { _, _, _ -> error("not built here") }
        }
    }
}
