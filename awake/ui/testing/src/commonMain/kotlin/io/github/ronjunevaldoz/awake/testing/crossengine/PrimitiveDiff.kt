// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.testing.crossengine

import io.github.ronjunevaldoz.awake.core.graphics2d.UiDrawPrimitive

/** One field of one primitive that the two engines disagree about. */
data class Divergence(
    val index: Int,
    val field: String,
    val reference: Any?,
    val candidate: Any?,
) {
    override fun toString(): String = "[$index] $field: reference=$reference candidate=$candidate"
}

/**
 * Compares two engines' draw output primitive by primitive, field by field.
 *
 * The point of comparing *fields* rather than whole objects: a whole-object mismatch prints two
 * long lines and leaves a reader to spot the one number that moved. `[1] y: 48.0 vs 40.0` names it.
 *
 * Deliberately knows nothing about either engine -- it takes two `UiDrawPrimitive` lists, which is
 * the type both already produce. That is what lets one scene be run through `ui-core` and
 * `:awake:compose:*` and diffed without either module depending on the other.
 */
object PrimitiveDiff {

    /**
     * Every field-level disagreement, in output order.
     *
     * A length mismatch is reported as `count` and stops the walk: two lists of different lengths
     * have no meaningful pairwise alignment, and reporting every subsequent index as different
     * would bury the one fact that matters.
     */
    fun compare(
        reference: List<UiDrawPrimitive>,
        candidate: List<UiDrawPrimitive>,
    ): List<Divergence> {
        if (reference.size != candidate.size) {
            return listOf(Divergence(-1, "count", reference.size, candidate.size))
        }
        val out = mutableListOf<Divergence>()
        for (i in reference.indices) {
            val a = fieldsOf(reference[i])
            val b = fieldsOf(candidate[i])
            if (a["type"] != b["type"]) {
                out += Divergence(i, "type", a["type"], b["type"])
                continue
            }
            for ((key, value) in a) {
                if (b[key] != value) out += Divergence(i, key, value, b[key])
            }
        }
        return out
    }

    /**
     * A primitive flattened to named fields.
     *
     * Anything not listed here is invisible to the diff, so a new primitive kind must be added or
     * it compares equal on `type` alone and nothing else -- the silent-pass failure this repo keeps
     * hitting. [UNCOMPARED] makes that loud instead.
     */
    @Suppress("CyclomaticComplexMethod")
    fun fieldsOf(primitive: UiDrawPrimitive): Map<String, Any?> = when (primitive) {
        is UiDrawPrimitive.Quad -> mapOf(
            "type" to "Quad",
            "x" to primitive.x,
            "y" to primitive.y,
            "w" to primitive.w,
            "h" to primitive.h,
            "color" to primitive.color,
            "transform" to primitive.transform,
        )
        is UiDrawPrimitive.RoundedQuad -> mapOf(
            "type" to "RoundedQuad",
            "x" to primitive.x,
            "y" to primitive.y,
            "w" to primitive.w,
            "h" to primitive.h,
            "color" to primitive.color,
            "radius" to primitive.radius,
        )
        is UiDrawPrimitive.GradientQuad -> mapOf(
            "type" to "GradientQuad",
            "x" to primitive.x,
            "y" to primitive.y,
            "w" to primitive.w,
            "h" to primitive.h,
            "gradient" to primitive.gradient,
        )
        is UiDrawPrimitive.Glyph -> mapOf(
            "type" to "Glyph",
            "x" to primitive.x,
            "y" to primitive.y,
            "w" to primitive.w,
            "h" to primitive.h,
            "color" to primitive.color,
        )
        is UiDrawPrimitive.ShadowQuad -> mapOf(
            "type" to "ShadowQuad",
            "x" to primitive.x,
            "y" to primitive.y,
            "w" to primitive.w,
            "h" to primitive.h,
            "color" to primitive.color,
            "radius" to primitive.radius,
        )
        is UiDrawPrimitive.ClipPush -> mapOf("type" to "ClipPush", "rect" to primitive.rect)
        is UiDrawPrimitive.ClipPop -> mapOf("type" to "ClipPop", "rect" to primitive.restoreRect)
        else -> mapOf("type" to primitive::class.simpleName, "fields" to UNCOMPARED)
    }

    /** Stands in for a primitive kind nobody has taught the diff to read yet. */
    const val UNCOMPARED: String = "<not compared -- add it to PrimitiveDiff.fieldsOf>"
}

/**
 * A divergence the engines are *expected* to have, with the reason it is allowed.
 *
 * Every divergence has to be classified as one of these or it fails the diff. An unexplained
 * difference between the engine being replaced and its replacement is the whole thing this tool
 * exists to surface, so "known" must be written down rather than assumed.
 */
data class ExpectedDivergence(
    val field: String,
    val because: String,
)

/** Divergences left after removing the ones [expected] accounts for. */
fun List<Divergence>.unexplained(expected: List<ExpectedDivergence>): List<Divergence> =
    filter { divergence -> expected.none { it.field == divergence.field } }
