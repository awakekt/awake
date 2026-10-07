/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * One expression in a [ShaderDocument], written in JSON as an object whose `op` names it:
 * `{"op":"add","a":{"op":"input","input":"time"},"b":{"op":"const","value":[1]}}`.
 *
 * Values are one to four numbers (a scalar, or a 2-, 3- or 4-component vector) or a condition. Numbers
 * combine component by component, and a scalar combines with a vector by applying to each component.
 * A comparison takes two scalars and gives a condition; conditions combine with [And], [Or] and [Not].
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("op")
sealed interface ShaderExpr {
    /** `const`: a literal of one to four numbers. */
    @Serializable
    @SerialName("const")
    data class Constant(
        /** The numbers: one for a scalar, two to four for a vector. */
        val value: List<Float>,
    ) : ShaderExpr

    /** `vec2`, `vec3`, `vec4`: a vector built from scalars and smaller vectors, or from one scalar repeated. */
    sealed interface Construct : ShaderExpr {
        /** The parts, whose components add up to [size], or one scalar for every component. */
        val args: List<ShaderExpr>

        /** How many components the result has. */
        val size: Int
    }

    /** `vec2`: a 2-component vector. */
    @Serializable
    @SerialName("vec2")
    data class Vec2(override val args: List<ShaderExpr>) : Construct {
        override val size: Int get() = 2
    }

    /** `vec3`: a 3-component vector. */
    @Serializable
    @SerialName("vec3")
    data class Vec3(override val args: List<ShaderExpr>) : Construct {
        override val size: Int get() = 3
    }

    /** `vec4`: a 4-component vector. */
    @Serializable
    @SerialName("vec4")
    data class Vec4(override val args: List<ShaderExpr>) : Construct {
        override val size: Int get() = 4
    }

    /** `param`: the value a scene set for one of the document's [ShaderDocument.parameters], or its default. */
    @Serializable
    @SerialName("param")
    data class Param(
        /** The parameter's name. */
        val name: String,
    ) : ShaderExpr

    /** `input`: a value the engine supplies each frame. */
    @Serializable
    @SerialName("input")
    data class Input(
        /** Which one. */
        val input: ShaderInput,
    ) : ShaderExpr

    /** `local`: a `let` or `var` declared earlier in the stage, or a loop's counter. */
    @Serializable
    @SerialName("local")
    data class Local(
        /** The local's name. */
        val name: String,
    ) : ShaderExpr

    /** An arithmetic operator on numbers: [Add], [Sub], [Mul] or [Div]. */
    sealed interface Arithmetic : ShaderExpr {
        /** The left operand. */
        val a: ShaderExpr

        /** The right operand. */
        val b: ShaderExpr
    }

    /** `add`: `a + b`. */
    @Serializable
    @SerialName("add")
    data class Add(override val a: ShaderExpr, override val b: ShaderExpr) : Arithmetic

    /** `sub`: `a - b`. */
    @Serializable
    @SerialName("sub")
    data class Sub(override val a: ShaderExpr, override val b: ShaderExpr) : Arithmetic

    /** `mul`: `a * b`, component by component. */
    @Serializable
    @SerialName("mul")
    data class Mul(override val a: ShaderExpr, override val b: ShaderExpr) : Arithmetic

    /** `div`: `a / b`, component by component. */
    @Serializable
    @SerialName("div")
    data class Div(override val a: ShaderExpr, override val b: ShaderExpr) : Arithmetic

    /** `neg`: `-value`. */
    @Serializable
    @SerialName("neg")
    data class Negate(
        /** The number to negate. */
        val value: ShaderExpr,
    ) : ShaderExpr

    /** A comparison of two scalars, giving a condition: [Lt], [Le], [Gt], [Ge], [Eq] or [Ne]. */
    sealed interface Comparison : ShaderExpr {
        /** The left scalar. */
        val a: ShaderExpr

        /** The right scalar. */
        val b: ShaderExpr
    }

    /** `lt`: `a < b`. */
    @Serializable
    @SerialName("lt")
    data class Lt(override val a: ShaderExpr, override val b: ShaderExpr) : Comparison

    /** `le`: `a <= b`. */
    @Serializable
    @SerialName("le")
    data class Le(override val a: ShaderExpr, override val b: ShaderExpr) : Comparison

    /** `gt`: `a > b`. */
    @Serializable
    @SerialName("gt")
    data class Gt(override val a: ShaderExpr, override val b: ShaderExpr) : Comparison

    /** `ge`: `a >= b`. */
    @Serializable
    @SerialName("ge")
    data class Ge(override val a: ShaderExpr, override val b: ShaderExpr) : Comparison

    /** `eq`: `a == b`. */
    @Serializable
    @SerialName("eq")
    data class Eq(override val a: ShaderExpr, override val b: ShaderExpr) : Comparison

    /** `ne`: `a != b`. */
    @Serializable
    @SerialName("ne")
    data class Ne(override val a: ShaderExpr, override val b: ShaderExpr) : Comparison

    /** A combination of two conditions: [And] or [Or]. */
    sealed interface Logical : ShaderExpr {
        /** The left condition. */
        val a: ShaderExpr

        /** The right condition. */
        val b: ShaderExpr
    }

    /** `and`: both conditions hold. */
    @Serializable
    @SerialName("and")
    data class And(override val a: ShaderExpr, override val b: ShaderExpr) : Logical

    /** `or`: either condition holds. */
    @Serializable
    @SerialName("or")
    data class Or(override val a: ShaderExpr, override val b: ShaderExpr) : Logical

    /** `not`: the condition does not hold. */
    @Serializable
    @SerialName("not")
    data class Not(
        /** The condition to negate. */
        val value: ShaderExpr,
    ) : ShaderExpr

    /** `swizzle`: components of a vector, picked and reordered: `xy`, `zyx`, `rgb`, `w`. */
    @Serializable
    @SerialName("swizzle")
    data class Swizzle(
        /** The vector to read. */
        val value: ShaderExpr,
        /** One to four of `xyzw`, or one to four of `rgba`, within the vector's size. */
        val components: String,
    ) : ShaderExpr

    /** `call`: a built-in function. */
    @Serializable
    @SerialName("call")
    data class Call(
        /** The function. */
        val fn: ShaderFunction,
        /** Its arguments, as [ShaderFunction] describes for each. */
        val args: List<ShaderExpr>,
    ) : ShaderExpr

    /** `sample`: the colour of one of the document's [ShaderDocument.textures] at a 2D coordinate. Fragment only. */
    @Serializable
    @SerialName("sample")
    data class Sample(
        /** The texture's name. */
        val texture: String,
        /** Where to read, 0 to 1 across the image: a 2-component vector. */
        val uv: ShaderExpr,
    ) : ShaderExpr
}

/** A value the engine supplies each frame, read with [ShaderExpr.Input]. */
@Serializable
enum class ShaderInput {
    /**
     * 2 numbers. On a plane, the position across it, 0 to 1 along X and Z. On a full-screen surface,
     * the same as [ScreenUv].
     */
    @SerialName("uv")
    Uv,

    /** 2 numbers: the pixel's position on the screen, 0 to 1 from the top-left corner. Fragment only. */
    @SerialName("screenUv")
    ScreenUv,

    /** 3 numbers: the point on the plane in world space, before any displacement. Plane only. */
    @SerialName("worldPosition")
    WorldPosition,

    /** 3 numbers: the plane's facing direction in world space, unit length. Plane only. */
    @SerialName("normal")
    Normal,

    /** 3 numbers: the unit direction from the camera through the pixel. Fragment only. */
    @SerialName("viewDirection")
    ViewDirection,

    /** 3 numbers: the camera's position in world space. */
    @SerialName("cameraPosition")
    CameraPosition,

    /** 3 numbers: the scene light's direction, as the engine's scene light reports it. */
    @SerialName("sunDirection")
    SunDirection,

    /** 1 number: seconds since the effect started. */
    @SerialName("time")
    Time,

    /** 1 number: seconds since the previous frame. */
    @SerialName("deltaTime")
    DeltaTime,

    /** 2 numbers: the render target's width and height, in pixels. */
    @SerialName("resolution")
    Resolution,
}
