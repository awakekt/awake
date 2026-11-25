/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.pipeline.InstancedDrawKind
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.VulkanBufferBinding
import com.awakekt.awake.vulkan.renderer.Renderer as VulkanRenderer

/**
 * The per-instance colour buffer binding [cmd] draws with: a particle's colours, or, on a pipeline
 * that reads a tint per instance, the copies' tints and fades, sharing one immutable all-white
 * buffer whenever the draw carries no per-instance colours. Null for any other draw.
 */
internal fun VulkanRenderer.instanceColorsFor(
    cmd: GpuDrawRequest,
    kind: InstancedDrawKind,
    pipeline: RenderPipeline,
    frameIndex: Int,
    alphaInstancedIndex: Int,
): VulkanBufferBinding? = when {
    kind == InstancedDrawKind.Particle -> {
        val colors = cmd.instanceColors
        if (colors.isNullOrEmpty()) {
            bufferPools.whiteInstanceColorBinding
        } else {
            alphaInstanceBufferForRun(alphaInstancedIndex).also { it.update(frameIndex, colors) }.binding(frameIndex)
        }
    }
    kind == InstancedDrawKind.Plain && pipeline.readsInstanceTint -> {
        val colors = cmd.instanceColors
        if (colors.isNullOrEmpty()) {
            bufferPools.whiteInstanceColorBinding
        } else {
            alphaInstanceBufferForRun(alphaInstancedIndex).also { it.update(frameIndex, colors) }.binding(frameIndex)
        }
    }
    else -> null
}

/**
 * The per-instance sprite-strip frame buffer binding [cmd] draws with, sharing one immutable
 * all-zero buffer when a particle draw omits frame indices. Null for non-particle draws.
 */
internal fun VulkanRenderer.instanceFramesFor(
    cmd: GpuDrawRequest,
    kind: InstancedDrawKind,
    frameIndex: Int,
    frameInstancedIndex: Int,
): VulkanBufferBinding? = if (kind == InstancedDrawKind.Particle) {
    val instanceFrames = cmd.instanceFrames
    if (instanceFrames.isNullOrEmpty()) {
        bufferPools.zeroInstanceFrameBinding
    } else {
        frameInstanceBufferForRun(frameInstancedIndex).also { it.update(frameIndex, instanceFrames) }.binding(frameIndex)
    }
} else {
    null
}
