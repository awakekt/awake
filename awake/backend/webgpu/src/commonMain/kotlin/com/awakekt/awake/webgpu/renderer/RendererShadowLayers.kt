/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.pipeline.BindingSemantic

/**
 * Sizes the shadow depth array before [input] is recorded, as [Renderer.shadowArraySizing] decides
 * from the layers [input]'s pre-passes draw and the budget's map size (#1688, #1698): a one-texel
 * placeholder until a light casts; then the layers drawn (the cascades, and six faces for each point
 * light that casts) at the map size, growing while shadows are drawn; back to the placeholder once
 * no shadow has been drawn for a while.
 *
 * The old texture is closed at once; WebGPU lets work already submitted against it finish. Bind
 * groups cannot be rewritten, so each cached shadow group is rebuilt in place over the new view:
 * draws already prepared this frame hold the handle and bind the new group, and a retained bundle
 * that bound the old one no longer matches and is encoded again.
 */
internal fun Renderer.ensureShadowLayers(input: GpuPassInput) {
    val target = depthPrePass?.depthTarget ?: return
    var needed = 0
    for (index in input.prePasses.indices) needed = maxOf(needed, input.prePasses[index].targetLayer + 1)
    val changed = shadowArraySizing.update(needed, shadowBudget.mapSize) &&
        (target.layers != shadowArraySizing.layers || target.size != shadowArraySizing.size)
    if (!changed) return
    target.reallocate(shadowArraySizing.layers, shadowArraySizing.size)
    val groups = bufferPools.shadowBindGroups[BindingSemantic.ShadowDepth].orEmpty()
    for ((pipeline, handle) in groups) {
        val slot = bufferPools.shadowBindGroupSlots[pipeline] ?: continue
        val old = handle.bindGroup
        handle.bindGroup = bufferPools.depthBindGroup(pipeline, slot, target)
        old.close()
    }
}
