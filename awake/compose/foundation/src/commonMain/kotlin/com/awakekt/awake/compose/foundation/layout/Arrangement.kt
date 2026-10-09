/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.layout

import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp

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

    /** An arrangement along the vertical axis, for a Column. */
    interface Vertical {
        /** The fixed gap between neighbouring children. */
        val spacing: Dp

        /** Space before the first child. */
        fun leading(free: Int, count: Int): Int = 0

        /** Extra space added between each pair, on top of [spacing]. */
        fun between(free: Int, count: Int): Int = 0
    }

    /** An arrangement along the horizontal axis, for a Row. */
    interface Horizontal {
        /** The fixed gap between neighbouring children. */
        val spacing: Dp

        /** Space before the first child. */
        fun leading(free: Int, count: Int): Int = 0

        /** Extra space added between each pair, on top of [spacing]. */
        fun between(free: Int, count: Int): Int = 0
    }

    /** Packs at the top with no gap: the leftover all goes behind. */
    val Top: Vertical = Packed(0.dp)

    /** Packs at the start with no gap: the leftover all goes behind. */
    val Start: Horizontal = PackedHorizontal(0.dp)

    /** Packs at the far end: the leftover all goes in front. */
    val Bottom: Vertical = object : Vertical {
        override val spacing = 0.dp
        override fun leading(free: Int, count: Int) = free
    }

    /** Packs at the far end: the leftover all goes in front. */
    val End: Horizontal = object : Horizontal {
        override val spacing = 0.dp
        override fun leading(free: Int, count: Int) = free
    }

    /** Centres the group: half the leftover in front, half behind. */
    val Center: Vertical = object : Vertical {
        override val spacing = 0.dp
        override fun leading(free: Int, count: Int) = free / 2
    }

    /** Centres the group: half the leftover in front, half behind. */
    val CenterHorizontally: Horizontal = object : Horizontal {
        override val spacing = 0.dp
        override fun leading(free: Int, count: Int) = free / 2
    }

    /** First child at the start, last at the end, the leftover split between the gaps. */
    val SpaceBetween: Vertical = object : Vertical {
        override val spacing = 0.dp
        override fun between(free: Int, count: Int) = if (count > 1) free / (count - 1) else 0
    }

    /** First child at the start, last at the end, the leftover split between the gaps. */
    val SpaceBetweenHorizontal: Horizontal = object : Horizontal {
        override val spacing = 0.dp
        override fun between(free: Int, count: Int) = if (count > 1) free / (count - 1) else 0
    }

    /** Packs at the top with a fixed gap of [space] between neighbouring children. */
    fun spacedBy(space: Dp): Vertical = Packed(space)

    /** Packs at the start with a fixed gap of [space] between neighbouring children. */
    fun spacedByHorizontal(space: Dp): Horizontal = PackedHorizontal(space)

    private class Packed(override val spacing: Dp) : Vertical

    private class PackedHorizontal(override val spacing: Dp) : Horizontal
}
