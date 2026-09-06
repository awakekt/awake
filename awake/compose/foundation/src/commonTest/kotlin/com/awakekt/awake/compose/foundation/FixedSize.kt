/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.unit.Constraints

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
