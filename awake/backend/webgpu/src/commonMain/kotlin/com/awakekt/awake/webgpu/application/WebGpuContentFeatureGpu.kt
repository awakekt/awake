/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.application

import com.awakekt.awake.asset.shaders.ContentFeatureGpu
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
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.mesh.Mesh
import com.awakekt.awake.webgpu.pipeline.DepthOnlyPipeline
import com.awakekt.awake.webgpu.pipeline.DepthPrePassFeature
import com.awakekt.awake.webgpu.pipeline.RenderPipeline
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
        val textures = feature.textures.mapValues { (_, asset) ->
            Texture(graphicsDevice, {}, asset.data, asset.width, asset.height, asset.layerCount, asset.isCubemap, asset.filtering)
        }
        // Before anything binds the group: a GPUBindGroup is immutable once built, so unlike
        // Vulkan these have to arrive ahead of the first bind, not after.
        pipeline.writeContentTextures(textures, feature.samplerTextures)
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

    override suspend fun addDepthCaster(pipeline: RenderPipeline, depth: PipelineSpec, source: ContentDepthSource): ContentUpload? {
        val pass = depthPass ?: return null
        val caster = DepthPrePassFeature.ContentCaster(
            DepthOnlyPipeline(
                graphicsDevice = graphicsDevice,
                shaderCode = depth.vertexShader.resolveBytes(),
                vertexFormat = depth.vertexFormat,
                vertexEntryPoint = depth.vertexEntryPoint,
                fragmentEntryPoint = depth.fragmentEntryPoint,
                cascadeCount = MAX_SHADOW_TARGET_LAYERS,
                frontFace = depth.frontFace,
                bindingsByGroup = depth.bindingsByGroup,
                bindingsMetadataAvailable = depth.bindingsMetadataAvailable,
                // The content pipeline's own group-0 layout, so its bind group binds unchanged.
                groupZeroLayout = pipeline.groupZeroLayout,
            ),
            source,
        )
        pass.contentCasters += caster
        return ContentUpload(null) {
            pass.contentCasters -= caster
            caster.pipeline.destroy()
        }
    }

    override fun destroyPipeline(pipeline: RenderPipeline) = pipeline.destroy()

    /** Nothing to wait for: WebGPU frees a destroyed resource only after the work already
     * submitted against it completes. */
    override fun awaitIdle() = Unit
}
