/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.MASK_TARGET_LAYER_COUNT
import com.awakekt.awake.render.pipeline.BindingSemantic
import io.ygdrasil.webgpu.GPUCommandEncoder

/**
 * Sizes the scene-depth and mask targets to the scene a presented frame draws,
 * [Renderer.surfaceWidth] by [Renderer.surfaceHeight] after the render scale, before the frame is
 * recorded (#1709). Both were fixed 2048 squares; sizing them in lockstep keeps fog's
 * mask-vs-scene-depth silhouette comparison aligned at one texel grid while cutting both to the
 * scaled scene extent.
 * See [com.awakekt.awake.render.passes.MaskTargetSizing] for when they grow and shrink.
 */
internal fun Renderer.settleMaskTarget() {
    val current = currentScreenDepthEdge() ?: return
    resizeScreenDepthTargets(maskSizing.presented(current, surfaceWidth, surfaceHeight))
}

/** Grows the scene-depth and mask targets for an offscreen render [width] x [height] pixels, never shrinking them. */
internal fun Renderer.ensureMaskTarget(width: Int, height: Int) {
    val current = currentScreenDepthEdge() ?: return
    resizeScreenDepthTargets(maskSizing.offscreen(current, width, height))
}

/**
 * Records [input]'s mask sub-passes, which clear the layers they leave undrawn. On a frame with
 * nothing masked nothing is recorded, as the overlay reading the mask draws nothing then, unless
 * the texture is new: fog reads the mask every frame, and a new texture is zero-filled, which reads
 * as covered everywhere.
 */
internal fun Renderer.recordMaskPass(encoder: GPUCommandEncoder, input: GpuPassInput) {
    val pass = maskPass ?: return
    if (input.maskPasses.isNotEmpty()) {
        pass.recordCommands(encoder, input.maskPasses, input.environment)
        maskCleared = true
    } else if (!maskCleared) {
        pass.clearLayers(encoder)
        maskCleared = true
    }
}

private fun Renderer.currentScreenDepthEdge(): Int? =
    sceneDepthPass?.depthTarget?.size ?: maskPass?.depthTarget?.size

/**
 * Reallocates the scene-depth and mask targets at [edge] when either differs. Old textures are
 * closed at once, as WebGPU lets submitted work finish; cached depth bind groups are rebuilt over
 * the new views, as the shadow array rebuilds its groups.
 */
private fun Renderer.resizeScreenDepthTargets(edge: Int) {
    val sceneTarget = sceneDepthPass?.depthTarget
    if (sceneTarget != null && sceneTarget.size != edge) {
        sceneTarget.reallocate(1, edge)
        rebuildDepthBindGroups(BindingSemantic.SceneDepth, bufferPools.sceneDepthBindGroupSlots, sceneTarget)
    }
    val maskTarget = maskPass?.depthTarget
    if (maskTarget != null && maskTarget.size != edge) {
        maskTarget.reallocate(MASK_TARGET_LAYER_COUNT, edge)
        maskCleared = false
        rebuildDepthBindGroups(BindingSemantic.MaskDepth, bufferPools.maskBindGroupSlots, maskTarget)
    }
}

private fun Renderer.rebuildDepthBindGroups(
    semantic: BindingSemantic,
    slots: Map<io.ygdrasil.webgpu.GPURenderPipeline, Int>,
    target: com.awakekt.awake.webgpu.texture.DepthTarget,
) {
    val groups = bufferPools.shadowBindGroups[semantic].orEmpty()
    for ((pipeline, handle) in groups) {
        val slot = slots[pipeline] ?: continue
        val old = handle.bindGroup
        handle.bindGroup = bufferPools.depthBindGroup(pipeline, slot, target)
        old.close()
    }
}
