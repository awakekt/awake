/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.renderer.ShadowCascadeUniforms

data class PointLight(
    val position: Vec3f,
    val color: Vec3f,
    val range: Float,
)

data class SceneLight(
    val direction: Vec3f,
    val color: Vec3f,
    val points: List<PointLight> = emptyList(),
    val viewProjection: Mat4? = null,
    val cascades: ShadowCascadeUniforms? = null,
)

fun SceneLight.shadowCascades(): ShadowCascadeUniforms? = cascades
    ?: viewProjection?.let { ShadowCascadeUniforms(listOf(it), floatArrayOf(Float.MAX_VALUE)) }

const val MAX_POINT_LIGHTS = 4
