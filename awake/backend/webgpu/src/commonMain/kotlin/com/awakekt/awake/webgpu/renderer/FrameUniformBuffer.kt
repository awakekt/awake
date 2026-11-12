/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.passes.uniforms.FrameUniformBlock
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.identityFrameBlock
import com.awakekt.awake.render.pipeline.FRAME_UNIFORM_BINDING
import com.awakekt.awake.webgpu.device.GraphicsDevice
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice

/**
 * The device's frame blocks: what the camera, the light and the shadow cascades decide for every
 * lit draw of a batch, in a buffer each of those draws' material groups binds beside its own
 * block, at [FRAME_UNIFORM_BINDING].
 *
 * Written once per batch, and only when it changed, so a camera that moves uploads this block
 * and not a block per draw. One buffer serves every batch because a batch's writes land before
 * it records, as its draws' own blocks do.
 *
 * Two, because a batch's wireframe-overlay edges are a pass of their own within it: they draw
 * through a view-projection pulled toward the eye and in the edge debug code, so an edge
 * pipeline's groups bind the second.
 *
 * And a third that never changes, [identityFrameBlock]: what a camera-depth pass binds for a draw
 * whose single block starts with its whole MVP.
 */
internal class FrameUniformBuffer(private val device: GPUDevice) {
    private val scene = Block()
    private val edges = Block()
    private val identity = Block()
    private val identityFloats = identityFrameBlock()

    /** What a camera-depth group binds at [FRAME_UNIFORM_BINDING] for a draw whose block starts with its MVP. */
    val identityBuffer: GPUBuffer get() = identity.slot.buffer

    /** Stages the identity block the first time; nothing after. */
    fun stageIdentity(uploads: UniformUploads) {
        if (identity.slot.resident == null) uploads.stageChanged(identity.slot, identityFloats)
    }

    /** What a material group binds at [FRAME_UNIFORM_BINDING]: the batch's block, or its [edges]'. */
    fun buffer(edges: Boolean = false): GPUBuffer = block(edges).slot.buffer

    /**
     * Stages [context]'s frame block, as the batch's or as its [edges]', unless the buffer already
     * holds it; returns whether it staged.
     */
    fun stage(uploads: UniformUploads, context: GpuDrawPreparationContext, cascades: GpuShadowCascadeData?, edges: Boolean = false): Boolean {
        val environment = context.environment
        val target = block(edges)
        val floats = target.packer.pack(
            viewProjection = context.viewProjection,
            cameraEye = context.cameraEye,
            cameraForward = context.cameraForward,
            lightPayload = context.passUniforms,
            shadowCascades = cascades,
            fogColor = environment.fogColor,
            fogDensity = environment.fogDensity,
            debugView = environment.debugView,
            exposure = environment.exposure,
        )
        return uploads.stageChanged(target.slot, floats)
    }

    private fun block(edges: Boolean): Block = if (edges) this.edges else scene

    fun close() {
        scene.slot.close()
        edges.slot.close()
        identity.slot.close()
    }

    private inner class Block {
        val slot = UniformSlot(
            device.createBuffer(
                BufferDescriptor(
                    size = (MaterialUniformLayouts.Frame.total * Float.SIZE_BYTES).toULong(),
                    usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                ),
            ),
            binding = null,
        )
        val packer = FrameUniformBlock()
    }
}

/**
 * The buffer a material group's uniform [binding] reads: a frame block's at
 * [FRAME_UNIFORM_BINDING], the [edges]' for a wireframe-overlay edge pipeline, else the [draw]'s own.
 */
internal fun GraphicsDevice.uniformBufferAt(binding: Int, draw: GPUBuffer, edges: Boolean = false): GPUBuffer =
    if (binding == FRAME_UNIFORM_BINDING) frameUniforms.buffer(edges) else draw
