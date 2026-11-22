/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.render.command.MASK_TARGET_LAYER_COUNT
import com.awakekt.awake.render.passes.maskExtentHeight
import com.awakekt.awake.render.passes.maskExtentWidth
import com.awakekt.awake.vulkan.texture.DepthTarget

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

private fun Renderer.currentScreenDepthTarget(): DepthTarget? =
    sceneDepthTarget ?: maskPass?.depthTarget

/**
 * Reallocates the scene-depth and mask targets at [width] x [height] when either differs, after the
 * device goes idle: frames in flight may read the old images, and their descriptor sets are
 * rewritten in place. A one-off hitch on a resize, never one per frame. New layers are cleared
 * before anything samples them.
 */
private fun Renderer.resizeScreenDepthTargets(width: Int, height: Int) {
    val scenePass = sceneDepthPassFeature
    val mask = maskPass
    val resizeScene = scenePass != null && (scenePass.depthTarget.width != width || scenePass.depthTarget.height != height)
    val resizeMask = mask != null && (mask.depthTarget.width != width || mask.depthTarget.height != height)
    if (!resizeScene && !resizeMask) return
    waitIdle()
    if (resizeScene) scenePass.resize(1, width, height)
    if (resizeMask) mask.resize(MASK_TARGET_LAYER_COUNT, width, height)
}
