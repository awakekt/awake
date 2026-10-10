/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.render.command.GpuDebugView
import com.awakekt.awake.render.command.GpuEnvironmentState

/**
 * Per-frame environmental uniforms passed to the renderer for sky, fog, and global shadows.
 *
 * This represents the hardware/GPU render contract for environment shading (pure shader uniforms),
 * strictly decoupled from any ECS scene-graph or authoring components.
 */
data class EnvironmentUniforms(
    /** Whether the procedural sky should be drawn. */
    val showSky: Boolean = false,
    /** Clear sky color at the horizon. */
    val horizonColor: Color = DEFAULT_HORIZON_COLOR,
    /** Clear sky color at the zenith (top of the sky hemisphere). */
    val zenithColor: Color = DEFAULT_ZENITH_COLOR,
    /** Atmospheric exponential fog density factor (0 = no fog). */
    val fogDensity: Float = 0f,
    /** Atmospheric fog color. */
    val fogColor: Color = DEFAULT_FOG_COLOR,
    /** Whether directional shadow cascades are rendered. */
    val shadowsEnabled: Boolean = true,
    /** What the scene shaders draw instead of their lit colour. */
    val debugView: RenderDebugView = RenderDebugView.Off,
    /** The shadow-map layer [RenderDebugView.ShadowMap] shows. */
    val debugLayer: Int = 0,
    /**
     * What the lit shaders multiply their radiance by before tone mapping. At 1 a white surface
     * facing a light of intensity 1 shows near white; 2 is one stop brighter.
     */
    val exposure: Float = 1f,
    /**
     * Whether the plan's content features (sky, terrain, fog and the like) draw. False draws only
     * the pass's own draws: an asset preview, which must not show the scene around it.
     */
    val contentFeatures: Boolean = true,
    /** The colour the pass clears to; null keeps the renderer's own. */
    val clearColor: Color? = null,
    /**
     * Whether the opaque draws' triangle edges are drawn over the frame, lit or under [debugView]:
     * a skinned mesh's edges deform with it. Draws whose shader shows no debug views, and instanced
     * draws, draw no edges.
     */
    val wireframe: Boolean = false,
) {
    init {
        require(exposure > 0f && exposure.isFinite()) { "exposure must be finite and above 0; was $exposure." }
    }

    /** Companion object containing default environment uniform instances. */
    companion object {
        /** Default environment uniform configuration. */
        val Default = EnvironmentUniforms()
    }

    /**
     * Lowers this to the pass's hardware-ready state. A debug view hides the sky, which would
     * otherwise read as data behind it.
     *
     * @param viewDepthRange The camera's far distance: what [RenderDebugView.LinearDepth] shows
     *   as white.
     */
    fun toGpuState(viewDepthRange: Float): GpuEnvironmentState = GpuEnvironmentState(
        showSky = showSky && debugView == RenderDebugView.Off,
        horizonColor = horizonColor,
        zenithColor = zenithColor,
        fogDensity = fogDensity,
        fogColor = fogColor,
        shadowsEnabled = shadowsEnabled,
        debugView = if (debugView == RenderDebugView.Off) {
            GpuDebugView.Off
        } else {
            GpuDebugView(debugView.code, viewDepthRange, debugLayer)
        },
        exposure = exposure,
        contentFeatures = contentFeatures,
        clearColor = clearColor,
    )
}
