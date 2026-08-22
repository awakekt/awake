// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui.node

import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.graphics.drawscope.DrawScope
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEvent
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEventPass
import io.github.ronjunevaldoz.awake.compose.ui.layout.IntrinsicMeasurable
import io.github.ronjunevaldoz.awake.compose.ui.layout.Measurable
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureResult
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureScope
import io.github.ronjunevaldoz.awake.compose.ui.layout.onPlaced
import io.github.ronjunevaldoz.awake.compose.ui.semantics.SemanticsConfiguration
import io.github.ronjunevaldoz.awake.compose.ui.text.input.EditCommand
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints
import io.github.ronjunevaldoz.awake.compose.ui.unit.Density

// Every `*ModifierNode` interface lives here, as in Compose: a modifier's link type is a node
// concern, while `Modifier` itself is only the chain.

/**
 * A link that participates in measurement: it may tighten or loosen what it passes inward, and
 * grow or shrink what it reports outward.
 *
 * [measurable] is the rest of the chain, ending at the node's own [MeasurePolicy]. Measure it
 * exactly once -- the same contract [Measurable] carries.
 */
interface LayoutModifierNode : Modifier.Element {
    fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult

    /**
     * How this link changes what the rest of the chain would ask for.
     *
     * Defaults pass straight through, which is right for anything that only paints. A link that
     * changes geometry -- padding, size -- has to override, or an intrinsic query walks past it and
     * reports the inner size as if the link were not there.
     */
    fun Density.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        measurable.minIntrinsicWidth(height)

    fun Density.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        measurable.maxIntrinsicWidth(height)

    fun Density.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        measurable.minIntrinsicHeight(width)

    fun Density.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        measurable.maxIntrinsicHeight(width)
}

/**
 * A link that paints, around whatever the rest of the chain paints.
 *
 * [drawContent] is the rest of the chain plus the node's own children -- call it where the wrapped
 * content should appear. A background calls it last; an overlay calls it first.
 */
interface DrawModifierNode : Modifier.Element {
    fun DrawScope.draw(drawContent: () -> Unit)
}

/**
 * A link told where its node ended up, once placement has resolved it.
 *
 * Replaces reading a container's returned rect, which is what `ui-core` offers and what 46 call
 * sites do today. A returned rect *is* the immediate-mode model -- it can only exist because the
 * container already knows its geometry mid-build -- so the retained engine reports afterwards
 * instead.
 */
interface OnPlacedModifierNode : Modifier.Element {
    /** Tree-space position and resolved size. Called after every layout pass. */
    fun onPlaced(x: Int, y: Int, width: Int, height: Int)
}

/**
 * A link that hands data upward to the parent's [MeasurePolicy] -- how `weight()` and `align()`
 * reach a Row or Column without the parent reaching into its children.
 */
interface ParentDataModifierNode : Modifier.Element {
    fun modifyParentData(current: Any?): Any?
}

/** A modifier link that reacts to pointer input. */
interface PointerInputNode : Modifier.Element {
    fun onPointerEvent(event: PointerEvent, pass: PointerEventPass)
}

/** A modifier link that contributes semantics. */
interface SemanticsModifierNode : Modifier.Element {
    val semanticsConfiguration: SemanticsConfiguration
}

/**
 * A link that can hold focus.
 *
 * Focus is identified by the node rather than by a string, which is the whole change from
 * `UiRuntimeCoordinator`'s single `focusedId: String?`: two widgets could pick the same id and
 * silently steal each other's focus, and there was no tree to derive a traversal order from.
 */
interface FocusTargetNode : Modifier.Element {
    /** False for a disabled control, which stays in the tree but out of the traversal ring. */
    val canFocus: Boolean

    fun onFocusChanged(focused: Boolean)
}

/**
 * A link that needs a handle on the node it decorates.
 *
 * Rare by design -- most links are told everything they need through their own callback. A focus
 * requester is the exception: it exists to point at a node the caller has no other reference to.
 */
interface NodeAttachedModifierNode : Modifier.Element {
    fun onAttachedTo(node: LayoutNode)
}

/**
 * A link that can consume scroll.
 *
 * Exists so `:ui` can answer "is the pointer over something scrollable" without knowing what a
 * scroller is -- `verticalScroll` lives in `:foundation`, as in Compose. The frame reports it so
 * gameplay knows the wheel was not meant for it.
 */
interface ScrollableNode : Modifier.Element {
    /** False at both ends, so an outer scrollable can take over at the boundary. */
    val canScroll: Boolean
}

/**
 * A link that accepts keyboard text.
 *
 * The seam that lets `:ui` route typing without knowing what a text field is -- `BasicTextField`
 * lives in `:foundation`, as in Compose. Its presence on the focused node is also what makes
 * `FrameOutput.ownership.isTextInputFocused` true, which is how gameplay knows W/A/S/D is being
 * typed rather than walking the player.
 */
interface TextInputNode : Modifier.Element {
    fun onTextTyped(text: String)

    fun onEditCommand(command: EditCommand)
}
