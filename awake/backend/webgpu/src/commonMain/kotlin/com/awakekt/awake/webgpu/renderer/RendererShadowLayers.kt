/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.passes.shadowLayersNeeded
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.webgpu.pipeline.resetLayerState
import com.awakekt.awake.webgpu.texture.DepthTarget

/**
 * Sizes the shadow depth array before a presented frame's [input] is recorded (#1688, #1698), with
 * the policy Vulkan uses: no array until a light casts, the layers the frame's depth passes target at
 * the budget's map size, and released again once a residency window passes with no light casting.
 * See [com.awakekt.awake.render.passes.ShadowArrayResidency] for when it shrinks.
 */
internal fun Renderer.settleShadowArray(input: GpuPassInput) {
    if (depthPrePass == null) return
    applyShadowLayers(shadowResidency.frame(depthLayersDrawn(input), residentShadowLayers()))
}

/**
 * Grows the shadow depth array for an offscreen render of [input], as [settleShadowArray] does, but
 * never shrinks it: an offscreen view that draws no shadow says nothing about the presented frames.
 */
internal fun Renderer.ensureShadowLayers(input: GpuPassInput) {
    if (depthPrePass == null) return
    applyShadowLayers(shadowResidency.layersFor(depthLayersDrawn(input), residentShadowLayers()))
}

/** The array layers [input] draws into: none while its environment turns shadows off. */
private fun depthLayersDrawn(input: GpuPassInput): Int =
    if (input.environment.shadowsEnabled) shadowLayersNeeded(input.prePasses) else 0

private fun Renderer.residentShadowLayers(): Int {
    val target = depthPrePass?.depthTarget ?: return 0
    return if (target.resident) target.layers else 0
}

/**
 * Reallocates the array to [layers] at the budget's map size, or back to the 1x1 stand-in for none,
 * when that differs from what it holds.
 *
 * The old texture is closed at once; WebGPU lets work already submitted against it finish. Bind
 * groups cannot be rewritten, so each cached shadow group is rebuilt in place over the new view:
 * draws already prepared this frame hold the handle and bind the new group, and a retained bundle
 * that bound the old one no longer matches and is encoded again.
 */
private fun Renderer.applyShadowLayers(layers: Int) {
    val target = depthPrePass?.depthTarget ?: return
    val size = shadowBudget.mapSize
    val unchanged = if (layers == 0) !target.resident else target.resident && layers == target.layers && size == target.size
    if (unchanged) return
    if (layers == 0) target.reallocate(1, DepthTarget.DORMANT_SIZE) else target.reallocate(layers, size)
    depthPrePass?.resetLayerState()
    val groups = bufferPools.shadowBindGroups[BindingSemantic.ShadowDepth].orEmpty()
    for ((pipeline, handle) in groups) {
        val slot = bufferPools.shadowBindGroupSlots[pipeline] ?: continue
        val old = handle.bindGroup
        handle.bindGroup = bufferPools.depthBindGroup(pipeline, slot, target)
        old.close()
    }
}
