/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

import com.awakekt.awake.asset.shaderdsl.AslConstruct
import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.abs
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.cos
import com.awakekt.awake.asset.shaderdsl.cross
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.exp
import com.awakekt.awake.asset.shaderdsl.floor
import com.awakekt.awake.asset.shaderdsl.fract
import com.awakekt.awake.asset.shaderdsl.length
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.min
import com.awakekt.awake.asset.shaderdsl.mix
import com.awakekt.awake.asset.shaderdsl.normalize
import com.awakekt.awake.asset.shaderdsl.pow
import com.awakekt.awake.asset.shaderdsl.saturate
import com.awakekt.awake.asset.shaderdsl.select
import com.awakekt.awake.asset.shaderdsl.sin
import com.awakekt.awake.asset.shaderdsl.smoothstep
import com.awakekt.awake.asset.shaderdsl.sqrt
import com.awakekt.awake.asset.shaderdsl.step
import com.awakekt.awake.core.geometry.GpuDataShape

/**
 * [fn] over lowered [args], through ASL's own builtin, which checks the shapes again.
 *
 * WGSL's `min`, `max`, `pow`, `step`, `clamp` and `smoothstep` take arguments of one shape, so a scalar
 * a document passes where the others are vectors is widened to a vector first: `max(v, vec3f(s))`.
 */
@Suppress("CyclomaticComplexMethod") // One branch per builtin.
internal fun lowerCall(fn: ShaderFunction, args: List<AslExpr>): AslExpr {
    val target = args[resultArgument(fn)].dataShape
    val splat = splattedArguments(fn)
    val a = args.mapIndexed { i, arg ->
        val widen = i in splat && arg.dataShape == GpuDataShape.Float && target != null && target != GpuDataShape.Float
        if (widen) AslConstruct(requireNotNull(target), listOf(arg)) else arg
    }
    return when (fn) {
        ShaderFunction.Sin -> sin(a[0])
        ShaderFunction.Cos -> cos(a[0])
        ShaderFunction.Abs -> abs(a[0])
        ShaderFunction.Floor -> floor(a[0])
        ShaderFunction.Fract -> fract(a[0])
        ShaderFunction.Sqrt -> sqrt(a[0])
        ShaderFunction.Exp -> exp(a[0])
        ShaderFunction.Saturate -> saturate(a[0])
        ShaderFunction.Normalize -> normalize(a[0])
        ShaderFunction.Length -> length(a[0])
        ShaderFunction.Dot -> dot(a[0], a[1])
        ShaderFunction.Cross -> cross(a[0], a[1])
        ShaderFunction.Min -> min(a[0], a[1])
        ShaderFunction.Max -> max(a[0], a[1])
        ShaderFunction.Pow -> pow(a[0], a[1])
        ShaderFunction.Step -> step(a[0], a[1])
        ShaderFunction.Clamp -> clamp(a[0], a[1], a[2])
        ShaderFunction.Smoothstep -> smoothstep(a[0], a[1], a[2])
        ShaderFunction.Mix -> mix(a[0], a[1], a[2])
        ShaderFunction.Select -> select(a[0], a[1], a[2])
    }
}

/** The argument whose shape the result takes, and the shape a scalar argument is widened to. */
private fun resultArgument(fn: ShaderFunction): Int = when (fn) {
    ShaderFunction.Step -> 1
    ShaderFunction.Smoothstep -> 2
    else -> 0
}
