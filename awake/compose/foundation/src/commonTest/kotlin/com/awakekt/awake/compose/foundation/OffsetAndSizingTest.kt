/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.aspectRatio
import com.awakekt.awake.compose.foundation.layout.defaultMinSize
import com.awakekt.awake.compose.foundation.layout.fillMaxHeight
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.heightIn
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.requiredSize
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.layout.widthIn
import com.awakekt.awake.compose.foundation.layout.wrapContentHeight
import com.awakekt.awake.compose.foundation.layout.wrapContentSize
import com.awakekt.awake.compose.foundation.layout.wrapContentWidth
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Hands its child exactly the constraints it was given, so a test can dictate them. */
private val passThrough = MeasurePolicy { measurables, constraints ->
    val placeables = measurables.map { it.measure(constraints) }
    val w = placeables.maxOfOrNull { it.width } ?: 0
    val h = placeables.maxOfOrNull { it.height } ?: 0
    layout(constraints.constrainWidth(w), constraints.constrainHeight(h)) {
        placeables.forEach { it.placeAt(0, 0) }
    }
}

/** One node under [modifier], measured in a 200x200 box. */
private fun measured(modifier: Modifier, max: Int = 200): LayoutNode {
    val root = LayoutNode(ColumnMeasurePolicy())
    composeInto(root) { Spacer(modifier) }
    root.layoutTree(Constraints.of(0, max, 0, max))
    return root.children[0]
}

class OffsetTest {

    @Test
    fun itMovesTheContentWithoutChangingTheMeasuredSize() {
        val node = measured(Modifier.size(40.dp).offset(10.dp, 5.dp))

        assertEquals(40, node.width, "an offset must not resize")
        assertEquals(40, node.height)
    }

    @Test
    fun aSiblingDoesNotShift() {
        // The whole difference from padding: a badge nudged 2 dp must not move the row it sits in.
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Column {
                Spacer(Modifier.size(40.dp).offset(x = 30.dp))
                Spacer(Modifier.size(20.dp))
            }
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))
        val column = root.children[0]

        assertEquals(40, column.children[1].absoluteY, "the offset pushed its sibling down")
    }
}

class AspectRatioTest {

    @Test
    fun aBoundedWidthDictatesTheHeight() {
        val node = measured(Modifier.fillMaxWidth().aspectRatio(2f))

        assertEquals(200, node.width)
        assertEquals(100, node.height, "200 wide at 2:1")
    }

    @Test
    fun theHeightIsUsedWhenOnlyItIsBounded() {
        // Measured through a pass-through policy rather than a Column: a Column always bounds width
        // to the viewport, so the width-first branch would win and this fallback would go untested.
        val root = LayoutNode(passThrough)
        composeInto(root) { Spacer(Modifier.aspectRatio(0.5f)) }
        root.layoutTree(Constraints.of(0, Constraints.Infinity, 80, 80))

        assertEquals(40, root.children[0].width, "80 tall at 1:2")
    }
}

class FillFractionTest {

    @Test
    fun boundedAxesUseTheRequestedFraction() {
        val width = measured(Modifier.fillMaxWidth(0.5f))
        val root = LayoutNode(passThrough)
        composeInto(root) { Spacer(Modifier.fillMaxHeight(0.25f)) }
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        assertEquals(100, width.width)
        assertEquals(50, root.children[0].height)
    }

    @Test
    fun unboundedAxisStaysIntrinsicEvenWithAFraction() {
        val root = LayoutNode(passThrough)
        composeInto(root) { Spacer(Modifier.fillMaxWidth(0.5f).size(30.dp)) }
        root.layoutTree(Constraints.of(0, Constraints.Infinity, 0, 100))

        assertEquals(30, root.children[0].width)
    }

    @Test
    fun invalidFractionsFailAtModifierConstruction() {
        assertFailsWith<IllegalArgumentException> { Modifier.fillMaxWidth(-0.01f) }
        assertFailsWith<IllegalArgumentException> { Modifier.fillMaxHeight(1.01f) }
    }
}

class WrapContentTest {

    @Test
    fun widthRelaxesTheChildMinimumAndCentersItInTheReportedWidth() {
        val root = LayoutNode(passThrough)
        composeInto(root) {
            Box(Modifier.wrapContentWidth()) { Spacer(Modifier.size(40.dp)) }
        }
        root.layoutTree(Constraints.of(100, 100, 0, 100))

        assertEquals(100, root.children[0].width)
        assertEquals(30, root.children[0].children[0].absoluteX)
    }

    @Test
    fun heightUsesTheRequestedAlignment() {
        val root = LayoutNode(passThrough)
        composeInto(root) {
            Box(Modifier.wrapContentHeight(Alignment.Bottom)) { Spacer(Modifier.size(40.dp)) }
        }
        root.layoutTree(Constraints.of(0, 100, 100, 100))

        assertEquals(60, root.children[0].children[0].absoluteY)
    }

    @Test
    fun unboundedWidthLetsTheChildExceedTheParentsMaximum() {
        val root = LayoutNode(passThrough)
        composeInto(root) {
            Box(Modifier.wrapContentSize(unbounded = true)) { Spacer(Modifier.size(140.dp, 20.dp)) }
        }
        root.layoutTree(Constraints.of(0, 100, 0, 100))

        assertEquals(100, root.children[0].width)
        assertEquals(-20, root.children[0].children[0].absoluteX)
    }
}

class SizeInTest {

    @Test
    fun aCeilingCapsTheContent() {
        val node = measured(Modifier.widthIn(max = 60.dp).fillMaxWidth())

        assertEquals(60, node.width)
    }

    @Test
    fun anOuterFillMaxWinsOverAnInnerCeiling() {
        // Chain order is meaningful and this is the surprising direction, so it is pinned rather
        // than left to be rediscovered: `fillMaxWidth` outermost hands down min == max == 200, and
        // a ceiling inside it cannot go below an incoming minimum. Compose behaves the same way.
        val node = measured(Modifier.fillMaxWidth().widthIn(max = 60.dp))

        assertEquals(200, node.width)
    }

    @Test
    fun aFloorRaisesIt() {
        val node = measured(Modifier.heightIn(min = 30.dp))

        assertEquals(30, node.height)
    }

    @Test
    fun aCeilingCannotExceedWhatTheParentOffers() {
        // Asking for a 500 dp ceiling in a 100 px parent must not hand the child a bound it can take.
        val node = measured(Modifier.fillMaxWidth().widthIn(max = 500.dp), max = 100)

        assertEquals(100, node.width)
    }
}

class RequiredSizeTest {

    @Test
    fun itOverflowsRatherThanShrinking() {
        // `size` clamps into the parent; this does not. A 24 dp icon silently shrunk to 16 reads as
        // a rendering bug, so the overflow is deliberate and visible.
        val clamped = measured(Modifier.size(40.dp), max = 20)
        val required = measured(Modifier.requiredSize(40.dp), max = 20)

        assertEquals(20, clamped.width, "size clamps")
        assertEquals(40, required.width, "requiredSize does not")
    }
}

class DefaultMinSizeTest {

    @Test
    fun itAppliesWhenNothingWasAsked() {
        val node = measured(Modifier.defaultMinSize(minHeight = 36.dp))

        assertEquals(36, node.height)
    }

    @Test
    fun itYieldsToAnExplicitSize() {
        // A default that overrode the caller would not be a default.
        val node = measured(Modifier.size(10.dp).defaultMinSize(minHeight = 36.dp))

        assertEquals(10, node.height)
    }
}
