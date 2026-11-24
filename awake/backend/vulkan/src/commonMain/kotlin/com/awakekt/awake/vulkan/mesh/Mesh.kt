/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.mesh

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.vulkan.commands.UploadBatcher
import com.awakekt.awake.vulkan.device.DeviceMemoryAllocation
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.device.SlabKind
import com.awakekt.awake.vulkan.device.destroyOrRetire
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.handles.BufferHandle
import com.awakekt.awake.vulkan.handles.DeviceMemoryHandle
import com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkBufferUsageFlagBits
import com.awakekt.awake.vulkan.models.info.VkIndexType
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
class Mesh
@Suppress("LongParameterList")
private constructor(
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
    private val vertexAllocation: DeviceMemoryAllocation
    private val indexAllocation: DeviceMemoryAllocation
    private val indexPendingUpload: UploadBatcher.PendingUpload?

    /** The same two buffers above, as the port's opaque handle type -- built once here so the
     * shared per-draw recording path allocates nothing per frame. */
    val vertexBinding: VulkanBufferBinding

    /**
     * [indexBuffer] as the shared render layer's opaque buffer handle, built once at construction.
     */
    val indexBinding: VulkanBufferBinding

    /** This mesh's buffers as a scene draw binds them: always all of its indices. */
    internal val sceneBuffers: SceneMeshBuffers = object : SceneMeshBuffers {
        override val vertexBinding get() = this@Mesh.vertexBinding
        override val indexBinding get() = this@Mesh.indexBinding
        override val indexCount get() = this@Mesh.indexCount
    }

    private var destroyed = false

    init {
        val vertexBufferSize = if (sharedFrom == null) vertices.size.toLong() * Float.SIZE_BYTES else 0L
        val indexOffset = alignStagingOffset(vertexBufferSize)
        val indexBufferSize = indices.size.toLong() * Int.SIZE_BYTES
        val totalStagingBytes = indexOffset + indexBufferSize

        if (sharedFrom != null) {
            vertexOwner = sharedFrom.vertexOwner.also { it.meshes++ }
            vertexBuffer = sharedFrom.vertexBuffer
            vertexBufferMemory = sharedFrom.vertexBufferMemory
            vertexAllocation = sharedFrom.vertexAllocation
            val (iBuffer, iAlloc) = allocateDeviceLocalBuffer(
                byteSize = indexBufferSize,
                usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
            )
            var indexUpload: UploadBatcher.PendingUpload? = null
            var uploaded = false
            try {
                val batcher = graphicsDevice.activeUploadBatcher?.takeIf { it.isBatching }
                if (batcher != null) {
                    indexUpload = batcher.enqueue(
                        byteSize = indexBufferSize,
                        alignment = STAGING_ALIGN_BYTES,
                        flushSync = runOneTimeCommands,
                        writeStaging = { mappedPtr, stagingOffset ->
                            VulkanBuffers.writeMappedMemoryInts(mappedPtr, stagingOffset, indices, 0, indices.size)
                        },
                        recordCopy = { commandBuffer, stagingBuffer, stagingOffset ->
                            VulkanBuffers.vkCmdCopyBufferRange(commandBuffer, stagingBuffer, iBuffer, stagingOffset, 0L, indexBufferSize)
                        },
                    )
                } else {
                    graphicsDevice.stagingBufferPool.withStaging(indexBufferSize) { staging ->
                        VulkanBuffers.writeMappedMemoryInts(staging.mappedPtr, 0L, indices, 0, indices.size)
                        runOneTimeCommands { commandBuffer ->
                            VulkanBuffers.vkCmdCopyBufferRange(commandBuffer, staging.buffer, iBuffer, 0L, 0L, indexBufferSize)
                        }
                    }
                }
                uploaded = true
            } finally {
                if (!uploaded) {
                    indexUpload?.cancel()
                    VulkanBuffers.vkDestroyBuffer(device, iBuffer)
                    graphicsDevice.deviceMemoryAllocator.free(iAlloc)
                    vertexOwner.meshes--
                }
            }
            indexBuffer = BufferHandle(iBuffer)
            indexBufferMemory = DeviceMemoryHandle(iAlloc.memory)
            indexAllocation = iAlloc
            indexPendingUpload = indexUpload
        } else {
            vertexOwner = SharedVertices()
            val (vBuffer, vAlloc) = allocateDeviceLocalBuffer(
                byteSize = vertexBufferSize,
                usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
            )
            var iBuffer = 0L
            var iAlloc: DeviceMemoryAllocation? = null
            var indexUpload: UploadBatcher.PendingUpload? = null
            var uploaded = false
            try {
                val (allocatedIndexBuffer, allocatedIndexAlloc) = allocateDeviceLocalBuffer(
                    byteSize = indexBufferSize,
                    usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                )
                iBuffer = allocatedIndexBuffer
                iAlloc = allocatedIndexAlloc
                // Upload both vertex and index buffers through the active upload batcher (zero
                // per-asset fence stall when streaming or inside withUploadBatch) or one reusable
                // persistently-mapped staging buffer and a single GPU submit.
                val batcher = graphicsDevice.activeUploadBatcher?.takeIf { it.isBatching }
                if (batcher != null) {
                    vertexOwner.pendingUpload = batcher.enqueue(
                        byteSize = vertexBufferSize,
                        alignment = STAGING_ALIGN_BYTES,
                        flushSync = runOneTimeCommands,
                        writeStaging = { mappedPtr, stagingOffset ->
                            VulkanBuffers.writeMappedMemoryFloats(mappedPtr, stagingOffset, vertices, 0, vertices.size)
                        },
                        recordCopy = { commandBuffer, stagingBuffer, stagingOffset ->
                            VulkanBuffers.vkCmdCopyBufferRange(commandBuffer, stagingBuffer, vBuffer, stagingOffset, 0L, vertexBufferSize)
                        },
                    )
                    indexUpload = batcher.enqueue(
                        byteSize = indexBufferSize,
                        alignment = STAGING_ALIGN_BYTES,
                        flushSync = runOneTimeCommands,
                        writeStaging = { mappedPtr, stagingOffset ->
                            VulkanBuffers.writeMappedMemoryInts(mappedPtr, stagingOffset, indices, 0, indices.size)
                        },
                        recordCopy = { commandBuffer, stagingBuffer, stagingOffset ->
                            VulkanBuffers.vkCmdCopyBufferRange(
                                commandBuffer,
                                stagingBuffer,
                                allocatedIndexBuffer,
                                stagingOffset,
                                0L,
                                indexBufferSize,
                            )
                        },
                    )
                } else {
                    graphicsDevice.stagingBufferPool.withStaging(totalStagingBytes) { staging ->
                        VulkanBuffers.writeMappedMemoryFloats(staging.mappedPtr, 0L, vertices, 0, vertices.size)
                        VulkanBuffers.writeMappedMemoryInts(staging.mappedPtr, indexOffset, indices, 0, indices.size)
                        runOneTimeCommands { commandBuffer ->
                            VulkanBuffers.vkCmdCopyBufferRange(commandBuffer, staging.buffer, vBuffer, 0L, 0L, vertexBufferSize)
                            VulkanBuffers.vkCmdCopyBufferRange(commandBuffer, staging.buffer, allocatedIndexBuffer, indexOffset, 0L, indexBufferSize)
                        }
                    }
                }
                uploaded = true
            } finally {
                if (!uploaded) {
                    indexUpload?.cancel()
                    vertexOwner.pendingUpload?.cancel()
                    if (iBuffer != 0L) VulkanBuffers.vkDestroyBuffer(device, iBuffer)
                    if (iAlloc != null) graphicsDevice.deviceMemoryAllocator.free(iAlloc)
                    VulkanBuffers.vkDestroyBuffer(device, vBuffer)
                    graphicsDevice.deviceMemoryAllocator.free(vAlloc)
                }
            }
            vertexBuffer = BufferHandle(vBuffer)
            vertexBufferMemory = DeviceMemoryHandle(vAlloc.memory)
            vertexAllocation = vAlloc
            indexBuffer = BufferHandle(iBuffer)
            indexBufferMemory = DeviceMemoryHandle(iAlloc.memory)
            indexAllocation = iAlloc
            indexPendingUpload = indexUpload
        }

        vertexBinding = VulkanBufferBinding(vertexBuffer.handle)
        indexBinding = VulkanBufferBinding(indexBuffer.handle)
    }

    /** Allocates a `DEVICE_LOCAL` destination buffer backed by [GraphicsDevice.deviceMemoryAllocator]. */
    private fun allocateDeviceLocalBuffer(byteSize: Long, usage: Int): Pair<Long, DeviceMemoryAllocation> {
        val destBuffer = VulkanBuffers.vkCreateBuffer(
            device,
            VkBufferCreateInfo(
                size = byteSize,
                usage = usage or VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_DST_BIT,
            ),
        )
        val destRequirements = VulkanBuffers.vkGetBufferMemoryRequirements(device, destBuffer)
        var allocation: DeviceMemoryAllocation? = null
        try {
            allocation = graphicsDevice.deviceMemoryAllocator.allocate(destRequirements, SlabKind.Buffer)
        } finally {
            if (allocation == null) VulkanBuffers.vkDestroyBuffer(device, destBuffer)
        }
        VulkanBuffers.vkBindBufferMemory(device, destBuffer, allocation.memory, allocation.offset)
        return destBuffer to allocation
    }

    /** Binds this mesh's vertex + index buffers. Descriptor-set binding (a Material/Shader
     * concern) happens in between this and [draw], not here. */
    fun bind(commandBuffer: Long) {
        VulkanBuffers.vkCmdBindVertexBuffer(commandBuffer, 0, vertexBuffer.handle, 0L)
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

    internal var onDestroy: (() -> Unit)? = null

    /**
     * A mesh drawing this one's vertex buffer with [indices] instead of its own, or null when this
     * mesh has been destroyed and its vertex buffer with it.
     */
    internal fun sharingVertices(indices: IntArray, bounds: Aabb?): Mesh? = if (destroyed || vertexOwner.meshes == 0) {
        null
    } else {
        Mesh(graphicsDevice, runOneTimeCommands, EMPTY_VERTICES, indices, format, bounds, this)
    }

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        val freeVertices = --vertexOwner.meshes == 0
        if (freeVertices) vertexOwner.pendingUpload?.cancel()
        indexPendingUpload?.cancel()
        val vBuf = vertexBuffer.handle
        val vAlloc = vertexAllocation
        val iBuf = indexBuffer.handle
        val iAlloc = indexAllocation
        val vkDevice = device
        val allocator = graphicsDevice.deviceMemoryAllocator
        val release = onDestroy
        onDestroy = null
        release?.invoke()
        graphicsDevice.destroyOrRetire {
            if (freeVertices) {
                VulkanBuffers.vkDestroyBuffer(vkDevice, vBuf)
                allocator.free(vAlloc)
            }
            VulkanBuffers.vkDestroyBuffer(vkDevice, iBuf)
            allocator.free(iAlloc)
        }
    }

    /** How many meshes still draw one vertex buffer. */
    private class SharedVertices {
        var meshes = 1
        var pendingUpload: UploadBatcher.PendingUpload? = null
    }

    private companion object {
        val EMPTY_VERTICES = FloatArray(0)

        fun alignStagingOffset(bytes: Long): Long =
            (bytes + STAGING_ALIGN_BYTES - 1L) and (STAGING_ALIGN_BYTES - 1L).inv()
    }
}

private const val STAGING_ALIGN_BYTES = 16L
