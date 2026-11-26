/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.command.COVERING_EXEMPT_MASK_LAYER
import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.command.MASK_LAYER_COUNT
import com.awakekt.awake.render.command.MASK_TARGET_LAYER_COUNT
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.command.SortedDraws
import com.awakekt.awake.render.command.allDraws
import com.awakekt.awake.render.passes.maskExtentHeight
import com.awakekt.awake.render.passes.maskExtentWidth
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.webgpu.pipeline.DepthPrePassFeature
import com.awakekt.awake.webgpu.pipeline.resetLayerState
import com.awakekt.awake.webgpu.texture.DepthTarget
import io.ygdrasil.webgpu.GPUCommandEncoder
import io.ygdrasil.webgpu.GPURenderPipeline

/**
 * Settles the scene-depth and mask targets before a presented frame's [input] is recorded (#1709):
 * each stays at a dormant 1x1 stand-in until a frame actually needs it, grows immediately to the
 * rectangular scene extent (`surfaceWidth x surfaceHeight`) on demand, and releases back to 1x1
 * once a residency window passes with no active consumer.
 */
internal fun Renderer.settleMaskTarget(input: GpuPassInput) {
    sceneDepthPass?.let { pass ->
        val needed = if (needsSceneDepth(input.environment)) 1 else 0
        val hold = sceneDepthResidency.frame(needed, if (pass.depthTarget.resident) 1 else 0)
        val extent = if (hold > 0) {
            sceneDepthSizing.presentedExtent(pass.depthTarget.width, pass.depthTarget.height, surfaceWidth, surfaceHeight)
        } else {
            0L
        }
        applyDepthTargetResize(pass, 1, BindingSemantic.SceneDepth, bufferPools.sceneDepthBindGroupSlots, hold, extent)
    }
    maskPass?.let { pass ->
        val needed = if (filterMasksForFrame(input.maskPasses, input.environment).isNotEmpty()) 1 else 0
        val hold = maskResidency.frame(needed, if (pass.depthTarget.resident) 1 else 0)
        val extent = if (hold > 0) {
            maskSizing.presentedExtent(pass.depthTarget.width, pass.depthTarget.height, surfaceWidth, surfaceHeight)
        } else {
            0L
        }
        if (applyDepthTargetResize(pass, MASK_TARGET_LAYER_COUNT, BindingSemantic.MaskDepth, bufferPools.maskBindGroupSlots, hold, extent)) {
            maskCleared = false
        }
    }
}

/**
 * Grows the scene-depth and/or mask targets for an offscreen render of [input] at
 * [width] x [height] pixels when needed, never shrinking either target.
 */
internal fun Renderer.ensureMaskTarget(width: Int, height: Int, input: GpuPassInput) {
    sceneDepthPass?.let { pass ->
        val needed = if (needsSceneDepth(input.environment)) 1 else 0
        val hold = sceneDepthResidency.layersFor(needed, if (pass.depthTarget.resident) 1 else 0)
        val extent = if (hold > 0) {
            sceneDepthSizing.offscreenExtent(pass.depthTarget.width, pass.depthTarget.height, width, height)
        } else {
            0L
        }
        applyDepthTargetResize(pass, 1, BindingSemantic.SceneDepth, bufferPools.sceneDepthBindGroupSlots, hold, extent)
    }
    maskPass?.let { pass ->
        val needed = if (filterMasksForFrame(input.maskPasses, input.environment).isNotEmpty()) 1 else 0
        val hold = maskResidency.layersFor(needed, if (pass.depthTarget.resident) 1 else 0)
        val extent = if (hold > 0) {
            maskSizing.offscreenExtent(pass.depthTarget.width, pass.depthTarget.height, width, height)
        } else {
            0L
        }
        if (applyDepthTargetResize(pass, MASK_TARGET_LAYER_COUNT, BindingSemantic.MaskDepth, bufferPools.maskBindGroupSlots, hold, extent)) {
            maskCleared = false
        }
    }
}

/**
 * Records the camera-space scene-depth pre-pass only when an active feature or scene pipeline
 * reads scene depth; otherwise initializes/clears any dirty layer once so idle frames pay zero
 * render passes.
 */
internal fun Renderer.recordSceneDepthPass(
    encoder: GPUCommandEncoder,
    input: GpuPassInput,
    sorted: SortedDraws<out PreparedDraw>,
) {
    val pass = sceneDepthPass ?: return
    if (!needsSceneDepth(input.environment)) {
        pass.clearLayers(encoder, onlyDirty = true)
    } else {
        pass.recordCommands(
            encoder,
            sorted.allDraws,
            GpuShadowCascadeData(
                viewProjections = listOf(input.viewProjection),
                splitDistances = floatArrayOf(Float.MAX_VALUE),
            ),
            input.environment,
        )
    }
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

internal fun Renderer.needsSceneDepth(environment: GpuEnvironmentState): Boolean {
    var needed = anyScenePipelineSamplesSceneDepth
    var i = 0
    while (!needed && i < renderFeatures.size) {
        needed = renderFeatures[i].needsSceneDepth(environment)
        i += 1
    }
    return needed
}

private fun Renderer.needsCoveringExemptMask(environment: GpuEnvironmentState): Boolean {
    for (i in renderFeatures.indices) {
        if (renderFeatures[i].needsCoveringExemptMask(environment)) return true
    }
    return false
}

private fun Renderer.filterMasksForFrame(masks: List<GpuSubPass>, environment: GpuEnvironmentState): List<GpuSubPass> {
    val dropExempt = !needsCoveringExemptMask(environment)
    val dropOutlines = effectQuality == EffectQuality.Off
    if (masks.isEmpty() || (!dropExempt && !dropOutlines)) return masks
    var skipped = 0
    for (i in masks.indices) {
        val layer = masks[i].targetLayer
        val skipExempt = dropExempt && layer == COVERING_EXEMPT_MASK_LAYER
        val skipOutline = dropOutlines && layer < MASK_LAYER_COUNT
        if (skipExempt || skipOutline) skipped++
    }
    return when {
        skipped == 0 -> masks
        skipped == masks.size -> emptyList()
        else -> collectKeptMasks(masks, dropExempt, dropOutlines)
    }
}

private fun Renderer.collectKeptMasks(
    masks: List<GpuSubPass>,
    dropExempt: Boolean,
    dropOutlines: Boolean,
): List<GpuSubPass> {
    filteredMasks.clear()
    for (i in masks.indices) {
        val subPass = masks[i]
        val layer = subPass.targetLayer
        val skipExempt = dropExempt && layer == COVERING_EXEMPT_MASK_LAYER
        val skipOutline = dropOutlines && layer < MASK_LAYER_COUNT
        if (!skipExempt && !skipOutline) {
            filteredMasks.add(subPass)
        }
    }
    return filteredMasks
}

@Suppress("LongParameterList")
private fun Renderer.applyDepthTargetResize(
    pass: DepthPrePassFeature,
    layers: Int,
    semantic: BindingSemantic,
    slots: MutableMap<GPURenderPipeline, Int>,
    hold: Int,
    extent: Long,
): Boolean {
    val target = pass.depthTarget
    val width = if (hold == 0) DepthTarget.DORMANT_SIZE else maskExtentWidth(extent)
    val height = if (hold == 0) DepthTarget.DORMANT_SIZE else maskExtentHeight(extent)
    if (target.width == width && target.height == height) return false
    target.reallocate(layers, width, height)
    pass.resetLayerState()
    rebuildDepthBindGroups(semantic, slots, target)
    return true
}

private fun Renderer.rebuildDepthBindGroups(
    semantic: BindingSemantic,
    slots: Map<GPURenderPipeline, Int>,
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
