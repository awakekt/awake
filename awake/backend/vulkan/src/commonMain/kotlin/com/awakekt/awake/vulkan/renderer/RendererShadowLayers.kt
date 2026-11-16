/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.render.command.GpuPassInput

/**
 * Grows the shadow depth array before [input] is recorded, when its depth passes target a layer it
 * does not hold: the array starts with the cascades in use, and gains a point light's six faces the
 * first frame that light casts (#1688). Grows only, so a scene settles after its first frames.
 *
 * It waits for the device to go idle first (`vkDeviceWaitIdle`): the frames still in flight may read
 * the old image, and the target's descriptor set is rewritten in place, which no submitted or
 * recording command buffer may hold. The cost is a one-off hitch on the frame the first point shadow
 * appears: the frames in flight drain, then the new image is allocated. It is never paid per frame.
 * The array never shrinks, so a scene whose point shadows come and go returns here before the wait;
 * `WindowedShadowGrowthTest` toggles one and counts a single reallocation.
 */
internal fun Renderer.ensureShadowLayers(input: GpuPassInput) {
    val pass = depthPrePass ?: return
    var needed = 0
    for (index in input.prePasses.indices) needed = maxOf(needed, input.prePasses[index].targetLayer + 1)
    if (needed <= pass.depthTarget.layers) return
    waitIdle()
    pass.ensureLayers(needed)
}
