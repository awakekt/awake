/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.color.Color

/**
 * Per-frame environmental uniforms passed to the render graph for sky, fog, and global shadows.
 *
 * This represents the render graph contract for environment shading (pure shader uniforms),
 * strictly decoupled from any ECS scene-graph or authoring components.
 */
data class EnvironmentUniforms(
    val showSky: Boolean = false,
    val horizonColor: Color = DEFAULT_HORIZON_COLOR,
    val zenithColor: Color = DEFAULT_ZENITH_COLOR,
    val fogDensity: Float = 0f,
    val fogColor: Color = DEFAULT_FOG_COLOR,
    val shadowsEnabled: Boolean = true,
) {
    companion object {
        val Default = EnvironmentUniforms()
    }
}
