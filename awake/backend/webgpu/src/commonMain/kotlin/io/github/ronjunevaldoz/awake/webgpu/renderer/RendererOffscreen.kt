// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.webgpu.renderer

import io.github.ronjunevaldoz.awake.render.passes.uniforms.sceneLightUniforms
import io.github.ronjunevaldoz.awake.render.passes.uniforms.SceneLightUniforms
import io.github.ronjunevaldoz.awake.render.renderer.UniformWriter
import io.github.ronjunevaldoz.awake.render.renderer.UniformFields
import io.github.ronjunevaldoz.awake.render.renderer.ParticleExtraUniformLayout
import io.github.ronjunevaldoz.awake.render.renderer.ParticleUniformLayout
import io.github.ronjunevaldoz.awake.render.renderer.InstancedUniformLayout
import io.github.ronjunevaldoz.awake.core.math.Lens
import io.github.ronjunevaldoz.awake.core.math.times
import io.github.ronjunevaldoz.awake.render.material.Material as RenderMaterial
import io.github.ronjunevaldoz.awake.render.mesh.Mesh as RenderMesh
import io.github.ronjunevaldoz.awake.core.geometry.MeshGeometry
import io.github.ronjunevaldoz.awake.render.passes.uniforms.sceneLightFloats
import io.github.ronjunevaldoz.awake.render.renderer.DEFAULT_SCENE_LIGHT
import io.github.ronjunevaldoz.awake.render.renderer.SceneLight
import io.github.ronjunevaldoz.awake.render.renderer.DrawCall
import io.github.ronjunevaldoz.awake.render.texture.PbrTextureSet
import io.github.ronjunevaldoz.awake.render.texture.RenderTarget
import io.github.ronjunevaldoz.awake.render.texture.TextureAsset
import io.github.ronjunevaldoz.awake.webgpu.WebGpuHandles
import io.github.ronjunevaldoz.awake.webgpu.fastArrayBufferOf
import io.github.ronjunevaldoz.awake.webgpu.material.Material
import io.github.ronjunevaldoz.awake.webgpu.mesh.Mesh
import io.github.ronjunevaldoz.awake.webgpu.mesh.meshIndexFormat
import io.github.ronjunevaldoz.awake.webgpu.texture.OffscreenRenderTarget
import io.github.ronjunevaldoz.awake.webgpu.texture.Texture
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUMapMode
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDepthStencilAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.SamplerDescriptor
import io.ygdrasil.webgpu.TexelCopyBufferInfo
import io.ygdrasil.webgpu.TexelCopyTextureInfo
import io.ygdrasil.webgpu.beginRenderPass

internal fun Renderer.performCreateMesh(geometry: MeshGeometry): RenderMesh =
    Mesh(graphicsDevice, {}, geometry.vertices, geometry.indices, geometry.format)

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
                asset?.let(::uploadTexture) ?: neutralPbrTextures.getOrPut(neutral) { uploadTexture(neutral) }
            }
        } else {
            emptyList()
        }
        material.createResources(uploadTexture(texture), pbr)
    }
    return material
}

internal fun Renderer.uploadTexture(asset: TextureAsset): Texture =
    Texture(graphicsDevice, {}, asset.data, asset.width, asset.height).also { createdTextures += it }

internal fun Renderer.performCreateRenderTarget(width: Int, height: Int): RenderTarget =
    OffscreenRenderTarget(graphicsDevice, width, height).also { createdRenderTargets += it }

internal fun Renderer.performRenderToTexture(
    target: RenderTarget,
    camera: Lens,
    drawCalls: List<DrawCall>,
    light: SceneLight,
) {
    val offscreen = target as OffscreenRenderTarget
    val device = graphicsDevice.wgpuContext.device
    val pipeline = renderPipeline.handle.pipeline

    val aspect = offscreen.width.toFloat() / offscreen.height.toFloat()
    val viewProjection = camera.viewProjectionMatrix(aspect, clipSpace)

    val encoder = device.createCommandEncoder()
    encoder.beginRenderPass(
        RenderPassDescriptor(
            colorAttachments = listOf(
                RenderPassColorAttachment(
                    view = offscreen.colorView,
                    loadOp = GPULoadOp.Clear,
                    clearValue = clearColorValue,
                    storeOp = GPUStoreOp.Store,
                ),
            ),
            depthStencilAttachment = RenderPassDepthStencilAttachment(
                view = offscreen.depthView,
                depthClearValue = 1.0f,
                depthLoadOp = GPULoadOp.Clear,
                depthStoreOp = GPUStoreOp.Store,
            ),
        ),
    ) {
        val lightUniforms = sceneLightUniforms(light, camera.eye)
        setPipeline(pipeline)
        var drawIndex = 0
        var slotIndex = 0
        while (drawIndex < drawCalls.size) {
            val drawCall = drawCalls[drawIndex]
            if (drawCall.mesh.format != primaryVertexFormat) {
                drawIndex += 1
                continue
            }
            val mvp = drawCall.model * viewProjection
            // Per-draw slot, not one shared buffer -- see primaryDraw's own comment.
            val slot = bufferPools.uniformSlotForDraw(pipeline, slotIndex)
            slotIndex += 1
            device.queue.writeBuffer(
                slot.buffer,
                0uL,
                fastArrayBufferOf(
                    UniformWriter(InstancedUniformLayout)
                        .put(mvp.data, UniformFields.Mvp)
                        .let(lightUniforms::writeDirectionalTo)
                        .build(),
                ),
            )
            setBindGroup(0u, slot.binding.bindGroup)
            val mesh = drawCall.mesh as Mesh
            setVertexBuffer(0u, WebGpuHandles.resolve(mesh.vertexBuffer.handle))
            setIndexBuffer(WebGpuHandles.resolve(mesh.indexBuffer.handle), meshIndexFormat)
            drawIndexed(mesh.indexCount.toUInt())
            drawIndex += 1
        }
        end()
    }
    device.queue.submit(listOf(encoder.finish()))
}

internal suspend fun Renderer.performReadPixels(target: RenderTarget): TextureAsset {
    val offscreen = target as OffscreenRenderTarget
    val device = graphicsDevice.wgpuContext.device
    val unpaddedBytesPerRow = offscreen.width * 4
    val bytesPerRow = ((unpaddedBytesPerRow + 255) / 256) * 256
    val bufferSize = (bytesPerRow * offscreen.height).toULong()
    val readbackBuffer = device.createBuffer(
        BufferDescriptor(size = bufferSize, usage = GPUBufferUsage.CopyDst or GPUBufferUsage.MapRead),
    )
    val encoder = device.createCommandEncoder()
    encoder.copyTextureToBuffer(
        source = TexelCopyTextureInfo(texture = offscreen.colorTexture),
        destination = TexelCopyBufferInfo(buffer = readbackBuffer, bytesPerRow = bytesPerRow.toUInt()),
        copySize = Extent3D(width = offscreen.width.toUInt(), height = offscreen.height.toUInt()),
    )
    device.queue.submit(listOf(encoder.finish()))

    readbackBuffer.mapAsync(GPUMapMode.Read).getOrThrow()
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
    readbackBuffer.unmap()
    readbackBuffer.close()
    return TextureAsset(packed, offscreen.width, offscreen.height)
}
