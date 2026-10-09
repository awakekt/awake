/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.layout

import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.Density
import kotlin.math.roundToInt

/**
 * Something that can be measured exactly once per layout pass.
 *
 * "Exactly once" is the contract the whole engine exists for -- `ui-core` learns a size by
 * re-running a content lambda, which is what multiplies with nesting. A second [measure] call on
 * the same pass is a bug, not a slow path.
 */
interface IntrinsicMeasurable {
    /** Set by a [ParentDataModifierNode] -- how `weight()` and `align()` reach the parent policy. */
    val parentData: Any?
        get() = null

    /** The narrowest this can be without its content failing to fit -- a word, not a paragraph. */
    fun minIntrinsicWidth(height: Int): Int

    /** The widest it would like to be if nothing constrained it -- the line, unwrapped. */
    fun maxIntrinsicWidth(height: Int): Int

    /** The shortest this can be, at the given [width], without its content failing to fit. */
    fun minIntrinsicHeight(width: Int): Int

    /** The tallest it would like to be, at the given [width], if nothing constrained it. */
    fun maxIntrinsicHeight(width: Int): Int
}

/** An [IntrinsicMeasurable] that can also be measured, at most once per layout pass. */
interface Measurable : IntrinsicMeasurable {
    /** Measures within [constraints] and returns the sized [Placeable]. */
    fun measure(constraints: Constraints): Placeable
}

/** A measured thing, awaiting a position. */
abstract class Placeable {
    /** The measured width in pixels. */
    var width: Int = 0
        protected set

    /** The measured height in pixels. */
    var height: Int = 0
        protected set

    /** Position relative to the parent that is placing it. Absolutes are resolved by the place
     * walk, so a policy never needs to know where it sits in the tree. */
    var x: Int = 0
        private set

    /** Vertical counterpart of [x]. */
    var y: Int = 0
        private set

    protected fun setMeasuredSize(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    /** Positions this at [x], [y] relative to its parent, then places whatever it deferred. */
    fun placeAt(x: Int, y: Int) {
        this.x = x
        this.y = y
        onPlaced()
    }

    protected open fun onPlaced() = Unit

    /** Returns the position of the requested alignment line within this placeable, or [AlignmentLine.Unspecified]. */
    open operator fun get(alignmentLine: AlignmentLine): Int = AlignmentLine.Unspecified
}

/** The size a policy chose, plus the placement it deferred. */
interface MeasureResult {
    /** The width the policy chose, in pixels. */
    val width: Int

    /** The height the policy chose, in pixels. */
    val height: Int

    /** Positions of this layout's alignment lines, in pixels; empty unless the policy reports any. */
    val alignmentLines: Map<AlignmentLine, Int>
        get() = emptyMap()

    /** Runs the placement block the policy deferred, positioning its children. */
    fun placeChildren()
}

/** Receiver for the `layout(w, h) { … }` block. Exists so placement can only happen there. */
interface PlacementScope {
    /** Width in pixels of the layout being placed into, which right-to-left placement mirrors against. */
    val parentWidth: Int get() = 0

    /** Layout direction that decides whether [placeRelativeAt] mirrors. */
    val parentLayoutDirection: com.awakekt.awake.compose.ui.unit.LayoutDirection
        get() = com.awakekt.awake.compose.ui.unit.LayoutDirection.Ltr

    /** Places this at [x] pixels from the start edge, mirroring in right-to-left layouts, and at [y] from the top. */
    fun Placeable.placeRelativeAt(x: Int, y: Int) {
        val targetX = if (parentLayoutDirection == com.awakekt.awake.compose.ui.unit.LayoutDirection.Ltr) {
            x
        } else {
            parentWidth - width - x
        }
        placeAt(targetX, y)
    }

    /** Places this at [x], [y] pixels from the left and top edges, whatever the layout direction. */
    fun Placeable.placeAbsoluteAt(x: Int, y: Int) {
        placeAt(x, y)
    }
}

/** Receiver of a [MeasurePolicy]: dp conversion from [Density], plus the layout direction and [layout]. */
interface MeasureScope : Density {
    /** The layout direction of the node being measured. */
    val layoutDirection: com.awakekt.awake.compose.ui.unit.LayoutDirection
        get() = com.awakekt.awake.compose.ui.unit.LayoutDirection.Ltr

    /**
     * Reports the size the policy chose and defers positioning to [place].
     *
     * [place] runs later, during placement, with a [PlacementScope]. The returned result may be
     * reused by the next call on the same scope, so do not keep it past the pass.
     */
    fun layout(
        width: Int,
        height: Int,
        alignmentLines: Map<AlignmentLine, Int> = emptyMap(),
        place: PlacementScope.() -> Unit,
    ): MeasureResult
}

/**
 * How a layout sizes and positions its children.
 *
 * Receives every child as a [Measurable] and must call [Measurable.measure] on each at most once.
 */
fun interface MeasurePolicy {
    /** Measures [measurables] within [constraints] and returns the layout's size and deferred placement. */
    fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult

    /**
     * How large this layout needs to be, asked without committing to a measure.
     *
     * The defaults answer as a Box would -- the largest child wins on both axes. A layout whose
     * geometry differs **must** override them, and Row and Column do. A wrong-but-plausible default
     * is the failure `awake-ui-performance` Rule 4 keeps describing, so [LayoutStats] counts every
     * query: an intrinsic asked of a policy that never overrode these surfaces as a number rather
     * than as a subtly wrong width nobody attributes.
     */
    fun Density.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.maxOfOrNull { it.minIntrinsicWidth(height) } ?: 0

    /** The widest this layout would like to be at the given [height]; see [minIntrinsicWidth]. */
    fun Density.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.maxOfOrNull { it.maxIntrinsicWidth(height) } ?: 0

    /** The shortest this layout can be at the given [width]; see [minIntrinsicWidth]. */
    fun Density.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurables.maxOfOrNull { it.minIntrinsicHeight(width) } ?: 0

    /** The tallest this layout would like to be at the given [width]; see [minIntrinsicWidth]. */
    fun Density.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurables.maxOfOrNull { it.maxIntrinsicHeight(width) } ?: 0
}
