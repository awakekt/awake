/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.render.command.MASK_TARGET_LAYER_COUNT

/**
 * Sizes the scene-depth and mask targets to the scene a presented frame draws,
 * [Renderer.surfaceWidth] by [Renderer.surfaceHeight] after the render scale, before the frame is
 * recorded (#1709). Both were fixed 2048 squares (66.3 MiB combined), cleared and stored in full
 * whatever the scene's size; sizing them in lockstep keeps fog's mask-vs-scene-depth silhouette
 * comparison aligned at one texel grid while cutting both to the scaled scene extent.
 * See [com.awakekt.awake.render.passes.MaskTargetSizing] for when they grow and shrink.
 */
internal fun Renderer.settleMaskTarget() {
    val current = currentScreenDepthEdge() ?: return
    resizeScreenDepthTargets(maskSizing.presented(current, surfaceWidth, surfaceHeight))
}

/** Grows the scene-depth and mask targets for an offscreen render [width] x [height] pixels, never shrinking them. */
internal fun Renderer.ensureMaskTarget(width: Int, height: Int) {
    val current = currentScreenDepthEdge() ?: return
    resizeScreenDepthTargets(maskSizing.offscreen(current, width, height))
}

private fun Renderer.currentScreenDepthEdge(): Int? =
    sceneDepthTarget?.size ?: maskPass?.depthTarget?.size

/**
 * Reallocates the scene-depth and mask targets at [edge] when either differs, after the device
 * goes idle: frames in flight may read the old images, and their descriptor sets are rewritten in
 * place. A one-off hitch on a resize, never one per frame. New layers are cleared before anything
 * samples them.
 */
private fun Renderer.resizeScreenDepthTargets(edge: Int) {
    val scenePass = sceneDepthPassFeature
    val mask = maskPass
    val resizeScene = scenePass != null && scenePass.depthTarget.size != edge
    val resizeMask = mask != null && mask.depthTarget.size != edge
    if (!resizeScene && !resizeMask) return
    waitIdle()
    if (resizeScene) scenePass.resize(1, edge)
    if (resizeMask) mask.resize(MASK_TARGET_LAYER_COUNT, edge)
}
