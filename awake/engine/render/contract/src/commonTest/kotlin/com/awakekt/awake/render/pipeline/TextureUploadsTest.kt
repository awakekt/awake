/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.PbrImageSet
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** An installed [TextureUploads.observer] hears each image upload, named and by path; none hears nothing (#1621). */
class TextureUploadsTest {
    private val heard = mutableListOf<TextureUpload>()

    @AfterTest
    fun uninstall() {
        TextureUploads.observer = null
    }

    @Test
    fun eachUploadIsReportedWithItsLabelAndPath() {
        TextureUploads.observer = TextureUploadObserver { heard += it }

        Device(copies = false).createImageMaterial(Image(), TextureFiltering.Nearest, 8, null, label = "effects/smoke.png")
        Device(copies = true).createImageMaterial(Image(), TextureFiltering.Linear, 8, null, label = "water/ripple.png")
        Device(copies = false).createImageMaterial(Image())

        assertEquals(
            listOf(
                "effects/smoke.png: 2x1, CpuRgba8, Nearest",
                "water/ripple.png: 2x1, GpuCopy, Linear",
                "unnamed image: 2x1, CpuRgba8, Linear",
            ),
            heard.map { it.toString() },
        )
    }

    @Test
    fun withNoObserverNothingIsReported() {
        Device(copies = false).createImageMaterial(Image(), TextureFiltering.Nearest, 8, null, label = "effects/smoke.png")

        assertEquals(emptyList(), heard)
    }

    private class Image : GpuImageSource {
        override val width = 2
        override val height = 1
        override val externalImage: Any? = null
        override fun readRgba8(): ByteArray = ByteArray(8)
    }

    private class Device(private val copies: Boolean) : GpuDevice {
        private val upload = object : GpuImageUpload {
            override fun createMaterial(image: GpuImageSource, filtering: TextureFiltering, uniformFloatCount: Int, pbrImages: PbrImageSet?): Material = Plain
        }
        override val clipSpace: ClipSpace = ClipSpace.Vulkan
        override fun createMesh(geometry: MeshGeometry): Mesh = error("unused")
        override fun createMaterial(texture: TextureAsset?, renderTarget: RenderTarget?, uniformFloatCount: Int, pbrTextures: PbrTextureSet?): Material = Plain
        override fun createRenderTarget(width: Int, height: Int): RenderTarget = error("unused")
        override fun destroy() = Unit

        @Suppress("UNCHECKED_CAST")
        override fun <T : GpuCapability> capability(kind: GpuCapabilityKind<T>): T? = if (kind == GpuImageUpload && copies) upload as T else null
    }

    private object Plain : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
}
