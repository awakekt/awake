/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.renderer.RenderViewport
import com.awakekt.awake.render.texture.RenderTarget

/**
 * A generic hardware render pass execution request.
 *
 * Used for pre-passes (depth pre-pass, shadow cascade layers), post-passes (bloom, tone-mapping,
 * color grading), or offscreen passes. Hardware backends iterate and execute these sub-passes
 * without inspecting scene or light semantics.
 */
data class GpuSubPass(
    /** Offscreen render target to render into, or `null` for the default framebuffer. */
    val target: RenderTarget?,
    /** Target array layer or cubemap face index. */
    val targetLayer: Int = 0,
    /** View-projection matrix used for camera or light transform in this pass. */
    val viewProjection: Mat4,
    /** Viewport rectangle override, or `null` to use the target dimensions. */
    val viewport: RenderViewport? = null,
    /** Fully resolved draw list for the generic command path. */
    val resolvedDraws: List<GpuResolvedDraw> = emptyList(),
    /** Pass-specific raw uniform float data uploaded for this pass. */
    val passUniforms: FloatArray = FloatArray(0),
    /** Constant depth bias added to fragments in this pass. */
    val depthBiasConstant: Float = 0f,
    /** Slope-scaled depth bias added to fragments in this pass. */
    val depthBiasSlope: Float = 0f,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GpuSubPass) return false

        if (target != other.target) return false
        if (targetLayer != other.targetLayer) return false
        if (viewProjection != other.viewProjection) return false
        if (viewport != other.viewport) return false
        if (resolvedDraws != other.resolvedDraws) return false
        if (!passUniforms.contentEquals(other.passUniforms)) return false
        if (depthBiasConstant != other.depthBiasConstant) return false
        if (depthBiasSlope != other.depthBiasSlope) return false

        return true
    }

    override fun hashCode(): Int {
        var result = target?.hashCode() ?: 0
        result = 31 * result + targetLayer
        result = 31 * result + viewProjection.hashCode()
        result = 31 * result + (viewport?.hashCode() ?: 0)
        result = 31 * result + resolvedDraws.hashCode()
        result = 31 * result + passUniforms.contentHashCode()
        result = 31 * result + depthBiasConstant.hashCode()
        result = 31 * result + depthBiasSlope.hashCode()
        return result
    }
}
