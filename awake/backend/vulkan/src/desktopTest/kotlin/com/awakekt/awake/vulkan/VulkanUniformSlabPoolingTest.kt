/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.debug.PerFrameUniformSlots
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.VkShaderStageFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.material.PbrImageViews
import com.awakekt.awake.vulkan.texture.Texture
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that Vulkan `Material` slot blocks and `PerFrameUniformSlots` sub-allocate host-visible
 * uniform memory from shared slabs, share ref-counted `VkDescriptorPool` chunks, deduplicate
 * `VkDescriptorSetLayout` handles, isolate mapped slot writes cleanly, reuse coalesced freed
 * ranges, and reclaim every empty slab and pool on trim with zero Vulkan validation errors.
 */
class VulkanUniformSlabPoolingTest {
    @Test
    fun materialsAndPerFrameSlotsShareMemorySlabsDescriptorPoolsAndLayouts() {
        val device = GraphicsDevice().apply { createHeadless() }
        val transfer = TransferContext(device)
        val texture = Texture(device, transfer::runOneTimeCommands, ByteArray(4) { -1 }, 1, 1)
        val view = texture.imageView.handle
        val pbr = PbrImageViews(view, view, view, view)
        val materials = mutableListOf<Material>()
        val perFrameSlots = mutableListOf<PerFrameUniformSlots>()
        try {
            repeat(MATERIAL_COUNT) { index ->
                val mat = Material.createSharedLayout(device, uniformFloatCount = UBO_FLOATS).apply { createResources(texture, pbr) }
                materials += mat
                val payload = FloatArray(UBO_FLOATS) { index * 100f + it }
                mat.updateUniformBuffer(frameIndex = 0, drawSlotIndex = 0, values = payload)
                mat.updateUniformBuffer(frameIndex = 1, drawSlotIndex = 0, values = FloatArray(UBO_FLOATS) { index * 100f + 50f + it })
            }
            repeat(PER_FRAME_COUNT) { index ->
                val slots = PerFrameUniformSlots(
                    graphicsDevice = device,
                    uniformBytes = UBO_FLOATS * Float.SIZE_BYTES,
                    stageFlags = VkShaderStageFlagBits.VERTEX.value,
                    framesInFlight = FRAMES_IN_FLIGHT,
                )
                perFrameSlots += slots
                repeat(FRAMES_IN_FLIGHT + 1) { slotIdx ->
                    slots.write(slotIdx, FloatArray(UBO_FLOATS) { index * 1000f + slotIdx * 100f + it })
                }
            }

            // 64 materials (4 slots each) + 16 PerFrameUniformSlots (3 slots each) = 304 UBO buffers.
            // Without pooling this took 112+ vkAllocateMemory calls, 112+ VkDescriptorPools, and 80 layouts.
            val memPool = device.uniformMemoryPool
            val descPool = device.descriptorSetPool
            val layoutCache = device.descriptorSetLayoutCache
            assertTrue(
                memPool.slabCountCreated <= MAX_EXPECTED_SLABS,
                "Expected <= $MAX_EXPECTED_SLABS shared uniform memory slabs, got ${memPool.slabCountCreated}",
            )
            assertEquals(0L, memPool.standaloneAllocationCount, "Small UBO blocks must sub-allocate from slabs")
            assertEquals(MATERIAL_COUNT + PER_FRAME_COUNT, memPool.activeSubAllocations)
            assertTrue(
                descPool.poolsCreated <= MAX_EXPECTED_POOLS,
                "Expected <= $MAX_EXPECTED_POOLS shared descriptor pools, got ${descPool.poolsCreated}",
            )
            assertEquals(2, layoutCache.cachedLayoutCount, "1 StandardMaterial layout + 1 single-uniform layout")

            verifyMappedIsolation(materials, perFrameSlots)
            verifyChurnAndTrim(device, texture, pbr, materials, perFrameSlots)
        } finally {
            materials.forEach { it.destroy() }
            perFrameSlots.forEach { it.destroy() }
            texture.destroy()
            transfer.destroy()
            device.destroy()
        }
    }

    private fun verifyMappedIsolation(
        materials: List<Material>,
        perFrameSlots: List<PerFrameUniformSlots>,
    ) {
        materials.forEachIndexed { index, mat ->
            val slot0 = mat.uniformSlot(0, 0)
            val slot1 = mat.uniformSlot(1, 0)
            assertEquals(
                List(UBO_FLOATS) { index * 100f + it },
                readFloats(slot0.block.mappedPtr, slot0.memoryOffset, UBO_FLOATS),
                "Material $index frame 0 data corrupted",
            )
            assertEquals(
                List(UBO_FLOATS) { index * 100f + 50f + it },
                readFloats(slot1.block.mappedPtr, slot1.memoryOffset, UBO_FLOATS),
                "Material $index frame 1 data corrupted",
            )
        }
        perFrameSlots.forEachIndexed { index, slots ->
            repeat(FRAMES_IN_FLIGHT + 1) { slotIdx ->
                assertEquals(
                    List(UBO_FLOATS) { index * 1000f + slotIdx * 100f + it },
                    readFloats(slots[slotIdx].mappedPtr, 0L, UBO_FLOATS),
                    "PerFrameUniformSlots $index slot $slotIdx data corrupted",
                )
            }
        }
    }

    private fun verifyChurnAndTrim(
        device: GraphicsDevice,
        texture: Texture,
        pbr: PbrImageViews,
        materials: MutableList<Material>,
        perFrameSlots: MutableList<PerFrameUniformSlots>,
    ) {
        val memPool = device.uniformMemoryPool
        val descPool = device.descriptorSetPool
        val slabsBeforeChurn = memPool.slabCountCreated
        val half = MATERIAL_COUNT / 2
        repeat(half) { idx -> materials[idx].destroy() }
        val replacements = List(half) { idx ->
            Material.createSharedLayout(device, uniformFloatCount = UBO_FLOATS).apply {
                createResources(texture, pbr)
                updateUniformBuffer(0, 0, FloatArray(UBO_FLOATS) { 9000f + idx * 10f + it })
            }
        }
        assertEquals(
            slabsBeforeChurn,
            memPool.slabCountCreated,
            "Replacement materials must reuse freed slab spans without allocating new slabs",
        )
        replacements.forEachIndexed { idx, mat ->
            val slot = mat.uniformSlot(0, 0)
            assertEquals(
                List(UBO_FLOATS) { 9000f + idx * 10f + it },
                readFloats(slot.block.mappedPtr, slot.memoryOffset, UBO_FLOATS),
            )
        }

        replacements.forEach { it.destroy() }
        for (idx in half until MATERIAL_COUNT) materials[idx].destroy()
        materials.clear()
        perFrameSlots.forEach { it.destroy() }
        perFrameSlots.clear()

        assertEquals(0, memPool.activeSubAllocations)
        assertEquals(0, descPool.activeBlockCount)
        memPool.trimEmptySlabs()
        descPool.trimEmptyPools()
        assertEquals(0, memPool.liveSlabCount)
        assertEquals(0L, memPool.liveSlabBytes)
        assertEquals(0, descPool.livePoolCount)
    }

    private fun readFloats(mappedPtr: Long, offset: Long, count: Int): List<Float> {
        val bytes = VulkanBuffers.readMappedMemoryBytes(mappedPtr, offset, count * Float.SIZE_BYTES)
        val floats = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asFloatBuffer()
        return List(count) { floats[it] }
    }

    private companion object {
        const val MATERIAL_COUNT = 64
        const val PER_FRAME_COUNT = 16
        const val FRAMES_IN_FLIGHT = 2
        const val UBO_FLOATS = 28
        const val MAX_EXPECTED_SLABS = 2L
        const val MAX_EXPECTED_POOLS = 4L
    }
}
