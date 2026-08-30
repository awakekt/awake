/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.compose.foundation.layout

import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.layout.Layout
import io.github.awakelab.awake.compose.ui.layout.Measurable
import io.github.awakelab.awake.compose.ui.layout.MeasurePolicy
import io.github.awakelab.awake.compose.ui.layout.MeasureResult
import io.github.awakelab.awake.compose.ui.layout.MeasureScope
import io.github.awakelab.awake.compose.ui.unit.Constraints

// PascalCase composables: Compose's own convention, and this is public API surface. See
// 11-refinements.md rule 1.

private object SpacerNodeType

/**
 * Takes up space and draws nothing.
 *
 * Sizes to the **minimum** its constraints allow, so `Modifier.size`/`weight`/`fillMax` decide what
 * it occupies rather than the widget having an opinion of its own.
 */
context(_: Composer)
fun Spacer(modifier: Modifier = Modifier) {
    Layout(SpacerNodeType, modifier = modifier, measurePolicy = EmptyLayoutPolicy)
}

/** Sizes to the smallest the constraints allow and places nothing. Shared with `Canvas`. */
internal object EmptyLayoutPolicy : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult = layout(constraints.minWidth, constraints.minHeight) {}
}
