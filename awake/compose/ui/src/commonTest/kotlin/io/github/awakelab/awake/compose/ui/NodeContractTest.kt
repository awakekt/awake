/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui

import io.github.awakelab.awake.compose.ui.layout.IntrinsicMeasurable
import io.github.awakelab.awake.compose.ui.layout.Measurable
import io.github.awakelab.awake.compose.ui.layout.MeasurePolicy
import io.github.awakelab.awake.compose.ui.layout.MeasureResult
import io.github.awakelab.awake.compose.ui.layout.MeasureScope
import io.github.awakelab.awake.compose.ui.layout.Placeable
import io.github.awakelab.awake.compose.ui.node.LayoutModifierNode
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.node.NodeChildren
import io.github.awakelab.awake.compose.ui.unit.Constraints
import io.github.awakelab.awake.compose.ui.unit.Density
import io.github.awakelab.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** A link that changes nothing, so the interface's own defaults are what answer. */
private object PassThroughElement : ModifierNodeElement<PassThroughNode>() {
    override fun create(): PassThroughNode = PassThroughNode()

    override fun update(node: PassThroughNode) = Unit
}

private class PassThroughNode : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeAt(0, 0) }
    }
}

/** Reports a fixed size for both measurement and every intrinsic query. */
private class FixedPolicy(private val w: Int, private val h: Int) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult = layout(w, h) {}

    override fun Density.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = w
    override fun Density.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = w
    override fun Density.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = h
    override fun Density.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = h
}

/** Overrides nothing, so [MeasurePolicy]'s own Box-shaped defaults answer. */
private class BarePolicy : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult = layout(constraints.minWidth, constraints.minHeight) {}
}

/**
 * The defaults every `Modifier.Element` inherits, which no shipped link exercises.
 *
 * Every link this engine ships overrides its intrinsics, so the interface defaults were dead code as
 * far as the suite knew -- and they are exactly what a *consumer's* paint-only modifier will hit.
 */
class ModifierDefaultsTest {

    private fun node(): LayoutNode = LayoutNode(FixedPolicy(30, 20)).also {
        it.modifier = Modifier then PassThroughElement
    }

    @Test
    fun aPassThroughLinkForwardsEveryIntrinsic() {
        val node = node()

        assertEquals(30, node.minIntrinsicWidth(100))
        assertEquals(30, node.maxIntrinsicWidth(100))
        assertEquals(20, node.minIntrinsicHeight(100))
        assertEquals(20, node.maxIntrinsicHeight(100))
    }

    @Test
    fun aPolicyWithNoIntrinsicOverridesMaxesItsChildren() {
        // The `MeasurePolicy` defaults are Box-shaped: max over children on both axes, and zero
        // when there are none. A Column overrides them precisely because summing is not maxing.
        val parent = LayoutNode(BarePolicy())
        parent.children.insertAt(0, LayoutNode(FixedPolicy(30, 20)))
        parent.children.insertAt(1, LayoutNode(FixedPolicy(10, 50)))

        assertEquals(30, parent.minIntrinsicWidth(100), "the widest child")
        assertEquals(50, parent.maxIntrinsicHeight(100), "the tallest child")
        assertEquals(0, LayoutNode(BarePolicy()).minIntrinsicHeight(100), "nothing to max")
    }

    @Test
    fun aPlaceableStartsUnplacedAndUnsized() {
        val placeable = object : Placeable() {}

        assertEquals(listOf(0, 0, 0, 0), listOf(placeable.width, placeable.height, placeable.x, placeable.y))
    }

    @Test
    fun placingRecordsThePositionAndRunsTheDefaultHook() {
        // `onPlaced` is an open no-op on the base class; placing a bare Placeable is what runs it.
        val placeable = object : Placeable() {}

        placeable.placeAt(4, 7)

        assertEquals(listOf(4, 7), listOf(placeable.x, placeable.y))
    }

    @Test
    fun aMeasurableCarriesNoParentDataUnlessAModifierAddsIt() {
        val bare = object : IntrinsicMeasurable {
            override fun minIntrinsicWidth(height: Int) = 0
            override fun maxIntrinsicWidth(height: Int) = 0
            override fun minIntrinsicHeight(width: Int) = 0
            override fun maxIntrinsicHeight(width: Int) = 0
        }

        assertEquals(null, bare.parentData)
    }
}

class UnitAndChildrenTest {

    @Test
    fun unboundedIsInfiniteOnBothAxes() {
        val constraints = Constraints.unbounded()

        assertEquals(0, constraints.minWidth)
        assertEquals(false, constraints.hasBoundedWidth)
        assertEquals(false, constraints.hasBoundedHeight)
    }

    @Test
    fun dpConvertsToPixelsAtTheScopeDensity() {
        val twoX = object : Density {
            override val density = 2f
            override val fontScale = 1f
        }

        with(twoX) {
            assertEquals(20f, 10.dp.toPx())
            assertEquals(20, 10.dp.roundToPx())
        }
    }

    @Test
    fun clearingDropsEveryChild() {
        val children = NodeChildren()
        children.insertAt(0, LayoutNode(FixedPolicy(1, 1)))
        children.insertAt(1, LayoutNode(FixedPolicy(1, 1)))

        children.clear()

        assertEquals(0, children.size)
    }

    @Test
    fun theEmptyModifierNamesItself() {
        assertEquals("Modifier", Modifier.toString())
    }
}
