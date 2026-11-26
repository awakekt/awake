/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.debug

import com.awakekt.awake.core.math.putFloats
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.pipeline.FRAME_UNIFORM_BINDING
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.renderer.UniformWriter
import com.awakekt.awake.vulkan.device.DescriptorPoolChunk
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.device.HostUniformAllocation
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.models.VkMemoryRequirements
import com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkBufferUsageFlagBits
import com.awakekt.awake.vulkan.models.info.VkDescriptorBufferInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorImageInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorType
import com.awakekt.awake.vulkan.pipeline.VulkanMaterialBinding
import com.awakekt.awake.vulkan.renderer.FrameBlockKind
import com.awakekt.awake.vulkan.renderer.FrameUniformBuffers
import com.awakekt.awake.vulkan.texture.Texture

/** One `VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER` binding (binding 0, one descriptor set layout) with
 * one buffer/descriptor-set pair per frame in flight -- was hand-rolled identically in both
 * [LineRenderPipeline] and [SkyboxRenderPipeline] (only [uniformBytes]/[stageFlags] differed).
 * Owns create/write/destroy for the whole per-frame set; the pipeline class that holds one of
 * these only ever reads [get]/[write] and calls [destroy] alongside its own teardown.
 *
 * [bindings] widens that to whatever a content pipeline declares -- a splat weightmap beside the
 * uniform block, say. Null keeps the uniform-only shape every caller had before, which is still
 * every caller but a content feature that supplies textures.
 *
 * @param graphicsDevice The device the slots allocate from.
 * @param uniformBytes Size of the uniform block, per slot.
 * @param stageFlags Which stages read the uniform block. Ignored when [bindings] is non-null:
 * a declaration states stages per entry, which is the point of having one.
 * @param framesInFlight How many slots to allocate: one per frame the renderer keeps in flight.
 * @param bindings What this pipeline's group holds, or null for the uniform-only default.
 */
internal class PerFrameUniformSlots(
    private val graphicsDevice: GraphicsDevice,
    private val uniformBytes: Int,
    stageFlags: Int,
    framesInFlight: Int,
    private val bindings: GroupBindings? = null,
) {
    class Slot(
        val descriptorPool: Long,
        val descriptorSet: Long,
        val buffer: Long,
        val bufferMemory: Long,
        internal val mappedPtr: Long,
    ) : VulkanMaterialBinding {
        override val descriptorSetHandle: Long get() = descriptorSet
        internal var lastWritten: FloatArray? = null

        /** The textures [writeTextures] wrote here, so a sampler made again can reach the set; see [get]. */
        internal var textures: Map<Int, Texture>? = null
        internal var samplerTextures: Map<Int, Int> = emptyMap()

        /** The texture a sampler binding with no texture of its own takes: the lowest-numbered one. */
        internal var sharedTexture: Texture? = null

        /** The sampler each `Sampler` binding holds, in declaration order. */
        internal var samplers: LongArray = LongArray(0)

        internal fun matchesLastWritten(floats: FloatArray): Boolean {
            val prev = lastWritten
            if (prev == null || prev.size != floats.size) return false
            var index = 0
            while (index < floats.size && prev[index].toRawBits() == floats[index].toRawBits()) index += 1
            return index == floats.size
        }

        internal fun recordWritten(floats: FloatArray) {
            val prev = lastWritten
            if (prev == null || prev.size != floats.size) {
                lastWritten = floats.copyOf()
            } else {
                prev.putFloats(0, floats)
            }
        }
    }

    private val device get() = graphicsDevice.device

    // var, not val: assigned inside init's try block below, and destroy() (called from the
    // catch path on partial-failure) must be able to read it even if construction never
    // reached the assignment -- 0 is Vulkan's documented null-handle, destroy() no-ops on it.
    var descriptorSetLayout: Long = 0
        private set
    private val slots = mutableListOf<Slot>()
    private val createdBuffers = mutableListOf<Long>()
    private var memoryAllocation: HostUniformAllocation? = null
    private var descriptorPoolChunk: DescriptorPoolChunk? = null
    val size: Int get() = slots.size

    // Same partial-creation-leak guard as RenderPipeline's own init -- if slot N throws, slots
    // 0..N-1 (and the shared slab/pool reservations) would otherwise leak with nothing yet holding
    // this half-built instance to call destroy() on.
    init {
        require(framesInFlight > 0) { "framesInFlight must be positive." }
        try {
            descriptorSetLayout = if (bindings != null) {
                graphicsDevice.descriptorSetLayoutCache.layoutFor(bindings).handle
            } else {
                graphicsDevice.descriptorSetLayoutCache.singleUniformLayoutFor(stageFlags).handle
            }
            // One MORE than the frames in flight. The offscreen path (renderToTexture) indexes
            // one past the last frame -- `frameIndex = commandBuffers.size` -- so that an
            // offscreen render never overwrites a slot a queued frame still refers to. Material
            // already honours that index by growing its list on demand; these slots are fixed
            // size, so the extra one has to be allocated up front or the offscreen index is out
            // of range.
            val totalSlots = framesInFlight + OFFSCREEN_SLOTS
            createConsolidatedSlots(totalSlots)
        } catch (e: Throwable) {
            destroy()
            throw e
        }
    }

    private fun createConsolidatedSlots(totalSlots: Int) {
        val rawBuffers = LongArray(totalSlots) {
            VulkanBuffers.vkCreateBuffer(
                device,
                VkBufferCreateInfo(
                    size = uniformBytes.toLong(),
                    usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                ),
            ).also { createdBuffers += it }
        }
        val requirements = VulkanBuffers.vkGetBufferMemoryRequirements(device, rawBuffers[0])
        val alignment = maxOf(requirements.alignment, Float.SIZE_BYTES.toLong())
        val stride = (requirements.size + alignment - 1) / alignment * alignment
        val allocation = graphicsDevice.uniformMemoryPool.allocate(
            VkMemoryRequirements(stride * totalSlots, alignment, requirements.memoryTypeBits),
        )
        memoryAllocation = allocation
        val poolChunk = if (bindings != null) {
            graphicsDevice.descriptorSetPool.reserveBlock(bindings, totalSlots)
        } else {
            graphicsDevice.descriptorSetPool.reserveSingleUniformBlock(totalSlots)
        }
        descriptorPoolChunk = poolChunk
        for (slotIndex in 0 until totalSlots) {
            slots += bindSlot(slotIndex, rawBuffers[slotIndex], slotIndex * stride, allocation, poolChunk.pool)
        }
    }

    private fun bindSlot(
        slotIndex: Int,
        buffer: Long,
        slotOffset: Long,
        allocation: HostUniformAllocation,
        descriptorPool: Long,
    ): Slot {
        VulkanBuffers.vkBindBufferMemory(device, buffer, allocation.memory, allocation.offset + slotOffset)
        val descriptorSet = VulkanDescriptors.vkAllocateDescriptorSet(device, descriptorPool, descriptorSetLayout)
        VulkanDescriptors.vkUpdateDescriptorSetBuffer(
            device,
            descriptorSet,
            0,
            VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
            VkDescriptorBufferInfo(buffer = buffer, range = uniformBytes.toLong()),
        )
        if (bindings?.at(FRAME_UNIFORM_BINDING)?.kind == ResourceKind.UniformBuffer) {
            VulkanDescriptors.vkUpdateDescriptorSetBuffer(
                device,
                descriptorSet,
                FRAME_UNIFORM_BINDING,
                VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                VkDescriptorBufferInfo(
                    buffer = graphicsDevice.frameUniforms.bufferFor(slotIndex, FrameBlockKind.Scene),
                    range = FrameUniformBuffers.BYTES,
                ),
            )
        }
        return Slot(descriptorPool, descriptorSet, buffer, allocation.memory, allocation.mappedPtr + slotOffset)
    }

    operator fun get(frameIndex: Int): Slot {
        require(frameIndex in slots.indices) {
            "PerFrameUniformSlots frame index $frameIndex is outside 0..${slots.lastIndex}."
        }
        return slots[frameIndex].also(::refreshSamplers)
    }

    /**
     * Writes into [slot] any texture sampler made again since it was written, for a new anisotropy
     * (`Renderer.maxAnisotropy`, #1660), before the set is bound. Only this frame's slot, which no
     * frame in flight still reads; with no change it compares one handle per sampler binding.
     */
    private fun refreshSamplers(slot: Slot) {
        val textures = slot.textures ?: return
        val declared = bindings?.entries ?: return
        var samplerIndex = 0
        for (index in declared.indices) {
            val entry = declared[index]
            if (entry.kind != ResourceKind.Sampler) continue
            val handle = samplerFor(entry.binding, textures, slot.samplerTextures, slot.sharedTexture)
            if (samplerIndex < slot.samplers.size && slot.samplers[samplerIndex] != handle) {
                writeSampler(slot, entry.binding, handle)
                slot.samplers[samplerIndex] = handle
                samplerRewrites++
            }
            samplerIndex++
        }
    }

    /** Sampler bindings [refreshSamplers] rewrote for a texture's new sampler, for tests. */
    internal var samplerRewrites = 0
        private set

    private fun samplerFor(binding: Int, textures: Map<Int, Texture>, samplerTextures: Map<Int, Int>, shared: Texture?): Long {
        val own = samplerTextures[binding]
        val texture = if (own != null) textures.getValue(own) else shared
        return texture?.sampler?.handle ?: 0L
    }

    private fun writeSampler(slot: Slot, binding: Int, sampler: Long) = VulkanDescriptors.vkUpdateDescriptorSetImage(
        device,
        slot.descriptorSet,
        binding,
        VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER,
        VkDescriptorImageInfo(sampler = sampler, imageView = 0L),
    )

    fun write(frameIndex: Int, floats: FloatArray) {
        writeChanged(frameIndex, floats)
    }

    private fun writeChanged(frameIndex: Int, floats: FloatArray): Boolean {
        val slot = this[frameIndex]
        if (slot.matchesLastWritten(floats)) return false
        slot.recordWritten(floats)
        VulkanBuffers.writeMappedMemoryFloats(slot.mappedPtr, 0L, floats)
        return true
    }

    /** These slots as a [UniformBlock] laid out by [layout]: one slot per frame index. */
    fun uniformBlock(layout: UniformLayout): UniformBlock = object : UniformBlock {
        private val writer = UniformWriter(layout)

        override fun binding(frameIndex: Int) = this@PerFrameUniformSlots[frameIndex]
        override fun write(frameIndex: Int, fill: UniformWriter.() -> Unit) {
            if (this@PerFrameUniformSlots.writeChanged(frameIndex, writer.reset().apply(fill).build())) {
                graphicsDevice.contentUniformWrites += 1
            }
        }
    }

    /**
     * Writes [textures] into every slot's descriptor set, once.
     *
     * Separate from construction because the two facts arrive at different times: the layout
     * comes from a `PipelineSpec` the registry compiles up front, while the pixels come from a
     * `ContentFeature` the engine only reaches afterwards. Every slot gets the same image --
     * these are load-time data, not per-frame state (see `ContentFeature`'s own note).
     *
     * Every declared sampler binding is written with the lowest-numbered texture's sampler.
     * [com.awakekt.awake.vulkan.texture.Texture] builds all of them from the same
     * default `VkSamplerCreateInfo`, so which one is picked cannot differ today; a feature
     * needing distinct filtering per texture is what would force a real choice here.
     */
    fun writeTextures(textures: Map<Int, Texture>, frameIndex: Int? = null, samplerTextures: Map<Int, Int> = emptyMap()) {
        val declared = bindings ?: return
        if (textures.isEmpty()) return
        val samplerCount = declared.entries.count { it.kind == ResourceKind.Sampler }
        slots.forEachIndexed { index, slot ->
            if (frameIndex != null && index != frameIndex) return@forEachIndexed
            slot.textures = textures
            slot.samplerTextures = samplerTextures
            slot.sharedTexture = textures.getValue(textures.keys.min())
            slot.samplers = LongArray(samplerCount)
            var samplerIndex = 0
            declared.entries.forEach { entry ->
                when (entry.kind) {
                    ResourceKind.SampledTexture -> {
                        val texture = textures[entry.binding] ?: return@forEach
                        VulkanDescriptors.vkUpdateDescriptorSetImage(
                            device,
                            slot.descriptorSet,
                            entry.binding,
                            VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE,
                            VkDescriptorImageInfo(sampler = 0L, imageView = texture.imageView.handle),
                        )
                    }
                    ResourceKind.Sampler -> {
                        val sampler = samplerFor(entry.binding, textures, samplerTextures, slot.sharedTexture)
                        writeSampler(slot, entry.binding, sampler)
                        slot.samplers[samplerIndex++] = sampler
                    }
                    // The uniform buffer is written at slot creation; a storage buffer has no
                    // source here, and ContentFeature rejects one before it reaches this point.
                    ResourceKind.UniformBuffer, ResourceKind.StorageBuffer -> Unit
                }
            }
        }
    }

    private companion object {
        /** The single extra slot the offscreen path uses; see [init]. */
        const val OFFSCREEN_SLOTS = 1
    }

    fun destroy() {
        createdBuffers.forEach { VulkanBuffers.vkDestroyBuffer(device, it) }
        createdBuffers.clear()
        slots.clear()
        memoryAllocation?.let { graphicsDevice.uniformMemoryPool.free(it) }
        memoryAllocation = null
        descriptorPoolChunk?.let { graphicsDevice.descriptorSetPool.releaseBlock(it) }
        descriptorPoolChunk = null
    }
}
