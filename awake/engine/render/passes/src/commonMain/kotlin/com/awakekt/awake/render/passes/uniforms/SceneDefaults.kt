/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.renderer.SceneLight

private const val DEFAULT_LIGHT_DIRECTION_X = 0.4f
private const val DEFAULT_LIGHT_DIRECTION_Y = 0.8f
private const val DEFAULT_LIGHT_DIRECTION_Z = 0.4f

/** Default scene light used when no explicit directional light exists in a scene. */
val DEFAULT_SCENE_LIGHT = SceneLight(
    direction = Vec3f(
        DEFAULT_LIGHT_DIRECTION_X,
        DEFAULT_LIGHT_DIRECTION_Y,
        DEFAULT_LIGHT_DIRECTION_Z,
    ).normalize(),
    color = Vec3f(1f, 1f, 1f),
)

val DEFAULT_HORIZON_COLOR = Color(r = 0.72f, g = 0.80f, b = 0.88f, a = 1f)
val DEFAULT_ZENITH_COLOR = Color(r = 0.20f, g = 0.38f, b = 0.68f, a = 1f)
val DEFAULT_FOG_COLOR = Color(r = 0.55f, g = 0.62f, b = 0.70f, a = 1f)

/** Sized for MVP (16) + SceneLight direction & color (8) floats. */
const val DEFAULT_UNIFORM_FLOAT_COUNT = 24
