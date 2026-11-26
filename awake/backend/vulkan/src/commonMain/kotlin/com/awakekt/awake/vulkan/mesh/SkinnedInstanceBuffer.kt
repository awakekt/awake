/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.mesh

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.render.passes.InstancePacker
import com.awakekt.awake.render.passes.InstanceTintPacker
import com.awakekt.awake.render.passes.InstanceWriter
import com.awakekt.awake.render.renderer.SkinnedInstanceLayout
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.device.allocateHostVisibleBufferMemory
import com.awakekt.awake.vulkan.device.destroyOrRetire
import com.awakekt.awake.vulkan.enums.VkShaderStageFlagBits
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
import com.awakekt.awake.vulkan.models.info.VkDescriptorPoolCreateInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorPoolSize
import com.awakekt.awake.vulkan.models.info.VkDescriptorSetLayoutBinding
import com.awakekt.awake.vulkan.models.info.VkDescriptorSetLayoutCreateInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorType
import com.awakekt.awake.vulkan.pipeline.VulkanMaterialBinding
import com.awakekt.awake.vulkan.renderer.sameBitsAs

/**
 * The per-instance joint palettes and tints behind one animated instanced draw call -- [InstanceBuffer]'s
 * animated companion, with the same HOST_VISIBLE|HOST_COHERENT, grow-on-demand lifecycle. Each frame slot
 * starts unallocated and grows in powers of two from [MIN_CAPACITY] up to [maxInstances], retiring
 * outgrown storage buffers and descriptor pools through the device reaper once frames in flight complete.
 *
 * Two things differ from [InstanceBuffer]:
 * - it is a STORAGE buffer bound through a descriptor set, not an instance-rate vertex buffer.
 *   A vertex attribute can't carry 64 matrices, and a uniform buffer can't hold enough of them
 *   (64 `mat4` = 4 KB per instance blows the 64 KB uniform limit after 16 copies). See
 *   `skinned_instanced.wgsl`, which reads it as `array<JointPalette>` indexed by
 *   `@builtin(instance_index)`.
 * - it owns its own descriptor set layout/pool/set, rather than binding through
 *   [com.awakekt.awake.vulkan.material.Material]'s set. That set (set 0) is shared
 *   by every pipeline in the renderer; adding a palette binding to it would force every
 *   material -- textured, lit, UI -- to carry a storage descriptor it never writes. So this
 *   is bound by the shared renderer at its joint-palette semantic slot. Binding 0 holds
 *   the unchanged palette records; binding 1 holds one RGBA tint per instance.
 */
class SkinnedInstanceBuffer(
    private val graphicsDevice: GraphicsDevice,
    /** Hard ceiling on animated instances per draw call. Each one costs
     * [FLOATS_PER_INSTANCE] palette floats plus four tint floats per frame slot (64 `mat4`
     * joints and one RGBA tint), approximately 16x what a static instance costs in [InstanceBuffer] -- so raising this is a
     * real memory decision, not a free knob. [update] fails loudly naming it rather than
     * silently truncating. */
    private val maxInstances: Int = DEFAULT_MAX_INSTANCES,
    framesInFlight: Int = 1,
) {
    private val device get() = graphicsDevice.device

    /** Layout-compatible with (a separate instance of) the one the skinned-instanced pipeline's
     * layout was built from -- see [createDescriptorSetLayout]. */
    private val descriptorSetLayout: DescriptorSetLayoutHandle =
        createDescriptorSetLayout(graphicsDevice)

    private class ResidentFloatSlice {
        private var cached: FloatArray? = null
        private var count = 0

        fun matches(floats: FloatArray, floatCount: Int): Boolean {
            val held = cached
            return held != null && count == floatCount && held.sameBitsAs(floats, floatCount)
        }

        fun record(floats: FloatArray, floatCount: Int) {
            val held = cached
            val target = if (held != null && held.size >= floatCount) {
                held
            } else {
                FloatArray(floatCount).also { cached = it }
            }
            floats.copyInto(target, 0, 0, floatCount)
            count = floatCount
        }
    }

    private data class FrameResources(
        val buffer: BufferHandle,
        val memory: DeviceMemoryHandle,
        val mappedPtr: Long,
        val descriptorPool: DescriptorPoolHandle,
        val descriptorSet: DescriptorSetHandle,
        val capacity: Int,
        val paletteByteSize: Long,
        val tintByteSize: Long,
    ) : VulkanMaterialBinding {
        override val descriptorSetHandle: Long get() = descriptorSet.handle
        val residentPalettes = ResidentFloatSlice()
        val residentTints = ResidentFloatSlice()
        val totalByteSize: Long get() = paletteByteSize + tintByteSize

        fun free(vkDevice: Long) {
            if (vkDevice == 0L) return
            VulkanBuffers.vkDestroyBuffer(vkDevice, buffer.handle)
            if (mappedPtr != 0L) VulkanBuffers.vkUnmapMemory(vkDevice, memory.handle)
            VulkanBuffers.vkFreeMemory(vkDevice, memory.handle)
            VulkanDescriptors.vkDestroyDescriptorPool(vkDevice, descriptorPool.handle)
        }
    }

    /** Palette and tint buffer uploads made, for tests: unchanged static/idle skeletons skip upload. */
    internal var uploads = 0L
        private set

    /** Outgrown slot buffers handed to the deferred reaper so far, for tests. */
    internal var retiredCount = 0L
        private set

    private val tintPacker = InstanceTintPacker()
    private val frameResources = arrayOfNulls<FrameResources>(framesInFlight)

    /** Current allocated instance capacity for [frameIndex], or 0 before first use. */
    internal fun capacityFor(frameIndex: Int): Int = frameResources.getOrNull(frameIndex)?.capacity ?: 0

    /** Total requested GPU buffer bytes currently held across live frame slots, for tests. */
    internal val allocatedBytes: Long
        get() {
            var total = 0L
            for (i in frameResources.indices) {
                total += frameResources[i]?.totalByteSize ?: 0L
            }
            return total
        }

    // Reused across frames so a steady instance count allocates nothing per frame.

    /** Packs [palettes] into this frame slot's buffer at a fixed [FLOATS_PER_INSTANCE] stride --
     * the shader indexes `palettes[instance_index]` as an array of fixed-size structs, so a
     * shorter palette (a skin with fewer than [MAX_JOINTS] joints) is
     * written at the start of its slot and the rest of the slot is simply never indexed. */
    private val packer = InstancePacker<FloatArray>(
        GpuDataShape.Mat4,
        "SkinnedInstanceBuffer",
        repeat = MAX_JOINTS,
        write = InstanceWriter { out, offset, palette ->
            require(palette.size <= MAX_JOINTS * GpuDataShape.Mat4.componentCount) {
                "Joint palette ${palette.size} floats exceeds MAX_JOINTS ($MAX_JOINTS) * 16."
            }
            palette.copyInto(out, offset)
        },
    )

    /**
     * Writes each instance's joint palette into [frameIndex]'s buffer and resets every tint to
     * opaque white.
     *
     * @param frameIndex The frame slot to write.
     * @param palettes One palette per instance: up to [MAX_JOINTS] column-major matrices as flat
     * floats. A shorter palette is written at the start of its record.
     * @throws IllegalArgumentException If there are more palettes than the instance ceiling, a
     * palette exceeds [MAX_JOINTS] matrices, or [frameIndex] is not a valid slot.
     */
    fun update(frameIndex: Int, palettes: List<FloatArray>) = update(frameIndex, palettes, null)

    /** Uploads index-aligned tints, defaulting to white when omitted, growing that slot on demand. */
    fun update(frameIndex: Int, palettes: List<FloatArray>, colors: List<Vec4>?) {
        val tintCount = tintPacker.packSlice(colors, palettes.size, maxInstances)
        val paletteCount = packer.packSlice(palettes, maxInstances)
        if (tintCount == 0 || paletteCount == 0) return
        val resources = resourcesFor(frameIndex, palettes.size)
        uploadSliceIfChanged(resources.mappedPtr, 0L, packer.buffer, paletteCount, resources.residentPalettes)
        uploadSliceIfChanged(resources.mappedPtr, resources.paletteByteSize, tintPacker.buffer, tintCount, resources.residentTints)
    }

    private fun uploadSliceIfChanged(
        mappedPtr: Long,
        offset: Long,
        floats: FloatArray,
        count: Int,
        resident: ResidentFloatSlice,
    ) {
        if (resident.matches(floats, count)) return
        VulkanBuffers.writeMappedMemoryFloats(mappedPtr, offset, floats, 0, count)
        uploads += 1
        resident.record(floats, count)
    }

    /** This frame slot's descriptor set, bound at the shared joint-palette semantic slot. */
    fun binding(frameIndex: Int): VulkanMaterialBinding = resourcesFor(frameIndex)

    /**
     * Binds [frameIndex]'s palette descriptor set at set [PALETTE_SET].
     *
     * @param frameIndex The frame slot whose set to bind.
     * @param commandBuffer The command buffer being recorded.
     * @param pipelineLayout The layout of the skinned-instanced pipeline the set is used with.
     * @throws IllegalArgumentException If [frameIndex] is not a valid slot.
     */
    fun bind(frameIndex: Int, commandBuffer: Long, pipelineLayout: Long) {
        VulkanDescriptors.vkCmdBindDescriptorSet(
            commandBuffer,
            pipelineLayout,
            PALETTE_SET,
            resourcesFor(frameIndex).descriptorSet.handle,
        )
    }

    /**
     * Destroys every live frame slot's buffer, memory and descriptor pool, and this buffer's
     * descriptor set layout. Call once, after the GPU has finished with them.
     */
    fun destroy() {
        val vkDevice = device
        for (i in frameResources.indices) {
            val frame = frameResources[i] ?: continue
            frameResources[i] = null
            frame.free(vkDevice)
        }
        VulkanDescriptors.vkDestroyDescriptorSetLayout(vkDevice, descriptorSetLayout.handle)
    }

    private fun resourcesFor(frameIndex: Int, instances: Int = 1): FrameResources {
        require(frameIndex in frameResources.indices) {
            "SkinnedInstanceBuffer frame index $frameIndex is outside 0..${frameResources.lastIndex}."
        }
        val current = frameResources[frameIndex]
        if (current != null && current.capacity >= instances) return current
        current?.let(::retireFrameResources)
        var capacity = MIN_CAPACITY.coerceAtMost(maxInstances)
        while (capacity < instances) capacity *= 2
        capacity = capacity.coerceAtMost(maxInstances)
        // paletteByteSize = capacity * 4096 bytes, always aligned to max Vulkan minStorageBufferOffsetAlignment (256B).
        val paletteByteSize = capacity.toLong() * FLOATS_PER_INSTANCE * Float.SIZE_BYTES
        val tintByteSize = capacity.toLong() * SkinnedInstanceLayout.TINT_FLOATS * Float.SIZE_BYTES
        val (buffer, memory, mappedPtr) = allocateHostVisibleBuffer(paletteByteSize + tintByteSize)
        val pool = createDescriptorPool()
        val set = VulkanDescriptors.vkAllocateDescriptorSet(device, pool, descriptorSetLayout.handle)
        VulkanDescriptors.vkUpdateDescriptorSetBuffer(
            device,
            set,
            SkinnedInstanceLayout.PALETTE_BINDING,
            VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
            VkDescriptorBufferInfo(buffer = buffer, range = paletteByteSize),
        )
        VulkanDescriptors.vkUpdateDescriptorSetBuffer(
            device,
            set,
            SkinnedInstanceLayout.TINT_BINDING,
            VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
            VkDescriptorBufferInfo(buffer = buffer, offset = paletteByteSize, range = tintByteSize),
        )
        return FrameResources(
            BufferHandle(buffer),
            DeviceMemoryHandle(memory),
            mappedPtr,
            DescriptorPoolHandle(pool),
            DescriptorSetHandle(set),
            capacity,
            paletteByteSize,
            tintByteSize,
        ).also { frameResources[frameIndex] = it }
    }

    private fun retireFrameResources(old: FrameResources) {
        val vkDevice = device
        retiredCount += 1L
        graphicsDevice.destroyOrRetire {
            old.free(vkDevice)
        }
    }

    private fun createDescriptorPool(): Long = VulkanDescriptors.vkCreateDescriptorPool(
        device,
        VkDescriptorPoolCreateInfo(
            maxSets = 1,
            pPoolSizes = arrayOf(
                VkDescriptorPoolSize(
                    type = VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                    descriptorCount = 2,
                ),
            ),
        ),
    )

    private fun allocateHostVisibleBuffer(byteSize: Long): Triple<Long, Long, Long> {
        val buffer = VulkanBuffers.vkCreateBuffer(
            device,
            VkBufferCreateInfo(
                size = byteSize,
                usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
            ),
        )
        val requirements = VulkanBuffers.vkGetBufferMemoryRequirements(device, buffer)
        val memory = graphicsDevice.allocateHostVisibleBufferMemory(requirements)
        VulkanBuffers.vkBindBufferMemory(device, buffer, memory, 0)
        val mappedPtr = VulkanBuffers.vkMapMemory(device, memory, 0L, requirements.size)
        return Triple(buffer, memory, mappedPtr)
    }

    /** Capacity, layout and descriptor-set constants for the joint-palette storage buffer. */
    companion object {
        /** Alias of the engine-wide constant that also sizes the WGSL palette arrays. */
        const val MAX_JOINTS = com.awakekt.awake.render.renderer.MAX_JOINTS

        /** One fixed-size `JointPalette` struct: 64 `mat4` = 1024 floats = 4 KB per instance. */
        val FLOATS_PER_INSTANCE = SkinnedInstanceLayout.PALETTE_FLOATS

        /** 256 * 4 KB = 1 MB per frame slot. Far lower than [InstanceBuffer]'s 4096 because an
         * animated instance costs 16x a static one -- 4096 here would be 16 MB per slot. */
        const val DEFAULT_MAX_INSTANCES = 256

        /** Initial per-slot capacity (4 * 4096B = 16 KiB instead of 1 MiB) before power-of-two growth. */
        internal const val MIN_CAPACITY = 4

        /** Descriptor set 1 -- set 0 is the material's. See this class's own doc comment. */
        const val PALETTE_SET = 1

        /** The storage layout declares palettes at binding 0 and tints at binding 1.
         * Both this class (for its own descriptor sets) and whoever
         * builds the skinned-instanced pipeline layout call this and get their OWN handle:
         * Vulkan set layouts are compatible by identical declaration, not by object identity,
         * so nothing has to be threaded from the bootstrap into every pooled buffer. */
        fun createDescriptorSetLayout(graphicsDevice: GraphicsDevice): DescriptorSetLayoutHandle =
            DescriptorSetLayoutHandle(
                VulkanDescriptors.vkCreateDescriptorSetLayout(
                    graphicsDevice.device,
                    VkDescriptorSetLayoutCreateInfo(
                        pBindings = arrayOf(
                            VkDescriptorSetLayoutBinding(
                                binding = SkinnedInstanceLayout.PALETTE_BINDING,
                                descriptorType = VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                                stageFlags = VkShaderStageFlagBits.VERTEX.value,
                            ),
                            VkDescriptorSetLayoutBinding(
                                binding = SkinnedInstanceLayout.TINT_BINDING,
                                descriptorType = VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                                stageFlags = VkShaderStageFlagBits.VERTEX.value,
                            ),
                        ),
                    ),
                ),
            )
    }
}
