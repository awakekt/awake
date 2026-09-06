/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.foundation.layout

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.unit.Constraints

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
