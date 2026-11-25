/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.foundation.layout

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.Slot
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.node.LayoutNode

// PascalCase composables: Compose's own convention, and this is public API surface -- the same
// reason Constraints.Infinity keeps its casing. See 11-refinements.md rule 1.

private object ColumnNodeType

private object RowNodeType

@PublishedApi
context(composer: Composer)
internal fun openColumnNode(
    modifier: Modifier,
    verticalArrangement: Arrangement.Vertical,
    horizontalAlignment: Alignment.Horizontal,
): Any? {
    val node = composer.startNode(ColumnNodeType, Slot.Children) as LayoutNode
    val previous = node.measurePolicy as? ColumnMeasurePolicy
    val policy = if (previous != null &&
        sameLayoutValue(previous.verticalArrangement, verticalArrangement) &&
        sameLayoutValue(previous.horizontalAlignment, horizontalAlignment)
    ) {
        previous
    } else {
        StandardLayoutPolicies.column(verticalArrangement, horizontalAlignment)
            ?: ColumnMeasurePolicy(verticalArrangement, horizontalAlignment)
    }
    configureLayoutNode(node, modifier, policy)
    return composer.enterNode()
}

@PublishedApi
internal fun columnScopeInstance(): ColumnScope = ColumnScopeInstance

@PublishedApi
context(composer: Composer)
internal fun openRowNode(
    modifier: Modifier,
    horizontalArrangement: Arrangement.Horizontal,
    verticalAlignment: Alignment.Vertical,
): Any? {
    val node = composer.startNode(RowNodeType, Slot.Children) as LayoutNode
    val previous = node.measurePolicy as? RowMeasurePolicy
    val policy = if (previous != null &&
        sameLayoutValue(previous.horizontalArrangement, horizontalArrangement) &&
        sameLayoutValue(previous.verticalAlignment, verticalAlignment)
    ) {
        previous
    } else {
        StandardLayoutPolicies.row(horizontalArrangement, verticalAlignment)
            ?: RowMeasurePolicy(horizontalArrangement, verticalAlignment)
    }
    configureLayoutNode(node, modifier, policy)
    return composer.enterNode()
}

@PublishedApi
internal fun rowScopeInstance(): RowScope = RowScopeInstance

context(composer: Composer)
/**
 * Stacks children vertically.
 *
 * [content] runs with [ColumnScope] as its receiver, which is what makes `weight()` available here
 * and a compile error anywhere else. Inlined so steady-state callers allocate no capturing lambda.
 */
inline fun Column(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    crossinline content: context(Composer) ColumnScope.() -> Unit,
) {
    val enclosingKey = openColumnNode(modifier, verticalArrangement, horizontalAlignment)
    content(composer, columnScopeInstance())
    composer.exitNode(Slot.Children, enclosingKey)
}

context(composer: Composer)
/**
 * Stacks children horizontally. See [Column] for why the scope is a receiver and why [content] is
 * inlined.
 */
inline fun Row(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    crossinline content: context(Composer) RowScope.() -> Unit,
) {
    val enclosingKey = openRowNode(modifier, horizontalArrangement, verticalAlignment)
    content(composer, rowScopeInstance())
    composer.exitNode(Slot.Children, enclosingKey)
}

context(composer: Composer)
/**
 * An empty box that carries [modifier] (for example a background, border, or fixed size) without
 * children.
 */
fun Box(modifier: Modifier = Modifier) {
    emitEmptyBoxNode(modifier)
}

context(composer: Composer)
/**
 * Stacks children on top of each other, sized to the largest.
 *
 * Shrink-wraps to content, matching Compose. The removed `ui-core`'s `box()` filled instead,
 * a divergence from Compose this engine did not carry forward. Inlined so steady-state callers
 * allocate no capturing lambda.
 */
inline fun Box(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    crossinline content: context(Composer) BoxScope.() -> Unit,
) {
    // The scope is a receiver for the same reason Column's is: it makes matchParentSize()
    // available here and a compile error anywhere it would silently do nothing.
    val enclosingKey = openBoxNode(modifier, contentAlignment)
    content(composer, boxScopeInstance())
    composer.exitNode(Slot.Children, enclosingKey)
}

context(composer: Composer)
/**
 * Stacks children on top of each other, aligned by a separate [horizontalAlignment] and
 * [verticalAlignment] instead of one [Alignment].
 *
 * Otherwise the same as the overload that takes a single content alignment.
 */
inline fun Box(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    crossinline content: context(Composer) BoxScope.() -> Unit,
) {
    val enclosingKey = openAxisBoxNode(modifier, horizontalAlignment, verticalAlignment)
    content(composer, boxScopeInstance())
    composer.exitNode(Slot.Children, enclosingKey)
}
