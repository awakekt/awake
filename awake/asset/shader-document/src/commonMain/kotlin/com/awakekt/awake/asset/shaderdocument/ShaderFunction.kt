/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A built-in function a [ShaderExpr.Call] can name: each is the WGSL builtin of the same name.
 *
 * "Numbers" below means a scalar or a vector, and the result has the shape of the first argument
 * unless it says otherwise. Where an argument may be "a scalar or the same shape", a scalar applies to
 * every component.
 */
@Serializable
enum class ShaderFunction(
    /** How many arguments it takes. */
    val arity: Int,
) {
    /** `sin(x)`, radians. */
    @SerialName("sin")
    Sin(1),

    /** `cos(x)`, radians. */
    @SerialName("cos")
    Cos(1),

    /** `abs(x)`. */
    @SerialName("abs")
    Abs(1),

    /** `floor(x)`. */
    @SerialName("floor")
    Floor(1),

    /** `fract(x)`: `x - floor(x)`. */
    @SerialName("fract")
    Fract(1),

    /** `sqrt(x)`. */
    @SerialName("sqrt")
    Sqrt(1),

    /** `exp(x)`: e to the power `x`. */
    @SerialName("exp")
    Exp(1),

    /** `saturate(x)`: `x` clamped to 0 to 1. */
    @SerialName("saturate")
    Saturate(1),

    /** `normalize(v)`: `v` scaled to unit length. A vector only. */
    @SerialName("normalize")
    Normalize(1),

    /** `length(v)`: a vector's length, as a scalar. */
    @SerialName("length")
    Length(1),

    /** `dot(a, b)`: two vectors of one size, giving a scalar. */
    @SerialName("dot")
    Dot(2),

    /** `cross(a, b)`: two 3-component vectors, giving a 3-component vector. */
    @SerialName("cross")
    Cross(2),

    /** `min(a, b)`: `b` a scalar or the same shape as `a`. */
    @SerialName("min")
    Min(2),

    /** `max(a, b)`: `b` a scalar or the same shape as `a`. */
    @SerialName("max")
    Max(2),

    /** `pow(a, b)`: `a` to the power `b`, `b` a scalar or the same shape as `a`. */
    @SerialName("pow")
    Pow(2),

    /** `step(edge, x)`: 0 where `x < edge`, else 1, in `x`'s shape; `edge` a scalar or that shape. */
    @SerialName("step")
    Step(2),

    /** `clamp(x, low, high)`: `low` and `high` each a scalar or the same shape as `x`. */
    @SerialName("clamp")
    Clamp(3),

    /** `smoothstep(low, high, x)`: a smooth 0 to 1 ramp, in `x`'s shape; `low` and `high` each a scalar or that shape. */
    @SerialName("smoothstep")
    Smoothstep(3),

    /** `mix(a, b, t)`: `a` and `b` of one shape, `t` a scalar or the same shape. */
    @SerialName("mix")
    Mix(3),

    /** `select(ifFalse, ifTrue, condition)`: `ifFalse` and `ifTrue` of one shape, and a condition. */
    @SerialName("select")
    Select(3),
}
