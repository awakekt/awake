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
 * Sizes the mask target to the scene a presented frame draws, [Renderer.surfaceWidth] by
 * [Renderer.surfaceHeight] after the render scale, before the frame is recorded (#1709). It was a
 * fixed 2048 square, cleared and stored in full on every masked frame whatever the scene's size.
 * See [com.awakekt.awake.render.passes.MaskTargetSizing] for when it grows and shrinks.
 */
internal fun Renderer.settleMaskTarget() {
    val target = maskPass?.depthTarget ?: return
    resizeMaskTarget(maskSizing.presented(target.size, surfaceWidth, surfaceHeight))
}

/** Grows the mask target for an offscreen render [width] x [height] pixels, never shrinking it. */
internal fun Renderer.ensureMaskTarget(width: Int, height: Int) {
    val target = maskPass?.depthTarget ?: return
    resizeMaskTarget(maskSizing.offscreen(target.size, width, height))
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

/**
 * Reallocates the mask's layers at [edge] when that differs. The old texture is closed at once, as
 * WebGPU lets submitted work finish; each cached mask group is rebuilt over the new view, as
 * the shadow array rebuilds its groups.
 */
private fun Renderer.resizeMaskTarget(edge: Int) {
    val target = maskPass?.depthTarget ?: return
    if (target.size == edge) return
    target.reallocate(MASK_TARGET_LAYER_COUNT, edge)
    maskCleared = false
    val groups = bufferPools.shadowBindGroups[BindingSemantic.MaskDepth].orEmpty()
    for ((pipeline, handle) in groups) {
        val slot = bufferPools.maskBindGroupSlots[pipeline] ?: continue
        val old = handle.bindGroup
        handle.bindGroup = bufferPools.depthBindGroup(pipeline, slot, target)
        old.close()
    }
}
