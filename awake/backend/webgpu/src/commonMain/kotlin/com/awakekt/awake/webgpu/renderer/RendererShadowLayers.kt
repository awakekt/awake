/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.pipeline.BindingSemantic

/**
 * Grows the shadow depth array to hold every layer [input]'s pre-passes draw (#1688): the cascades
 * in use, and six faces for each point light that casts. It starts at the default cascade count and
 * never shrinks, so a scene that gains a point shadow reallocates once.
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
    if (needed <= target.layers) return
    target.reallocate(needed)
    val groups = bufferPools.shadowBindGroups[BindingSemantic.ShadowDepth].orEmpty()
    for ((pipeline, handle) in groups) {
        val slot = bufferPools.shadowBindGroupSlots[pipeline] ?: continue
        val old = handle.bindGroup
        handle.bindGroup = bufferPools.depthBindGroup(pipeline, slot, target)
        old.close()
    }
}
