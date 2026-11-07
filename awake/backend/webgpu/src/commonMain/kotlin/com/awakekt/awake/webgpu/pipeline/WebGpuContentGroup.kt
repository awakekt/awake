/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.renderer.UniformWriter
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.fastArrayBufferOf
import com.awakekt.awake.webgpu.texture.Texture
import com.awakekt.awake.webgpu.writeBufferData
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBufferUsage

/**
 * One content feature's own group 0 over a [pipeline] other features draw through too: a uniform
 * buffer of its own, and a bind group of that buffer and [textures] against the pipeline's group-0
 * layout. Vulkan's counterpart is `RenderPipeline.newContentGroup`.
 *
 * The bind group is built on first bind and again after a shader swap replaced the pipeline, as
 * the pipeline's own group is. The textures are the caller's; [destroy] frees the rest.
 */
internal class WebGpuContentGroup(
    graphicsDevice: GraphicsDevice,
    private val pipeline: RenderPipeline,
    private val layout: UniformLayout,
    private val textures: Map<Int, Texture>,
    private val samplers: Map<Int, Int>,
) : UniformBlock {
    private val device = graphicsDevice.wgpuContext.device
    private val buffer = device.createBuffer(
        BufferDescriptor(
            size = (layout.total * Float.SIZE_BYTES).toULong(),
            usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
        ),
    )
    private var bindGroup: WebGpuBindGroupHandle? = null

    /** The pipeline [bindGroup] was built for; a swap gives the wrapper a new one. */
    private var builtFor = NOT_BUILT

    init {
        // Built now, so a missing texture fails the attach rather than the first frame.
        var built = false
        try {
            binding(0)
            built = true
        } finally {
            if (!built) buffer.close()
        }
    }

    override fun binding(frameIndex: Int): MaterialBinding {
        val current = pipeline.graphicsPipeline[0]
        bindGroup?.takeIf { builtFor == current }?.let { return it }
        bindGroup?.bindGroup?.close()
        val group = WebGpuBindGroupHandle(
            device.createBindGroupReleasingLayout(
                BindGroupDescriptor(
                    layout = pipeline.groupZeroLayout,
                    entries = pipeline.contentBindGroupEntries(buffer, textures, samplers),
                ),
            ),
        )
        bindGroup = group
        builtFor = current
        return group
    }

    override fun write(frameIndex: Int, fill: UniformWriter.() -> Unit) {
        val floats = UniformWriter(layout).apply(fill).build()
        device.queue.writeBufferData(buffer, 0uL, fastArrayBufferOf(floats))
    }

    /** Call once, after the last frame that binds it. */
    fun destroy() {
        bindGroup?.bindGroup?.close()
        bindGroup = null
        buffer.close()
    }

    private companion object {
        const val NOT_BUILT = -1L
    }
}
