/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.foundation.layout.FlexAlignContent
import com.awakekt.awake.compose.foundation.layout.FlexAlignItems
import com.awakekt.awake.compose.foundation.layout.FlexBoxConfig
import com.awakekt.awake.compose.foundation.layout.FlexDirection
import com.awakekt.awake.compose.foundation.layout.FlexJustifyContent
import com.awakekt.awake.compose.foundation.layout.FlexWrap
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.schema.PropertyRange
import kotlinx.serialization.Serializable

/**
 * How an element places its children itself, as a CSS flexbox does, instead of each child being
 * anchored and offset by hand: a row of slots, a wrapping strip of icons, a list. A child keeps its
 * `width` and `height` as its size, takes a share of the leftover space by its `grow`, and ignores
 * its anchor and offsets.
 *
 * @property direction The axis the children run along, and which way.
 * @property wrap Whether children that do not fit start a new line.
 * @property gap The space between children, and between lines, in dp.
 * @property padding The space between the element's edge and its children, in dp.
 * @property justify How a line spreads its leftover space along the axis.
 * @property align Where children sit across the axis within their line.
 */
@Serializable
data class CanvasLayout(
    val direction: CanvasDirection = CanvasDirection.Row,
    val wrap: Boolean = false,
    @PropertyRange(min = 0.0) val gap: Float = 0f,
    @PropertyRange(min = 0.0) val padding: Float = 0f,
    val justify: CanvasJustify = CanvasJustify.Start,
    val align: CanvasAlign = CanvasAlign.Start,
) {
    internal fun problems(): List<String> = buildList {
        if (gap < 0f) add("gap must not be negative")
        if (padding < 0f) add("padding must not be negative")
    }

    internal fun toConfig(): FlexBoxConfig {
        val flexDirection = FlexDirection.valueOf(direction.name)
        val flexJustify = FlexJustifyContent.valueOf(justify.name)
        val flexAlign = FlexAlignItems.valueOf(align.name)
        return FlexBoxConfig {
            this.direction = flexDirection
            this.wrap = if (this@CanvasLayout.wrap) FlexWrap.Wrap else FlexWrap.NoWrap
            justifyContent = flexJustify
            alignItems = flexAlign
            // One line spans the element, as a single-line CSS flexbox's does, so align centres in
            // it; wrapped lines stay packed at the start rather than spreading over the element.
            alignContent = if (this@CanvasLayout.wrap) FlexAlignContent.Start else FlexAlignContent.Stretch
            gap(this@CanvasLayout.gap.coerceAtLeast(0f).dp)
        }
    }
}

/** The axis a [CanvasLayout]'s children run along. */
@Serializable
enum class CanvasDirection {
    /** Left to right. */
    Row,

    /** Right to left. */
    RowReverse,

    /** Top to bottom. */
    Column,

    /** Bottom to top. */
    ColumnReverse,
}

/** How a [CanvasLayout]'s line spreads its leftover space along the axis. */
@Serializable
enum class CanvasJustify {
    /** Packed at the start. */
    Start,

    /** Packed in the middle. */
    Center,

    /** Packed at the end. */
    End,

    /** The first child at the start, the last at the end, the rest spread between. */
    SpaceBetween,

    /** Equal space around each child. */
    SpaceAround,

    /** Equal gaps between the children and at both ends. */
    SpaceEvenly,
}

/** Where a [CanvasLayout]'s children sit across the axis within their line. */
@Serializable
enum class CanvasAlign {
    /** At the start: the top of a row, the left of a column. */
    Start,

    /** In the middle. */
    Center,

    /** At the end. */
    End,

    /** Stretched across the whole line. */
    Stretch,
}
