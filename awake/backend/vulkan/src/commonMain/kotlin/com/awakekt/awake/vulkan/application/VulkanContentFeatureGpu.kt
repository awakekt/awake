/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.application

import com.awakekt.awake.asset.shaders.ContentDepthCaster
import com.awakekt.awake.asset.shaders.ContentFeatureGpu
import com.awakekt.awake.asset.shaders.ContentGroupUpload
import com.awakekt.awake.asset.shaders.ContentUpload
import com.awakekt.awake.asset.shaders.RenderBackend
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.passes.ContentDepthSource
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.ContentGeometry
import com.awakekt.awake.render.passes.uniforms.MAX_SHADOW_TARGET_LAYERS
import com.awakekt.awake.render.pipeline.PipelineRegistry
import com.awakekt.awake.render.pipeline.PipelineSpec
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.toTextureAsset
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.mesh.Mesh
import com.awakekt.awake.vulkan.pipeline.DepthOnlyPipeline
import com.awakekt.awake.vulkan.pipeline.DepthPrePassFeature
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.ShaderPair
import com.awakekt.awake.vulkan.texture.Texture

/**
 * Vulkan's half of building a content feature, whether the plan declared it or it was attached later.
 *
 * A feature's depth pipeline draws into [depthPass], or nowhere when the engine renders none; it
 * takes the engine's [framesInFlight] for its cascade slots and compiles through [loadShaders], as
 * the registry's factory compiles a spec.
 */
internal class VulkanContentFeatureGpu(
    private val graphicsDevice: GraphicsDevice,
    private val transferContext: TransferContext,
    override val registry: PipelineRegistry<RenderPipeline>,
    private val depthPass: DepthPrePassFeature? = null,
    private val framesInFlight: Int = 1,
    private val loadShaders: suspend (PipelineSpec) -> ShaderPair = { error("No shader loader for a depth pipeline.") },
    override val supportsSceneDepth: Boolean = false,
    private val sceneDepthPass: DepthPrePassFeature? = null,
) : ContentFeatureGpu<RenderPipeline> {
    override val backend = RenderBackend.Vulkan

    override fun handle(pipeline: RenderPipeline): PipelineHandle = pipeline

    override fun upload(pipeline: RenderPipeline, feature: ContentFeature): ContentUpload =
        uploadInto(pipeline.contentFrameSlotCount, feature) { index, frame -> pipeline.writeContentTextureSlot(index, frame, feature.samplerTextures) }

    override fun uploadGroup(pipeline: RenderPipeline, feature: ContentFeature): ContentGroupUpload {
        val layout = checkNotNull(feature.spec.uniforms) { "Content feature '${feature.name}' declares no uniforms." }
        val slots = pipeline.newContentGroup()
        var uploaded: ContentUpload? = null
        try {
            uploaded = uploadInto(slots.size, feature) { index, frame -> slots.writeTextures(frame, index, feature.samplerTextures) }
        } finally {
            if (uploaded == null) slots.destroy()
        }
        val upload = checkNotNull(uploaded)
        return ContentGroupUpload(
            slots.uniformBlock(layout),
            ContentUpload(upload.geometry, upload.prepare) {
                upload.release()
                slots.destroy()
            },
        )
    }

    /** [feature]'s textures, one copy per frame slot of the mutable ones, written by [writeSlot]; and its geometry. */
    private fun uploadInto(slotCount: Int, feature: ContentFeature, writeSlot: (Int, Map<Int, Texture>) -> Unit): ContentUpload {
        fun create(asset: TextureAsset): Texture =
            Texture(
                graphicsDevice,
                transferContext::runOneTimeCommands,
                asset.data,
                asset.width,
                asset.height,
                layerCount = asset.layerCount,
                isCubemap = asset.isCubemap,
                filtering = asset.filtering,
            )
        // A binding written after upload keeps every level, at the size its writes are in; the rest are
        // only sampled by UV and leave out the top levels mip skip asks for (#1660).
        val written = feature.textureUpdates?.bindings.orEmpty()
        fun sampled(asset: TextureAsset): Texture = Texture.skippingMips(graphicsDevice, transferContext::runOneTimeCommands, asset)
        // Images take the CPU path here: Vulkan has no platform image to copy (awakekt/awake#1184).
        val textures = feature.textures.mapValues { (binding, asset) -> if (binding in written) create(asset) else sampled(asset) } +
            feature.images.mapValues { (_, image) -> sampled(image.toTextureAsset()) }
        // After the registry compiled the pipeline, because the layout comes from a spec and the
        // pixels come from the feature -- see PerFrameUniformSlots.writeTextures.
        // Held on its own: the upload outlives this call, and must not keep the feature's pixels with it (#1619).
        val updates = feature.textureUpdates
        val mutable = updates?.bindings.orEmpty()
        val frames = List(slotCount) { index ->
            if (index == 0) {
                textures
            } else {
                textures.mapValues { (binding, texture) ->
                    if (binding in mutable) create(feature.textures.getValue(binding)) else texture
                }
            }
        }
        frames.forEachIndexed(writeSlot)
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
        return ContentUpload(mesh?.let { ContentGeometry(it.vertexBinding, it.indexBinding, it.indexCount) }, prepare = { frameIndex, uploads ->
            val frame = frames[frameIndex]
            updates?.updates(frameIndex)?.forEach { update ->
                require(update.binding in mutable) { "Update names an immutable content binding." }
                uploads.write(frame.getValue(update.binding), update.region)
            }
        }) {
            frames.flatMap { it.values }.distinct().forEach(Texture::destroy)
            mesh?.destroy()
        }
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
        // Set 0 is the content pipeline's own layout, so the descriptor sets of every group made
        // from it (newContentGroup) bind here unchanged.
        val setLayout = checkNotNull(pipeline.uniformSetLayout) { "A content pipeline owns its uniform group." }
        val shaders = loadShaders(depth)
        val built = passes.map { pass ->
            val target = pass.depthTarget
            pass to DepthOnlyPipeline(
                graphicsDevice,
                target.renderPass,
                setLayout,
                shaders,
                depth.vertexFormat,
                target.size,
                depth.vertexEntryPoint,
                depth.fragmentEntryPoint,
                // Every layer the array can grow to, not the layers it holds now (#1688).
                cascadeCount = MAX_SHADOW_TARGET_LAYERS,
                framesInFlight = framesInFlight,
                frontFace = depth.frontFace,
                shadowBias = pass === depthPass,
            )
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

    override fun destroyPipeline(pipeline: RenderPipeline) = pipeline.destroy()

    override fun awaitIdle() {
        VulkanBuffers.vkDeviceWaitIdle(graphicsDevice.device)
    }
}
