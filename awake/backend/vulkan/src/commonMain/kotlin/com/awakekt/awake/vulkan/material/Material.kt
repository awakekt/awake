/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.material

import com.awakekt.awake.render.passes.DrawUniformInputs
import com.awakekt.awake.render.pipeline.FRAME_UNIFORM_BINDING
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
import com.awakekt.awake.vulkan.models.VkMemoryRequirements
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
import com.awakekt.awake.vulkan.renderer.FrameBlockKind
import com.awakekt.awake.vulkan.renderer.FrameUniformBuffers
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
    /** The declared size lets transitional preparation select the matching shared ABI without
     * inferring shader semantics from a vertex format alone. */
    val uniformFloatCount: Int = DEFAULT_UNIFORM_FLOAT_COUNT,
    /** What this material's descriptor set declares. Defaults to the glTF metallic-roughness
     * shape every material had before the declaration existed, so a caller that does not care
     * gets exactly the previous layout. */
    private val bindings: GroupBindings = GroupBindings.StandardMaterial,
) : RenderMaterial {
    internal val graphicsDevice = graphicsDevice
    private val device get() = graphicsDevice.device

    val descriptorSetLayout: DescriptorSetLayoutHandle

    /**
     * Descriptor pool of the first uniform slot (frame 0, draw 0), or handle 0 until that slot has
     * been requested. One pool serves a whole block of slots.
     */
    var descriptorPool: DescriptorPoolHandle = DescriptorPoolHandle(0)
        private set

    /**
     * Descriptor set of the first uniform slot (frame 0, draw 0), binding its uniform buffer and
     * the material's textures, or handle 0 until that slot has been requested.
     */
    var descriptorSet: DescriptorSetHandle = DescriptorSetHandle(0)
        private set

    /**
     * Uniform buffer of the first slot (frame 0, draw 0), or handle 0 until that slot has been
     * requested. It holds [uniformFloatCount] floats.
     */
    var uniformBuffer: BufferHandle = BufferHandle(0)
        private set

    /**
     * Device memory backing [uniformBuffer], or handle 0 until the first slot has been requested.
     * The memory is shared with the other slots of its block.
     */
    var uniformBufferMemory: DeviceMemoryHandle = DeviceMemoryHandle(0)
        private set

    private val uniformSlotsByFrame = mutableListOf<MutableList<UniformSlot>>()

    /**
     * Where uniform slots are carved from: one memory allocation and one descriptor pool per
     * [SLOTS_PER_BLOCK] slots rather than one of each per slot. A slot is one draw of this material
     * in one frame slot, so a scene of a few thousand distinct draws used to allocate memory a few
     * thousand times -- and many drivers cap allocations at 4096 for the whole device.
     */
    private val slotBlocks = mutableListOf<SlotBlock>()
    private var slotsInLastBlock = SLOTS_PER_BLOCK

    /** The sampler/image view this material was built with -- exposed (read-only) so
     * `UiTextureRenderPipeline` can bind the SAME sampled image into its own (screen-space
     * quad) descriptor set for on-screen compositing, without re-deriving them from whatever
     * [Texture]/`OffscreenRenderTarget` this material was created from. */
    var samplerHandle: Long = 0
        private set

    /**
     * The image view bound together with [samplerHandle]; 0 until [createResources] or
     * [createResourcesFromRenderTarget] has run.
     */
    var imageViewHandle: Long = 0
        private set

    /**
     * Whether a sampler and an image view have both been supplied, which is what allocating any
     * uniform slot requires.
     */
    val hasTexture: Boolean get() = samplerHandle != 0L && imageViewHandle != 0L

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

    private fun createUniformSlot(frameIndex: Int): UniformSlot {
        require(samplerHandle != 0L && imageViewHandle != 0L) {
            "Material resources must be created before allocating uniform slots."
        }
        val rawUniformBuffer = VulkanBuffers.vkCreateBuffer(
            device,
            VkBufferCreateInfo(
                size = (uniformFloatCount * Float.SIZE_BYTES).toLong(),
                usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
            ),
        )
        if (slotsInLastBlock == SLOTS_PER_BLOCK) {
            slotBlocks += allocateSlotBlock(
                graphicsDevice,
                bindings,
                VulkanBuffers.vkGetBufferMemoryRequirements(device, rawUniformBuffer),
            )
            slotsInLastBlock = 0
        }
        val block = slotBlocks.last()
        val indexInBlock = slotsInLastBlock
        val memoryOffset = indexInBlock * block.stride
        VulkanBuffers.vkBindBufferMemory(device, rawUniformBuffer, block.memory, memoryOffset)
        slotsInLastBlock += 1
        val rawDescriptorSet = createMaterialDescriptorSet(
            device = device,
            descriptorPool = block.descriptorPool,
            bindings = MaterialDescriptorSetBindings(
                descriptorSetLayout = descriptorSetLayout.handle,
                uniformBuffer = rawUniformBuffer,
                uniformFloatCount = uniformFloatCount,
                sampler = samplerHandle,
                imageView = imageViewHandle,
                pbr = requireNotNull(pbrImageViews),
            ),
        )
        if (readsFrameBlock) bindFrameBlock(graphicsDevice, rawDescriptorSet, frameIndex, FrameBlockKind.Identity)
        return UniformSlot(
            descriptorSet = DescriptorSetHandle(rawDescriptorSet),
            uniformBuffer = BufferHandle(rawUniformBuffer),
            block = block,
            indexInBlock = indexInBlock,
            uniformFloatCount = uniformFloatCount,
        )
    }

    /** Whether this material's set declares the frame block beside the draw's own. */
    internal val readsFrameBlock = bindings.at(FRAME_UNIFORM_BINDING)?.kind == ResourceKind.UniformBuffer

    /** Frame [frameIndex]'s slot for this material's draw [drawSlotIndex] of the batch, made on first use. */
    internal fun uniformSlot(frameIndex: Int, drawSlotIndex: Int): UniformSlot {
        require(frameIndex >= 0) { "frameIndex must be non-negative." }
        require(drawSlotIndex >= 0) { "drawSlotIndex must be non-negative." }
        while (uniformSlotsByFrame.size <= frameIndex) uniformSlotsByFrame.add(mutableListOf())
        val frameSlots = uniformSlotsByFrame[frameIndex]
        while (frameSlots.size <= drawSlotIndex) frameSlots += createUniformSlot(frameIndex)
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
    ): VulkanMaterialBinding = updateUniformBuffer(frameIndex, drawSlotIndex, values, FrameBlockKind.Identity)

    /**
     * As above, naming the frame block the draw's set binds beside its own: see [FrameBlockKind].
     * A slot is rebound only when it changes hands between kinds, which a steady frame never
     * does: its fills take the first slots and its edges the ones after.
     *
     * Null [values] leave the buffer as it is, for a draw whose block it already holds.
     */
    internal fun updateUniformBuffer(
        frameIndex: Int,
        drawSlotIndex: Int,
        values: FloatArray?,
        frameBlock: FrameBlockKind,
    ): VulkanMaterialBinding {
        // Catches an oversized write here, in Kotlin, with the actual float counts involved --
        // the alternative is vkMapMemory rejecting it deep in native code as a bare
        // VUID-vkMapMemory-size-00681 with no indication of which Material/packet draw was at
        // fault (see the entity-debugger/skybox session this check was added after).
        require(values == null || values.size <= uniformFloatCount) {
            "Uniform write of ${values?.size} floats overflows this Material's " +
                "$uniformFloatCount-float buffer -- createMaterial(uniformFloatCount = ...) " +
                "was sized for a smaller layout than what's actually being written."
        }
        val slot = uniformSlot(frameIndex, drawSlotIndex)
        if (values != null && !slot.matchesLastWritten(values)) {
            // Whatever the block was packed from, it no longer holds that; the caller records anew.
            slot.inputs.clear()
            slot.recordWritten(values)
            values.copyInto(slot.block.stagedFloats, destinationOffset = slot.indexInBlock * slot.block.strideFloats)
            VulkanBuffers.writeBufferMemoryFloats(device, slot.uniformBufferMemory.handle, slot.memoryOffset, values)
            graphicsDevice.drawBlockWrites += 1
        }
        if (slot.frameBlock != frameBlock && readsFrameBlock) {
            // Safe to rewrite: this frame's earlier commands, the only readers of its sets, have completed.
            bindFrameBlock(graphicsDevice, slot.descriptorSet.handle, frameIndex, frameBlock)
            slot.frameBlock = frameBlock
        }
        return slot
    }

    /**
     * Binds frame slot 0, draw slot 0's descriptor set at set 0.
     *
     * @param commandBuffer The command buffer being recorded.
     * @param pipelineLayout The layout of the pipeline the set is used with.
     */
    fun bind(commandBuffer: Long, pipelineLayout: Long) {
        bind(commandBuffer, pipelineLayout, frameIndex = 0, drawSlotIndex = 0)
    }

    /**
     * Binds the descriptor set of one frame and draw slot at set 0, creating the slot on first use.
     *
     * @param commandBuffer The command buffer being recorded.
     * @param pipelineLayout The layout of the pipeline the set is used with.
     * @param frameIndex The frame slot, so frames in flight never share a set.
     * @param drawSlotIndex The per-frame draw slot, so several draws of this material in one frame
     * keep separate uniforms.
     * @throws IllegalArgumentException If [frameIndex] or [drawSlotIndex] is negative.
     */
    fun bind(commandBuffer: Long, pipelineLayout: Long, frameIndex: Int, drawSlotIndex: Int) {
        VulkanDescriptors.vkCmdBindDescriptorSet(
            commandBuffer,
            pipelineLayout,
            0,
            uniformSlot(frameIndex, drawSlotIndex).descriptorSet.handle,
        )
    }

    override fun destroy() {
        // Every frame/draw slot can still be referenced by submitted commands during scene reload.
        VulkanBuffers.vkDeviceWaitIdle(device)
        uniformSlotsByFrame.forEach { frameSlots ->
            frameSlots.forEach { slot -> VulkanBuffers.vkDestroyBuffer(device, slot.uniformBuffer.handle) }
        }
        slotBlocks.forEach { block ->
            block.destroyed = true
            VulkanBuffers.vkFreeMemory(device, block.memory)
            VulkanDescriptors.vkDestroyDescriptorPool(device, block.descriptorPool)
        }
        VulkanDescriptors.vkDestroyDescriptorSetLayout(device, descriptorSetLayout.handle)
    }

    internal class UniformSlot(
        val descriptorSet: DescriptorSetHandle,
        val uniformBuffer: BufferHandle,
        val block: SlotBlock,
        val indexInBlock: Int,
        uniformFloatCount: Int,
    ) : VulkanMaterialBinding {
        val descriptorPool: DescriptorPoolHandle = DescriptorPoolHandle(block.descriptorPool)
        val uniformBufferMemory: DeviceMemoryHandle = DeviceMemoryHandle(block.memory)

        /** Where [uniformBuffer] sits in [uniformBufferMemory], which its block shares. */
        val memoryOffset: Long = indexInBlock * block.stride
        override val descriptorSetHandle: Long get() = descriptorSet.handle

        /** The frame block [descriptorSet] binds: the identity one until a draw asks for another. */
        var frameBlock: FrameBlockKind = FrameBlockKind.Identity

        /**
         * What the draw's own block in [uniformBuffer] was last packed from, when it was packed as
         * one beside the frame block; cleared by every other write. A draw whose inputs still
         * match leaves the buffer as it is.
         */
        val inputs = DrawUniformInputs()
        val lastWritten = FloatArray(uniformFloatCount)
        var lastWrittenSize = -1

        fun matchesLastWritten(values: FloatArray): Boolean =
            lastWrittenSize == values.size &&
                values.indices.all { lastWritten[it].toRawBits() == values[it].toRawBits() }

        fun recordWritten(values: FloatArray) {
            values.copyInto(lastWritten)
            lastWrittenSize = values.size
        }
    }

    /**
     * Slot sizing, texture-binding numbers and the descriptor-set-layout builder shared by
     * materials and pipelines.
     */
    companion object {
        /** A bare MVP matrix -- every material before skinning existed. A skinned material
         * requests `16 + 16 * jointCount` (MVP + joint palette) instead, see
         * `Renderer.createMaterial`'s own `uniformFloatCount` parameter. */
        /** One MVP matrix -- a plain-colored mesh's whole uniform block. */
        private val DEFAULT_UNIFORM_FLOAT_COUNT = UniformFields.DefaultMaterial.total

        /** Uniform slots per memory allocation and descriptor pool. */
        internal const val SLOTS_PER_BLOCK = 32

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
    /** Image view of the metallic-roughness map, a neutral 1x1 view when the material has none. */
    val metallicRoughness: Long,
    /**
     * Image view of the tangent-space normal map, a neutral 1x1 view when the material has none.
     */
    val normal: Long,
    /** Image view of the ambient-occlusion map, a neutral 1x1 view when the material has none. */
    val occlusion: Long,
    /** Image view of the emissive map, a neutral 1x1 view when the material has none. */
    val emissive: Long,
) {
    /**
     * Returns the four views in descriptor-binding order: metallic-roughness, normal, occlusion,
     * emissive.
     */
    fun asList(): List<Long> = listOf(metallicRoughness, normal, occlusion, emissive)
}

/** Points [descriptorSet]'s frame binding at frame [frameIndex]'s block of [kind]. */
private fun bindFrameBlock(graphicsDevice: GraphicsDevice, descriptorSet: Long, frameIndex: Int, kind: FrameBlockKind) {
    VulkanDescriptors.vkUpdateDescriptorSetBuffer(
        graphicsDevice.device,
        descriptorSet,
        FRAME_UNIFORM_BINDING,
        VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
        VkDescriptorBufferInfo(buffer = graphicsDevice.frameUniforms.bufferFor(frameIndex, kind), range = FrameUniformBuffers.BYTES),
    )
}

/** One memory allocation and descriptor pool shared by [Material.SLOTS_PER_BLOCK] uniform slots. */
internal class SlotBlock(val memory: Long, val descriptorPool: Long, val stride: Long) {
    val strideFloats: Int = (stride / Float.SIZE_BYTES).toInt()
    val stagedFloats: FloatArray = FloatArray(Material.SLOTS_PER_BLOCK * strideFloats)
    var minDirtySlot: Int = Int.MAX_VALUE
    var maxDirtySlot: Int = -1
    var dirtyCount: Int = 0
    private var singleDirtySlot: Material.UniformSlot? = null
    var destroyed: Boolean = false

    internal fun stage(slotIndexInBlock: Int, slot: Material.UniformSlot) {
        slot.lastWritten.copyInto(
            stagedFloats,
            destinationOffset = slotIndexInBlock * strideFloats,
            startIndex = 0,
            endIndex = slot.lastWrittenSize,
        )
        if (dirtyCount == 0) {
            minDirtySlot = slotIndexInBlock
            maxDirtySlot = slotIndexInBlock
            singleDirtySlot = slot
        } else {
            if (slotIndexInBlock < minDirtySlot) minDirtySlot = slotIndexInBlock
            if (slotIndexInBlock > maxDirtySlot) maxDirtySlot = slotIndexInBlock
            if (minDirtySlot != maxDirtySlot) singleDirtySlot = null
        }
        dirtyCount++
    }

    fun flush(device: Long) {
        if (destroyed || dirtyCount == 0 || maxDirtySlot < minDirtySlot) {
            minDirtySlot = Int.MAX_VALUE
            maxDirtySlot = -1
            dirtyCount = 0
            singleDirtySlot = null
            return
        }
        val single = singleDirtySlot
        if (minDirtySlot == maxDirtySlot && single != null && single.lastWrittenSize == single.lastWritten.size) {
            VulkanBuffers.writeBufferMemoryFloats(device, memory, minDirtySlot * stride, single.lastWritten)
        } else if (minDirtySlot == 0 && maxDirtySlot == Material.SLOTS_PER_BLOCK - 1) {
            VulkanBuffers.writeBufferMemoryFloats(device, memory, 0L, stagedFloats)
        } else {
            val start = minDirtySlot * strideFloats
            val end = (maxDirtySlot + 1) * strideFloats
            VulkanBuffers.writeBufferMemoryFloats(
                device,
                memory,
                minDirtySlot * stride,
                stagedFloats.copyOfRange(start, end),
            )
        }
        minDirtySlot = Int.MAX_VALUE
        maxDirtySlot = -1
        dirtyCount = 0
        singleDirtySlot = null
    }
}

/** Memory for [Material.SLOTS_PER_BLOCK] buffers shaped like [requirements], each at an aligned stride. */
private fun allocateSlotBlock(
    graphicsDevice: GraphicsDevice,
    bindings: GroupBindings,
    requirements: VkMemoryRequirements,
): SlotBlock {
    val alignment = maxOf(requirements.alignment, Float.SIZE_BYTES.toLong())
    val stride = (requirements.size + alignment - 1) / alignment * alignment
    val memory = VulkanBuffers.vkAllocateMemory(
        graphicsDevice.device,
        VkMemoryAllocateInfo(
            allocationSize = stride * Material.SLOTS_PER_BLOCK,
            memoryTypeIndex = VulkanBuffers.findMemoryType(
                graphicsDevice.physicalDevice,
                requirements.memoryTypeBits,
                VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or
                    VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            ),
        ),
    )
    val pool = createMaterialDescriptorPool(graphicsDevice.device, bindings, maxSets = Material.SLOTS_PER_BLOCK)
    return SlotBlock(memory, pool, stride)
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

private fun createMaterialDescriptorPool(device: Long, bindings: GroupBindings, maxSets: Int): Long =
    VulkanDescriptors.vkCreateDescriptorPool(
        device,
        VkDescriptorPoolCreateInfo(
            maxSets = maxSets,
            pPoolSizes = materialPoolSizes(bindings)
                .map { VkDescriptorPoolSize(type = it.type, descriptorCount = it.descriptorCount * maxSets) }
                .toTypedArray(),
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

/**
 * Stages [values] into [frameIndex]/[drawSlotIndex]'s [SlotBlock] so all dirty slots in the
 * block can be flushed in a single `vkMapMemory`/`vkUnmapMemory` call before command recording.
 */
internal fun Material.stageUniformBuffer(
    frameIndex: Int,
    drawSlotIndex: Int,
    values: FloatArray?,
    dirtyBlocks: MutableSet<SlotBlock>,
    frameBlock: FrameBlockKind = FrameBlockKind.Identity,
): VulkanMaterialBinding {
    require(values == null || values.size <= uniformFloatCount) {
        "Uniform write of ${values?.size} floats overflows this Material's " +
            "$uniformFloatCount-float buffer -- createMaterial(uniformFloatCount = ...) " +
            "was sized for a smaller layout than what's actually being written."
    }
    val slot = uniformSlot(frameIndex, drawSlotIndex)
    if (values != null && !slot.matchesLastWritten(values)) {
        slot.inputs.clear()
        slot.recordWritten(values)
        slot.block.stage(slot.indexInBlock, slot)
        dirtyBlocks.add(slot.block)
        graphicsDevice.drawBlockWrites += 1
    }
    if (slot.frameBlock != frameBlock && readsFrameBlock) {
        bindFrameBlock(graphicsDevice, slot.descriptorSet.handle, frameIndex, frameBlock)
        slot.frameBlock = frameBlock
    }
    return slot
}
