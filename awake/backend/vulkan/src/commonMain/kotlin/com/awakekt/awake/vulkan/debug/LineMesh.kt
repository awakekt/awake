/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.debug

import com.awakekt.awake.render.passes.debug.DebugLineLayout
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.handles.BufferHandle
import com.awakekt.awake.vulkan.handles.DeviceMemoryHandle
import com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkBufferUsageFlagBits
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo
import com.awakekt.awake.vulkan.pipeline.VulkanBufferBinding

/**
 * A world-space `LINE_LIST` vertex buffer rewritten every frame -- same HOST_VISIBLE
 * rewrite-every-frame lifecycle as `ui.DynamicMesh`, but no index buffer at all: each
 * consecutive pair of vertices *is* one line segment under `VK_PRIMITIVE_TOPOLOGY_LINE_LIST`,
 * so there's nothing an index buffer would deduplicate.
 *
 * [initialLines] is a starting size, not a limit. A slot whose frame needs more than it holds
 * reallocates, because debug line counts are a property of what a producer has to say this frame
 * -- a navigation grid draws two lines per blocked sample, a frustum twelve -- and no single fixed
 * number is right for both. The growth rule itself is [DebugLineLayout.grownVertexCapacity], not
 * restated here.
 */
class LineMesh(
    private val graphicsDevice: GraphicsDevice,
    initialLines: Int = DebugLineLayout.INITIAL_LINES,
    private val framesInFlight: Int = 1,
) {
    private val device get() = graphicsDevice.device
    private val physicalDevice get() = graphicsDevice.physicalDevice

    /**
     * One frame slot's buffer. Mutable because [growTo] replaces the buffer in place, and
     * [binding] is stored rather than computed because `VulkanBufferBinding` allocates a
     * `LongArray` -- rebuilding it per frame is the garbage this class already avoided.
     */
    private class FrameResources(
        var vertexBuffer: BufferHandle,
        var vertexBufferMemory: DeviceMemoryHandle,
        var capacityVertices: Int,
    ) {
        var binding = VulkanBufferBinding(vertexBuffer.handle)
        var vertexCount: Int = 0
    }

    private val frameResources: Array<FrameResources>
    private var activeFrameIndex: Int = 0

    /** How many vertices this frame's [update] actually wrote -- [draw] only draws this
     * many, not the full buffer capacity. */
    val vertexCount: Int
        get() = frameResources[activeFrameIndex].vertexCount

    init {
        require(framesInFlight > 0) { "framesInFlight must be positive." }
        // framesInFlight + 1: the offscreen path indexes one past the last frame, so an
        // offscreen render never stages over geometry a queued frame still refers to. Same
        // reason and same spelling as PerFrameUniformSlots.
        require(initialLines > 0) { "initialLines must be positive; was $initialLines." }
        frameResources = Array(framesInFlight + OFFSCREEN_FRAMES) {
            allocate(initialLines * VERTICES_PER_LINE)
        }
    }

    /** Current capacity of [frameIndex]'s buffer, in whole lines. Grows; never shrinks. */
    fun capacityLines(frameIndex: Int): Int =
        resourcesFor(frameIndex).capacityVertices / VERTICES_PER_LINE

    /**
     * Replaces [frame]'s buffer with one large enough for [neededVertices].
     *
     * Destroying the old buffer needs no device-wide wait: `performDrawDebugLines` waits on this
     * slot's fence before writing it, so the GPU is provably finished with this slot's resources.
     * Other slots keep their own buffers and grow on their own next write.
     */
    private fun growTo(frame: FrameResources, neededVertices: Int) {
        val capacity = DebugLineLayout.grownVertexCapacity(frame.capacityVertices, neededVertices)
        VulkanBuffers.vkDestroyBuffer(device, frame.vertexBuffer.handle)
        VulkanBuffers.vkFreeMemory(device, frame.vertexBufferMemory.handle)
        val grown = allocate(capacity)
        frame.vertexBuffer = grown.vertexBuffer
        frame.vertexBufferMemory = grown.vertexBufferMemory
        frame.capacityVertices = grown.capacityVertices
        frame.binding = VulkanBufferBinding(grown.vertexBuffer.handle)
    }

    private fun allocate(capacityVertices: Int): FrameResources {
        val buffer = VulkanBuffers.vkCreateBuffer(
            device,
            VkBufferCreateInfo(
                size = capacityVertices.toLong() * FLOATS_PER_VERTEX * Float.SIZE_BYTES,
                usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
            ),
        )
        val requirements = VulkanBuffers.vkGetBufferMemoryRequirements(device, buffer)
        val memoryTypeIndex = VulkanBuffers.findMemoryType(
            physicalDevice,
            requirements.memoryTypeBits,
            VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or
                VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
        )
        val memory = VulkanBuffers.vkAllocateMemory(
            device,
            VkMemoryAllocateInfo(allocationSize = requirements.size, memoryTypeIndex = memoryTypeIndex),
        )
        VulkanBuffers.vkBindBufferMemory(device, buffer, memory, 0)
        return FrameResources(BufferHandle(buffer), DeviceMemoryHandle(memory), capacityVertices)
    }

    /** Overwrites this frame's vertex contents. [vertices] must be at most
     * `maxLines * VERTICES_PER_LINE * FLOATS_PER_VERTEX` floats -- callers build this from
     * `LineSegment`s (3 floats position + 4 floats color per vertex, 2 vertices per line).
     *
     * Called unconditionally every frame regardless of whether any debug lines (grid,
     * frustum) are actually toggled on -- an empty [vertices] is a legitimate "nothing to
     * draw" call, not a caller bug. Skipping the GPU write in that case isn't just an
     * optimization: `vkMapMemory`/`vkUnmapMemory` with a 0-byte range is invalid per the
     * Vulkan spec, and MoltenVK's `vkUnmapMemory` throws `VK_ERROR_MEMORY_MAP_FAILED`
     * ("Memory is not mapped") for it every single frame -- same bug `ui.DynamicMesh.update`
     * already guards against, this mesh just never got the same fix. */
    fun update(vertices: FloatArray) = update(frameIndex = 0, vertices = vertices)

    fun update(frameIndex: Int, vertices: FloatArray) {
        val frame = resourcesFor(frameIndex)
        val neededVertices = vertices.size / FLOATS_PER_VERTEX
        if (neededVertices > frame.capacityVertices) growTo(frame, neededVertices)
        activeFrameIndex = frameIndex
        frame.vertexCount = neededVertices
        if (vertices.isEmpty()) return
        VulkanBuffers.writeBufferMemoryFloats(device, frame.vertexBufferMemory.handle, 0, vertices)
    }

    /** This frame slot's vertex buffer, for the shared opaque feature to bind at binding 0. */
    fun binding(frameIndex: Int): VulkanBufferBinding = resourcesFor(frameIndex).binding

    /** [vertexCount] for an explicit frame slot -- the active-slot property reads whichever slot
     * [update] last wrote, which is not necessarily the slot being recorded. */
    fun vertexCount(frameIndex: Int): Int = resourcesFor(frameIndex).vertexCount

    fun bind(commandBuffer: Long) = bind(activeFrameIndex, commandBuffer)

    fun bind(frameIndex: Int, commandBuffer: Long) {
        val frame = resourcesFor(frameIndex)
        VulkanBuffers.vkCmdBindVertexBuffers(
            commandBuffer,
            0,
            longArrayOf(frame.vertexBuffer.handle),
            longArrayOf(0L),
        )
    }

    fun draw(commandBuffer: Long) = draw(activeFrameIndex, commandBuffer)

    fun draw(frameIndex: Int, commandBuffer: Long) {
        val frame = resourcesFor(frameIndex)
        if (frame.vertexCount == 0) return
        Vulkan.vkCmdDraw(commandBuffer, frame.vertexCount, 1, 0, 0)
    }

    fun destroy() {
        frameResources.forEach { frame ->
            VulkanBuffers.vkDestroyBuffer(device, frame.vertexBuffer.handle)
            VulkanBuffers.vkFreeMemory(device, frame.vertexBufferMemory.handle)
        }
    }

    private fun resourcesFor(frameIndex: Int): FrameResources {
        require(frameIndex in frameResources.indices) {
            "LineMesh frame index $frameIndex is outside 0..${frameResources.lastIndex}."
        }
        return frameResources[frameIndex]
    }

    companion object {
        /** The single extra frame slot the offscreen path uses; see [init]. */
        const val OFFSCREEN_FRAMES = 1

        /** pos (vec3) + color (vec4) -- see `debug_line.vert`. */
        /** Aliased, not re-declared: a second literal is exactly the stride drift Phase 1 hit
         * on rounded quads (webgpu had 15 where the shared truth was 16). */
        val FLOATS_PER_VERTEX = DebugLineLayout.FLOATS_PER_VERTEX
        const val VERTICES_PER_LINE = DebugLineLayout.VERTICES_PER_LINE
    }
}
