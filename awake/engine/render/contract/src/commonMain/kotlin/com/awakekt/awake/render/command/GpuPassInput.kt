/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.renderer.RenderViewport

/**
 * Fully pre-packed, hardware-ready pass input for the HAL.
 *
 * References only HAL primitives plus generic per-pass environment state. It contains no camera,
 * light, mesh-scene, or authoring types; [GpuEnvironmentState] is the lowered feature state
 * consumed by generic sky, fog, and shadow execution.
 */
data class GpuPassInput(
    /** Any sub-passes executing before the primary scene pass (e.g. depth pre-passes, shadow cascade layers). */
    val prePasses: List<GpuSubPass> = emptyList(),
    /** Combined View-Projection matrix for the primary scene pass. */
    val viewProjection: Mat4,
    /** Camera world position for distance-based calculations / sorting. */
    val cameraEye: Vec3f,
    /** Optional scene sub-rect. This is packet state, so backends do not retain editor/game state. */
    val viewport: RenderViewport? = null,
    /** Fully resolved commands produced by the render-pipeline compiler. */
    val resolvedOpaqueDraws: List<GpuResolvedDraw> = emptyList(),
    val resolvedTransparentDraws: List<GpuResolvedDraw> = emptyList(),
    /** Any sub-passes executing after the primary scene pass (e.g. bloom, tone-mapping, color grading). */
    val postPasses: List<GpuSubPass> = emptyList(),
    /** Pre-packed uniform bytes (lighting, fog, shadow matrices) as raw floats. */
    val passUniforms: FloatArray,
    /** Generic feature policy for this pass; defaults preserve the historical renderer behavior. */
    val environment: GpuEnvironmentState = GpuEnvironmentState.Default,
    /** True when the compiler deliberately selected resolved packets, including zero draws. */
    val resolvedPath: Boolean = false,
) {
    /** Canonical draw sequence for executors; legacy lists are deliberately excluded. */
    val resolvedDraws: List<GpuResolvedDraw>
        get() = resolvedOpaqueDraws + resolvedTransparentDraws

    companion object {
        val EMPTY = GpuPassInput(
            prePasses = emptyList(),
            viewProjection = Mat4(),
            cameraEye = Vec3f(0f, 0f, 0f),
            viewport = null,
            postPasses = emptyList(),
            passUniforms = FloatArray(0),
            environment = GpuEnvironmentState.Default,
            // An empty frame is still an intentional resolved packet. This is used by
            // Renderer.presentWithoutScene() for UI-only applications and must pass the same
            // executor guard as a compiled frame with zero draws.
            resolvedPath = true,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GpuPassInput) return false

        if (prePasses != other.prePasses) return false
        if (viewProjection != other.viewProjection) return false
        if (cameraEye != other.cameraEye) return false
        if (viewport != other.viewport) return false
        if (resolvedOpaqueDraws != other.resolvedOpaqueDraws) return false
        if (resolvedTransparentDraws != other.resolvedTransparentDraws) return false
        if (postPasses != other.postPasses) return false
        if (!passUniforms.contentEquals(other.passUniforms)) return false
        if (environment != other.environment) return false
        if (resolvedPath != other.resolvedPath) return false

        return true
    }

    override fun hashCode(): Int {
        var result = prePasses.hashCode()
        result = 31 * result + viewProjection.hashCode()
        result = 31 * result + cameraEye.hashCode()
        result = 31 * result + (viewport?.hashCode() ?: 0)
        result = 31 * result + resolvedOpaqueDraws.hashCode()
        result = 31 * result + resolvedTransparentDraws.hashCode()
        result = 31 * result + postPasses.hashCode()
        result = 31 * result + passUniforms.contentHashCode()
        result = 31 * result + environment.hashCode()
        result = 31 * result + resolvedPath.hashCode()
        return result
    }
}
