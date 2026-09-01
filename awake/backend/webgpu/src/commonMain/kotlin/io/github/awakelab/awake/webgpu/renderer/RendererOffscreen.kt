/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.webgpu.renderer

import io.github.awakelab.awake.webgpu.pipeline.WebGpuPipelineHandle
import io.github.awakelab.awake.render.passes.uniforms.litShadowUniforms
import io.github.awakelab.awake.render.passes.uniforms.SceneFrameUniforms
import io.github.awakelab.awake.core.geometry.MeshGeometry
import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.core.math.Mat4
import io.github.awakelab.awake.core.math.times
import io.github.awakelab.awake.render.renderer.shadowCascades
import io.github.awakelab.awake.render.passes.recordPassFeatures
import io.github.awakelab.awake.render.passes.RenderPassSlot
import io.github.awakelab.awake.render.renderer.ShadowCascadeUniforms
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

/**
 * The same frame the screen gets, into [target] instead of the swapchain.
 *
 * This used to be a hand-written subset: primary-format draws only, a minimal uniform block
 * written inline, and no render features at all. So an offscreen capture silently lacked the
 * sky, debug lines, transparency, instancing, particles and every content feature -- and
 * anything verified through it, which on this backend is everything (there is no window in a
 * test), was verified against a renderer nobody ships.
 *
 * It now prepares draws and records features through exactly the calls [performDraw] uses. What
 * legitimately differs is the attachments it renders into, the aspect it derives from them, and
 * the absence of a UI overlay -- a capture is of the scene.
 */
internal fun Renderer.performRenderToTexture(
    target: RenderTarget,
    camera: Lens,
    drawCalls: List<DrawCall>,
    light: SceneLight,
) {
    val offscreen = target as OffscreenRenderTarget
    val device = graphicsDevice.wgpuContext.device
    val primary = PrimaryPipelineBinding(pipeline = renderPipeline.handle, wireframe = false)

    val aspect = offscreen.width.toFloat() / offscreen.height.toFloat()
    val viewProjection = camera.viewProjectionMatrix(aspect, clipSpace)
    val lightUniforms = sceneLightUniforms(light, camera.eye)
    val cascades = if (depthPrePass != null) light.shadowCascades() else null
    val frame = FrameDrawContext(camera.eye, viewProjection, lightUniforms, cascades, light)
    val opaqueDraws = prepareOpaqueDraws(drawCalls, frame, primary)

    val encoder = device.createCommandEncoder()
    // Before the colour pass, as on screen: whatever draws below may sample the depth these write.
    recordDepthPasses(encoder, drawCalls, frame)

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
        recordPassFeatures(
            renderFeatures,
            RenderPassSlot.Scene,
            sceneContext(
                this,
                opaqueDraws,
                primary.pipeline,
                frame,
                SurfaceSize(offscreen.width, offscreen.height),
            ),
        )
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
/**
 * [drawCalls] prepared against [depthPipeline]'s own layout, for a depth pass.
 *
 * The scene's prepared draws cannot be reused here, and that is not a style preference: WebGPU's
 * "auto" pipeline layout produces a bind group layout that belongs to ONE pipeline, so handing a
 * depth pipeline a bind group built for the scene pipeline fails validation with "Exclusive
 * pipelines don't match" -- which aborts the process rather than returning an error. Vulkan's
 * binding-compatibility rules let its depth pass borrow the scene's descriptor set; this backend
 * has to write its own.
 *
 * The block written is the one the depth shader reads: `shadow_depth` builds its clip position
 * from `model` and the cascade block, so a shadowed frame needs lit_shadow's full block, while
 * `scene_depth` reads only `mvp` and takes the small one.
 */
private fun Renderer.depthPassDraws(
    depthPipeline: WebGpuPipelineHandle,
    drawCalls: List<DrawCall>,
    frame: FrameDrawContext,
): List<WebGpuPreparedDraw> {
    val device = graphicsDevice.wgpuContext.device
    return drawCalls
        .filter { it.mesh.format == primaryVertexFormat }
        .mapIndexed { index, drawCall ->
            val slot = bufferPools.uniformSlotForDraw(depthPipeline.pipeline, index)
            val mvp = drawCall.model * frame.viewProjection
            val cascades = frame.cascades
            val floats = if (cascades != null) {
                litShadowUniforms(
                    drawCall = drawCall,
                    mvp = mvp,
                    cascades = cascades,
                    frame = SceneFrameUniforms(frame.lightUniforms, frame.cameraEye, fogFloats()),
                )
            } else {
                UniformWriter(InstancedUniformLayout)
                    .put(mvp.data, UniformFields.Mvp)
                    .let(frame.lightUniforms::writeDirectionalTo)
                    .build()
            }
            device.queue.writeBuffer(slot.buffer, 0uL, fastArrayBufferOf(floats))
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

/** [depthPassDraws] for whichever depth passes this renderer has, recorded into [encoder]. */
internal fun Renderer.recordDepthPasses(
    encoder: io.ygdrasil.webgpu.GPUCommandEncoder,
    drawCalls: List<DrawCall>,
    frame: FrameDrawContext,
) {
    val cascades = frame.cascades
    val shadowPass = depthPrePass
    if (shadowsEnabled && shadowPass != null && cascades != null) {
        shadowPass.recordCommands(encoder, depthPassDraws(shadowPass.depthOnlyHandle, drawCalls, frame), cascades)
    }
    // Not gated on shadowsEnabled -- that toggle is about shadows, and water or fog needs this
    // either way. Its "cascade" is the camera's own view-projection, and it reads mvp, so the
    // frame it prepares against carries no cascade set.
    val scenePass = sceneDepthPass
    if (scenePass != null) {
        val cameraFrame = FrameDrawContext(frame.cameraEye, frame.viewProjection, frame.lightUniforms, null, frame.light)
        scenePass.recordCommands(
            encoder,
            depthPassDraws(scenePass.depthOnlyHandle, drawCalls, cameraFrame),
            ShadowCascadeUniforms(listOf(frame.viewProjection), floatArrayOf(Float.MAX_VALUE)),
        )
    }
}

