/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation.layout

import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.ModifierNodeElement
import io.github.awakelab.awake.compose.ui.layout.Measurable
import io.github.awakelab.awake.compose.ui.node.ParentDataModifierNode

/** What a [Box] makes available to its children, and nothing else can offer. */
interface BoxScope {
    /**
     * Sizes this child to the box, without the box sizing itself to this child.
     *
     * The difference from `fillMaxSize` is which way the sizing flows. `fillMaxSize` takes the
     * incoming maximum, so a shrink-wrapping Box grows to it: an overlay that covers its siblings
     * made the Box report its parent's whole area, and the control's measured size then depended on
     * whether the overlay was there at all. This child is measured *after* the box has decided,
     * against exactly the size its siblings produced, and contributes nothing to that decision.
     *
     * This is what CSS `position: absolute; inset: 0` does, and it is why upstream's OTP input can
     * cover its slots without being what defines their extent.
     */
    fun Modifier.matchParentSize(): Modifier

    /**
     * Places this child at [alignment] instead of the box's own `contentAlignment`.
     *
     * What CSS's `position: absolute` plus an inset does, and what a viewport's floating chrome
     * needs: tools in one corner, display toggles centred on an edge, a gizmo in another corner,
     * all over a scene that keeps its whole area. Without it a Box can only stack every child at
     * one anchor, and a panel wanting five has to spend layout bands on rows and weights -- which
     * is a band of the 3D view it can no longer draw in.
     */
    fun Modifier.align(alignment: Alignment): Modifier
}

internal object BoxScopeInstance : BoxScope {
    override fun Modifier.matchParentSize(): Modifier = this then MatchParentSizeElement

    override fun Modifier.align(alignment: Alignment): Modifier = this then BoxChildAlignElement(alignment)
}

/**
 * Marks a child as sized by the box.
 *
 * A marker rather than a value: there is nothing to configure, so the same instance serves every
 * child and reconciling one never allocates.
 */
internal object MatchParentSizeParentData

private object MatchParentSizeElement : ModifierNodeElement<MatchParentSizeNode>() {
    override fun create(): MatchParentSizeNode = MatchParentSizeNode()
    override fun update(node: MatchParentSizeNode) = Unit
    override fun toString(): String = "matchParentSize()"
}

private class MatchParentSizeNode : Modifier.Node(), ParentDataModifierNode {
    override fun modifyParentData(current: Any?): Any = MatchParentSizeParentData
    override fun toString(): String = "matchParentSize()"
}

internal fun Measurable.matchesParentSize(): Boolean = parentData === MatchParentSizeParentData

/** A child's own alignment, or null when it follows the box's [contentAlignment]. */
internal fun Measurable.childAlignment(): Alignment? = (parentData as? BoxChildAlignment)?.alignment

/** Carries one child's alignment to the measure policy. */
internal data class BoxChildAlignment(val alignment: Alignment)

private data class BoxChildAlignElement(val alignment: Alignment) : ModifierNodeElement<BoxChildAlignNode>() {
    override fun create(): BoxChildAlignNode = BoxChildAlignNode(alignment)
    override fun update(node: BoxChildAlignNode) {
        node.alignment = alignment
    }

    override fun toString(): String = "align($alignment)"
}

private class BoxChildAlignNode(var alignment: Alignment) : Modifier.Node(), ParentDataModifierNode {
    override fun modifyParentData(current: Any?): Any = BoxChildAlignment(alignment)
    override fun toString(): String = "align($alignment)"
}
