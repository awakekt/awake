/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.mesh

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.toByteArrayLE
import com.awakekt.awake.render.mesh.DynamicMesh
import com.awakekt.awake.render.mesh.requireDynamicMeshShape
import com.awakekt.awake.render.mesh.requireVertexRange
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.models.info.VkBufferUsageFlagBits
import com.awakekt.awake.vulkan.pipeline.VulkanBufferBinding

/**
 * A scene mesh whose vertices are rewritten in place: Vulkan's [DynamicMesh].
 *
 * Frames overlap, so the GPU may still be reading one frame's vertices while the next is prepared.
 * Each frame in flight therefore has its own HOST_VISIBLE vertex buffer, and the mesh keeps the
 * current vertices on the CPU and, per frame slot, the range written since that slot was last brought
 * up to date. [prepare] runs once the slot's fence has been waited and copies that range alone, so a
 * write reaches every slot without a slot being written while the GPU reads it.
 *
 * A range goes up as one power-of-two window around it, taken from [scratch], because the native
 * write copies a whole array: the window is at most twice the range, and nothing is allocated once
 * the scratch has its sizes. The vertex buffers are sized to a power of two so every window fits. The
 * index buffer never changes and is written once.
 */
internal class VulkanDynamicMesh(
    private val graphicsDevice: GraphicsDevice,
    override val format: VertexFormat,
    override val vertexCapacity: Int,
    indices: IntArray,
    framesInFlight: Int,
    private val scratch: PowerOfTwoFloats,
) : DynamicMesh, SceneMeshBuffers {
    private val device get() = graphicsDevice.device
    private val stride = format.strideFloats
    private val bufferFloats = PowerOfTwoFloats.ceil(vertexCapacity * stride)

    /** The current vertices; slot buffers are brought up to this. */
    private val current = FloatArray(bufferFloats)

    private val slots: Array<Slot>
    private val indexBuffer: HostBuffer

    /** The index buffer as the shared recording path's handle. */
    override val indexBinding: VulkanBufferBinding

    /** The vertex buffer of the slot [prepare] last brought up to date. */
    override var vertexBinding: VulkanBufferBinding
        private set

    /** A draw submits [drawIndexCount] indices. */
    override val indexCount: Int get() = drawIndexCount

    override val indexCapacity: Int = indices.size

    override var drawIndexCount: Int = 0
        set(value) {
            require(value in 0..indexCapacity) { "drawIndexCount must be in 0..$indexCapacity, not $value." }
            field = value
        }

    override val sizeBytes: Long

    init {
        requireDynamicMeshShape(vertexCapacity, indices)
        require(framesInFlight > 0) { "framesInFlight must be positive." }
        val vertexBytes = bufferFloats.toLong() * Float.SIZE_BYTES
        // Every slot starts wholly out of date, so its first prepare writes the zeros its memory lacks.
        slots = Array(framesInFlight) {
            Slot(HostBuffer.allocate(graphicsDevice, vertexBytes, VkBufferUsageFlagBits.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT))
        }
        val indexBytes = indices.toByteArrayLE()
        indexBuffer = HostBuffer.allocate(graphicsDevice, indexBytes.size.toLong(), VkBufferUsageFlagBits.VK_BUFFER_USAGE_INDEX_BUFFER_BIT)
        VulkanBuffers.writeBufferMemoryBytes(device, indexBuffer.memory, 0, indexBytes)
        indexBinding = VulkanBufferBinding(indexBuffer.buffer)
        vertexBinding = slots[0].binding
        sizeBytes = vertexBytes * framesInFlight + indexBytes.size
    }

    override fun writeVertices(vertices: FloatArray, firstVertex: Int, vertexCount: Int) {
        requireVertexRange(vertices, firstVertex, vertexCount)
        if (vertexCount == 0) return
        val start = firstVertex * stride
        val end = start + vertexCount * stride
        vertices.copyInto(current, destinationOffset = start, startIndex = start, endIndex = end)
        for (slot in slots) slot.widen(start, end)
    }

    /**
     * Brings [frameIndex]'s vertex buffer up to date and makes it [vertexBinding]. Call after the
     * slot's fence has been waited, before recording the frame's draws of this mesh.
     */
    fun prepare(frameIndex: Int) {
        require(frameIndex in slots.indices) { "Frame index $frameIndex is outside 0..${slots.lastIndex}." }
        val slot = slots[frameIndex]
        if (slot.dirtyEnd > slot.dirtyStart) {
            val window = scratch.atLeast(slot.dirtyEnd - slot.dirtyStart)
            val windowStart = minOf(slot.dirtyStart, bufferFloats - window.size)
            current.copyInto(window, startIndex = windowStart, endIndex = windowStart + window.size)
            VulkanBuffers.writeBufferMemoryFloats(device, slot.vertices.memory, windowStart.toLong() * Float.SIZE_BYTES, window)
            slot.dirtyStart = bufferFloats
            slot.dirtyEnd = 0
        }
        vertexBinding = slot.binding
    }

    override fun destroy() {
        // A submitted frame may still read a slot; device idle covers every queue, as Mesh.destroy does.
        VulkanBuffers.vkDeviceWaitIdle(device)
        slots.forEach { it.vertices.free(graphicsDevice) }
        indexBuffer.free(graphicsDevice)
    }

    /** One frame slot's vertex buffer and the float range not yet written to it. */
    private inner class Slot(val vertices: HostBuffer) {
        val binding = VulkanBufferBinding(vertices.buffer)
        var dirtyStart = 0
        var dirtyEnd = bufferFloats

        fun widen(start: Int, end: Int) {
            if (start < dirtyStart) dirtyStart = start
            if (end > dirtyEnd) dirtyEnd = end
        }
    }
}
