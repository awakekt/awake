/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.application

import com.awakekt.awake.asset.shaders.ContentDepthCaster
import com.awakekt.awake.asset.shaders.ContentFeatureGpu
import com.awakekt.awake.asset.shaders.ContentGroupUpload
import com.awakekt.awake.asset.shaders.ContentUpload
import com.awakekt.awake.asset.shaders.RenderBackend
import com.awakekt.awake.asset.shaders.resolveBytes
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.passes.ContentDepthSource
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.ContentGeometry
import com.awakekt.awake.render.passes.uniforms.MAX_SHADOW_TARGET_LAYERS
import com.awakekt.awake.render.pipeline.PipelineRegistry
import com.awakekt.awake.render.pipeline.PipelineSpec
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.render.texture.toTextureAsset
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.mesh.Mesh
import com.awakekt.awake.webgpu.pipeline.DepthOnlyPipeline
import com.awakekt.awake.webgpu.pipeline.DepthPrePassFeature
import com.awakekt.awake.webgpu.pipeline.RenderPipeline
import com.awakekt.awake.webgpu.pipeline.WebGpuContentGroup
import com.awakekt.awake.webgpu.renderer.canCopyExternalImage
import com.awakekt.awake.webgpu.renderer.copyExternalImage
import com.awakekt.awake.webgpu.texture.GpuMipmaps
import com.awakekt.awake.webgpu.texture.Texture

/**
 * WebGPU's half of building a content feature, whether the plan declared it or it was attached
 * later. A feature's depth pipeline draws into [depthPass], or nowhere when the engine renders none.
 */
internal class WebGpuContentFeatureGpu(
    private val graphicsDevice: GraphicsDevice,
    override val registry: PipelineRegistry<RenderPipeline>,
    private val depthPass: DepthPrePassFeature? = null,
) : ContentFeatureGpu<RenderPipeline> {
    override val backend = RenderBackend.WebGpu

    override fun handle(pipeline: RenderPipeline): PipelineHandle = pipeline.handle

    override fun upload(pipeline: RenderPipeline, feature: ContentFeature): ContentUpload {
        val textures = uploadTextures(feature)
        // Before anything binds the group: a GPUBindGroup is immutable once built, so unlike
        // Vulkan these have to arrive ahead of the first bind, not after.
        pipeline.writeContentTextures(textures, feature.samplerTextures)
        return uploadGeometry(feature, textures)
    }

    override fun uploadGroup(pipeline: RenderPipeline, feature: ContentFeature): ContentGroupUpload {
        val layout = checkNotNull(feature.spec.uniforms) { "Content feature '${feature.name}' declares no uniforms." }
        val textures = uploadTextures(feature)
        var built: WebGpuContentGroup? = null
        try {
            built = WebGpuContentGroup(graphicsDevice, pipeline, layout, textures, feature.samplerTextures)
        } finally {
            if (built == null) textures.values.forEach(Texture::destroy)
        }
        val group = checkNotNull(built)
        val upload = uploadGeometry(feature, textures)
        return ContentGroupUpload(
            group,
            ContentUpload(upload.geometry, upload.prepare) {
                group.destroy()
                upload.release()
            },
        )
    }

    private fun uploadTextures(feature: ContentFeature): Map<Int, Texture> =
        feature.textures.mapValues { (_, asset) ->
            Texture(graphicsDevice, {}, asset.data, asset.width, asset.height, asset.layerCount, asset.isCubemap, asset.filtering)
        } + feature.images.mapValues { (_, image) -> uploadImage(image) }

    /** [feature]'s geometry, and what frees it with [textures]. */
    private fun uploadGeometry(feature: ContentFeature, textures: Map<Int, Texture>): ContentUpload {
        val mesh = feature.geometry?.let { source ->
            Mesh(graphicsDevice, {}, source.vertices, source.indices, source.format)
        }
        // Owned here: a bind group or a recorded bind references these without owning them.
        return ContentUpload(mesh?.let { ContentGeometry(it.vertexBinding, it.indexBinding, it.indexCount) }, prepare = { frameIndex, uploads ->
            feature.textureUpdates?.updates(frameIndex)?.forEach { update ->
                require(update.binding in requireNotNull(feature.textureUpdates).bindings)
                uploads.write(textures.getValue(update.binding), update.region)
            }
        }) {
            textures.values.forEach(Texture::destroy)
            mesh?.destroy()
        }
    }

    /** Draws content images' mips, made on the first image copied. */
    private var mipmaps: GpuMipmaps? = null

    /** How many content images this has copied on the GPU, for tests that check which path one took. */
    internal var copies = 0
        private set

    // Copied on the GPU when the browser can; otherwise its CPU pixels, as a TextureAsset uploads.
    private fun uploadImage(image: GpuImageSource): Texture {
        val external = image.externalImage
        if (external != null && graphicsDevice.wgpuContext.device.queue.canCopyExternalImage(external)) {
            val mipmaps = mipmaps ?: GpuMipmaps(graphicsDevice.wgpuContext.device).also { mipmaps = it }
            val copied = graphicsDevice.copyExternalImage(mipmaps, external, image.width, image.height, TextureFiltering.Linear)
            if (copied != null) {
                copies++
                return copied
            }
        }
        // A refused copy lands here too.
        val asset = image.toTextureAsset()
        return Texture(graphicsDevice, {}, asset.data, asset.width, asset.height, filtering = asset.filtering)
    }

    override suspend fun addDepthCaster(pipeline: RenderPipeline, depth: PipelineSpec, source: ContentDepthSource): ContentUpload? {
        val caster = createDepthCaster(pipeline, depth) ?: return null
        val cast = checkNotNull(caster.cast(source))
        return ContentUpload(null) {
            cast.release()
            caster.destroy()
        }
    }

    override suspend fun createDepthCaster(pipeline: RenderPipeline, depth: PipelineSpec): ContentDepthCaster? {
        val pass = depthPass ?: return null
        val shaderCode = depth.vertexShader.resolveBytes()
        // The content pipeline's own group-0 layout, so the bind group of every feature drawing
        // through it binds unchanged. The depth pipeline's layout keeps it; this reference ends
        // once that is built.
        val depthPipeline = pipeline.groupZeroLayout.use { groupZero ->
            DepthOnlyPipeline(
                graphicsDevice = graphicsDevice,
                shaderCode = shaderCode,
                vertexFormat = depth.vertexFormat,
                vertexEntryPoint = depth.vertexEntryPoint,
                fragmentEntryPoint = depth.fragmentEntryPoint,
                cascadeCount = MAX_SHADOW_TARGET_LAYERS,
                frontFace = depth.frontFace,
                bindingsByGroup = depth.bindingsByGroup,
                bindingsMetadataAvailable = depth.bindingsMetadataAvailable,
                groupZeroLayout = groupZero,
            )
        }
        return object : ContentDepthCaster {
            override suspend fun cast(source: ContentDepthSource): ContentUpload {
                val caster = DepthPrePassFeature.ContentCaster(depthPipeline, source)
                pass.contentCasters += caster
                return ContentUpload(null) { pass.contentCasters -= caster }
            }

            override fun destroy() = depthPipeline.destroy()
        }
    }

    override fun destroyPipeline(pipeline: RenderPipeline) = pipeline.destroy()

    /** Nothing to wait for: WebGPU frees a destroyed resource only after the work already
     * submitted against it completes. */
    override fun awaitIdle() = Unit
}
