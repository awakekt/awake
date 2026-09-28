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
    val showSky: Boolean = false,
    val horizonColor: Color = DEFAULT_HORIZON_COLOR,
    val zenithColor: Color = DEFAULT_ZENITH_COLOR,
    val fogDensity: Float = 0f,
    val fogColor: Color = DEFAULT_FOG_COLOR,
    val shadowsEnabled: Boolean = true,
    /** What the scene shaders draw instead of their lit colour. */
    val debugView: RenderDebugView = RenderDebugView.Off,
    /** The shadow-map layer [RenderDebugView.ShadowMap] shows. */
    val debugLayer: Int = 0,
) {
    companion object {
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
    )
}
