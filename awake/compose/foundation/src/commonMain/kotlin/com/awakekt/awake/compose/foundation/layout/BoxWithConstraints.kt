/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.foundation.layout

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.layout.Placeable
import com.awakekt.awake.compose.ui.layout.SubcomposeLayout
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.Density
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.IntSize

/**
 * Receiver scope for [BoxWithConstraints], exposing the incoming constraints in both device pixels
 * and density-independent pixels ([Dp]).
 */
@LayoutScopeMarker
interface BoxWithConstraintsScope : BoxScope {
    val constraints: Constraints
    val minWidth: Dp
    val maxWidth: Dp
    val minHeight: Dp
    val maxHeight: Dp
}

internal class BoxWithConstraintsScopeImpl(
    private val density: Density,
    override val constraints: Constraints,
) : BoxWithConstraintsScope,
    BoxScope by BoxScopeInstance {
    override val minWidth: Dp get() = with(density) { constraints.minWidth.toDp() }
    override val maxWidth: Dp get() = with(density) { constraints.maxWidth.toDp() }
    override val minHeight: Dp get() = with(density) { constraints.minHeight.toDp() }
    override val maxHeight: Dp get() = with(density) { constraints.maxHeight.toDp() }
}

/**
 * A composable that provides its measured constraints to its [content] lambda.
 *
 * Built on [SubcomposeLayout], subcomposing during the measure pass so that responsive branching
 * has zero frame delay.
 */
context(composer: Composer)
fun BoxWithConstraints(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    propagateMinConstraints: Boolean = false,
    content: context(Composer) BoxWithConstraintsScope.() -> Unit,
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val scope = BoxWithConstraintsScopeImpl(this, constraints)
        val measurables = subcompose(Unit) {
            scope.content()
        }

        val childConstraints = if (propagateMinConstraints) {
            constraints
        } else {
            Constraints.of(
                0,
                constraints.maxWidth,
                0,
                constraints.maxHeight,
            )
        }

        val count = measurables.size
        val placeables = arrayOfNulls<Placeable>(count)
        var maxWidth = 0
        var maxHeight = 0
        for (i in 0 until count) {
            val placeable = measurables[i].measure(childConstraints)
            placeables[i] = placeable
            maxWidth = maxOf(maxWidth, placeable.width)
            maxHeight = maxOf(maxHeight, placeable.height)
        }

        val width = constraints.constrainWidth(maxWidth)
        val height = constraints.constrainHeight(maxHeight)

        layout(width, height) {
            val space = IntSize(width, height)
            for (i in 0 until count) {
                val placeable = placeables[i] ?: continue
                // A child's own alignment wins, exactly as in `Box` -- this scope is a `BoxScope`,
                // so `align` has to mean the same thing here as it does there.
                val anchor = measurables[i].childAlignment() ?: contentAlignment
                val offset = anchor.align(IntSize(placeable.width, placeable.height), space)
                placeable.placeAt(offset.x, offset.y)
            }
        }
    }
}
