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
import com.awakekt.awake.core.geometry.MeshGeometry
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
@Suppress("TooManyFunctions") // Implements the ContentFeatureGpu contract alongside private upload helpers.
internal class WebGpuContentFeatureGpu(
    private val graphicsDevice: GraphicsDevice,
    override val registry: PipelineRegistry<RenderPipeline>,
    private val depthPass: DepthPrePassFeature? = null,
    override val supportsSceneDepth: Boolean = false,
    private val sceneDepthPass: DepthPrePassFeature? = null,
) : ContentFeatureGpu<RenderPipeline> {
    override val backend = RenderBackend.WebGpu

    internal var onDestroyPipeline: (RenderPipeline) -> Unit = {}

    override fun handle(pipeline: RenderPipeline): PipelineHandle = pipeline.handle

    override fun upload(pipeline: RenderPipeline, feature: ContentFeature): ContentUpload =
        upload(pipeline, feature, includeGeometry = true)

    override fun upload(pipeline: RenderPipeline, feature: ContentFeature, includeGeometry: Boolean): ContentUpload {
        val textures = uploadTextures(feature)
        // Before anything binds the group: a GPUBindGroup is immutable once built, so unlike
        // Vulkan these have to arrive ahead of the first bind, not after.
        pipeline.writeContentTextures(textures, feature.samplerTextures)
        return uploadFeatureGeometry(feature, textures, includeGeometry)
    }

    override fun uploadGroup(pipeline: RenderPipeline, feature: ContentFeature): ContentGroupUpload =
        uploadGroup(pipeline, feature, includeGeometry = true)

    override fun uploadGroup(pipeline: RenderPipeline, feature: ContentFeature, includeGeometry: Boolean): ContentGroupUpload {
        val layout = checkNotNull(feature.spec.uniforms) { "Content feature '${feature.name}' declares no uniforms." }
        val textures = uploadTextures(feature)
        var built: WebGpuContentGroup? = null
        try {
            built = WebGpuContentGroup(graphicsDevice, pipeline, layout, textures, feature.samplerTextures)
        } finally {
            if (built == null) textures.values.forEach(Texture::destroy)
        }
        val group = checkNotNull(built)
        val upload = uploadFeatureGeometry(feature, textures, includeGeometry)
        return ContentGroupUpload(
            group,
            ContentUpload(upload.geometry, upload.prepare) {
                group.destroy()
                upload.release()
            },
        )
    }

    override fun uploadGeometry(geometry: MeshGeometry): ContentUpload {
        val mesh = Mesh(graphicsDevice, {}, geometry.vertices, geometry.indices, geometry.format)
        return ContentUpload(ContentGeometry(mesh.vertexBinding, mesh.indexBinding, mesh.indexCount)) {
            mesh.destroy()
        }
    }

    // A binding written after upload keeps every level, at the size its writes are in; the rest are
    // only sampled by UV and leave out the top levels mip skip asks for (#1660).
    private fun uploadTextures(feature: ContentFeature): Map<Int, Texture> {
        val written = feature.textureUpdates?.bindings.orEmpty()
        return feature.textures.mapValues { (binding, asset) ->
            if (binding in written) {
                Texture(graphicsDevice, {}, asset.data, asset.width, asset.height, asset.layerCount, asset.isCubemap, asset.filtering)
            } else {
                Texture.skippingMips(graphicsDevice, asset)
            }
        } + feature.images.mapValues { (_, image) -> uploadImage(image) }
    }

    /** [feature]'s geometry (when [includeGeometry] is true), and what frees it with [textures]. */
    private fun uploadFeatureGeometry(
        feature: ContentFeature,
        textures: Map<Int, Texture>,
        includeGeometry: Boolean,
    ): ContentUpload {
        val mesh = if (includeGeometry) {
            feature.geometry?.let { source ->
                Mesh(graphicsDevice, {}, source.vertices, source.indices, source.format)
            }
        } else {
            null
        }
        // Held on its own: the upload outlives this call, and must not keep the feature's pixels with it (#1619).
        val updates = feature.textureUpdates
        // Owned here: a bind group or a recorded bind references these without owning them.
        return ContentUpload(mesh?.let { ContentGeometry(it.vertexBinding, it.indexBinding, it.indexCount) }, prepare = { frameIndex, uploads ->
            updates?.updates(frameIndex)?.forEach { update ->
                require(update.binding in updates.bindings)
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
        return Texture.skippingMips(graphicsDevice, image.toTextureAsset())
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
        val passes = listOfNotNull(depthPass, sceneDepthPass)
        if (passes.isEmpty()) return null
        val shaderCode = depth.vertexShader.resolveBytes()
        // The content pipeline's own group-0 layout, so the bind group of every feature drawing
        // through it binds unchanged. The depth pipeline's layout keeps it; this reference ends
        // once that is built.
        val built = pipeline.groupZeroLayout.use { groupZero ->
            passes.map { pass ->
                pass to DepthOnlyPipeline(
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
                    depthFormat = pass.depthTarget.textureFormat,
                )
            }
        }
        return object : ContentDepthCaster {
            override suspend fun cast(source: ContentDepthSource): ContentUpload {
                val casters = built.map { (pass, depthPipeline) ->
                    val caster = DepthPrePassFeature.ContentCaster(depthPipeline, source)
                    pass.contentCasters += caster
                    pass to caster
                }
                return ContentUpload(null) {
                    casters.forEach { (pass, caster) -> pass.contentCasters -= caster }
                }
            }

            override fun destroy() = built.forEach { (_, depthPipeline) -> depthPipeline.destroy() }
        }
    }

    override fun destroyPipeline(pipeline: RenderPipeline) {
        onDestroyPipeline(pipeline)
        pipeline.destroy()
    }

    /** Nothing to wait for: WebGPU frees a destroyed resource only after the work already
     * submitted against it completes. */
    override fun awaitIdle() = Unit
}
