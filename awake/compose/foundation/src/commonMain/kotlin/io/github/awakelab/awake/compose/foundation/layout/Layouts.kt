/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.compose.foundation.layout

import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.layout.Layout

// PascalCase composables: Compose's own convention, and this is public API surface -- the same
// reason Constraints.Infinity keeps its casing. See 11-refinements.md rule 1.

private object ColumnNodeType

private object RowNodeType

private object BoxNodeType

// A policy is immutable and depends only on its arrangement and alignment, but the composable
// rebuilt one -- plus the RowColumnMeasurePolicy it delegates to -- on every node on every frame.
// Caching the default shape covers the overwhelmingly common call. Stage 2's `remember` replaces
// this with the general answer; until then a non-default arrangement still allocates.
private val defaultColumnPolicy = ColumnMeasurePolicy()

private val defaultRowPolicy = RowMeasurePolicy()

private val defaultBoxPolicy = BoxMeasurePolicy()

/**
 * Stacks children vertically.
 *
 * [content] runs with [ColumnScope] as its receiver, which is what makes `weight()` available here
 * and a compile error anywhere else.
 */
context(composer: Composer)
fun Column(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: context(Composer) ColumnScope.() -> Unit,
) {
    Layout(
        nodeType = ColumnNodeType,
        modifier = modifier,
        measurePolicy = if (verticalArrangement === Arrangement.Top && horizontalAlignment === Alignment.Start) {
            defaultColumnPolicy
        } else {
            ColumnMeasurePolicy(verticalArrangement, horizontalAlignment)
        },
        content = { content(composer, ColumnScopeInstance) },
    )
}

/** Stacks children horizontally. See [Column] for why the scope is a receiver. */
context(composer: Composer)
fun Row(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: context(Composer) RowScope.() -> Unit,
) {
    Layout(
        nodeType = RowNodeType,
        modifier = modifier,
        measurePolicy = if (horizontalArrangement === Arrangement.Start && verticalAlignment === Alignment.Top) {
            defaultRowPolicy
        } else {
            RowMeasurePolicy(horizontalArrangement, verticalAlignment)
        },
        content = { content(composer, RowScopeInstance) },
    )
}

/**
 * Stacks children on top of each other, sized to the largest.
 *
 * Shrink-wraps to content, matching Compose. `ui-core`'s `box()` fills instead, which
 * `mirror-map.md` records as a divergence and this engine does not carry forward.
 */
context(composer: Composer)
fun Box(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: (
        context(Composer)
        BoxScope.() -> Unit
    )? = null,
) {
    Layout(
        nodeType = BoxNodeType,
        modifier = modifier,
        measurePolicy = if (contentAlignment === Alignment.TopStart) {
            defaultBoxPolicy
        } else {
            BoxMeasurePolicy(contentAlignment)
        },
        // The scope is a receiver for the same reason Column's is: it makes matchParentSize()
        // available here and a compile error anywhere it would silently do nothing.
        content = content?.let { body -> { body(composer, BoxScopeInstance) } },
    )
}

context(_: Composer)
fun Box(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: (
        context(Composer)
        BoxScope.() -> Unit
    )? = null,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment { size, space, layoutDirection ->
            io.github.awakelab.awake.compose.ui.unit.IntOffset(
                horizontalAlignment.align(size.width, space.width, layoutDirection),
                verticalAlignment.align(size.height, space.height),
            )
        },
        content = content,
    )
}
