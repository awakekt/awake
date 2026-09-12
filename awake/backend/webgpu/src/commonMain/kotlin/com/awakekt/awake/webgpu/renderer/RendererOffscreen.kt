/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.render.capture.FramebufferAttachment
import com.awakekt.awake.render.capture.FramebufferAttachmentData
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.webgpu.device.drivePendingWork
import com.awakekt.awake.webgpu.material.Material
import com.awakekt.awake.webgpu.mesh.Mesh
import com.awakekt.awake.webgpu.texture.OffscreenRenderTarget
import com.awakekt.awake.webgpu.texture.Texture
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUMapMode
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.SamplerDescriptor
import io.ygdrasil.webgpu.TexelCopyBufferInfo
import io.ygdrasil.webgpu.TexelCopyTextureInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.awakekt.awake.render.material.Material as RenderMaterial
import com.awakekt.awake.render.mesh.Mesh as RenderMesh

internal fun Renderer.performCreateMesh(geometry: MeshGeometry): RenderMesh =
    Mesh(graphicsDevice, {}, geometry.vertices, geometry.indices, geometry.format, geometry.bounds)

internal fun Renderer.performCreateMaterial(
    texture: TextureAsset?,
    renderTarget: RenderTarget?,
    uniformFloatCount: Int,
    pbrTextures: PbrTextureSet?,
): RenderMaterial {
    require(texture == null || renderTarget == null) { "Pass at most one of texture/renderTarget." }
    val material = Material(graphicsDevice, uniformFloatCount)
    if (renderTarget != null) {
        val offscreen = renderTarget as OffscreenRenderTarget
        val sampler = graphicsDevice.wgpuContext.device.createSampler(SamplerDescriptor())
        material.createResourcesFromRenderTarget(offscreen.colorView, sampler)
    } else if (texture != null) {
        val pbr = if (pbrTextures != null) {
            listOf(
                pbrTextures.metallicRoughness to Renderer.NEUTRAL_METALLIC_ROUGHNESS,
                pbrTextures.normal to Renderer.NEUTRAL_NORMAL,
                pbrTextures.occlusion to Renderer.NEUTRAL_OCCLUSION,
                pbrTextures.emissive to Renderer.NEUTRAL_EMISSIVE,
            ).map { (asset, neutral) ->
                asset?.let(::uploadTexture) ?: textureResources.neutral(neutral) {
                    uploadTexture(
                        neutral,
                    )
                }
            }
        } else {
            emptyList()
        }
        material.createResources(uploadTexture(texture), pbr)
    }
    return material
}

internal fun Renderer.uploadTexture(asset: TextureAsset): Texture =
    Texture(
        graphicsDevice,
        {},
        asset.data,
        asset.width,
        asset.height,
    ).let(textureResources::register)

internal fun Renderer.performCreateRenderTarget(width: Int, height: Int): RenderTarget {
    lateinit var target: OffscreenRenderTarget
    target = OffscreenRenderTarget(
        graphicsDevice,
        width,
        height,
        onDestroy = { createdRenderTargets.remove(target) },
    )
    createdRenderTargets += target
    return target
}

internal suspend fun Renderer.performReadPixels(target: RenderTarget): TextureAsset {
    val offscreen = target as OffscreenRenderTarget
    val device = graphicsDevice.wgpuContext.device
    val unpaddedBytesPerRow = offscreen.width * 4
    val bytesPerRow = ((unpaddedBytesPerRow + 255) / 256) * 256
    val bufferSize = (bytesPerRow * offscreen.height).toULong()
    val readbackBuffer = device.createBuffer(
        BufferDescriptor(
            size = bufferSize,
            usage = GPUBufferUsage.CopyDst or GPUBufferUsage.MapRead,
        ),
    )
    val encoder = device.createCommandEncoder()
    encoder.copyTextureToBuffer(
        source = TexelCopyTextureInfo(texture = offscreen.colorTexture),
        // rowsPerImage is required, not optional: wgpu rejects a zero with "invalid
        // rowsPerImage" and panics across the FFI boundary, which aborts the process rather
        // than throwing. Nothing caught it until this backend gained a pixel test, because
        // nothing had ever called readPixels here.
        destination = TexelCopyBufferInfo(
            buffer = readbackBuffer,
            bytesPerRow = bytesPerRow.toUInt(),
            rowsPerImage = offscreen.height.toUInt(),
        ),
        copySize = Extent3D(width = offscreen.width.toUInt(), height = offscreen.height.toUInt()),
    )
    device.queue.submit(listOf(encoder.finish()))

    // The map has to be awaited and the device driven at the same time: on wgpu-native the
    // callback only fires while polling, and without it getMappedRange aborts the process.
    // drivePendingWork is a no-op in a browser, where the event loop does this.
    coroutineScope {
        val mapping = async { readbackBuffer.mapAsync(GPUMapMode.Read) }
        device.drivePendingWork { mapping.isCompleted }
        mapping.await().getOrThrow()
    }
    val mapped = readbackBuffer.getMappedRange()
    val paddedBytes = mapped.toByteArray()
    val packed = ByteArray(unpaddedBytesPerRow * offscreen.height)
    var row = 0
    while (row < offscreen.height) {
        paddedBytes.copyInto(
            destination = packed,
            destinationOffset = row * unpaddedBytesPerRow,
            startIndex = row * bytesPerRow,
            endIndex = row * bytesPerRow + unpaddedBytesPerRow,
        )
        row += 1
    }
    if (offscreen.colorFormat == GPUTextureFormat.BGRA8Unorm || offscreen.colorFormat == GPUTextureFormat.BGRA8UnormSrgb) {
        var pixel = 0
        while (pixel < packed.size) {
            val blue = packed[pixel]
            packed[pixel] = packed[pixel + 2]
            packed[pixel + 2] = blue
            pixel += 4
        }
    }
    readbackBuffer.unmap()
    readbackBuffer.close()
    return TextureAsset(packed, offscreen.width, offscreen.height)
}

internal suspend fun Renderer.performReadFramebufferAttachment(
    target: RenderTarget,
    attachment: FramebufferAttachment,
): FramebufferAttachmentData = when (attachment) {
    FramebufferAttachment.Color0 -> FramebufferAttachmentData.fromRgba8(performReadPixels(target))
    FramebufferAttachment.Depth -> FramebufferAttachmentData.unavailable(
        attachment,
        "WebGPU offscreen depth is attached for testing but is not yet retained for CPU readback.",
    )
    FramebufferAttachment.Stencil,
    FramebufferAttachment.Normal,
    -> FramebufferAttachmentData.unavailable(
        attachment,
        "The current WebGPU scene framebuffer does not allocate a ${attachment.name.lowercase()} attachment.",
    )
}
