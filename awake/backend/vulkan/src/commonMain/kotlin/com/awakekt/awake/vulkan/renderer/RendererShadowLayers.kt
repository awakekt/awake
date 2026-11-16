/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.render.command.GpuPassInput

/**
 * Sizes the shadow depth array before [input] is recorded, as [Renderer.shadowArraySizing] decides
 * from the layers [input]'s depth passes draw and the budget's map size (#1688, #1698): a one-texel
 * placeholder until a light casts; then the layers drawn, at the map size, growing while shadows are
 * drawn (a point light's six faces the first frame it casts); back to the placeholder once no
 * shadow has been drawn for a while.
 *
 * Each change waits for the device to go idle first (`vkDeviceWaitIdle`): the frames still in flight
 * may read the old image, and the target's descriptor set is rewritten in place, which no submitted
 * or recording command buffer may hold. The cost is a one-off hitch on the frame that changes it:
 * the frames in flight drain, then the new image is allocated. It is never paid per frame: a scene
 * whose shadows come and go returns here before the wait; `WindowedShadowGrowthTest` toggles a point
 * shadow and counts its reallocations.
 */
internal fun Renderer.ensureShadowLayers(input: GpuPassInput) {
    val pass = depthPrePass ?: return
    var needed = 0
    for (index in input.prePasses.indices) needed = maxOf(needed, input.prePasses[index].targetLayer + 1)
    if (!shadowArraySizing.update(needed, shadowBudget.mapSize)) return
    waitIdle()
    pass.resize(shadowArraySizing.layers, shadowArraySizing.size)
}
