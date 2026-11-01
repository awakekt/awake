/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx.loader

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.pipeline.GpuCapability
import com.awakekt.awake.render.pipeline.GpuCapabilityKind
import com.awakekt.awake.render.pipeline.GpuCompressedTextures
import com.awakekt.awake.render.pipeline.GpuDevice
import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.render.texture.CompressedTextureImage
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering

/** A device's [GpuCompressedTextures] taking [formats], which makes a material only when it [accepts]. */
internal class FakeCompressedTextures(override val formats: Set<CompressedTextureFormat>, private val accepts: Boolean) : GpuCompressedTextures {
    var uploaded: CompressedTextureImage? = null

    override fun createMaterial(image: CompressedTextureImage, filtering: TextureFiltering, uniformFloatCount: Int): Material? {
        uploaded = image
        return if (accepts) StubMaterial else null
    }
}

internal object StubMaterial : Material {
    override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
    override fun destroy() = Unit
}

/** A device that records what it was asked to upload, with [compressed] as its only capability. */
internal class FakeDevice(val compressed: FakeCompressedTextures?) : GpuDevice {
    var rgba: TextureAsset? = null
    override val clipSpace: ClipSpace = ClipSpace.Vulkan
    override fun createMesh(geometry: MeshGeometry): Mesh = error("unused")
    override fun createMaterial(
        texture: TextureAsset?,
        renderTarget: RenderTarget?,
        uniformFloatCount: Int,
        pbrTextures: PbrTextureSet?,
    ): Material {
        rgba = texture
        return StubMaterial
    }
    override fun createRenderTarget(width: Int, height: Int): RenderTarget = error("unused")
    override fun destroy() = Unit

    @Suppress("UNCHECKED_CAST")
    override fun <T : GpuCapability> capability(kind: GpuCapabilityKind<T>): T? =
        if (kind == GpuCompressedTextures) compressed as T? else null
}
