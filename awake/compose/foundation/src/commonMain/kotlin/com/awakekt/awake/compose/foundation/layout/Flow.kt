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
import com.awakekt.awake.compose.ui.layout.AlignmentLine
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.layout.Placeable
import com.awakekt.awake.compose.ui.unit.Constraints
import kotlin.math.abs
import kotlin.math.roundToInt

private object FlowRowNodeType
private object FlowColumnNodeType

private class FlowItem(
    val measurable: Measurable,
    var placeable: Placeable,
)

private data class FlowMeasureLimits(
    val maxMainAxis: Int,
    val gap: Int,
    val childConstraints: Constraints,
)

private data class FlowLineSizes(
    internal val widths: MutableList<Int>,
    internal val heights: MutableList<Int>,
)

private data class AlignmentLineExtent(val before: Int, val after: Int)

private fun alignmentLineExtent(items: List<FlowItem>, horizontal: Boolean): AlignmentLineExtent {
    var before = 0
    var after = 0
    items.forEach { item ->
        val line = item.measurable.rowColumnParentData()?.alignmentLine ?: return@forEach
        val position = item.placeable[line]
        if (position == AlignmentLine.Unspecified) return@forEach
        val crossAxisSize = if (horizontal) item.placeable.height else item.placeable.width
        before = maxOf(before, position)
        after = maxOf(after, crossAxisSize - position)
    }
    return AlignmentLineExtent(before, after)
}

private fun clipLines(lines: MutableList<MutableList<FlowItem>>, sizes: FlowLineSizes, maxLines: Int) {
    if (lines.size <= maxLines) return
    lines.subList(maxLines, lines.size).flatten().forEach { item ->
        item.placeable = item.measurable.measure(Constraints.fixed(0, 0))
    }
    lines.subList(maxLines, lines.size).clear()
    sizes.widths.subList(maxLines, sizes.widths.size).clear()
    sizes.heights.subList(maxLines, sizes.heights.size).clear()
}

private fun remeasureWeightedRows(
    lines: List<List<FlowItem>>,
    sizes: FlowLineSizes,
    limits: FlowMeasureLimits,
) {
    if (limits.maxMainAxis == Constraints.Infinity) return
    lines.forEachIndexed { index, line ->
        remeasureWeightedLine(line, limits, horizontal = true)
        sizes.widths[index] = line.sumOf { it.placeable.width } + limits.gap * (line.size - 1).coerceAtLeast(0)
        sizes.heights[index] = line.maxOfOrNull { it.placeable.height } ?: 0
    }
}

private fun remeasureWeightedColumns(
    columns: List<List<FlowItem>>,
    sizes: FlowLineSizes,
    limits: FlowMeasureLimits,
) {
    if (limits.maxMainAxis == Constraints.Infinity) return
    columns.forEachIndexed { index, column ->
        remeasureWeightedLine(column, limits, horizontal = false)
        sizes.widths[index] = column.maxOfOrNull { it.placeable.width } ?: 0
        sizes.heights[index] = column.sumOf { it.placeable.height } + limits.gap * (column.size - 1).coerceAtLeast(0)
    }
}

private fun remeasureWeightedLine(
    items: List<FlowItem>,
    limits: FlowMeasureLimits,
    horizontal: Boolean,
) {
    val totalWeight = items.sumOf { item -> item.measurable.rowColumnParentData()?.weight?.toDouble() ?: 0.0 }.toFloat()
    if (totalWeight <= 0f) return
    val occupied = items.sumOf { item ->
        if ((item.measurable.rowColumnParentData()?.weight ?: 0f) > 0f) {
            0
        } else if (horizontal) {
            item.placeable.width
        } else {
            item.placeable.height
        }
    } + limits.gap * (items.size - 1).coerceAtLeast(0)
    val shares = weightedShares(items, (limits.maxMainAxis - occupied).coerceAtLeast(0), totalWeight)
    items.forEachIndexed { index, item ->
        val data = item.measurable.rowColumnParentData() ?: return@forEachIndexed
        if (data.weight <= 0f) return@forEachIndexed
        val mainMin = if (data.fill) shares[index] else 0
        val constraints = if (horizontal) {
            Constraints.of(mainMin, shares[index], 0, limits.childConstraints.maxHeight)
        } else {
            Constraints.of(0, limits.childConstraints.maxWidth, mainMin, shares[index])
        }
        item.placeable = item.measurable.measure(constraints)
    }
}

private fun weightedShares(items: List<FlowItem>, space: Int, totalWeight: Float): IntArray {
    val shares = IntArray(items.size)
    var remainder = space
    items.forEachIndexed { index, item ->
        val weight = item.measurable.rowColumnParentData()?.weight ?: 0f
        if (weight <= 0f) return@forEachIndexed
        val share = (space * weight / totalWeight).roundToInt()
        shares[index] = share
        remainder -= share
    }
    val step = if (remainder >= 0) 1 else -1
    var left = abs(remainder)
    var index = 0
    while (left > 0) {
        val item = items[index % items.size]
        if ((item.measurable.rowColumnParentData()?.weight ?: 0f) > 0f && shares[index % items.size] + step >= 0) {
            shares[index % items.size] += step
            left--
        }
        index++
    }
    return shares
}

/**
 * Places children left-to-right and wraps to a new line when a bounded width is exhausted.
 *
 * [horizontalArrangement] arranges items within each line and [verticalArrangement] arranges the
 * lines themselves. [itemVerticalAlignment] is the default cross-axis alignment for an item; a
 * child [RowScope.align] takes precedence. An unbounded width keeps every child on one line unless
 * [maxItemsInEachRow] supplies an explicit cap. [maxLines] clips later lines.
 *
 * We intentionally do not expose Compose's deprecated overflow-indicator API. A caller that needs
 * an expand control owns that state and renders it alongside this layout.
 */
context(composer: Composer)
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    itemVerticalAlignment: Alignment.Vertical = Alignment.Top,
    maxItemsInEachRow: Int = Int.MAX_VALUE,
    maxLines: Int = Int.MAX_VALUE,
    content: context(Composer) RowScope.() -> Unit,
) {
    require(maxItemsInEachRow > 0) { "maxItemsInEachRow must be > 0, was $maxItemsInEachRow" }
    require(maxLines > 0) { "maxLines must be > 0, was $maxLines" }
    Layout(
        nodeType = FlowRowNodeType,
        modifier = modifier,
        measurePolicy = FlowRowMeasurePolicy(
            horizontalArrangement,
            verticalArrangement,
            maxItemsInEachRow,
            maxLines,
            itemVerticalAlignment::align,
        ),
        content = { content(composer, RowScopeInstance) },
    )
}

/**
 * Places children top-to-bottom and wraps to a new column when a bounded height is exhausted.
 *
 * [verticalArrangement] arranges items within each column and [horizontalArrangement] arranges
 * the columns themselves. [itemHorizontalAlignment] is the default cross-axis alignment for an
 * item; a child [ColumnScope.align] takes precedence. An unbounded height keeps every child in one
 * column unless [maxItemsInEachColumn] supplies an explicit cap. [maxLines] clips later columns.
 *
 * We intentionally do not expose Compose's deprecated overflow-indicator API. A caller that needs
 * an expand control owns that state and renders it alongside this layout.
 */
context(composer: Composer)
fun FlowColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    itemHorizontalAlignment: Alignment.Horizontal = Alignment.Start,
    maxItemsInEachColumn: Int = Int.MAX_VALUE,
    maxLines: Int = Int.MAX_VALUE,
    content: context(Composer) ColumnScope.() -> Unit,
) {
    require(maxItemsInEachColumn > 0) { "maxItemsInEachColumn must be > 0, was $maxItemsInEachColumn" }
    require(maxLines > 0) { "maxLines must be > 0, was $maxLines" }
    Layout(
        nodeType = FlowColumnNodeType,
        modifier = modifier,
        measurePolicy = FlowColumnMeasurePolicy(
            verticalArrangement,
            horizontalArrangement,
            maxItemsInEachColumn,
            maxLines,
            itemHorizontalAlignment::align,
        ),
        content = { content(composer, ColumnScopeInstance) },
    )
}

private class FlowRowMeasurePolicy(
    private val horizontalArrangement: Arrangement.Horizontal,
    private val verticalArrangement: Arrangement.Vertical,
    private val maxItemsInEachRow: Int,
    private val maxLines: Int,
    private val itemVerticalAlignment: CrossAxisAlign,
) : MeasurePolicy {
    @Suppress("LongMethod") // Grouping and placing are one measure transaction; splitting would hide shared bounds.
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val horizontalGap = horizontalArrangement.spacing.roundToPx()
        val verticalGap = verticalArrangement.spacing.roundToPx()
        val maxWidth = constraints.maxWidth
        val childConstraints = Constraints.of(0, maxWidth, 0, constraints.maxHeight)
        val lines = mutableListOf<MutableList<FlowItem>>()
        val lineWidths = mutableListOf<Int>()
        val lineHeights = mutableListOf<Int>()
        var line = mutableListOf<FlowItem>()
        var lineWidth = 0
        var lineHeight = 0
        measurables.forEach { measurable ->
            val placeable = measurable.measure(childConstraints)
            val nextWidth = if (line.isEmpty()) placeable.width else lineWidth + horizontalGap + placeable.width
            if (line.isNotEmpty()) {
                val limitReached = line.size == maxItemsInEachRow
                val widthExhausted = constraints.hasBoundedWidth && nextWidth > maxWidth
                if (limitReached || widthExhausted) {
                    lines += line
                    lineWidths += lineWidth
                    lineHeights += lineHeight
                    line = mutableListOf()
                    lineWidth = 0
                    lineHeight = 0
                }
            }
            line += FlowItem(measurable, placeable)
            lineWidth = if (line.size == 1) placeable.width else lineWidth + horizontalGap + placeable.width
            lineHeight = maxOf(lineHeight, placeable.height)
        }
        if (line.isNotEmpty()) {
            lines += line
            lineWidths += lineWidth
            lineHeights += lineHeight
        }
        clipLines(lines, FlowLineSizes(lineWidths, lineHeights), maxLines)
        remeasureWeightedRows(
            lines,
            FlowLineSizes(lineWidths, lineHeights),
            FlowMeasureLimits(maxWidth, horizontalGap, childConstraints),
        )
        val lineAlignmentExtents = lines.map { alignmentLineExtent(it, horizontal = true) }
        lineAlignmentExtents.forEachIndexed { index, extent ->
            lineHeights[index] = maxOf(lineHeights[index], extent.before + extent.after)
        }
        val contentHeight = lineHeights.sum() + verticalGap * (lines.size - 1).coerceAtLeast(0)
        val width = constraints.constrainWidth(lineWidths.maxOrNull() ?: 0)
        val height = constraints.constrainHeight(contentHeight)
        return layout(width, height) {
            val verticalFree = (height - contentHeight).coerceAtLeast(0)
            val verticalExtra = verticalArrangement.between(verticalFree, lines.size)
            var y = verticalArrangement.leading(verticalFree, lines.size)
            lines.forEachIndexed { index, placeables ->
                val horizontalFree = (width - lineWidths[index]).coerceAtLeast(0)
                val horizontalExtra = horizontalArrangement.between(horizontalFree, placeables.size)
                var x = horizontalArrangement.leading(horizontalFree, placeables.size)
                placeables.forEach { item ->
                    val placeable = item.placeable
                    val data = item.measurable.rowColumnParentData()
                    val alignmentLine = data?.alignmentLine
                    val alignmentPosition = if (alignmentLine == null) AlignmentLine.Unspecified else placeable[alignmentLine]
                    val offset = if (alignmentPosition != AlignmentLine.Unspecified) {
                        lineAlignmentExtents[index].before - alignmentPosition
                    } else {
                        (data?.crossAxisAlign ?: itemVerticalAlignment)(placeable.height, lineHeights[index])
                    }
                    placeable.placeRelativeAt(x, y + offset)
                    x += placeable.width + horizontalGap + horizontalExtra
                }
                y += lineHeights[index] + verticalGap + verticalExtra
            }
        }
    }
}

private class FlowColumnMeasurePolicy(
    private val verticalArrangement: Arrangement.Vertical,
    private val horizontalArrangement: Arrangement.Horizontal,
    private val maxItemsInEachColumn: Int,
    private val maxLines: Int,
    private val itemHorizontalAlignment: CrossAxisAlign,
) : MeasurePolicy {
    @Suppress("LongMethod") // Grouping and placing are one measure transaction; splitting would hide shared bounds.
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val verticalGap = verticalArrangement.spacing.roundToPx()
        val horizontalGap = horizontalArrangement.spacing.roundToPx()
        val childConstraints = Constraints.of(0, constraints.maxWidth, 0, constraints.maxHeight)
        val columns = mutableListOf<MutableList<FlowItem>>()
        val columnWidths = mutableListOf<Int>()
        val columnHeights = mutableListOf<Int>()
        var column = mutableListOf<FlowItem>()
        var columnWidth = 0
        var columnHeight = 0
        measurables.forEach { measurable ->
            val placeable = measurable.measure(childConstraints)
            val nextHeight = if (column.isEmpty()) placeable.height else columnHeight + verticalGap + placeable.height
            if (column.isNotEmpty()) {
                val limitReached = column.size == maxItemsInEachColumn
                val heightExhausted = constraints.hasBoundedHeight && nextHeight > constraints.maxHeight
                if (limitReached || heightExhausted) {
                    columns += column
                    columnWidths += columnWidth
                    columnHeights += columnHeight
                    column = mutableListOf()
                    columnWidth = 0
                    columnHeight = 0
                }
            }
            column += FlowItem(measurable, placeable)
            columnHeight = if (column.size == 1) placeable.height else columnHeight + verticalGap + placeable.height
            columnWidth = maxOf(columnWidth, placeable.width)
        }
        if (column.isNotEmpty()) {
            columns += column
            columnWidths += columnWidth
            columnHeights += columnHeight
        }
        clipLines(columns, FlowLineSizes(columnWidths, columnHeights), maxLines)
        remeasureWeightedColumns(
            columns,
            FlowLineSizes(columnWidths, columnHeights),
            FlowMeasureLimits(constraints.maxHeight, verticalGap, childConstraints),
        )
        val columnAlignmentExtents = columns.map { alignmentLineExtent(it, horizontal = false) }
        columnAlignmentExtents.forEachIndexed { index, extent ->
            columnWidths[index] = maxOf(columnWidths[index], extent.before + extent.after)
        }
        val contentWidth = columnWidths.sum() + horizontalGap * (columns.size - 1).coerceAtLeast(0)
        val width = constraints.constrainWidth(contentWidth)
        val height = constraints.constrainHeight(columnHeights.maxOrNull() ?: 0)
        return layout(width, height) {
            val horizontalFree = (width - contentWidth).coerceAtLeast(0)
            val horizontalExtra = horizontalArrangement.between(horizontalFree, columns.size)
            var x = horizontalArrangement.leading(horizontalFree, columns.size)
            columns.forEachIndexed { index, placeables ->
                val verticalFree = (height - columnHeights[index]).coerceAtLeast(0)
                val verticalExtra = verticalArrangement.between(verticalFree, placeables.size)
                var y = verticalArrangement.leading(verticalFree, placeables.size)
                placeables.forEach { item ->
                    val placeable = item.placeable
                    val data = item.measurable.rowColumnParentData()
                    val alignmentLine = data?.alignmentLine
                    val alignmentPosition = if (alignmentLine == null) AlignmentLine.Unspecified else placeable[alignmentLine]
                    val offset = if (alignmentPosition != AlignmentLine.Unspecified) {
                        columnAlignmentExtents[index].before - alignmentPosition
                    } else {
                        (data?.crossAxisAlign ?: itemHorizontalAlignment)(placeable.width, columnWidths[index])
                    }
                    placeable.placeRelativeAt(x + offset, y)
                    y += placeable.height + verticalGap + verticalExtra
                }
                x += columnWidths[index] + horizontalGap + horizontalExtra
            }
        }
    }
}
