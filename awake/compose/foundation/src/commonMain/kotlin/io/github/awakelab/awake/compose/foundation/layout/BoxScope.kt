/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation.layout

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
}

internal object BoxScopeInstance : BoxScope {
    override fun Modifier.matchParentSize(): Modifier = this then MatchParentSizeElement
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
