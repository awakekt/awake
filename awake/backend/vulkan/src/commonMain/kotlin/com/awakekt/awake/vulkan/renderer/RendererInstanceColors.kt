/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.pipeline.InstancedDrawKind
import com.awakekt.awake.vulkan.mesh.AlphaInstanceBuffer
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.renderer.Renderer as VulkanRenderer

/**
 * The per-instance colour buffer [cmd] draws with: a particle's colours, or, on a pipeline that reads a
 * tint per instance, the copies' tints and fades, white where it carries none. Null for any other draw.
 */
internal fun VulkanRenderer.instanceColorsFor(
    cmd: GpuDrawRequest,
    kind: InstancedDrawKind,
    pipeline: RenderPipeline,
    frameIndex: Int,
    instancedIndex: Int,
): AlphaInstanceBuffer? = when {
    kind == InstancedDrawKind.Particle ->
        alphaInstanceBufferForRun(instancedIndex).also { it.update(frameIndex, cmd.instanceColors.orEmpty()) }
    kind == InstancedDrawKind.Plain && pipeline.readsInstanceTint -> alphaInstanceBufferForRun(instancedIndex).also { buffer ->
        cmd.instanceColors?.let { buffer.update(frameIndex, it) } ?: buffer.fillWhite(frameIndex)
    }
    else -> null
}
