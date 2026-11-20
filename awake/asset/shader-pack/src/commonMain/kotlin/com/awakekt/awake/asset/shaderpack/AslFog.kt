/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.a
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.exp
import com.awakekt.awake.asset.shaderdsl.gt
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.select
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.unaryMinus
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.z

/** The narrowest linear ramp the shaders divide by; the frame block never carries an empty one. */
private const val MIN_FOG_RAMP = 0.0001f

/**
 * How much fog covers a point [dist] metres from the eye, before saturating: exponential by
 * [fogColor]'s alpha, the density, unless [fogRange] (`UniformFields.FogRange`) says linear, which
 * ramps from its x (start) to its y (end) (#1807). Exponential is the expression the shaders always
 * had, so a scene with no range fogs exactly as before.
 */
internal fun fogAmount(dist: AslExpr, fogColor: AslExpr, fogRange: AslExpr): AslExpr = select(
    1f.lit - exp(-fogColor.a * dist),
    (dist - fogRange.x) / max(fogRange.y - fogRange.x, MIN_FOG_RAMP.lit),
    fogRange.z gt 0.5f.lit,
)
