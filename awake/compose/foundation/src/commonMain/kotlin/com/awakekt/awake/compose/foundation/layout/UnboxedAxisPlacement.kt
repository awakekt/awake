/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.layout

import com.awakekt.awake.compose.ui.Alignment

/*
 * Classes for [CrossAxisAlign] and [MainAxisSpace], never `CrossAxisAlign(alignment::align)` or a
 * lambda: Kotlin/Wasm wraps a SAM-converted reference or lambda in a generic function that boxes
 * every `Int` it passes and returns, one call per child placed per frame (#1621), as Box's
 * `AxisAlignment` found for `Alignment` (#1512). The JVM does not box them.
 */

/** [alignment] across a column. */
internal class HorizontalCrossAxisAlign(private val alignment: Alignment.Horizontal) : CrossAxisAlign {
    override fun align(size: Int, space: Int): Int = alignment.align(size, space)
}

/** [alignment] across a row. */
internal class VerticalCrossAxisAlign(private val alignment: Alignment.Vertical) : CrossAxisAlign {
    override fun align(size: Int, space: Int): Int = alignment.align(size, space)
}

/** The space [arrangement] leaves before a row's first child, or [between] two. */
internal class HorizontalMainAxisSpace(
    private val arrangement: Arrangement.Horizontal,
    private val between: Boolean,
) : MainAxisSpace {
    override fun of(free: Int, count: Int): Int =
        if (between) arrangement.between(free, count) else arrangement.leading(free, count)
}

/** The space [arrangement] leaves before a column's first child, or [between] two. */
internal class VerticalMainAxisSpace(
    private val arrangement: Arrangement.Vertical,
    private val between: Boolean,
) : MainAxisSpace {
    override fun of(free: Int, count: Int): Int =
        if (between) arrangement.between(free, count) else arrangement.leading(free, count)
}

/** No space, where a layout is not given an arrangement. */
internal object NoMainAxisSpace : MainAxisSpace {
    override fun of(free: Int, count: Int): Int = 0
}
