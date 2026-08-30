/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.ui

import io.github.awakelab.awake.core.geometry.toByteArrayLE
import io.github.awakelab.awake.vulkan.device.GraphicsDevice
import io.github.awakelab.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import io.github.awakelab.awake.vulkan.gen.VulkanBuffers
import io.github.awakelab.awake.vulkan.handles.BufferHandle
import io.github.awakelab.awake.vulkan.handles.DeviceMemoryHandle
import io.github.awakelab.awake.vulkan.models.info.VkBufferCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkBufferUsageFlagBits
import io.github.awakelab.awake.vulkan.models.info.VkIndexType
import io.github.awakelab.awake.vulkan.models.info.VkMemoryAllocateInfo

/**
 * A vertex+index buffer rewritten every frame -- unlike [io.github.awakelab.awake
 * .vulkan.mesh.Mesh] (built once, DEVICE_LOCAL, uploaded via a one-time staging-buffer copy,
 * no update path), this is HOST_VISIBLE|HOST_COHERENT so [update] can write straight into it
 * each frame with no staging buffer and no staging copy. [maxQuads] is the size it starts at,
 * not a limit: [update] grows this frame's buffers when a run does not fit and keeps the larger
 * size, so after the first frames that draw a given shape nothing reallocates again.
 *
 * Growing rather than requiring a caller-picked ceiling because the ceiling's failure was silent
 * and cliff-shaped. At a fixed 256 quads a run held 1,024 vertices while an icon tessellated to
 * ~1,500, so every icon was re-split and re-indexed on the way to the GPU, every frame -- 3.2 ms
 * of a frame against 0.2 ms, with nothing to indicate it. A buffer that fits what it is given
 * cannot fall off that cliff, and a small UI no longer pays for a large one's ceiling.
 *
 * A distinct class from `Mesh` rather than an `update()` bolted onto it: `Mesh`'s DEVICE_LOCAL
 * + one-time-copy lifecycle and this class's HOST_VISIBLE + rewrite-every-frame lifecycle are
 * different contracts serving different callers (static scene geometry vs. a UI overlay whose
 * quads move every frame) -- merging them would force one class to carry two lifecycle
 * branches neither caller needs to know about.
 */
class DynamicMesh(
    private val graphicsDevice: GraphicsDevice,
    private val maxQuads: Int,
    /** Floats per vertex -- 6 for colored quads (pos2+color4, see `ui_quad.vert`), 8 for
     * textured glyph quads (pos2+uv2+color4, see `ui_glyph.vert`). Parameterized (not a
     * fixed companion constant) so this one class serves both vertex layouts. */
    private val floatsPerVertex: Int = FLOATS_PER_VERTEX,
    private val framesInFlight: Int = 1,
) {
    private val device get() = graphicsDevice.device
    private val physicalDevice get() = graphicsDevice.physicalDevice

    /**
     * How many times a buffer has been reallocated to fit a run.
     *
     * Steady state is zero: capacity converges to the largest run this mesh has ever carried.
     * A count that keeps climbing means content is growing every frame, which is a real defect
     * and the reason this is observable rather than silent.
     */
    var growthCount: Int = 0
        private set

    private data class FrameResources(
        var vertexBuffer: BufferHandle,
        var vertexBufferMemory: DeviceMemoryHandle,
        var indexBuffer: BufferHandle,
        var indexBufferMemory: DeviceMemoryHandle,
        /** Capacity belongs to one frame slot. A slot only grows after its own fence is waited. */
        var maxVertices: Int,
        var maxIndices: Int,
        var drawIndexCount: Int = 0,
    )

    private val frameResources: Array<FrameResources>
    private var activeFrameIndex: Int = 0

    /** How many indices this frame's [update] actually wrote -- [draw] only draws this many,
     * not the full buffer capacity. */
    val drawIndexCount: Int
        get() = frameResources[activeFrameIndex].drawIndexCount

    init {
        require(framesInFlight > 0) { "framesInFlight must be positive." }
        frameResources = Array(framesInFlight) {
            val initialVertices = maxQuads * VERTICES_PER_QUAD
            val initialIndices = maxQuads * INDICES_PER_QUAD
            val (vBuffer, vMemory) = allocateHostVisibleBuffer(
                byteSize = (initialVertices * floatsPerVertex * Float.SIZE_BYTES).toLong(),
                usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
            )
            val (iBuffer, iMemory) = allocateHostVisibleBuffer(
                byteSize = (initialIndices * Int.SIZE_BYTES).toLong(),
                usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
            )
            FrameResources(
                vertexBuffer = BufferHandle(vBuffer),
                vertexBufferMemory = DeviceMemoryHandle(vMemory),
                indexBuffer = BufferHandle(iBuffer),
                indexBufferMemory = DeviceMemoryHandle(iMemory),
                maxVertices = initialVertices,
                maxIndices = initialIndices,
            )
        }
    }

    /** Overwrites this frame's vertex/index contents. [vertices] must be at most
     * `maxQuads * VERTICES_PER_QUAD * FLOATS_PER_VERTEX` floats -- callers build this from
     * [io.github.awakelab.awake.core.graphics2d.UiDrawPrimitive.Quad]s (2 floats position + 4 floats
     * color per vertex).
     *
     * Called unconditionally every frame by `Renderer.drawUi` regardless of whether this
     * frame actually has any quads for this mesh (e.g. `uiGlyphMesh` when a scene's
     * `onDrawUi` never calls `UiContext.text`) -- a 0-length [indices] is a legitimate,
     * common "nothing to draw" call, not a caller bug, so it's handled here rather than
     * pushing an empty-check onto every caller. Skipping the GPU write in that case isn't
     * just an optimization: `vkMapMemory`/`vkUnmapMemory` with a 0-byte range is invalid
     * per the Vulkan spec, and MoltenVK's `vkUnmapMemory` throws `VK_ERROR_MEMORY_MAP_FAILED`
     * ("Memory is not mapped") for it, since the preceding 0-size `vkMapMemory` never
     * actually establishes a mapping. */
    fun update(vertices: FloatArray, indices: IntArray) = update(frameIndex = 0, vertices = vertices, indices = indices)

    fun update(frameIndex: Int, vertices: FloatArray, indices: IntArray) {
        val frame = resourcesFor(frameIndex)
        growTo(frame, vertexFloats = vertices.size, indexCount = indices.size)
        activeFrameIndex = frameIndex
        frame.drawIndexCount = indices.size
        if (indices.isEmpty()) return
        VulkanBuffers.writeBufferMemoryFloats(device, frame.vertexBufferMemory.handle, 0, vertices)
        VulkanBuffers.writeBufferMemoryBytes(device, frame.indexBufferMemory.handle, 0, indices.toByteArrayLE())
    }

    /**
     * Reallocates this slot's buffers when a run does not fit, and leaves them at the new size.
     *
     * Each frame slot owns its capacity. The renderer waits for that slot's fence before calling
     * [update], so the GPU is finished with these handles; submitted sibling slots retain their
     * own buffers and continue without a device-wide stall. A previous shared-capacity design
     * replaced every slot at once, which required `vkDeviceWaitIdle` to avoid freeing queued
     * resources. Keeping capacity per slot restores the narrower, fence-proven lifetime.
     *
     * Capacity doubles past what was asked for rather than fitting exactly, so a caller whose
     * content creeps up by one vertex a frame reallocates a handful of times rather than every
     * frame.
     */
    private fun growTo(frame: FrameResources, vertexFloats: Int, indexCount: Int) {
        val neededVertices = (vertexFloats + floatsPerVertex - 1) / floatsPerVertex
        if (neededVertices <= frame.maxVertices && indexCount <= frame.maxIndices) return

        val grownVertices = maxOf(frame.maxVertices * 2, neededVertices)
        val grownIndices = maxOf(frame.maxIndices * 2, indexCount)
        growthCount += 1

        VulkanBuffers.vkDestroyBuffer(device, frame.vertexBuffer.handle)
        VulkanBuffers.vkFreeMemory(device, frame.vertexBufferMemory.handle)
        VulkanBuffers.vkDestroyBuffer(device, frame.indexBuffer.handle)
        VulkanBuffers.vkFreeMemory(device, frame.indexBufferMemory.handle)
        val (vBuffer, vMemory) = allocateHostVisibleBuffer(
            byteSize = (grownVertices * floatsPerVertex * Float.SIZE_BYTES).toLong(),
            usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
        )
        val (iBuffer, iMemory) = allocateHostVisibleBuffer(
            byteSize = (grownIndices * Int.SIZE_BYTES).toLong(),
            usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
        )
        frame.vertexBuffer = BufferHandle(vBuffer)
        frame.vertexBufferMemory = DeviceMemoryHandle(vMemory)
        frame.indexBuffer = BufferHandle(iBuffer)
        frame.indexBufferMemory = DeviceMemoryHandle(iMemory)
        frame.maxVertices = grownVertices
        frame.maxIndices = grownIndices
        frame.drawIndexCount = 0
    }

    fun bind(commandBuffer: Long) = bind(activeFrameIndex, commandBuffer)

    fun bind(frameIndex: Int, commandBuffer: Long) {
        val frame = resourcesFor(frameIndex)
        VulkanBuffers.vkCmdBindVertexBuffers(
            commandBuffer,
            0,
            longArrayOf(frame.vertexBuffer.handle),
            longArrayOf(0L),
        )
        VulkanBuffers.vkCmdBindIndexBuffer(
            commandBuffer,
            frame.indexBuffer.handle,
            0,
            VkIndexType.VK_INDEX_TYPE_UINT32,
        )
    }

    fun draw(commandBuffer: Long) = draw(activeFrameIndex, commandBuffer)

    fun draw(frameIndex: Int, commandBuffer: Long) {
        val frame = resourcesFor(frameIndex)
        if (frame.drawIndexCount == 0) return
        VulkanBuffers.vkCmdDrawIndexed(commandBuffer, frame.drawIndexCount, 1, 0, 0, 0)
    }

    fun destroy() {
        frameResources.forEach { frame ->
            VulkanBuffers.vkDestroyBuffer(device, frame.vertexBuffer.handle)
            VulkanBuffers.vkFreeMemory(device, frame.vertexBufferMemory.handle)
            VulkanBuffers.vkDestroyBuffer(device, frame.indexBuffer.handle)
            VulkanBuffers.vkFreeMemory(device, frame.indexBufferMemory.handle)
        }
    }

    private fun resourcesFor(frameIndex: Int): FrameResources {
        require(frameIndex in frameResources.indices) {
            "DynamicMesh frame index $frameIndex is outside 0..${frameResources.lastIndex}."
        }
        return frameResources[frameIndex]
    }

    private fun allocateHostVisibleBuffer(byteSize: Long, usage: Int): Pair<Long, Long> {
        val buffer = VulkanBuffers.vkCreateBuffer(device, VkBufferCreateInfo(size = byteSize, usage = usage))
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
        return buffer to memory
    }

    companion object {
        /** Default (colored-quad) layout: pos (vec2) + color (vec4) + transform (vec4:
         * scale.xy + pivot.xy, see `UiPrimitiveTransform`) -- see `ui_quad.vert`. */
        const val FLOATS_PER_VERTEX = io.github.awakelab.awake.core.geometry.VertexFormats2D.FLOATS_PER_VERTEX
        const val GLYPH_FLOATS_PER_VERTEX = io.github.awakelab.awake.core.geometry.VertexFormats2D.GLYPH_FLOATS_PER_VERTEX

        /** pos(vec2) + localPos(vec2) + halfSize(vec2) + radius(float) + smoothing(float) + color(vec4) +
         * transform(vec4) -- see `ui_rounded_quad.vert`. */
        const val ROUNDED_QUAD_FLOATS_PER_VERTEX = io.github.awakelab.awake.core.geometry.VertexFormats2D.ROUNDED_QUAD_FLOATS_PER_VERTEX
        const val VERTICES_PER_QUAD = io.github.awakelab.awake.core.geometry.VertexFormats2D.VERTICES_PER_QUAD
        const val INDICES_PER_QUAD = io.github.awakelab.awake.core.geometry.VertexFormats2D.INDICES_PER_QUAD
    }
}
