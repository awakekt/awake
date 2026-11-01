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
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.asGpuImageSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A content feature's decoded [ContentFeature.images] fill declared bindings as its textures do: each
 * declared binding takes exactly one of a texture and an image, and an image only fits a plain 2D one.
 */
class ContentFeatureImagesTest {
    private val pixel = TextureAsset(byteArrayOf(-1, -1, -1, -1), width = 1, height = 1)
    private val image = pixel.asGpuImageSource()

    private fun spec(vararg bindings: ResourceBinding) = PipelineSpec(
        vertexFormat = VertexFormat.None,
        vertexShader = ShaderSource.ResourcePath("v.wgsl", "vertexMain"),
        fragmentShader = ShaderSource.ResourcePath("f.wgsl", "fragmentMain"),
        materialBindings = GroupBindings(listOf(ResourceBinding(0, ResourceKind.UniformBuffer, setOf(ShaderStage.Vertex))) + bindings),
        uniforms = UniformLayout(UniformFields.Mvp),
    )

    private fun texture(binding: Int, arrayed: Boolean = false) =
        ResourceBinding(binding, ResourceKind.SampledTexture, setOf(ShaderStage.Fragment), arrayed = arrayed)

    private fun feature(spec: PipelineSpec, textures: Map<Int, TextureAsset>, images: Map<Int, GpuImageSource>) =
        ContentFeature(name = "effect", spec = spec, textures = textures, images = images) { _, _, _ -> error("not built here") }

    @Test
    fun texturesAndImagesTogetherFillTheDeclaredBindings() {
        val built = feature(spec(texture(1), texture(2)), textures = mapOf(1 to pixel), images = mapOf(2 to image))

        assertEquals(setOf(1), built.textures.keys)
        assertEquals(setOf(2), built.images.keys)
    }

    @Test
    fun aBindingGivenBothATextureAndAnImageFails() {
        assertFailsWith<IllegalArgumentException> { feature(spec(texture(1)), textures = mapOf(1 to pixel), images = mapOf(1 to image)) }
    }

    @Test
    fun anImageForAnUndeclaredBindingFails() {
        assertFailsWith<IllegalArgumentException> { feature(spec(texture(1)), textures = mapOf(1 to pixel), images = mapOf(5 to image)) }
    }

    @Test
    fun anImageAtAnArrayedBindingFails() {
        assertFailsWith<IllegalArgumentException> { feature(spec(texture(1, arrayed = true)), textures = emptyMap(), images = mapOf(1 to image)) }
    }
}
