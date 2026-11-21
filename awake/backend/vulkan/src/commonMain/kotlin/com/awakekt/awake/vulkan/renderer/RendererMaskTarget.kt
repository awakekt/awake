/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.render.command.MASK_TARGET_LAYER_COUNT

/**
 * Sizes the mask target to the scene a presented frame draws, [Renderer.surfaceWidth] by
 * [Renderer.surfaceHeight] after the render scale, before the frame is recorded (#1709). It was a
 * fixed 2048 square, cleared and stored in full on every masked frame whatever the scene's size.
 * See [com.awakekt.awake.render.passes.MaskTargetSizing] for when it grows and shrinks.
 */
internal fun Renderer.settleMaskTarget() {
    val target = maskPass?.depthTarget ?: return
    resizeMaskTarget(sceneDepthEdge() ?: maskSizing.presented(target.size, surfaceWidth, surfaceHeight))
}

/** Grows the mask target for an offscreen render [width] x [height] pixels, never shrinking it. */
internal fun Renderer.ensureMaskTarget(width: Int, height: Int) {
    val target = maskPass?.depthTarget ?: return
    resizeMaskTarget(sceneDepthEdge() ?: maskSizing.offscreen(target.size, width, height))
}

/**
 * The scene depth target's edge, when the plan has one: fog compares an exempt draw's depth in the mask
 * with scene depth at the same texture coordinate, which agrees only while both are drawn at one size.
 * At different sizes their silhouettes rasterize apart, and fog lands on an unlit draw's edge pixels.
 */
private fun Renderer.sceneDepthEdge(): Int? = sceneDepthTarget?.size

/**
 * Reallocates the mask's layers at [edge] when that differs, after the device goes idle: frames in
 * flight may read the old image, and its descriptor set is rewritten in place. A one-off hitch on a
 * resize, never one per frame. The new layers are cleared before anything samples them.
 */
private fun Renderer.resizeMaskTarget(edge: Int) {
    val pass = maskPass ?: return
    if (pass.depthTarget.size == edge) return
    waitIdle()
    pass.resize(MASK_TARGET_LAYER_COUNT, edge)
}
