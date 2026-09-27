/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.application

import com.awakekt.awake.asset.shaders.ContentFeatureGpu
import com.awakekt.awake.asset.shaders.ContentUpload
import com.awakekt.awake.asset.shaders.RenderBackend
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.ContentGeometry
import com.awakekt.awake.render.pipeline.PipelineRegistry
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.mesh.Mesh
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.texture.Texture

/** Vulkan's half of building a content feature, whether the plan declared it or it was attached later. */
internal class VulkanContentFeatureGpu(
    private val graphicsDevice: GraphicsDevice,
    private val transferContext: TransferContext,
    override val registry: PipelineRegistry<RenderPipeline>,
) : ContentFeatureGpu<RenderPipeline> {
    override val backend = RenderBackend.Vulkan

    override fun handle(pipeline: RenderPipeline): PipelineHandle = pipeline

    override fun upload(pipeline: RenderPipeline, feature: ContentFeature): ContentUpload {
        val textures = feature.textures.mapValues { (_, asset) ->
            Texture(
                graphicsDevice,
                transferContext::runOneTimeCommands,
                asset.data,
                asset.width,
                asset.height,
                layerCount = asset.layerCount,
                isCubemap = asset.isCubemap,
            )
        }
        // After the registry compiled the pipeline, because the layout comes from a spec and the
        // pixels come from the feature -- see PerFrameUniformSlots.writeTextures.
        pipeline.writeContentTextures(textures)
        val mesh = feature.geometry?.let { source ->
            Mesh(
                graphicsDevice,
                transferContext::runOneTimeCommands,
                source.vertices,
                source.indices,
                source.format,
            )
        }
        // Owned here: a descriptor write or a recorded bind references these without owning them.
        return ContentUpload(mesh?.let { ContentGeometry(it.vertexBinding, it.indexBinding, it.indexCount) }) {
            textures.values.forEach(Texture::destroy)
            mesh?.destroy()
        }
    }

    override fun destroyPipeline(pipeline: RenderPipeline) = pipeline.destroy()

    override fun awaitIdle() {
        VulkanBuffers.vkDeviceWaitIdle(graphicsDevice.device)
    }
}
