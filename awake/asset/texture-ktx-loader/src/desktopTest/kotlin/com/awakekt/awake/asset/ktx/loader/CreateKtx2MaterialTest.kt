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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CreateKtx2MaterialTest {

    @Test
    fun aDeviceWithBc7TakesTheTextureCompressed() {
        val device = Device(Compressed(setOf(CompressedTextureFormat.Bc7Rgba), accepts = true))

        assertNotNull(device.createKtx2Material(CookedTextures.bytes("rock.ktx2"), LibKtxBasisTranscoder))

        assertEquals(CompressedTextureFormat.Bc7Rgba, device.compressed?.uploaded?.format)
        assertNull(device.rgba, "nothing went the RGBA8 way")
    }

    @Test
    fun aDeviceThatRefusesTheImageGetsRgba8AndOneWithoutTheCapabilityToo() {
        val refusing = Device(Compressed(setOf(CompressedTextureFormat.Bc7Rgba), accepts = false))
        val plain = Device(null)

        assertNotNull(refusing.createKtx2Material(CookedTextures.bytes("rock.ktx2"), LibKtxBasisTranscoder))
        assertNotNull(plain.createKtx2Material(CookedTextures.bytes("rock.ktx2"), LibKtxBasisTranscoder))

        assertEquals(CookedTextures.SIZE, refusing.rgba?.width)
        assertEquals(CookedTextures.SIZE, plain.rgba?.width)
    }

    @Test
    fun aFileThisDeviceCantTakeMakesNoMaterial() {
        assertNull(Device(null).createKtx2Material(CookedTextures.bytes("rock.astc.ktx2"), LibKtxBasisTranscoder))
    }

    private class Compressed(override val formats: Set<CompressedTextureFormat>, private val accepts: Boolean) : GpuCompressedTextures {
        var uploaded: CompressedTextureImage? = null

        override fun createMaterial(image: CompressedTextureImage, filtering: TextureFiltering, uniformFloatCount: Int): Material? {
            uploaded = image
            return if (accepts) Stub else null
        }
    }

    private object Stub : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private class Device(val compressed: Compressed?) : GpuDevice {
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
            return Stub
        }
        override fun createRenderTarget(width: Int, height: Int): RenderTarget = error("unused")
        override fun destroy() = Unit

        @Suppress("UNCHECKED_CAST")
        override fun <T : GpuCapability> capability(kind: GpuCapabilityKind<T>): T? =
            if (kind == GpuCompressedTextures) compressed as T? else null
    }
}
