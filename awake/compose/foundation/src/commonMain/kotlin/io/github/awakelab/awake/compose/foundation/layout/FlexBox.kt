/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.compose.foundation.layout

import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.ModifierNodeElement
import io.github.awakelab.awake.compose.ui.layout.AlignmentLine
import io.github.awakelab.awake.compose.ui.layout.FirstBaseline
import io.github.awakelab.awake.compose.ui.layout.Layout
import io.github.awakelab.awake.compose.ui.layout.Measurable
import io.github.awakelab.awake.compose.ui.layout.MeasurePolicy
import io.github.awakelab.awake.compose.ui.layout.MeasureResult
import io.github.awakelab.awake.compose.ui.layout.MeasureScope
import io.github.awakelab.awake.compose.ui.layout.Placeable
import io.github.awakelab.awake.compose.ui.node.ParentDataModifierNode
import io.github.awakelab.awake.compose.ui.unit.Constraints
import io.github.awakelab.awake.compose.ui.unit.Density
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Which axis [FlexBox] lays children out along, and whether that axis runs forward or reversed. */
enum class FlexDirection { Row, RowReverse, Column, ColumnReverse }

/** Whether children that overflow the main axis start a new line, and which edge new lines are added at. */
enum class FlexWrap { NoWrap, Wrap, WrapReverse }

/** How a line distributes leftover main-axis space between and around its children. */
enum class FlexJustifyContent { Start, Center, End, SpaceBetween, SpaceAround, SpaceEvenly }

/** How children are positioned on the cross axis within their line. Overridden per item by [FlexItemConfig.alignSelf]. */
enum class FlexAlignItems { Start, End, Center, Stretch, Baseline }

/** How lines are distributed on the cross axis when their combined size is less than the container's. */
enum class FlexAlignContent { Start, End, Center, Stretch, SpaceBetween, SpaceAround }

/** Per-item override of [FlexBoxConfigScope.alignItems]. [Auto] defers to the container's value. */
enum class FlexAlignSelf { Auto, Start, End, Center, Stretch, Baseline }

/** The initial main-axis size used before FlexBox distributes free space. */
sealed interface FlexBasis {
    /** Use the child's intrinsic main-axis size as its starting basis. */
    data object Auto : FlexBasis

    /** Use a fixed main-axis size as the starting basis, before [FlexItemConfig.grow]/[FlexItemConfig.shrink] apply. */
    data class Fixed(val value: Dp) : FlexBasis

    /** Use a fraction (`0f..1f`) of the container's main-axis size as the starting basis. Requires a bounded main axis. */
    data class Percent(val value: Float) : FlexBasis {
        init { require(value in 0f..1f) { "Flex basis percent must be in 0f..1f, was $value" } }
    }

    companion object {
        /** Shorthand for [Fixed]. */
        fun Dp(value: Dp): FlexBasis = Fixed(value)

        /** Shorthand for [Percent]. */
        fun Percent(value: Float): FlexBasis = FlexBasis.Percent(value)
    }
}

/** Layout-time configuration for [FlexBox]. Reuse an instance when its policy is static. */
fun interface FlexBoxConfig {
    fun FlexBoxConfigScope.configure()

    companion object : FlexBoxConfig {
        override fun FlexBoxConfigScope.configure() = Unit
    }
}

interface FlexBoxConfigScope : Density {
    /** The incoming constraints [FlexBox] is measuring under. */
    val constraints: Constraints

    /** Which axis children lay out along. Defaults to [FlexDirection.Row]. */
    var direction: FlexDirection

    /** Whether overflowing children wrap onto a new line. Defaults to [FlexWrap.NoWrap]. */
    var wrap: FlexWrap

    /** How each line distributes leftover main-axis space. Defaults to [FlexJustifyContent.Start]. */
    var justifyContent: FlexJustifyContent

    /** How children are aligned on the cross axis within their line. Defaults to [FlexAlignItems.Start]. */
    var alignItems: FlexAlignItems

    /** How lines are distributed on the cross axis when wrapped. Defaults to [FlexAlignContent.Start]. */
    var alignContent: FlexAlignContent

    /** Space between lines, on the cross axis. Defaults to `0.dp`. */
    var rowGap: Dp

    /** Space between children within a line, on the main axis. Defaults to `0.dp`. */
    var columnGap: Dp

    /** Sets [rowGap] and [columnGap] to the same [value]. */
    fun gap(value: Dp) { rowGap = value; columnGap = value }

    /** Sets [rowGap] and [columnGap] independently. */
    fun gap(row: Dp, column: Dp) { rowGap = row; columnGap = column }
}

/** Per-item configuration accepted by [Modifier.flex]. */
interface FlexItemConfig {
    /** Sets the item's starting main-axis size before [grow]/[shrink] are applied. Defaults to [FlexBasis.Auto]. */
    fun basis(value: FlexBasis)

    /** Shorthand for `basis(FlexBasis.Fixed(value))`. */
    fun basis(value: Dp) = basis(FlexBasis.Fixed(value))

    /** Shorthand for `basis(FlexBasis.Percent(value))`. */
    fun basis(value: Float) = basis(FlexBasis.Percent(value))

    /** The item's share of a line's leftover main-axis space, relative to its siblings' [grow]. Must be `>= 0`. Defaults to `0`. */
    fun grow(value: Float)

    /** The item's share of a line's main-axis deficit, relative to its siblings' [shrink]. Must be `>= 0`. Defaults to `1`. */
    fun shrink(value: Float)

    /** Overrides [FlexBoxConfigScope.alignItems] for this item alone. Defaults to [FlexAlignSelf.Auto]. */
    fun alignSelf(value: FlexAlignSelf)

    /** Reorders this item within its line, independent of declaration order. Ties break by declaration order. Defaults to `0`. */
    fun order(value: Int)
}

/** Adds FlexBox parent data to this direct child. */
fun Modifier.flex(configure: FlexItemConfig.() -> Unit): Modifier {
    val config = MutableFlexItemConfig().apply(configure).toData()
    return this then FlexItemElement(config)
}

@LayoutScopeMarker
interface FlexBoxScope

private object FlexBoxScopeInstance : FlexBoxScope
private object FlexBoxNodeType

/**
 * CSS-shaped multi-line layout. The public API follows Compose's experimental FlexBox surface;
 * this implementation uses Awake intrinsics to plan every final child constraint before the one
 * permitted measure call.
 */
context(composer: Composer)
fun FlexBox(
    modifier: Modifier = Modifier,
    config: FlexBoxConfig = FlexBoxConfig,
    content: context(Composer) FlexBoxScope.() -> Unit,
) {
    Layout(
        nodeType = FlexBoxNodeType,
        modifier = modifier,
        measurePolicy = FlexBoxMeasurePolicy(config),
        content = { content(composer, FlexBoxScopeInstance) },
    )
}

internal data class FlexItemData(
    val basis: FlexBasis = FlexBasis.Auto,
    val grow: Float = 0f,
    val shrink: Float = 1f,
    val alignSelf: FlexAlignSelf = FlexAlignSelf.Auto,
    val order: Int = 0,
)

private class MutableFlexItemConfig : FlexItemConfig {
    private var basis: FlexBasis = FlexBasis.Auto
    private var grow = 0f
    private var shrink = 1f
    private var alignSelf = FlexAlignSelf.Auto
    private var order = 0
    override fun basis(value: FlexBasis) { basis = value }
    override fun grow(value: Float) { require(value >= 0f) { "grow must be >= 0, was $value" }; grow = value }
    override fun shrink(value: Float) { require(value >= 0f) { "shrink must be >= 0, was $value" }; shrink = value }
    override fun alignSelf(value: FlexAlignSelf) { alignSelf = value }
    override fun order(value: Int) { order = value }
    fun toData() = FlexItemData(basis, grow, shrink, alignSelf, order)
}

private class FlexItemElement(private val data: FlexItemData) : ModifierNodeElement<FlexItemNode>() {
    override fun create() = FlexItemNode(data)
    override fun update(node: FlexItemNode) { node.data = data }
}

private class FlexItemNode(var data: FlexItemData) : Modifier.Node(), ParentDataModifierNode {
    override fun modifyParentData(current: Any?): Any =
        ((current as? RowColumnParentData) ?: RowColumnParentData()).also { it.flex = data }
}

private class ResolvedFlexConfig : FlexBoxConfigScope {
    private var activeDensity = 1f
    private var activeFontScale = 1f
    override var constraints: Constraints = Constraints.unbounded()
        private set
    override var direction = FlexDirection.Row
    override var wrap = FlexWrap.NoWrap
    override var justifyContent = FlexJustifyContent.Start
    override var alignItems = FlexAlignItems.Start
    override var alignContent = FlexAlignContent.Start
    override var rowGap = 0.dp
    override var columnGap = 0.dp
    override val density: Float get() = activeDensity
    override val fontScale: Float get() = activeFontScale

    fun resolve(scope: MeasureScope, incoming: Constraints, config: FlexBoxConfig) {
        activeDensity = scope.density
        activeFontScale = scope.fontScale
        constraints = incoming
        direction = FlexDirection.Row
        wrap = FlexWrap.NoWrap
        justifyContent = FlexJustifyContent.Start
        alignItems = FlexAlignItems.Start
        alignContent = FlexAlignContent.Start
        rowGap = 0.dp
        columnGap = 0.dp
        with(config) { configure() }
    }
}

private data class FlexChild(
    val measurable: Measurable,
    val sourceIndex: Int,
    val item: FlexItemData,
    var main: Int = 0,
    var crossPlan: Int = 0,
    var placeable: Placeable? = null,
)

private class FlexBoxMeasurePolicy(private val config: FlexBoxConfig) : MeasurePolicy {
    private val resolved = ResolvedFlexConfig()

    @Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth") // Resolving and placing one flex transaction shares all line bounds.
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        resolved.resolve(this, constraints, config)
        val vertical = resolved.direction == FlexDirection.Column || resolved.direction == FlexDirection.ColumnReverse
        val reverseMain = resolved.direction == FlexDirection.RowReverse || resolved.direction == FlexDirection.ColumnReverse
        val mainLimit = if (vertical) constraints.maxHeight else constraints.maxWidth
        val crossLimit = if (vertical) constraints.maxWidth else constraints.maxHeight
        val mainGap = (if (vertical) resolved.rowGap else resolved.columnGap).roundToPx()
        val crossGap = (if (vertical) resolved.columnGap else resolved.rowGap).roundToPx()
        val children = ArrayList<FlexChild>(measurables.size)
        for (index in measurables.indices) {
            val measurable = measurables[index]
            val item = measurable.rowColumnParentData()?.flex ?: FlexItemData()
            val intrinsicMain = if (vertical) measurable.maxIntrinsicHeight(crossLimit) else measurable.maxIntrinsicWidth(crossLimit)
            val minMain = if (vertical) measurable.minIntrinsicHeight(crossLimit) else measurable.minIntrinsicWidth(crossLimit)
            val basis = basisPx(item.basis, intrinsicMain, minMain, mainLimit)
            val intrinsicCross = if (vertical) measurable.maxIntrinsicWidth(basis) else measurable.maxIntrinsicHeight(basis)
            children += FlexChild(measurable, index, item, basis, intrinsicCross)
        }
        children.sortWith(compareBy<FlexChild> { it.item.order }.thenBy { it.sourceIndex })
        val lines = buildLines(children, mainLimit, mainGap)
        val lineCrosses = IntArray(lines.size)
        val lineMains = IntArray(lines.size)
        for (lineIndex in lines.indices) {
            val line = lines[lineIndex]
            resolveMainSizes(line, mainLimit, mainGap, vertical)
            lineMains[lineIndex] = line.sumOf { it.main } + mainGap * (line.size - 1).coerceAtLeast(0)
            lineCrosses[lineIndex] = line.maxOfOrNull { it.crossPlan } ?: 0
        }
        val plannedMain = lineMains.maxOrNull() ?: 0
        val plannedCross = lineCrosses.sum() + crossGap * (lines.size - 1).coerceAtLeast(0)
        val plannedWidth = constraints.constrainWidth(if (vertical) plannedCross else plannedMain)
        val plannedHeight = constraints.constrainHeight(if (vertical) plannedMain else plannedCross)
        val plannedCrossSize = if (vertical) plannedWidth else plannedHeight
        val crossDistribution = distribution(
            resolved.alignContent,
            (plannedCrossSize - plannedCross).coerceAtLeast(0),
            lines.size,
        )
        for (lineIndex in lineCrosses.indices) lineCrosses[lineIndex] += crossDistribution.stretch
        val lineBaselines = IntArray(lines.size) { AlignmentLine.Unspecified }
        for (lineIndex in lines.indices) {
            val line = lines[lineIndex]
            for (child in line) {
                val alignment = child.item.alignSelf.toItems(resolved.alignItems)
                val cross = if (alignment == FlexAlignItems.Stretch) lineCrosses[lineIndex] else 0
                val childConstraints = if (vertical) {
                    Constraints.of(cross, if (cross == 0) crossLimit else cross, child.main, child.main)
                } else {
                    Constraints.of(child.main, child.main, cross, if (cross == 0) crossLimit else cross)
                }
                child.placeable = child.measurable.measure(childConstraints)
                val actualCross = if (vertical) child.placeable!!.width else child.placeable!!.height
                lineCrosses[lineIndex] = maxOf(lineCrosses[lineIndex], actualCross)
            }
            if (!vertical) {
                var before = 0
                var after = 0
                var hasBaseline = false
                for (child in line) {
                    if (child.item.alignSelf.toItems(resolved.alignItems) == FlexAlignItems.Baseline) {
                        val placeable = child.placeable!!
                        val position = placeable[FirstBaseline]
                        if (position != AlignmentLine.Unspecified) {
                            hasBaseline = true
                            before = maxOf(before, position)
                            after = maxOf(after, placeable.height - position)
                        }
                    }
                }
                if (hasBaseline) {
                    lineBaselines[lineIndex] = before
                    lineCrosses[lineIndex] = maxOf(lineCrosses[lineIndex], before + after)
                }
            }
        }
        val contentMain = lineMains.maxOrNull() ?: 0
        val contentCross = lineCrosses.sum() + crossGap * (lines.size - 1).coerceAtLeast(0)
        val width = constraints.constrainWidth(if (vertical) contentCross else contentMain)
        val height = constraints.constrainHeight(if (vertical) contentMain else contentCross)
        val mainSize = if (vertical) height else width

        return layout(width, height) {
            var crossCursor = crossDistribution.before
            for (lineIndex in lines.indices) {
                val line = lines[lineIndex]
                val lineCross = lineCrosses[lineIndex] + crossDistribution.stretch
                val mainDistribution = distribution(resolved.justifyContent, (mainSize - lineMains[lineIndex]).coerceAtLeast(0), line.size)
                var mainCursor = mainDistribution.before
                for (child in line) {
                    val placeable = child.placeable!!
                    val alignment = child.item.alignSelf.toItems(resolved.alignItems)
                    val crossOffset = crossOffset(
                        alignment,
                        if (vertical) placeable.width else placeable.height,
                        lineCross,
                        lineBaselines[lineIndex],
                        if (vertical) AlignmentLine.Unspecified else placeable[FirstBaseline],
                    )
                    val placedMain = if (reverseMain) mainSize - mainCursor - (if (vertical) placeable.height else placeable.width) else mainCursor
                    if (vertical) placeable.placeRelativeAt(crossCursor + crossOffset, placedMain)
                    else placeable.placeRelativeAt(placedMain, crossCursor + crossOffset)
                    mainCursor += (if (vertical) placeable.height else placeable.width) + mainGap + mainDistribution.between
                }
                crossCursor += lineCross + crossGap + crossDistribution.between
            }
        }
    }

    private fun buildLines(children: List<FlexChild>, limit: Int, gap: Int): List<List<FlexChild>> {
        if (children.isEmpty()) return emptyList()
        val lines = ArrayList<List<FlexChild>>()
        if (resolved.wrap == FlexWrap.NoWrap || limit == Constraints.Infinity) {
            lines += children
        } else {
            var current = ArrayList<FlexChild>()
            var used = 0
            for (child in children) {
                val needed = child.main + if (current.isEmpty()) 0 else gap
                if (current.isNotEmpty() && used + needed > limit) {
                    lines += current
                    current = ArrayList()
                    used = 0
                }
                current += child
                used += if (current.size == 1) child.main else needed
            }
            if (current.isNotEmpty()) lines += current
        }
        return if (resolved.wrap == FlexWrap.WrapReverse) lines.asReversed() else lines
    }

    private fun resolveMainSizes(line: List<FlexChild>, limit: Int, gap: Int, vertical: Boolean) {
        if (limit == Constraints.Infinity) return
        val used = line.sumOf { it.main } + gap * (line.size - 1).coerceAtLeast(0)
        val delta = limit - used
        val totalFactor = if (delta >= 0) line.sumOf { it.item.grow.toDouble() }.toFloat()
        else line.sumOf { (it.item.shrink * it.main).toDouble() }.toFloat()
        if (totalFactor <= 0f) return
        var remainder = delta
        for (child in line) {
            val factor = if (delta >= 0) child.item.grow else child.item.shrink * child.main
            if (factor == 0f) continue
            val change = (delta * factor / totalFactor).roundToInt()
            val min = if (vertical) child.measurable.minIntrinsicHeight(Constraints.Infinity) else child.measurable.minIntrinsicWidth(Constraints.Infinity)
            val next = (child.main + change).coerceAtLeast(min)
            remainder -= next - child.main
            child.main = next
        }
        for (child in line) {
            if (remainder == 0) break
            val min = if (vertical) child.measurable.minIntrinsicHeight(Constraints.Infinity) else child.measurable.minIntrinsicWidth(Constraints.Infinity)
            val step = if (remainder > 0) 1 else -1
            if (child.main + step >= min) { child.main += step; remainder -= step }
        }
    }

    override fun Density.minIntrinsicWidth(measurables: List<io.github.awakelab.awake.compose.ui.layout.IntrinsicMeasurable>, height: Int): Int =
        measurables.sumOf { it.minIntrinsicWidth(height) }

    override fun Density.maxIntrinsicWidth(measurables: List<io.github.awakelab.awake.compose.ui.layout.IntrinsicMeasurable>, height: Int): Int =
        measurables.sumOf { it.maxIntrinsicWidth(height) }

    private fun basisPx(basis: FlexBasis, intrinsic: Int, minimum: Int, limit: Int): Int = when (basis) {
        FlexBasis.Auto -> intrinsic
        is FlexBasis.Fixed -> maxOf(minimum, with(resolved) { basis.value.roundToPx() })
        is FlexBasis.Percent -> if (limit == Constraints.Infinity) intrinsic else maxOf(minimum, (limit * basis.value).roundToInt())
    }
}

private data class FlexDistribution(val before: Int, val between: Int, val stretch: Int = 0)

private fun distribution(value: FlexJustifyContent, free: Int, count: Int): FlexDistribution = when (value) {
    FlexJustifyContent.Start -> FlexDistribution(0, 0)
    FlexJustifyContent.End -> FlexDistribution(free, 0)
    FlexJustifyContent.Center -> FlexDistribution(free / 2, 0)
    FlexJustifyContent.SpaceBetween -> FlexDistribution(0, if (count > 1) free / (count - 1) else 0)
    FlexJustifyContent.SpaceAround -> FlexDistribution(if (count > 0) free / (count * 2) else 0, if (count > 0) free / count else 0)
    FlexJustifyContent.SpaceEvenly -> FlexDistribution(if (count > 0) free / (count + 1) else 0, if (count > 0) free / (count + 1) else 0)
}

private fun distribution(value: FlexAlignContent, free: Int, count: Int): FlexDistribution = when (value) {
    FlexAlignContent.Start -> FlexDistribution(0, 0)
    FlexAlignContent.End -> FlexDistribution(free, 0)
    FlexAlignContent.Center -> FlexDistribution(free / 2, 0)
    FlexAlignContent.Stretch -> FlexDistribution(0, 0, if (count > 0) free / count else 0)
    FlexAlignContent.SpaceBetween -> FlexDistribution(0, if (count > 1) free / (count - 1) else 0)
    FlexAlignContent.SpaceAround -> FlexDistribution(if (count > 0) free / (count * 2) else 0, if (count > 0) free / count else 0)
}

private fun FlexAlignSelf.toItems(default: FlexAlignItems): FlexAlignItems = when (this) {
    FlexAlignSelf.Auto -> default
    FlexAlignSelf.Start -> FlexAlignItems.Start
    FlexAlignSelf.End -> FlexAlignItems.End
    FlexAlignSelf.Center -> FlexAlignItems.Center
    FlexAlignSelf.Stretch -> FlexAlignItems.Stretch
    FlexAlignSelf.Baseline -> FlexAlignItems.Baseline
}

private fun crossOffset(
    alignment: FlexAlignItems,
    childCross: Int,
    lineCross: Int,
    lineBaseline: Int,
    childBaseline: Int,
): Int = when (alignment) {
    FlexAlignItems.Start, FlexAlignItems.Stretch -> 0
    FlexAlignItems.Baseline -> if (
        lineBaseline == AlignmentLine.Unspecified || childBaseline == AlignmentLine.Unspecified
    ) 0 else lineBaseline - childBaseline
    FlexAlignItems.End -> lineCross - childCross
    FlexAlignItems.Center -> (lineCross - childCross) / 2
}
