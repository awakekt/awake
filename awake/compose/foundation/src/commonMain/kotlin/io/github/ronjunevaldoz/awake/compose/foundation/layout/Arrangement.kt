// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation.layout

import io.github.ronjunevaldoz.awake.compose.ui.unit.Dp
import io.github.ronjunevaldoz.awake.compose.ui.unit.dp

/**
 * Spacing between a Row's or Column's children.
 *
 * [Top]/[Start] pack with **zero** gap, matching Compose. `ui-core` defaults to an unrequested 8dp
 * `spacedBy`, which `docs/reference/mirror-map.md` records as a divergence -- not carried forward.
 */
object Arrangement {

    interface Vertical {
        val spacing: Dp
    }

    interface Horizontal {
        val spacing: Dp
    }

    val Top: Vertical = FixedSpacing(0.dp)
    val Start: Horizontal = FixedSpacingHorizontal(0.dp)

    fun spacedBy(space: Dp): Vertical = FixedSpacing(space)

    fun spacedByHorizontal(space: Dp): Horizontal = FixedSpacingHorizontal(space)

    private class FixedSpacing(override val spacing: Dp) : Vertical

    private class FixedSpacingHorizontal(override val spacing: Dp) : Horizontal
}
