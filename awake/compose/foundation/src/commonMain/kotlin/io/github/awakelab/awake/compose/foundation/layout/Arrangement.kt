/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation.layout

import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp

/**
 * How a Row's or Column's children are distributed along its main axis.
 *
 * [Top]/[Start] pack with **zero** gap, matching Compose. `ui-core` defaults to an unrequested 8dp
 * `spacedBy`, which `docs/reference/mirror-map.md` records as a divergence -- not carried forward.
 *
 * Two numbers rather than Compose's `arrange(total, sizes, out)`: a fixed gap plus what to do with
 * the leftover covers every arrangement here, and it keeps the measure policy's placement a single
 * cursor walk. The full form is what a custom arrangement would need, and none exists yet.
 */
object Arrangement {

    interface Vertical {
        val spacing: Dp

        /** Space before the first child. */
        fun leading(free: Int, count: Int): Int = 0

        /** Extra space added between each pair, on top of [spacing]. */
        fun between(free: Int, count: Int): Int = 0
    }

    interface Horizontal {
        val spacing: Dp

        fun leading(free: Int, count: Int): Int = 0

        fun between(free: Int, count: Int): Int = 0
    }

    val Top: Vertical = Packed(0.dp)
    val Start: Horizontal = PackedHorizontal(0.dp)

    /** Packs at the far end: the leftover all goes in front. */
    val Bottom: Vertical = object : Vertical {
        override val spacing = 0.dp
        override fun leading(free: Int, count: Int) = free
    }

    val End: Horizontal = object : Horizontal {
        override val spacing = 0.dp
        override fun leading(free: Int, count: Int) = free
    }

    /** Centres the group: half the leftover in front, half behind. */
    val Center: Vertical = object : Vertical {
        override val spacing = 0.dp
        override fun leading(free: Int, count: Int) = free / 2
    }

    val CenterHorizontally: Horizontal = object : Horizontal {
        override val spacing = 0.dp
        override fun leading(free: Int, count: Int) = free / 2
    }

    /** First child at the start, last at the end, the leftover split between the gaps. */
    val SpaceBetween: Vertical = object : Vertical {
        override val spacing = 0.dp
        override fun between(free: Int, count: Int) = if (count > 1) free / (count - 1) else 0
    }

    val SpaceBetweenHorizontal: Horizontal = object : Horizontal {
        override val spacing = 0.dp
        override fun between(free: Int, count: Int) = if (count > 1) free / (count - 1) else 0
    }

    fun spacedBy(space: Dp): Vertical = Packed(space)

    fun spacedByHorizontal(space: Dp): Horizontal = PackedHorizontal(space)

    private class Packed(override val spacing: Dp) : Vertical

    private class PackedHorizontal(override val spacing: Dp) : Horizontal
}
