/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.cos
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.sin
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec3
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.z

/**
 * Shared vertex displacement for the visible shadowed pass and every depth caster.
 * A caster rendered with a different animated position than the visible pass produces a valid
 * depth map for the wrong geometry, which presents as a detached instanced/skinned shadow.
 */
internal fun animatedShadowPosition(u: ShadowUniforms, position: AslExpr): AslExpr {
    val wavelength = max(u.vertexAnimation.y, 0.0001f.lit)
    val phase = u.vertexAnimation.w * u.vertexAnimation.z
    val diagonal = (position.x + position.z) / wavelength + phase
    val cross = (position.x - position.z) / wavelength * 0.7f.lit + phase * 0.8f.lit
    val wave = (sin(diagonal) + cos(cross)) * u.vertexAnimation.x * 0.5f.lit
    return vec3(position.x, position.y + wave, position.z)
}
