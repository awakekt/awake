/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.debug

import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.material.materialLayoutBindings
import com.awakekt.awake.vulkan.material.materialPoolSizes
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
    ) : VulkanMaterialBinding {
        override val descriptorSetHandle: Long get() = descriptorSet
    }

    private val device get() = graphicsDevice.device

    // var, not val: assigned inside init's try block below, and destroy() (called from the
    // catch path on partial-failure) must be able to read it even if construction never
    // reached the assignment -- 0 is Vulkan's documented null-handle, destroy() no-ops on it.
    var descriptorSetLayout: Long = 0
        private set
    private val slots = mutableListOf<Slot>()

    // Same partial-creation-leak guard as RenderPipeline's own init -- if slot N throws, slots
    // 0..N-1 (and the layout, if already built) would otherwise leak with nothing yet holding
    // this half-built instance to call destroy() on.
    init {
        require(framesInFlight > 0) { "framesInFlight must be positive." }
        try {
            val layoutBindings = bindings?.let { materialLayoutBindings(it) } ?: listOf(
                VkDescriptorSetLayoutBinding(
                    binding = 0,
                    descriptorType = VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                    stageFlags = stageFlags,
                ),
            )
            descriptorSetLayout = VulkanDescriptors.vkCreateDescriptorSetLayout(
                device,
                VkDescriptorSetLayoutCreateInfo(pBindings = layoutBindings.toTypedArray()),
            )
            // One MORE than the frames in flight. The offscreen path (renderToTexture) indexes
            // one past the last frame -- `frameIndex = commandBuffers.size` -- so that an
            // offscreen render never overwrites a slot a queued frame still refers to. Material
            // already honours that index by growing its list on demand; these slots are fixed
            // size, so the extra one has to be allocated up front or the offscreen index is out
            // of range.
            repeat(framesInFlight + OFFSCREEN_SLOTS) { slots += createSlot() }
        } catch (e: Throwable) {
            destroy()
            throw e
        }
    }

    private fun createBuffer(): Pair<Long, Long> {
        val buffer = VulkanBuffers.vkCreateBuffer(
            device,
            VkBufferCreateInfo(
                size = uniformBytes.toLong(),
                usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
            ),
        )
        val requirements = VulkanBuffers.vkGetBufferMemoryRequirements(device, buffer)
        val memoryTypeIndex = VulkanBuffers.findMemoryType(
            graphicsDevice.physicalDevice,
            requirements.memoryTypeBits,
            VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or
                VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
        )
        val memory = VulkanBuffers.vkAllocateMemory(
            device,
            VkMemoryAllocateInfo(allocationSize = requirements.size, memoryTypeIndex = memoryTypeIndex),
        )
        VulkanBuffers.vkBindBufferMemory(device, buffer, memory, 0)
        return buffer to memory
    }

    private fun createSlot(): Slot {
        val (buffer, bufferMemory) = createBuffer()
        // Sized from the same declaration the layout came from -- see materialPoolSizes for why
        // the two must not be counted separately.
        val poolSizes = bindings?.let { materialPoolSizes(it) } ?: listOf(
            VkDescriptorPoolSize(type = VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, descriptorCount = 1),
        )
        val descriptorPool = VulkanDescriptors.vkCreateDescriptorPool(
            device,
            VkDescriptorPoolCreateInfo(maxSets = 1, pPoolSizes = poolSizes.toTypedArray()),
        )
        val descriptorSet = VulkanDescriptors.vkAllocateDescriptorSet(device, descriptorPool, descriptorSetLayout)
        VulkanDescriptors.vkUpdateDescriptorSetBuffer(
            device,
            descriptorSet,
            0,
            VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
            VkDescriptorBufferInfo(buffer = buffer, range = uniformBytes.toLong()),
        )
        return Slot(descriptorPool, descriptorSet, buffer, bufferMemory)
    }

    operator fun get(frameIndex: Int): Slot {
        require(frameIndex in slots.indices) {
            "PerFrameUniformSlots frame index $frameIndex is outside 0..${slots.lastIndex}."
        }
        return slots[frameIndex]
    }

    fun write(frameIndex: Int, floats: FloatArray) {
        VulkanBuffers.writeBufferMemoryFloats(device, this[frameIndex].bufferMemory, 0, floats)
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
    fun writeTextures(textures: Map<Int, Texture>) {
        val declared = bindings ?: return
        if (textures.isEmpty()) return
        val sharedSampler = textures.entries.minBy { it.key }.value.sampler.handle
        slots.forEach { slot ->
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
                    ResourceKind.Sampler -> VulkanDescriptors.vkUpdateDescriptorSetImage(
                        device,
                        slot.descriptorSet,
                        entry.binding,
                        VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER,
                        VkDescriptorImageInfo(sampler = sharedSampler, imageView = 0L),
                    )
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
        slots.forEach { slot ->
            VulkanBuffers.vkDestroyBuffer(device, slot.buffer)
            VulkanBuffers.vkFreeMemory(device, slot.bufferMemory)
            VulkanDescriptors.vkDestroyDescriptorPool(device, slot.descriptorPool)
        }
        VulkanDescriptors.vkDestroyDescriptorSetLayout(device, descriptorSetLayout)
    }
}
