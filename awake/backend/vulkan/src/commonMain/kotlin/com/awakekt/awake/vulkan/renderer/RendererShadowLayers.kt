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
 * It waits for the device first. The GPU may still read the old image, and the target's descriptor
 * set is rewritten in place, which no submitted or recording command buffer may hold. That wait
 * happens only on the frame the array grows, not every frame.
 */
internal fun Renderer.ensureShadowLayers(input: GpuPassInput) {
    val pass = depthPrePass ?: return
    var needed = 0
    for (index in input.prePasses.indices) needed = maxOf(needed, input.prePasses[index].targetLayer + 1)
    if (needed <= pass.depthTarget.layers) return
    waitIdle()
    pass.ensureLayers(needed)
}
