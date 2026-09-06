/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.material

import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.render.pipeline.ShaderStage
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.VkShaderStageFlagBits
import com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.handles.BufferHandle
import com.awakekt.awake.vulkan.handles.DescriptorPoolHandle
import com.awakekt.awake.vulkan.handles.DescriptorSetHandle
import com.awakekt.awake.vulkan.handles.DescriptorSetLayoutHandle
import com.awakekt.awake.vulkan.handles.DeviceMemoryHandle
import com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkBufferUsageFlagBits
import com.awakekt.awake.vulkan.models.info.VkDescriptorBufferInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorImageInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorPoolCreateInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorPoolSize
import com.awakekt.awake.vulkan.models.info.VkDescriptorSetLayoutBinding
import com.awakekt.awake.vulkan.models.info.VkDescriptorSetLayoutCreateInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorType
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo
import com.awakekt.awake.vulkan.pipeline.VulkanMaterialBinding
import com.awakekt.awake.vulkan.texture.Texture
import com.awakekt.awake.render.material.Material as RenderMaterial

/**
 * Owns the MVP-matrix uniform buffer and the descriptor set that binds it (plus a [Texture]'s
 * sampler/view) to the pipeline. Construction is split in two: the constructor only creates
 * [descriptorSetLayout], since a graphics pipeline needs that layout to exist before it's
 * built; [createResources] (called once a real [Texture] exists) stores the texture binding.
 * Per-frame/per-draw uniform buffers and descriptor sets are created lazily as the renderer
 * requests specific frame/draw slots.
 */
class Material(
    graphicsDevice: GraphicsDevice,
    private val uniformFloatCount: Int = DEFAULT_UNIFORM_FLOAT_COUNT,
    /** What this material's descriptor set declares. Defaults to the glTF metallic-roughness
     * shape every material had before the declaration existed, so a caller that does not care
     * gets exactly the previous layout. */
    private val bindings: GroupBindings = GroupBindings.StandardMaterial,
) : RenderMaterial {
    private val graphicsDevice = graphicsDevice
    private val device get() = graphicsDevice.device

    val descriptorSetLayout: DescriptorSetLayoutHandle

    var descriptorPool: DescriptorPoolHandle = DescriptorPoolHandle(0)
        private set
    var descriptorSet: DescriptorSetHandle = DescriptorSetHandle(0)
        private set
    var uniformBuffer: BufferHandle = BufferHandle(0)
        private set
    var uniformBufferMemory: DeviceMemoryHandle = DeviceMemoryHandle(0)
        private set

    private val uniformSlotsByFrame = mutableListOf<MutableList<UniformSlot>>()

    /** The sampler/image view this material was built with -- exposed (read-only) so
     * `UiTextureRenderPipeline` can bind the SAME sampled image into its own (screen-space
     * quad) descriptor set for on-screen compositing, without re-deriving them from whatever
     * [Texture]/`OffscreenRenderTarget` this material was created from. */
    var samplerHandle: Long = 0
        private set
    var imageViewHandle: Long = 0
        private set

    private var pbrImageViews: PbrImageViews? = null

    init {
        descriptorSetLayout = createDescriptorSetLayout(graphicsDevice, bindings)
    }

    /** Creates the uniform buffer, descriptor pool, and descriptor set (written to bind
     * both [uniformBuffer] and [texture]'s sampler/view). Must be called once, after a real
     * [Texture] exists. [pbr] fills bindings 5-8 -- see [PbrImageViews]. */
    fun createResources(texture: Texture, pbr: PbrImageViews) {
        createResources(texture.sampler.handle, texture.imageView.handle, pbr)
    }

    /** Same as [createResources] but binds an
     * [com.awakekt.awake.vulkan.texture.OffscreenRenderTarget]'s color
     * attachment directly instead of a [Texture]'s -- the on-screen compositing/portal-camera
     * use case (`Renderer.createMaterial(renderTarget = ...)`). Same descriptor-writing code
     * either way: a `VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER` binding doesn't care whether
     * the sampler/image view it's given came from a CPU-uploaded texture or a GPU-only
     * render target. */
    fun createResourcesFromRenderTarget(sampler: Long, imageView: Long, pbr: PbrImageViews) {
        createResources(sampler, imageView, pbr)
    }

    private fun createResources(sampler: Long, imageView: Long, pbr: PbrImageViews) {
        samplerHandle = sampler
        imageViewHandle = imageView
        pbrImageViews = pbr
    }

    private fun createUniformSlot(): UniformSlot {
        require(samplerHandle != 0L && imageViewHandle != 0L) {
            "Material resources must be created before allocating uniform slots."
        }
        val (rawUniformBuffer, rawUniformBufferMemory) = createMaterialUniformBuffer(
            graphicsDevice,
            uniformFloatCount,
        )
        val rawDescriptorPool = createMaterialDescriptorPool(device, bindings)
        val rawDescriptorSet = createMaterialDescriptorSet(
            device = device,
            descriptorPool = rawDescriptorPool,
            bindings = MaterialDescriptorSetBindings(
                descriptorSetLayout = descriptorSetLayout.handle,
                uniformBuffer = rawUniformBuffer,
                uniformFloatCount = uniformFloatCount,
                sampler = samplerHandle,
                imageView = imageViewHandle,
                pbr = requireNotNull(pbrImageViews),
            ),
        )
        return UniformSlot(
            descriptorPool = DescriptorPoolHandle(rawDescriptorPool),
            descriptorSet = DescriptorSetHandle(rawDescriptorSet),
            uniformBuffer = BufferHandle(rawUniformBuffer),
            uniformBufferMemory = DeviceMemoryHandle(rawUniformBufferMemory),
        )
    }

    private fun uniformSlot(frameIndex: Int, drawSlotIndex: Int): UniformSlot {
        require(frameIndex >= 0) { "frameIndex must be non-negative." }
        require(drawSlotIndex >= 0) { "drawSlotIndex must be non-negative." }
        while (uniformSlotsByFrame.size <= frameIndex) uniformSlotsByFrame.add(mutableListOf())
        val frameSlots = uniformSlotsByFrame[frameIndex]
        while (frameSlots.size <= drawSlotIndex) frameSlots += createUniformSlot()
        val slot = frameSlots[drawSlotIndex]
        if (frameIndex == 0 && drawSlotIndex == 0) {
            descriptorPool = slot.descriptorPool
            descriptorSet = slot.descriptorSet
            uniformBuffer = slot.uniformBuffer
            uniformBufferMemory = slot.uniformBufferMemory
        }
        return slot
    }

    /** Rewrites the whole uniform buffer with a new MVP matrix (column-major `FloatArray`,
     * as produced by `Mat4.data`). This compatibility overload targets frame/draw slot 0;
     * the renderer's frame path uses [updateUniformBuffer] with explicit frame/draw slots so
     * one shared material can be drawn multiple times without later draws overwriting earlier
     * uniforms before the GPU consumes them. */
    override fun updateUniformBuffer(uniformFloats: FloatArray) {
        updateUniformBuffer(frameIndex = 0, drawSlotIndex = 0, values = uniformFloats)
    }

    /** Returns the slot it wrote into, as the shared render layer's opaque binding handle -- the
     * caller (`prepareDrawCalls`) needs exactly that to record the draw, and resolving it here
     * costs nothing extra (the slot was already looked up to write). */
    fun updateUniformBuffer(
        frameIndex: Int,
        drawSlotIndex: Int,
        values: FloatArray,
    ): VulkanMaterialBinding {
        // Catches an oversized write here, in Kotlin, with the actual float counts involved --
        // the alternative is vkMapMemory rejecting it deep in native code as a bare
        // VUID-vkMapMemory-size-00681 with no indication of which Material/DrawCall was at
        // fault (see the entity-debugger/skybox session this check was added after).
        require(values.size <= uniformFloatCount) {
            "Uniform write of ${values.size} floats overflows this Material's " +
                "$uniformFloatCount-float buffer -- createMaterial(uniformFloatCount = ...) " +
                "was sized for a smaller layout than what's actually being written."
        }
        val slot = uniformSlot(frameIndex, drawSlotIndex)
        VulkanBuffers.writeBufferMemoryFloats(device, slot.uniformBufferMemory.handle, 0, values)
        return slot
    }

    fun bind(commandBuffer: Long, pipelineLayout: Long) {
        bind(commandBuffer, pipelineLayout, frameIndex = 0, drawSlotIndex = 0)
    }

    fun bind(commandBuffer: Long, pipelineLayout: Long, frameIndex: Int, drawSlotIndex: Int) {
        VulkanDescriptors.vkCmdBindDescriptorSet(
            commandBuffer,
            pipelineLayout,
            0,
            uniformSlot(frameIndex, drawSlotIndex).descriptorSet.handle,
        )
    }

    override fun destroy() {
        uniformSlotsByFrame.forEach { frameSlots ->
            frameSlots.forEach { slot ->
                VulkanBuffers.vkDestroyBuffer(device, slot.uniformBuffer.handle)
                VulkanBuffers.vkFreeMemory(device, slot.uniformBufferMemory.handle)
                VulkanDescriptors.vkDestroyDescriptorPool(device, slot.descriptorPool.handle)
            }
        }
        VulkanDescriptors.vkDestroyDescriptorSetLayout(device, descriptorSetLayout.handle)
    }

    private data class UniformSlot(
        val descriptorPool: DescriptorPoolHandle,
        val descriptorSet: DescriptorSetHandle,
        val uniformBuffer: BufferHandle,
        val uniformBufferMemory: DeviceMemoryHandle,
    ) : VulkanMaterialBinding {
        override val descriptorSetHandle: Long get() = descriptorSet.handle
    }

    companion object {
        /** A bare MVP matrix -- every material before skinning existed. A skinned material
         * requests `16 + 16 * jointCount` (MVP + joint palette) instead, see
         * `Renderer.createMaterial`'s own `uniformFloatCount` parameter. */
        /** One MVP matrix -- a plain-colored mesh's whole uniform block. */
        private val DEFAULT_UNIFORM_FLOAT_COUNT = UniformFields.Mvp.floats

        /** textured.wgsl's base-color image. Its sampler at 2 serves every other sampled
         * texture in the group too, which is why the PBR maps need no samplers of their own. */
        private const val BASE_COLOR_IMAGE_BINDING = 1

        /** metallicRoughness, normal, occlusion, emissive -- textured.wgsl's binding numbers,
         * in [PbrImageViews.asList]'s order. Derived from [GroupBindings.StandardMaterial] so
         * the two cannot disagree: every sampled texture past the base-color one at 1. */
        internal val PBR_TEXTURE_BINDINGS: List<Int> =
            GroupBindings.StandardMaterial.entries
                .filter { it.kind == ResourceKind.SampledTexture && it.binding > BASE_COLOR_IMAGE_BINDING }
                .map { it.binding }

        /** Builds just the descriptor set layout a [Material] would build, without allocating
         * a whole Material -- for callers (pipeline-layout construction) that need the layout's
         * shape before any real Material exists.
         *
         * Every binding comes from [bindings] rather than being written here, so a pipeline
         * that needs something else -- a terrain splat weightmap, a heightmap sampled in the
         * vertex stage -- states it in its `PipelineSpec` instead of being unable to express
         * it. [GroupBindings.StandardMaterial] reproduces exactly what this function hardcoded
         * before, including the deliberate 3/4 gap left by the shadow bindings that moved to
         * their own group.
         *
         * Note that a declared binding still has to be *written* before a draw reads it; this
         * function only shapes the layout. See [createMaterialDescriptorSet] for what the
         * write path currently covers.
         */
        fun createDescriptorSetLayout(
            graphicsDevice: GraphicsDevice,
            bindings: GroupBindings = GroupBindings.StandardMaterial,
        ): DescriptorSetLayoutHandle = DescriptorSetLayoutHandle(
            VulkanDescriptors.vkCreateDescriptorSetLayout(
                graphicsDevice.device,
                VkDescriptorSetLayoutCreateInfo(
                    pBindings = materialLayoutBindings(bindings).toTypedArray(),
                ),
            ),
        )
    }
}

/** The four non-base-color glTF PBR image views a material binds at [Material
 * .PBR_TEXTURE_BINDINGS], in that order. Every field is a REAL image view -- the caller
 * substitutes a neutral 1x1 placeholder for a channel the material doesn't have, since a
 * descriptor the layout declares and the shader samples has to be written either way. */
data class PbrImageViews(
    val metallicRoughness: Long,
    val normal: Long,
    val occlusion: Long,
    val emissive: Long,
) {
    fun asList(): List<Long> = listOf(metallicRoughness, normal, occlusion, emissive)
}

private fun createMaterialUniformBuffer(graphicsDevice: GraphicsDevice, uniformFloatCount: Int): Pair<Long, Long> {
    val device = graphicsDevice.device
    val rawUniformBuffer = VulkanBuffers.vkCreateBuffer(
        device,
        VkBufferCreateInfo(
            size = (uniformFloatCount * Float.SIZE_BYTES).toLong(),
            usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
        ),
    )
    val memRequirements = VulkanBuffers.vkGetBufferMemoryRequirements(device, rawUniformBuffer)
    val memoryTypeIndex = VulkanBuffers.findMemoryType(
        graphicsDevice.physicalDevice,
        memRequirements.memoryTypeBits,
        VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or
            VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
    )
    val rawUniformBufferMemory = VulkanBuffers.vkAllocateMemory(
        device,
        VkMemoryAllocateInfo(
            allocationSize = memRequirements.size,
            memoryTypeIndex = memoryTypeIndex,
        ),
    )
    VulkanBuffers.vkBindBufferMemory(device, rawUniformBuffer, rawUniformBufferMemory, 0)
    return rawUniformBuffer to rawUniformBufferMemory
}

/** One layout binding per declared entry. Extracted from [Material.createDescriptorSetLayout]
 * so it can be asserted without a device -- it and [materialPoolSizes] are two derivations of
 * one declaration, and they have to stay consistent. */
internal fun materialLayoutBindings(bindings: GroupBindings): List<VkDescriptorSetLayoutBinding> =
    bindings.entries.map { entry ->
        VkDescriptorSetLayoutBinding(
            binding = entry.binding,
            descriptorType = entry.kind.toVkDescriptorType(),
            stageFlags = entry.stages.toVkStageFlags(),
        )
    }

/** One pool size per distinct resource kind, counted from the same declaration the layout was
 * built from. A pool that undercounts what the layout declares fails at
 * `vkAllocateDescriptorSets` with `VK_ERROR_OUT_OF_POOL_MEMORY` -- at draw time, not build
 * time -- which is why this counts the declaration instead of restating the numbers. */
internal fun materialPoolSizes(bindings: GroupBindings): List<VkDescriptorPoolSize> =
    bindings.entries
        .groupingBy { it.kind }
        .eachCount()
        .map { (kind, count) ->
            VkDescriptorPoolSize(type = kind.toVkDescriptorType(), descriptorCount = count)
        }

private fun createMaterialDescriptorPool(device: Long, bindings: GroupBindings): Long =
    VulkanDescriptors.vkCreateDescriptorPool(
        device,
        VkDescriptorPoolCreateInfo(
            maxSets = 1,
            pPoolSizes = materialPoolSizes(bindings).toTypedArray(),
        ),
    )

private fun ResourceKind.toVkDescriptorType(): Int = when (this) {
    ResourceKind.UniformBuffer -> VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER
    ResourceKind.StorageBuffer -> VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
    ResourceKind.Sampler -> VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER
    ResourceKind.SampledTexture -> VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE
}

private fun Set<ShaderStage>.toVkStageFlags(): Int = fold(0) { flags, stage ->
    flags or when (stage) {
        ShaderStage.Vertex -> VkShaderStageFlagBits.VERTEX.value
        ShaderStage.Fragment -> VkShaderStageFlagBits.FRAGMENT.value
    }
}

private fun createMaterialDescriptorSet(
    device: Long,
    descriptorPool: Long,
    bindings: MaterialDescriptorSetBindings,
): Long {
    val rawDescriptorSet = VulkanDescriptors.vkAllocateDescriptorSet(
        device,
        descriptorPool,
        bindings.descriptorSetLayout,
    )
    VulkanDescriptors.vkUpdateDescriptorSetBuffer(
        device,
        rawDescriptorSet,
        0,
        VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
        VkDescriptorBufferInfo(
            buffer = bindings.uniformBuffer,
            range = (bindings.uniformFloatCount * Float.SIZE_BYTES).toLong(),
        ),
    )
    // Two separate writes -- see the descriptor set layout's own comment for why. Vulkan
    // ignores whichever of sampler/imageView doesn't apply to a given descriptorType, so
    // passing 0 (VK_NULL_HANDLE) for the other field each time is correct, not just harmless.
    VulkanDescriptors.vkUpdateDescriptorSetImage(
        device,
        rawDescriptorSet,
        1,
        VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE,
        VkDescriptorImageInfo(
            sampler = 0L,
            imageView = bindings.imageView,
        ),
    )
    VulkanDescriptors.vkUpdateDescriptorSetImage(
        device,
        rawDescriptorSet,
        2,
        VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER,
        VkDescriptorImageInfo(
            sampler = bindings.sampler,
            imageView = 0L,
        ),
    )
    Material.PBR_TEXTURE_BINDINGS.forEachIndexed { index, binding ->
        VulkanDescriptors.vkUpdateDescriptorSetImage(
            device,
            rawDescriptorSet,
            binding,
            VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE,
            VkDescriptorImageInfo(sampler = 0L, imageView = bindings.pbr.asList()[index]),
        )
    }
    return rawDescriptorSet
}

private data class MaterialDescriptorSetBindings(
    val descriptorSetLayout: Long,
    val uniformBuffer: Long,
    val uniformFloatCount: Int,
    val sampler: Long,
    val imageView: Long,
    val pbr: PbrImageViews,
)
