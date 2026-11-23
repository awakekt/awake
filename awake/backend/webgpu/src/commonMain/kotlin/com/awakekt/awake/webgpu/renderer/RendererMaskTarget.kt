/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.command.COVERING_EXEMPT_MASK_LAYER
import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.command.MASK_TARGET_LAYER_COUNT
import com.awakekt.awake.render.passes.maskExtentHeight
import com.awakekt.awake.render.passes.maskExtentWidth
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.webgpu.pipeline.resetLayerState
import com.awakekt.awake.webgpu.texture.DepthTarget
import io.ygdrasil.webgpu.GPUCommandEncoder

/**
 * Sizes the scene-depth and mask targets to the rectangular extent of the scene a presented frame
 * draws, [Renderer.surfaceWidth] by [Renderer.surfaceHeight] after the render scale, before the
 * frame is recorded (#1709). Sizing both axes independently in lockstep keeps fog's
 * mask-vs-scene-depth silhouette comparison aligned at one texel grid while cutting widescreen
 * frames (e.g. 1093x480 -> 1152x512) by 2.25x compared to a square target.
 * See [com.awakekt.awake.render.passes.MaskTargetSizing] for when they grow and shrink.
 */
internal fun Renderer.settleMaskTarget() {
    val current = currentScreenDepthTarget() ?: return
    val extent = maskSizing.presentedExtent(current.width, current.height, surfaceWidth, surfaceHeight)
    resizeScreenDepthTargets(maskExtentWidth(extent), maskExtentHeight(extent))
}

/** Grows the scene-depth and mask targets for an offscreen render [width] x [height] pixels, never shrinking them. */
internal fun Renderer.ensureMaskTarget(width: Int, height: Int) {
    val current = currentScreenDepthTarget() ?: return
    val extent = maskSizing.offscreenExtent(current.width, current.height, width, height)
    resizeScreenDepthTargets(maskExtentWidth(extent), maskExtentHeight(extent))
}

/**
 * Records the frame's mask sub-passes, or clears every layer once when the frame masks nothing
 * (`maskCleared`). A cleared target stays clear across unmasked frames, and is dirtied by any
 * frame that draws into it or reallocates it.
 */
internal fun Renderer.recordMaskPass(encoder: GPUCommandEncoder, input: GpuPassInput) {
    val pass = maskPass ?: return
    val effective = filterMasksForFrame(input.maskPasses, input.environment)
    if (effective.isNotEmpty()) {
        pass.recordCommands(encoder, effective, input.environment)
        maskCleared = true
    } else if (!maskCleared) {
        pass.clearLayers(encoder)
        maskCleared = true
    } else {
        pass.clearLayers(encoder, onlyDirty = true)
    }
}

private fun Renderer.needsCoveringExemptMask(environment: GpuEnvironmentState): Boolean {
    for (i in renderFeatures.indices) {
        if (renderFeatures[i].needsCoveringExemptMask(environment)) return true
    }
    return false
}

private fun Renderer.filterMasksForFrame(masks: List<GpuSubPass>, environment: GpuEnvironmentState): List<GpuSubPass> {
    if (masks.isEmpty() || needsCoveringExemptMask(environment)) return masks
    var exemptCount = 0
    for (i in masks.indices) {
        if (masks[i].targetLayer == COVERING_EXEMPT_MASK_LAYER) exemptCount++
    }
    return when {
        exemptCount == 0 -> masks
        exemptCount == masks.size -> emptyList()
        else -> collectNonExemptMasks(masks, masks.size - exemptCount)
    }
}

private fun collectNonExemptMasks(masks: List<GpuSubPass>, count: Int): List<GpuSubPass> {
    val filtered = ArrayList<GpuSubPass>(count)
    for (i in masks.indices) {
        val subPass = masks[i]
        if (subPass.targetLayer != COVERING_EXEMPT_MASK_LAYER) filtered.add(subPass)
    }
    return filtered
}

private fun Renderer.currentScreenDepthTarget(): DepthTarget? =
    sceneDepthPass?.depthTarget ?: maskPass?.depthTarget

/**
 * Reallocates the scene-depth and mask targets at [width] x [height] when either differs. Old
 * textures are closed at once, as WebGPU lets submitted work finish; cached depth bind groups are
 * rebuilt over the new views, as the shadow array rebuilds its groups.
 */
private fun Renderer.resizeScreenDepthTargets(width: Int, height: Int) {
    val sceneTarget = sceneDepthPass?.depthTarget
    if (sceneTarget != null && (sceneTarget.width != width || sceneTarget.height != height)) {
        sceneTarget.reallocate(1, width, height)
        sceneDepthPass?.resetLayerState()
        rebuildDepthBindGroups(BindingSemantic.SceneDepth, bufferPools.sceneDepthBindGroupSlots, sceneTarget)
    }
    val maskTarget = maskPass?.depthTarget
    if (maskTarget != null && (maskTarget.width != width || maskTarget.height != height)) {
        maskTarget.reallocate(MASK_TARGET_LAYER_COUNT, width, height)
        maskPass?.resetLayerState()
        maskCleared = false
        rebuildDepthBindGroups(BindingSemantic.MaskDepth, bufferPools.maskBindGroupSlots, maskTarget)
    }
}

private fun Renderer.rebuildDepthBindGroups(
    semantic: BindingSemantic,
    slots: Map<io.ygdrasil.webgpu.GPURenderPipeline, Int>,
    target: DepthTarget,
) {
    val groups = bufferPools.shadowBindGroups[semantic].orEmpty()
    for ((pipeline, handle) in groups) {
        val slot = slots[pipeline] ?: continue
        val old = handle.bindGroup
        handle.bindGroup = bufferPools.depthBindGroup(pipeline, slot, target)
        old.close()
    }
}
