/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.mesh

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.putFloats
import com.awakekt.awake.render.passes.InstancePacker
import com.awakekt.awake.render.passes.InstanceWriter
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.pipeline.WebGpuBufferHandle
import com.awakekt.awake.webgpu.renderer.GpuBufferCreations
import com.awakekt.awake.webgpu.renderer.sameBitsAs
import com.awakekt.awake.webgpu.writeBufferFloats
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage

/**
 * The per-instance model matrices behind one instanced draw call -- bound as vertex buffer slot
 * 1, alongside the mesh's own slot 0 (see `RenderPipeline`'s `instanced` parameter, which
 * declares the matching `GPUVertexStepMode.Instance` layout). Mirrors Vulkan's `InstanceBuffer`;
 * thinner for the same reason this backend's `ui.DynamicMesh` is thinner than Vulkan's --
 * `queue.writeBuffer` is already safe to call every frame with no mapping ceremony.
 *
 * Rewritten wholesale every frame its models changed -- unlike this module's `Mesh`, which sizes itself to the exact
 * array it was constructed with and never updates. Grows to the largest set it has held, in powers
 * of two, rather than starting at [maxInstances]: a frame of batched props has one of these per
 * instanced draw, mostly small.
 */
class InstanceBuffer(
    private val graphicsDevice: GraphicsDevice,
    /** Hard ceiling on instances per draw call -- [update] fails loudly (rather than silently
     * truncating) with a message naming this knob. */
    private val maxInstances: Int = DEFAULT_MAX_INSTANCES,
) {
    /** Counts each buffer this creates after it is set, for the per-site creation gate (#1555). */
    internal var creations: GpuBufferCreations? = null

    private var capacity = MIN_CAPACITY.coerceAtMost(maxInstances)
    private var buffer: GPUBuffer = createBuffer(capacity)

    // Reused across frames so a steady instance count allocates nothing per frame.

    /** What [buffer] holds, as [update] last wrote it, or null when it holds nothing yet. */
    private var resident: FloatArray? = null
    private var residentCount = 0

    /** Packs [models] into this buffer. `Mat4.data` is already column-major, which is the order
     * `instanced.wgsl`'s 4 `vec4` attributes reassemble into a `mat4x4` -- a straight copy, no
     * transpose. */
    private val packer = InstancePacker<Mat4>(
        GpuDataShape.Mat4,
        "InstanceBuffer",
        write = InstanceWriter { out, offset, model ->
            out.putFloats(offset, model.data)
        },
    )

    /** Uploads [update] made, for tests: one a frame whose models changed, none for one whose held. */
    internal var uploads = 0L
        private set

    /**
     * Uploads this frame's per-instance model matrices, growing the buffer first when they do not
     * fit.
     *
     * Capacity grows in powers of two up to the construction-time ceiling and never shrinks. Growth
     * closes the old buffer and replaces [binding], so anything read from [bufferRef] or [binding]
     * before the call is stale afterwards. An empty list uploads nothing.
     *
     * @param models One model matrix per instance, column-major as [Mat4] stores them.
     * @throws IllegalArgumentException If [models] holds more entries than the ceiling set at
     * construction.
     */

    fun update(models: List<Mat4>) {
        val count = packer.packSlice(models, maxInstances)
        if (count == 0) return
        val floats = packer.buffer
        if (models.size > capacity) {
            while (capacity < models.size) capacity *= 2
            capacity = capacity.coerceAtMost(maxInstances)
            // Work already submitted keeps the old buffer alive until it finishes.
            buffer.close()
            buffer = createBuffer(capacity)
            binding = WebGpuBufferHandle(buffer)
            residentCount = 0
        }
        // Copies of a static prop hold still frame after frame: the buffer already has them (#1452).
        val cached = resident
        if (cached != null && residentCount == count && cached.sameBitsAs(floats, count)) return
        graphicsDevice.wgpuContext.device.queue.writeBufferFloats(buffer, 0uL, floats, count)
        uploads += 1
        val target = if (cached != null && cached.size >= count) cached else FloatArray(count).also { resident = it }
        floats.copyInto(target, 0, 0, count)
        residentCount = count
    }

    /**
     * Returns the current GPU vertex buffer bound at slot 1 for instanced draws. It is replaced
     * when [update] grows capacity, so fetch it per draw rather than caching it.
     */
    fun bufferRef(): GPUBuffer = buffer

    /** This buffer as the shared render layer's opaque handle -- built once per size, not per draw. */
    var binding: WebGpuBufferHandle = WebGpuBufferHandle(buffer)
        private set

    private fun createBuffer(instances: Int): GPUBuffer {
        creations?.let { it.instanceBuffers += 1 }
        return graphicsDevice.wgpuContext.device.createBuffer(
            BufferDescriptor(
                size = (instances * FLOATS_PER_INSTANCE * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
            ),
        )
    }

    /** Closes the current GPU buffer. Call once, after the last frame that draws with it. */
    fun destroy() {
        buffer.close()
    }

    /** Stride and capacity constants for the per-instance model-matrix stream. */
    companion object {
        /** One `mat4` per instance. */
        val FLOATS_PER_INSTANCE = GpuDataShape.Mat4.componentCount

        /** Same 4096 (256 KB) default as Vulkan's `InstanceBuffer` -- see its doc comment. */
        const val DEFAULT_MAX_INSTANCES = 4096

        private const val MIN_CAPACITY = 16
    }
}
