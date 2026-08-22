// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation

import io.github.ronjunevaldoz.awake.compose.ui.layout.Measurable
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasurePolicy
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureResult
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureScope
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints

/** Reports a fixed size, clamped by whatever constraints it is handed, and counts its measures. */
internal class FixedSize(private val width: Int, private val height: Int) : MeasurePolicy {
    var measureCount = 0
        private set

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        measureCount++
        return layout(constraints.constrainWidth(width), constraints.constrainHeight(height)) {}
    }
}
