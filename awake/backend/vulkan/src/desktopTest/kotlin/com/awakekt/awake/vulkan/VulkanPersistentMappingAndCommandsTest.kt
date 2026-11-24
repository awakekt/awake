/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.geometry.toByteArrayLE
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanCommands
import com.awakekt.awake.vulkan.models.VkExtent2D
import com.awakekt.awake.vulkan.models.VkOffset2D
import com.awakekt.awake.vulkan.models.VkRect2D
import com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkBufferUsageFlagBits
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies persistent host-memory mapping ([VulkanBuffers.vkMapMemory], [VulkanBuffers.writeMappedMemoryFloats],
 * [VulkanBuffers.writeMappedMemoryInts], [VulkanBuffers.writeMappedMemoryBytes], [VulkanBuffers.readMappedMemoryBytes])
 * and primitive JNI command entry points ([VulkanCommands]) under real Vulkan validation layers.
 */
class VulkanPersistentMappingAndCommandsTest {
    @Test
    fun persistentMappingRoundTripsFloatsIntsBytesAndCoexistsWithReadBufferMemoryBytes() {
        val device = GraphicsDevice()
        device.createHeadless()
        try {
            val byteSize = 4096L
            val buffer = VulkanBuffers.vkCreateBuffer(
                device.device,
                VkBufferCreateInfo(
                    size = byteSize,
                    usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT or
                        VkBufferUsageFlagBits.VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                ),
            )
            val req = VulkanBuffers.vkGetBufferMemoryRequirements(device.device, buffer)
            val memType = VulkanBuffers.findMemoryType(
                device.physicalDevice,
                req.memoryTypeBits,
                VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or
                    VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            )
            val memory = VulkanBuffers.vkAllocateMemory(
                device.device,
                VkMemoryAllocateInfo(allocationSize = req.size, memoryTypeIndex = memType),
            )
            VulkanBuffers.vkBindBufferMemory(device.device, buffer, memory, 0L)
            val mappedPtr = VulkanBuffers.vkMapMemory(device.device, memory, 0L, req.size)
            try {
                assertTrue(mappedPtr != 0L, "vkMapMemory must return a non-null native pointer")

                val floats = floatArrayOf(1.5f, -2.25f, 42.0f, 3.14159f, 99.0f)
                VulkanBuffers.writeMappedMemoryFloats(mappedPtr, 16L, floats, dataOffset = 1, count = 3)
                val floatBytes = VulkanBuffers.readMappedMemoryBytes(mappedPtr, 16L, 3 * Float.SIZE_BYTES)
                val floatBuf = ByteBuffer.wrap(floatBytes).order(ByteOrder.nativeOrder()).asFloatBuffer()
                assertEquals(-2.25f, floatBuf[0])
                assertEquals(42.0f, floatBuf[1])
                assertEquals(3.14159f, floatBuf[2])

                val ints = intArrayOf(10, 0x12345678, -99, 777)
                VulkanBuffers.writeMappedMemoryInts(mappedPtr, 128L, ints, dataOffset = 1, count = 2)
                val intBytes = VulkanBuffers.readMappedMemoryBytes(mappedPtr, 128L, 2 * Int.SIZE_BYTES)
                val intBuf = ByteBuffer.wrap(intBytes).order(ByteOrder.nativeOrder()).asIntBuffer()
                assertEquals(0x12345678, intBuf[0])
                assertEquals(-99, intBuf[1])

                val rawBytes = byteArrayOf(1, 2, 3, 4, 5, 6)
                VulkanBuffers.writeMappedMemoryBytes(mappedPtr, 256L, rawBytes, dataOffset = 2, count = 3)
                assertContentEquals(byteArrayOf(3, 4, 5), VulkanBuffers.readMappedMemoryBytes(mappedPtr, 256L, 3))

                // Coexistence: legacy readBufferMemoryBytes must not re-map already-mapped memory.
                assertContentEquals(
                    byteArrayOf(3, 4, 5),
                    VulkanBuffers.readBufferMemoryBytes(device.device, memory, 256L, 3),
                )
            } finally {
                VulkanBuffers.vkDestroyBuffer(device.device, buffer)
                VulkanBuffers.vkUnmapMemory(device.device, memory)
                VulkanBuffers.vkFreeMemory(device.device, memory)
            }
        } finally {
            device.destroy()
        }
    }

    @Test
    fun primitiveUploadAndScissorRecordingOutperformLegacyPaths() {
        val device = GraphicsDevice()
        device.createHeadless()
        try {
            val transfer = TransferContext(device)
            try {
                val (legacyUploadMs, mappedUploadMs) = measureUploadPaths(device)
                val (legacyScissorMs, fastScissorMs) = measureScissorPaths(transfer)
                println(
                    "VulkanFastPathMicrobench: dynamicUpload(200x128KB) legacy=${"%.2f".format(legacyUploadMs)}ms " +
                        "mapped=${"%.2f".format(mappedUploadMs)}ms (${"%.2f".format(legacyUploadMs / mappedUploadMs)}x) | " +
                        "vkCmdSetScissor(5000x) legacy=${"%.2f".format(legacyScissorMs)}ms " +
                        "fast=${"%.2f".format(fastScissorMs)}ms (${"%.2f".format(legacyScissorMs / fastScissorMs)}x)",
                )
                assertTrue(mappedUploadMs > 0.0 && fastScissorMs > 0.0)
            } finally {
                transfer.destroy()
            }
        } finally {
            device.destroy()
        }
    }

    private fun measureUploadPaths(device: GraphicsDevice): Pair<Double, Double> {
        val floatCount = 16_384
        val floats = FloatArray(floatCount) { it.toFloat() }
        val indices = IntArray(floatCount) { it }
        val byteSize = (floatCount * Float.SIZE_BYTES).toLong()
        val buffer = VulkanBuffers.vkCreateBuffer(
            device.device,
            VkBufferCreateInfo(size = byteSize, usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT),
        )
        val req = VulkanBuffers.vkGetBufferMemoryRequirements(device.device, buffer)
        val memType = VulkanBuffers.findMemoryType(
            device.physicalDevice,
            req.memoryTypeBits,
            VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or
                VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
        )
        val memory = VulkanBuffers.vkAllocateMemory(
            device.device,
            VkMemoryAllocateInfo(allocationSize = req.size, memoryTypeIndex = memType),
        )
        VulkanBuffers.vkBindBufferMemory(device.device, buffer, memory, 0L)
        try {
            var scratch = ByteArray(floatCount * Int.SIZE_BYTES)
            repeat(20) {
                VulkanBuffers.writeBufferMemoryFloats(device.device, memory, 0L, floats)
                scratch = indices.toByteArrayLE(scratch)
                VulkanBuffers.writeBufferMemoryBytes(device.device, memory, 0L, scratch)
            }
            val legacyStart = System.nanoTime()
            repeat(200) {
                VulkanBuffers.writeBufferMemoryFloats(device.device, memory, 0L, floats)
                scratch = indices.toByteArrayLE(scratch)
                VulkanBuffers.writeBufferMemoryBytes(device.device, memory, 0L, scratch)
            }
            val legacyMs = (System.nanoTime() - legacyStart) / 1_000_000.0

            val mappedPtr = VulkanBuffers.vkMapMemory(device.device, memory, 0L, req.size)
            val mappedMs = try {
                repeat(20) {
                    VulkanBuffers.writeMappedMemoryFloats(mappedPtr, 0L, floats, 0, floats.size)
                    VulkanBuffers.writeMappedMemoryInts(mappedPtr, 0L, indices, 0, indices.size)
                }
                val mappedStart = System.nanoTime()
                repeat(200) {
                    VulkanBuffers.writeMappedMemoryFloats(mappedPtr, 0L, floats, 0, floats.size)
                    VulkanBuffers.writeMappedMemoryInts(mappedPtr, 0L, indices, 0, indices.size)
                }
                (System.nanoTime() - mappedStart) / 1_000_000.0
            } finally {
                VulkanBuffers.vkUnmapMemory(device.device, memory)
            }
            return legacyMs to mappedMs
        } finally {
            VulkanBuffers.vkDestroyBuffer(device.device, buffer)
            VulkanBuffers.vkFreeMemory(device.device, memory)
        }
    }

    private fun measureScissorPaths(transfer: TransferContext): Pair<Double, Double> {
        var legacyMs = 0.0
        var fastMs = 0.0
        transfer.runOneTimeCommands { cmd ->
            val rect = arrayOf(VkRect2D(offset = VkOffset2D(0, 0), extent = VkExtent2D(128, 128)))
            repeat(200) { Vulkan.vkCmdSetScissor(cmd, 0, rect) }
            val t0 = System.nanoTime()
            repeat(5_000) {
                Vulkan.vkCmdSetScissor(
                    cmd,
                    0,
                    arrayOf(VkRect2D(offset = VkOffset2D(0, 0), extent = VkExtent2D(128, 128))),
                )
            }
            legacyMs = (System.nanoTime() - t0) / 1_000_000.0

            repeat(200) { VulkanCommands.vkCmdSetScissorRect(cmd, 0, 0, 128, 128) }
            val t1 = System.nanoTime()
            repeat(5_000) { VulkanCommands.vkCmdSetScissorRect(cmd, 0, 0, 128, 128) }
            fastMs = (System.nanoTime() - t1) / 1_000_000.0
        }
        return legacyMs to fastMs
    }
}
