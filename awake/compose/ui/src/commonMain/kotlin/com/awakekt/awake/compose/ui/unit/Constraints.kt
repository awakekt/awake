/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.unit

import kotlin.jvm.JvmInline

/**
 * Bounds a node may size itself within: device pixels, never negative, min never above max.
 *
 * Packed into one `Long` so a measure pass allocates nothing -- constraints are built and rebuilt
 * once per node per modifier link, which is the frame's highest-frequency object if it is an
 * object at all.
 *
 * 16 bits per field caps a real dimension at 65534px, with 65535 reserved for [Infinity]. A
 * viewport that large does not exist; if one ever does, widen to Compose's focus-bucket scheme
 * (18/13, 13/18, 15/16, 16/15) rather than dropping the packing.
 */
@JvmInline
value class Constraints private constructor(private val packed: Long) {

    val minWidth: Int get() = unpack(SHIFT_MIN_WIDTH)
    val maxWidth: Int get() = unpackMax(SHIFT_MAX_WIDTH)
    val minHeight: Int get() = unpack(SHIFT_MIN_HEIGHT)
    val maxHeight: Int get() = unpackMax(SHIFT_MAX_HEIGHT)

    val hasBoundedWidth: Boolean get() = unpack(SHIFT_MAX_WIDTH) != RESERVED_INFINITY
    val hasBoundedHeight: Boolean get() = unpack(SHIFT_MAX_HEIGHT) != RESERVED_INFINITY

    private fun unpack(shift: Int): Int = ((packed ushr shift) and FIELD_MASK).toInt()

    private fun unpackMax(shift: Int): Int =
        unpack(shift).let { if (it == RESERVED_INFINITY) Infinity else it }

    fun copy(
        minWidth: Int = this.minWidth,
        maxWidth: Int = this.maxWidth,
        minHeight: Int = this.minHeight,
        maxHeight: Int = this.maxHeight,
    ): Constraints = of(minWidth, maxWidth, minHeight, maxHeight)

    /**
     * Shrinks both axes by the space a wrapping modifier consumes, e.g. padding.
     *
     * Saturating, not wrapping: an unbounded axis stays unbounded and a bounded one floors at zero.
     * Compose's `Infinity` is `Int.MAX_VALUE`, so the same arithmetic there silently overflows --
     * see docs/reference/compose-engine/11-refinements.md row 1.
     */
    fun offset(dx: Int = 0, dy: Int = 0): Constraints = of(
        minWidth = (minWidth + dx).coerceAtLeast(0),
        maxWidth = if (hasBoundedWidth) (maxWidth + dx).coerceAtLeast(0) else Infinity,
        minHeight = (minHeight + dy).coerceAtLeast(0),
        maxHeight = if (hasBoundedHeight) (maxHeight + dy).coerceAtLeast(0) else Infinity,
    )

    /** Clamps a desired size into these bounds. */
    fun constrainWidth(width: Int): Int = width.coerceIn(minWidth, maxWidth)

    fun constrainHeight(height: Int): Int = height.coerceIn(minHeight, maxHeight)

    override fun toString(): String {
        val w = if (hasBoundedWidth) "$minWidth..$maxWidth" else "$minWidth..∞"
        val h = if (hasBoundedHeight) "$minHeight..$maxHeight" else "$minHeight..∞"
        return "Constraints(w=$w, h=$h)"
    }

    // PascalCase, not SCREAMING_SNAKE: `Constraints.Infinity` is the name every line of Compose
    // documentation uses, and a public constant is API surface. See 11-refinements.md rule 1.
    @Suppress("ktlint:standard:property-naming")
    companion object {
        /** No upper bound on this axis. Distinct from a large number -- see [hasBoundedWidth]. */
        const val Infinity: Int = Int.MAX_VALUE

        const val MaxDimension: Int = RESERVED_INFINITY - 1

        fun of(minWidth: Int, maxWidth: Int, minHeight: Int, maxHeight: Int): Constraints {
            val minW = requireDimension(minWidth, "minWidth")
            val minH = requireDimension(minHeight, "minHeight")
            val maxW = requireMax(maxWidth, minW, "Width")
            val maxH = requireMax(maxHeight, minH, "Height")
            return Constraints(
                (minW.toLong() shl SHIFT_MIN_WIDTH) or
                    (maxW.toLong() shl SHIFT_MAX_WIDTH) or
                    (minH.toLong() shl SHIFT_MIN_HEIGHT) or
                    maxH.toLong(),
            )
        }

        fun fixed(width: Int, height: Int): Constraints = of(width, width, height, height)

        /** Loose in both axes -- the shape a wrap-content child is measured with. */
        fun unbounded(): Constraints = of(0, Infinity, 0, Infinity)

        private fun requireDimension(value: Int, name: String): Int {
            require(value in 0..MaxDimension) {
                "$name must be in 0..$MaxDimension, was $value"
            }
            return value
        }

        /** Returns the raw 16-bit field, so [Infinity] becomes the reserved sentinel. */
        private fun requireMax(value: Int, min: Int, axis: String): Int {
            if (value == Infinity) return RESERVED_INFINITY
            require(value in 0..MaxDimension) {
                "max$axis must be in 0..$MaxDimension or Infinity, was $value"
            }
            require(value >= min) { "max$axis ($value) must be >= min$axis ($min)" }
            return value
        }
    }
}

private const val FIELD_MASK = 0xFFFFL
private const val RESERVED_INFINITY = 0xFFFF
private const val SHIFT_MIN_WIDTH = 48
private const val SHIFT_MAX_WIDTH = 32
private const val SHIFT_MIN_HEIGHT = 16
private const val SHIFT_MAX_HEIGHT = 0
