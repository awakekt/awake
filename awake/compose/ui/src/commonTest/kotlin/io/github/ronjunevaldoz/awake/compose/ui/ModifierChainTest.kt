// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui

import io.github.ronjunevaldoz.awake.compose.foundation.layout.fillMaxHeight
import io.github.ronjunevaldoz.awake.compose.foundation.layout.fillMaxWidth
import io.github.ronjunevaldoz.awake.compose.foundation.layout.padding
import io.github.ronjunevaldoz.awake.compose.foundation.layout.size
import io.github.ronjunevaldoz.awake.compose.runtime.node
import io.github.ronjunevaldoz.awake.compose.ui.layout.Measurable
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasurePolicy
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureResult
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureScope
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutNode
import io.github.ronjunevaldoz.awake.compose.ui.node.ParentDataModifierNode
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints
import io.github.ronjunevaldoz.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

private class FixedSize(private val width: Int, private val height: Int) : MeasurePolicy {
    var lastConstraints: Constraints? = null
        private set

    var measureCount = 0
        private set

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        measureCount++
        lastConstraints = constraints
        return layout(constraints.constrainWidth(width), constraints.constrainHeight(height)) {}
    }
}

private fun node(width: Int, height: Int, modifier: Modifier = Modifier): Pair<LayoutNode, FixedSize> {
    val policy = FixedSize(width, height)
    return LayoutNode(policy).also { it.modifier = modifier } to policy
}

class ModifierChainTest {

    @Test
    fun theEmptyModifierIsTheIdentityOfThen() {
        assertEquals(Modifier, Modifier then Modifier)
        val padding = Modifier.padding(4.dp)
        assertEquals(padding, padding then Modifier)
        assertEquals(padding, Modifier then padding)
    }

    @Test
    fun paddingGrowsTheNodeAndInsetsItsContent() {
        val (layout, _) = node(20, 10, Modifier.padding(4.dp))
        layout.measure(Constraints.of(0, 200, 0, Constraints.Infinity))
        layout.placeAt(0, 0)

        assertEquals(28, layout.width, "20 + 4 + 4")
        assertEquals(18, layout.height, "10 + 4 + 4")
    }

    @Test
    fun paddingShrinksWhatItPassesInward() {
        val (layout, policy) = node(999, 999, Modifier.padding(10.dp))
        layout.measure(Constraints.of(0, 100, 0, 100))

        assertEquals(80, policy.lastConstraints?.maxWidth, "100 - 10 - 10")
        assertEquals(80, policy.lastConstraints?.maxHeight)
    }

    @Test
    fun paddingKeepsAnUnboundedAxisUnbounded() {
        val (layout, policy) = node(10, 10, Modifier.padding(8.dp))
        layout.measure(Constraints.of(0, 100, 0, Constraints.Infinity))

        assertEquals(false, policy.lastConstraints?.hasBoundedHeight)
    }

    @Test
    fun childPositionsAccountForAnInsettingChain() {
        // The content box is offset by the padding, so a child's tree-space position has to pick
        // that up -- resolving absolutes straight from the node would silently drop it.
        val child = LayoutNode(FixedSize(10, 10))
        val parent = LayoutNode(SingleChildPolicy()).also {
            it.modifier = Modifier.padding(start = 6.dp, top = 9.dp)
            it.children.add(child)
        }
        parent.measure(Constraints.of(0, 200, 0, Constraints.Infinity))
        parent.placeAt(100, 200)
        parent.resolveAbsolutePositions()

        assertEquals(106, child.absoluteX)
        assertEquals(209, child.absoluteY)
    }

    @Test
    fun orderMattersPaddingThenSizeIsNotSizeThenPadding() {
        val (outerPadding, _) = node(999, 999, Modifier.padding(10.dp).size(50.dp))
        outerPadding.measure(Constraints.of(0, 500, 0, 500))

        val (outerSize, _) = node(999, 999, Modifier.size(50.dp).padding(10.dp))
        outerSize.measure(Constraints.of(0, 500, 0, 500))

        assertEquals(70, outerPadding.width, "size(50) sits inside padding(10): 50 + 20")
        assertEquals(50, outerSize.width, "padding(10) sits inside size(50): still 50")
    }

    @Test
    fun fillMaxWidthTightensToTheIncomingMaximum() {
        val (layout, _) = node(10, 10, Modifier.fillMaxWidth())
        layout.measure(Constraints.of(0, 120, 0, Constraints.Infinity))

        assertEquals(120, layout.width)
        assertEquals(10, layout.height, "only the requested axis fills")
    }

    @Test
    fun fillMaxOnAnUnboundedAxisIsANoOp() {
        // Compose's rule. Without it the child adopts whatever stands in for "no bound" as a real
        // size -- the class ui-core's UNBOUNDED_MAIN_AXIS sentinel produced.
        val (layout, _) = node(10, 25, Modifier.fillMaxHeight())
        layout.measure(Constraints.of(0, 120, 0, Constraints.Infinity))

        assertEquals(25, layout.height)
    }

    @Test
    fun sizeIsStillBoundedByWhatTheParentOffers() {
        val (layout, _) = node(10, 10, Modifier.size(500.dp))
        layout.measure(Constraints.of(0, 100, 0, 100))

        assertEquals(100, layout.width, "a 500dp request inside a 100px parent yields 100")
    }

    @Test
    fun aChainDoesNotMeasureTheContentMoreThanOnce() {
        val (layout, policy) = node(10, 10, Modifier.padding(4.dp).fillMaxWidth().size(30.dp))
        layout.measure(Constraints.of(0, 200, 0, 200))

        assertEquals(1, policy.measureCount)
    }

    @Test
    fun parentDataFoldsThroughTheChain() {
        val layout = LayoutNode(FixedSize(10, 10))
        layout.modifier = Modifier.padding(4.dp) then TestParentData("tagged")

        assertEquals(TestParentData("tagged"), layout.parentData)
    }
}

private data class TestParentData(val label: String) : ParentDataModifierNode {
    override fun modifyParentData(current: Any?): Any? = this
}

private class SingleChildPolicy : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurables[0].measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeAt(0, 0) }
    }
}
