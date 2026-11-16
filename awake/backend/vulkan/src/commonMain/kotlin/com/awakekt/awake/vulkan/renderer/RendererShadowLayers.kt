/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.render.command.GpuPassInput

/**
 * Sizes the shadow depth array before a presented frame's [input] is recorded (#1688, #1698): it
 * holds no array until a light casts, grows to the layers the frame's depth passes target, takes the
 * budget's map size, and is released again once a residency window passes with no light casting.
 * See [ShadowArrayResidency] for when it shrinks.
 */
internal fun Renderer.settleShadowArray(input: GpuPassInput) {
    if (depthPrePass == null) return
    applyShadowLayers(shadowResidency.frame(shadowLayersNeeded(input), residentShadowLayers()))
}

/**
 * Grows the shadow depth array for an offscreen render of [input], as [settleShadowArray] does, but
 * never shrinks it: an offscreen view that draws no shadow says nothing about the presented frames.
 */
internal fun Renderer.ensureShadowLayers(input: GpuPassInput) {
    if (depthPrePass == null) return
    applyShadowLayers(shadowResidency.layersFor(shadowLayersNeeded(input), residentShadowLayers()))
}

/** The array layers [input] draws into: none while its environment turns shadows off. */
private fun shadowLayersNeeded(input: GpuPassInput): Int =
    if (input.environment.shadowsEnabled) shadowLayersNeeded(input.prePasses) else 0

private fun Renderer.residentShadowLayers(): Int {
    val target = depthPrePass?.depthTarget ?: return 0
    return if (target.resident) target.layers else 0
}

/**
 * Reallocates the array to [layers] at the budget's map size when that differs from what it holds.
 *
 * It waits for the device to go idle first (`vkDeviceWaitIdle`): the frames still in flight may read
 * the old image, and the target's descriptor set is rewritten in place, which no submitted or
 * recording command buffer may hold. The cost is a one-off hitch on the frame the array grows,
 * shrinks or changes size, never one per frame.
 */
private fun Renderer.applyShadowLayers(layers: Int) {
    val pass = depthPrePass ?: return
    val target = pass.depthTarget
    val size = shadowBudget.mapSize
    val unchanged = if (layers == 0) !target.resident else target.resident && layers == target.layers && size == target.size
    if (unchanged) return
    waitIdle()
    pass.resize(layers, size)
}
