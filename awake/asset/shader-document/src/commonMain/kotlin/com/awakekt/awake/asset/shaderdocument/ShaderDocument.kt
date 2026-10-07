/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A shader as project data: a restricted expression graph that a published project can ship and
 * that Core compiles, so a project brings its own look with no Kotlin and no change to Core.
 *
 * It is a subset of ASL, Awake's shader DSL, chosen so that untrusted input stays safe to run on other
 * people's GPUs. It has no functions, no storage buffers, no raw WGSL and no binding numbers, and its
 * loops have literal bounds. [ShaderDocuments.compile] checks a document against [ShaderDocumentLimits]
 * before anything compiles, and every name in it is replaced by a generated identifier, so no string
 * from the file reaches the shader source.
 *
 * A document draws one [surface]: a full-screen [ShaderSurface.Background] or
 * [ShaderSurface.Overlay], or a [ShaderSurface.Plane] placed in the scene by its entity. Its
 * [fragment] stage gives each pixel's colour from [parameters], [textures], engine inputs
 * ([ShaderInput]) and the expressions in [ShaderExpr].
 */
@Serializable
data class ShaderDocument(
    /** The JSON Schema the file names, for editors. Loading ignores it. */
    @SerialName("\$schema") val schema: String? = null,
    /** The format version. Only [CURRENT_FORMAT_VERSION] loads. */
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    /** A display name for logs and tools. It never reaches the shader source. */
    val name: String,
    /** What the document draws: the whole screen behind or over the scene, or a plane in it. */
    val surface: ShaderSurface,
    /** How the result combines with what is drawn already, or null for the [surface]'s own default. */
    val blend: ShaderBlend? = null,
    /** The rectangle a [ShaderSurface.Plane] draws. Required for that surface, absent for the others. */
    val plane: ShaderPlane? = null,
    /** Values a scene sets by name, each with a default. */
    val parameters: List<ShaderParameter> = emptyList(),
    /** Images a scene supplies by name, read with [ShaderExpr.Sample]. */
    val textures: List<ShaderTexture> = emptyList(),
    /** A plane's vertex stage, which can move each vertex along the plane's normal. Plane only. */
    val vertex: ShaderVertexStage? = null,
    /** The fragment stage: the colour of each pixel. */
    val fragment: ShaderFragmentStage,
) {
    /** The format constants. */
    companion object {
        /** The only format version this module reads. */
        const val CURRENT_FORMAT_VERSION: Int = 1
    }
}

/** What a [ShaderDocument] draws over. */
@Serializable
enum class ShaderSurface {
    /** The whole screen, behind the scene, like a sky: drawn before scene geometry with no depth test. */
    @SerialName("background")
    Background,

    /** The whole screen, over the scene: alpha-blended over what is drawn, with no depth test. */
    @SerialName("overlay")
    Overlay,

    /** A flat rectangle in the scene, placed by its entity and depth-tested against scene geometry. */
    @SerialName("plane")
    Plane,
}

/** How a [ShaderDocument]'s colour combines with what is drawn already. */
@Serializable
enum class ShaderBlend {
    /** Replaces what is behind. The default for a background and a plane, and the only blend a background takes. */
    @SerialName("opaque")
    Opaque,

    /** Mixes by the colour's alpha. The default for an overlay, and the only blend it takes. */
    @SerialName("alpha")
    Alpha,

    /** Adds the colour to what is behind, for glows and light effects. Plane only. */
    @SerialName("additive")
    Additive,
}

/** The rectangle a [ShaderSurface.Plane] draws, lying in its entity's XZ plane and facing +Y. */
@Serializable
data class ShaderPlane(
    /** Width along X and depth along Z, in metres, centred on the entity: two positive numbers. */
    val size: List<Float>,
    /** Quads along each side. More of them give a vertex displacement more points to move. */
    val segments: Int = 1,
)

/** A value a scene sets by [name], used in expressions through [ShaderExpr.Param]. */
@Serializable
data class ShaderParameter(
    /** The name a scene sets it by: a letter, then letters, digits or underscores, 32 at most. */
    val name: String,
    /** What it holds. */
    val type: ShaderValueType,
    /** The value when a scene sets none: as many numbers as [type] holds. */
    val default: List<Float>,
)

/** What a [ShaderParameter] holds. */
@Serializable
enum class ShaderValueType {
    /** One number. */
    @SerialName("float")
    Float,

    /** Two numbers. */
    @SerialName("vec2")
    Vec2,

    /** Three numbers. */
    @SerialName("vec3")
    Vec3,

    /** Four numbers. */
    @SerialName("vec4")
    Vec4,

    /** Four numbers, red, green, blue and alpha. A [Vec4] that tools show as a colour. */
    @SerialName("color")
    Color,
}

/** An image a scene supplies by [name], read with [ShaderExpr.Sample]. */
@Serializable
data class ShaderTexture(
    /** The name a scene supplies it by: a letter, then letters, digits or underscores, 32 at most. */
    val name: String,
)

/** A [ShaderSurface.Plane]'s vertex stage. */
@Serializable
data class ShaderVertexStage(
    /** Locals and loops the [displacement] reads. */
    val statements: List<ShaderStatement> = emptyList(),
    /** How far to move each vertex along the plane's normal, in metres: one number, or none. */
    val displacement: ShaderExpr? = null,
)

/** The fragment stage: the colour of each pixel. */
@Serializable
data class ShaderFragmentStage(
    /** Locals, loops, branches and discards, run in order before [color]. */
    val statements: List<ShaderStatement> = emptyList(),
    /** The pixel's colour: four numbers, red, green, blue and alpha. */
    val color: ShaderExpr,
)
