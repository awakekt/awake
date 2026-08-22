// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation

import io.github.ronjunevaldoz.awake.compose.foundation.layout.Arrangement
import io.github.ronjunevaldoz.awake.compose.foundation.layout.BoxMeasurePolicy
import io.github.ronjunevaldoz.awake.compose.foundation.layout.ColumnMeasurePolicy
import io.github.ronjunevaldoz.awake.compose.foundation.layout.IntrinsicSize
import io.github.ronjunevaldoz.awake.compose.foundation.layout.RowMeasurePolicy
import io.github.ronjunevaldoz.awake.compose.foundation.layout.height
import io.github.ronjunevaldoz.awake.compose.foundation.layout.padding
import io.github.ronjunevaldoz.awake.compose.foundation.layout.size
import io.github.ronjunevaldoz.awake.compose.foundation.layout.width
import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.layout.Layout
import io.github.ronjunevaldoz.awake.compose.ui.layout.composeInto
import io.github.ronjunevaldoz.awake.compose.ui.layout.layoutTree
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutNode
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints
import io.github.ronjunevaldoz.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

private object Item

context(_: Composer)
private fun item(width: Int, height: Int) =
    Layout(Item, modifier = Modifier.size(width.dp, height.dp), measurePolicy = BoxMeasurePolicy())

/**
 * The `Max` half of the intrinsics contract, which had no test at all.
 *
 * `IntrinsicSize.Min` was covered end to end while every `maxIntrinsic*` override -- on the policies,
 * on `padding`, on `size`, on the chain link -- was dead as far as the suite knew. A whole documented
 * half of the API can ship broken that way.
 */
class IntrinsicsMaxTest {

    @Test
    fun aColumnMaxesTheCrossAxisAndSumsTheMain() {
        val column = LayoutNode(ColumnMeasurePolicy())
        composeInto(column) {
            item(30, 10)
            item(50, 20)
        }

        assertEquals(50, column.maxIntrinsicWidth(500), "the widest child")
        assertEquals(30, column.maxIntrinsicHeight(500), "10 + 20")
    }

    @Test
    fun aRowIsTheTranspose() {
        val row = LayoutNode(RowMeasurePolicy())
        composeInto(row) {
            item(30, 10)
            item(50, 20)
        }

        assertEquals(80, row.maxIntrinsicWidth(500), "30 + 50")
        assertEquals(20, row.maxIntrinsicHeight(500), "the tallest child")
    }

    @Test
    fun arrangementSpacingCountsOnTheMaxSideToo() {
        val column = LayoutNode(ColumnMeasurePolicy(Arrangement.spacedBy(4.dp)))
        composeInto(column) {
            item(10, 10)
            item(10, 10)
            item(10, 10)
        }

        assertEquals(38, column.maxIntrinsicHeight(500), "30 plus two gaps, not three")
    }

    @Test
    fun paddingAddsToTheMaxAsWellAsTheMin() {
        // Without the override an intrinsic query walks past the link and reports the inner size as
        // if the padding were not there -- the same defect the Min side already guards.
        val node = LayoutNode(BoxMeasurePolicy())
        composeInto(node) { item(20, 20) }
        node.modifier = Modifier.padding(6.dp)

        assertEquals(32, node.maxIntrinsicWidth(500))
        assertEquals(32, node.maxIntrinsicHeight(500))
    }

    @Test
    fun aFixedSizeIsTheMaxAnswerToo() {
        val node = LayoutNode(BoxMeasurePolicy())
        composeInto(node) { item(200, 200) }
        node.modifier = Modifier.size(40.dp)

        assertEquals(40, node.maxIntrinsicWidth(500))
        assertEquals(40, node.maxIntrinsicHeight(500))
    }

    @Test
    fun widthIntrinsicMaxSizesToTheWidestChild() {
        val root = LayoutNode(BoxMeasurePolicy())
        composeInto(root) {
            Layout(Item, modifier = Modifier.width(IntrinsicSize.Max), measurePolicy = ColumnMeasurePolicy()) {
                item(30, 10)
                item(70, 10)
            }
        }
        root.layoutTree(Constraints.of(0, 500, 0, 500))

        assertEquals(70, root.children[0].width)
    }

    @Test
    fun heightIntrinsicSizesToTheContentToo() {
        // The other axis of the same modifier: `height(IntrinsicSize.Min)` had no test either.
        val root = LayoutNode(BoxMeasurePolicy())
        composeInto(root) {
            Layout(Item, modifier = Modifier.height(IntrinsicSize.Min), measurePolicy = ColumnMeasurePolicy()) {
                item(10, 15)
                item(10, 25)
            }
        }
        root.layoutTree(Constraints.of(0, 500, 0, 500))

        assertEquals(40, root.children[0].height, "15 + 25, not the 500 offered")
    }

    @Test
    fun anIntrinsicSizedNodeReportsTheSameAnswerToItsOwnParent() {
        // A chain of intrinsics has to agree, or an outer query sees the inner node's raw content.
        val node = LayoutNode(ColumnMeasurePolicy())
        composeInto(node) {
            item(30, 10)
            item(70, 10)
        }
        node.modifier = Modifier.width(IntrinsicSize.Max)

        assertEquals(70, node.maxIntrinsicWidth(500))
        assertEquals(70, node.minIntrinsicWidth(500))
    }

    @Test
    fun heightIntrinsicLeavesTheWidthQueryAlone() {
        val node = LayoutNode(ColumnMeasurePolicy())
        composeInto(node) {
            item(30, 10)
            item(70, 20)
        }
        node.modifier = Modifier.height(IntrinsicSize.Min)

        assertEquals(70, node.maxIntrinsicWidth(500), "the height modifier hijacked the width query")
        assertEquals(30, node.maxIntrinsicHeight(500))
    }
}
