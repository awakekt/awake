/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.webgpu.renderer

import io.github.awakelab.awake.core.geometry.MeshGeometry
import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.core.math.Mat4
import io.github.awakelab.awake.core.math.times
import io.github.awakelab.awake.render.passes.uniforms.sceneLightUniforms
import io.github.awakelab.awake.render.renderer.DrawCall
import io.github.awakelab.awake.render.renderer.InstancedUniformLayout
import io.github.awakelab.awake.render.renderer.SceneLight
import io.github.awakelab.awake.render.renderer.UniformFields
import io.github.awakelab.awake.render.renderer.UniformWriter
import io.github.awakelab.awake.render.texture.PbrTextureSet
import io.github.awakelab.awake.render.texture.RenderTarget
import io.github.awakelab.awake.render.texture.TextureAsset
import io.github.awakelab.awake.render.pipeline.BindingSemantic
import io.github.awakelab.awake.webgpu.WebGpuHandles
import io.github.awakelab.awake.webgpu.pipeline.WebGpuBindGroupHandle
import io.github.awakelab.awake.webgpu.device.drivePendingWork
import io.github.awakelab.awake.webgpu.fastArrayBufferOf
import io.github.awakelab.awake.webgpu.material.Material
import io.github.awakelab.awake.webgpu.mesh.Mesh
import io.github.awakelab.awake.webgpu.mesh.meshIndexFormat
import io.github.awakelab.awake.webgpu.texture.OffscreenRenderTarget
import io.github.awakelab.awake.webgpu.texture.Texture
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUMapMode
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDepthStencilAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.SamplerDescriptor
import io.ygdrasil.webgpu.TexelCopyBufferInfo
import io.ygdrasil.webgpu.TexelCopyTextureInfo
import io.ygdrasil.webgpu.beginRenderPass
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import io.github.awakelab.awake.render.material.Material as RenderMaterial
import io.github.awakelab.awake.render.mesh.Mesh as RenderMesh

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
                asset?.let(::uploadTexture) ?: textureResources.neutral(neutral) { uploadTexture(neutral) }
            }
        } else {
            emptyList()
        }
        material.createResources(uploadTexture(texture), pbr)
    }
    return material
}

internal fun Renderer.uploadTexture(asset: TextureAsset): Texture =
    Texture(graphicsDevice, {}, asset.data, asset.width, asset.height).let(textureResources::register)

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
    // Before the colour pass, as on screen: whatever draws below may sample the depth this
    // writes. The on-screen path does this in RendererDraw3D; without it here, an offscreen
    // render of a scene-depth-reading pipeline has no bind group for the group it declares.
    val renderer = this
    val sceneDepth = sceneDepthPass
    val lightUniforms = sceneLightUniforms(light, camera.eye)
    sceneDepth?.recordCommands(encoder, offscreenDepthDraws(drawCalls, viewProjection, lightUniforms))
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
        setPipeline(pipeline)
        drawCalls
            .filter { it.mesh.format == renderer.primaryVertexFormat }
            .forEachIndexed { slotIndex, drawCall ->
                // Per-draw slot, not one shared buffer -- see primaryDraw's own comment.
                val slot = renderer.bufferPools.uniformSlotForDraw(pipeline, slotIndex)
                device.queue.writeBuffer(
                    slot.buffer,
                    0uL,
                    fastArrayBufferOf(
                        UniformWriter(InstancedUniformLayout)
                            .put((drawCall.model * viewProjection).data, UniformFields.Mvp)
                            .let(lightUniforms::writeDirectionalTo)
                            .build(),
                    ),
                )
                setBindGroup(0u, slot.binding.bindGroup)
                bindSceneDepthOn(renderer, sceneDepth)
                val mesh = drawCall.mesh as Mesh
                setVertexBuffer(0u, WebGpuHandles.resolve(mesh.vertexBuffer.handle))
                setIndexBuffer(WebGpuHandles.resolve(mesh.indexBuffer.handle), meshIndexFormat)
                drawIndexed(mesh.indexCount.toUInt())
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

/**
 * [drawCalls] as the prepared draws the depth pass rasterises, each with its own MVP written.
 *
 * The offscreen path does not build `PreparedDraw`s for its colour pass -- it writes uniforms
 * and draws inline -- so the depth pass, which does take them, needs them built here. Its own
 * uniform slots, keyed off the depth pipeline, so nothing it writes disturbs the colour pass's.
 */
private fun Renderer.offscreenDepthDraws(
    drawCalls: List<DrawCall>,
    viewProjection: Mat4,
    lightUniforms: io.github.awakelab.awake.render.passes.uniforms.SceneLightUniforms,
): List<WebGpuPreparedDraw> {
    val depthPipeline = sceneDepthPass?.depthOnlyHandle ?: return emptyList()
    val device = graphicsDevice.wgpuContext.device
    return drawCalls
        .filter { it.mesh.format == primaryVertexFormat }
        .mapIndexed { index, drawCall ->
            val slot = bufferPools.uniformSlotForDraw(depthPipeline.pipeline, index)
            device.queue.writeBuffer(
                slot.buffer,
                0uL,
                fastArrayBufferOf(
                    UniformWriter(InstancedUniformLayout)
                        .put((drawCall.model * viewProjection).data, UniformFields.Mvp)
                        .let(lightUniforms::writeDirectionalTo)
                        .build(),
                ),
            )
            val mesh = drawCall.mesh as Mesh
            WebGpuPreparedDraw(
                pipeline = depthPipeline,
                materialBinding = slot.binding,
                vertexBuffer = mesh.vertexBinding,
                indexBuffer = mesh.indexBinding,
                elementCount = mesh.indexCount,
            )
        }
}

/** Binds [pass]'s depth target at the group the primary pipeline declares for it, if any. */
private fun io.ygdrasil.webgpu.GPURenderPassEncoder.bindSceneDepthOn(
    renderer: Renderer,
    pass: io.github.awakelab.awake.webgpu.pipeline.DepthPrePassFeature?,
) {
    val depth = pass?.depthTarget ?: return
    val handle = renderer.renderPipeline.handle
    val binding = renderer.bufferPools.sceneDepthBindingFor(handle, depth)
    setBindGroup(
        handle.bindingLayout.slot(BindingSemantic.SceneDepth).toUInt(),
        (binding as WebGpuBindGroupHandle).bindGroup,
    )
}
