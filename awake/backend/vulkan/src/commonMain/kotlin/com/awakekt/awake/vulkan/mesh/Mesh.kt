/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.mesh

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.toByteArrayLE
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.handles.BufferHandle
import com.awakekt.awake.vulkan.handles.DeviceMemoryHandle
import com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkBufferUsageFlagBits
import com.awakekt.awake.vulkan.models.info.VkIndexType
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo
import com.awakekt.awake.vulkan.pipeline.VulkanBufferBinding
import com.awakekt.awake.render.mesh.Mesh as RenderMesh

/**
 * Phase 2 (renderer abstraction): owns a single mesh's vertex/index buffer upload -- the
 * standard Vulkan staging-buffer pattern extracted verbatim from `VulkanApplication`'s
 * `createDeviceLocalBuffer`/`createVertexBuffer`/`createIndexBuffer` functions and their
 * backing fields. A HOST_VISIBLE staging buffer is written and copied into a real
 * DEVICE_LOCAL buffer via a one-time command buffer, same pattern already proven for
 * texture upload.
 *
 * [runOneTimeCommands] is injected rather than owned here: it needs a command pool and
 * graphics queue, neither of which is extracted into a dedicated class yet (they're still
 * pipeline/render-pass-adjacent state on `VulkanApplication`) -- passing the mechanism in
 * avoids taking on that scope here.
 *
 * Only owns vertex+index buffer bind/draw, not descriptor-set binding: that's a
 * Material/Shader concern (interleaved between [bind] and [draw] by the caller), not part
 * of what a mesh is.
 */
class Mesh @Suppress("LongParameterList") private constructor(
    graphicsDevice: GraphicsDevice,
    runOneTimeCommands: ((commandBuffer: Long) -> Unit) -> Unit,
    vertices: FloatArray,
    indices: IntArray,
    override val format: VertexFormat,
    override val localBounds: Aabb?,
    /** The mesh whose vertex buffer this one draws instead of uploading its own; see [sharingVertices]. */
    sharedFrom: Mesh?,
) : RenderMesh {
    constructor(
        graphicsDevice: GraphicsDevice,
        runOneTimeCommands: ((commandBuffer: Long) -> Unit) -> Unit,
        vertices: FloatArray,
        indices: IntArray,
        format: VertexFormat = VertexFormat.PositionColorUv,
        /** The box these vertices occupy -- see `Mesh.localBounds`; `createMesh` fills it from the
         * geometry, which is the last point anything can. */
        localBounds: Aabb? = null,
    ) : this(graphicsDevice, runOneTimeCommands, vertices, indices, format, localBounds, null)

    private val graphicsDevice = graphicsDevice
    private val runOneTimeCommands = runOneTimeCommands
    private val device get() = graphicsDevice.device
    private val physicalDevice get() = graphicsDevice.physicalDevice

    /**
     * The device-local vertex buffer holding the interleaved vertices that were uploaded at
     * construction. Destroyed by [destroy].
     */
    var vertexBuffer: BufferHandle = BufferHandle(0)

    /** The device memory backing [vertexBuffer]. */
    var vertexBufferMemory: DeviceMemoryHandle = DeviceMemoryHandle(0)

    /**
     * The device-local buffer of 32-bit indices uploaded at construction. Destroyed by [destroy].
     */
    var indexBuffer: BufferHandle = BufferHandle(0)

    /** The device memory backing [indexBuffer]. */
    var indexBufferMemory: DeviceMemoryHandle = DeviceMemoryHandle(0)

    /** Number of indices in [indexBuffer], which is how many indices [draw] submits. */
    val indexCount: Int = indices.size

    override val sizeBytes: Long =
        (if (sharedFrom == null) vertices.size.toLong() * Float.SIZE_BYTES else 0L) + indices.size.toLong() * Int.SIZE_BYTES

    /** Counts the meshes drawing [vertexBuffer], so the last one destroyed frees it. */
    private val vertexOwner: SharedVertices

    /** The same two buffers above, as the port's opaque handle type -- built once here so the
     * shared per-draw recording path allocates nothing per frame. */
    val vertexBinding: VulkanBufferBinding

    /**
     * [indexBuffer] as the shared render layer's opaque buffer handle, built once at construction.
     */
    val indexBinding: VulkanBufferBinding

    init {
        if (sharedFrom != null) {
            vertexOwner = sharedFrom.vertexOwner.also { it.meshes++ }
            vertexBuffer = sharedFrom.vertexBuffer
            vertexBufferMemory = sharedFrom.vertexBufferMemory
        } else {
            vertexOwner = SharedVertices()
            val vertexBufferSize = (vertices.size * Float.SIZE_BYTES).toLong()
            val (vBuffer, vMemory) = createDeviceLocalBuffer(
                byteSize = vertexBufferSize,
                usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                write = { stagingMemory ->
                    VulkanBuffers.writeBufferMemoryFloats(device, stagingMemory, 0, vertices)
                },
            )
            vertexBuffer = BufferHandle(vBuffer)
            vertexBufferMemory = DeviceMemoryHandle(vMemory)
        }

        val indexBytes = indices.toByteArrayLE()
        val indexBufferSize = indexBytes.size.toLong()
        val (iBuffer, iMemory) = createDeviceLocalBuffer(
            byteSize = indexBufferSize,
            usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
            write = { stagingMemory ->
                VulkanBuffers.writeBufferMemoryBytes(device, stagingMemory, 0, indexBytes)
            },
        )
        indexBuffer = BufferHandle(iBuffer)
        indexBufferMemory = DeviceMemoryHandle(iMemory)

        vertexBinding = VulkanBufferBinding(vertexBuffer.handle)
        indexBinding = VulkanBufferBinding(indexBuffer.handle)
    }

    /** Allocates a `byteSize`-byte HOST_VISIBLE staging buffer, writes into it via [write],
     * copies it into a new DEVICE_LOCAL buffer (usage = [usage] or TRANSFER_DST) using a
     * one-time command buffer + `vkCmdCopyBuffer`, then frees the staging buffer. */
    private fun createDeviceLocalBuffer(
        byteSize: Long,
        usage: Int,
        write: (memory: Long) -> Unit,
    ): Pair<Long, Long> {
        val stagingBuffer = VulkanBuffers.vkCreateBuffer(
            device,
            VkBufferCreateInfo(
                size = byteSize,
                usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
            ),
        )
        val stagingRequirements = VulkanBuffers.vkGetBufferMemoryRequirements(device, stagingBuffer)
        val stagingMemoryTypeIndex = VulkanBuffers.findMemoryType(
            physicalDevice,
            stagingRequirements.memoryTypeBits,
            VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or
                VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
        )
        val stagingMemory = VulkanBuffers.vkAllocateMemory(
            device,
            VkMemoryAllocateInfo(
                allocationSize = stagingRequirements.size,
                memoryTypeIndex = stagingMemoryTypeIndex,
            ),
        )
        VulkanBuffers.vkBindBufferMemory(device, stagingBuffer, stagingMemory, 0)
        // The staging buffer/memory must be freed even if something below throws (e.g. the
        // dest buffer's allocation fails, or runOneTimeCommands does) -- otherwise a failed
        // upload leaks GPU memory silently. The dest buffer/memory themselves aren't covered
        // here: if the copy fails, Mesh's constructor throws anyway and the whole object
        // never becomes visible to a caller, so nothing (correctly) reaches use it.
        try {
            write(stagingMemory)

            val destBuffer = VulkanBuffers.vkCreateBuffer(
                device,
                VkBufferCreateInfo(
                    size = byteSize,
                    usage = usage or VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                ),
            )
            val destRequirements = VulkanBuffers.vkGetBufferMemoryRequirements(device, destBuffer)
            val destMemoryTypeIndex = VulkanBuffers.findMemoryType(
                physicalDevice,
                destRequirements.memoryTypeBits,
                VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
            )
            val destMemory = VulkanBuffers.vkAllocateMemory(
                device,
                VkMemoryAllocateInfo(
                    allocationSize = destRequirements.size,
                    memoryTypeIndex = destMemoryTypeIndex,
                ),
            )
            VulkanBuffers.vkBindBufferMemory(device, destBuffer, destMemory, 0)

            runOneTimeCommands { commandBuffer ->
                VulkanBuffers.vkCmdCopyBuffer(commandBuffer, stagingBuffer, destBuffer, byteSize)
            }

            return destBuffer to destMemory
        } finally {
            VulkanBuffers.vkDestroyBuffer(device, stagingBuffer)
            VulkanBuffers.vkFreeMemory(device, stagingMemory)
        }
    }

    /** Binds this mesh's vertex + index buffers. Descriptor-set binding (a Material/Shader
     * concern) happens in between this and [draw], not here. */
    fun bind(commandBuffer: Long) {
        VulkanBuffers.vkCmdBindVertexBuffers(
            commandBuffer,
            0,
            longArrayOf(vertexBuffer.handle),
            longArrayOf(0L),
        )
        VulkanBuffers.vkCmdBindIndexBuffer(
            commandBuffer,
            indexBuffer.handle,
            0,
            VkIndexType.VK_INDEX_TYPE_UINT32,
        )
    }

    /**
     * Records an indexed draw of the whole mesh as one instance. Call after [bind] and after the
     * material's descriptor set has been bound.
     *
     * @param commandBuffer The command buffer being recorded.
     */
    fun draw(commandBuffer: Long) {
        VulkanBuffers.vkCmdDrawIndexed(commandBuffer, indexCount, 1, 0, 0, 0)
    }

    /** Same call as [draw], only the instance count differs -- each instance reads its own
     * model matrix from the [InstanceBuffer] the caller bound at binding 1. */
    fun drawInstanced(commandBuffer: Long, instanceCount: Int) {
        VulkanBuffers.vkCmdDrawIndexed(commandBuffer, indexCount, instanceCount, 0, 0, 0)
    }

    /**
     * A mesh drawing this one's vertex buffer with [indices] instead of its own, or null when this
     * mesh has been destroyed and its vertex buffer with it.
     */
    internal fun sharingVertices(indices: IntArray, bounds: Aabb?): Mesh? = if (vertexOwner.meshes == 0) {
        null
    } else {
        Mesh(graphicsDevice, runOneTimeCommands, EMPTY_VERTICES, indices, format, bounds, this)
    }

    override fun destroy() {
        // Scene replacement can release this mesh while another frame slot still references it.
        // Device idle covers every submitted queue without waiting on a reset, unsubmitted fence.
        VulkanBuffers.vkDeviceWaitIdle(device)
        if (--vertexOwner.meshes == 0) {
            VulkanBuffers.vkDestroyBuffer(device, vertexBuffer.handle)
            VulkanBuffers.vkFreeMemory(device, vertexBufferMemory.handle)
        }
        VulkanBuffers.vkDestroyBuffer(device, indexBuffer.handle)
        VulkanBuffers.vkFreeMemory(device, indexBufferMemory.handle)
    }

    /** How many meshes still draw one vertex buffer. */
    private class SharedVertices {
        var meshes = 1
    }

    private companion object {
        val EMPTY_VERTICES = FloatArray(0)
    }
}
