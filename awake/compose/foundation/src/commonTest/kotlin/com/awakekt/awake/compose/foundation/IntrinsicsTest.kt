/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.BoxMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.IntrinsicSize
import com.awakekt.awake.compose.foundation.layout.RowMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.LayoutStats
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private object ItemType

context(_: Composer)
private fun item(width: Int, height: Int) =
    Layout(ItemType, modifier = Modifier.size(width.dp, height.dp), measurePolicy = BoxMeasurePolicy())

private fun laidOut(content: context(Composer) () -> Unit): LayoutNode {
    val root = LayoutNode(BoxMeasurePolicy())
    composeInto(root, content)
    root.layoutTree(Constraints.of(0, 500, 0, 500))
    return root
}

class IntrinsicsTest {

    @AfterTest
    fun disableStats() {
        LayoutStats.enabled = false
        LayoutStats.reset()
    }

    @Test
    fun aColumnSumsItsChildrenOnTheMainAxisAndMaxesTheCross() {
        // The inherited Box-shaped default would max both, which reads as a Column claiming it
        // needs only its tallest child's height.
        val column = LayoutNode(ColumnMeasurePolicy())
        composeInto(column) {
            item(30, 10)
            item(50, 20)
        }

        assertEquals(30, column.minIntrinsicHeight(500), "10 + 20")
        assertEquals(50, column.minIntrinsicWidth(500), "the widest child")
    }

    @Test
    fun aRowIsTheTranspose() {
        val row = LayoutNode(RowMeasurePolicy())
        composeInto(row) {
            item(30, 10)
            item(50, 20)
        }

        assertEquals(80, row.minIntrinsicWidth(500), "30 + 50")
        assertEquals(20, row.minIntrinsicHeight(500), "the tallest child")
    }

    @Test
    fun arrangementSpacingIsCountedBetweenChildrenOnly() {
        val column = LayoutNode(ColumnMeasurePolicy(Arrangement.spacedBy(4.dp)))
        composeInto(column) {
            item(10, 10)
            item(10, 10)
            item(10, 10)
        }

        assertEquals(38, column.minIntrinsicHeight(500), "30 plus two gaps, not three")
    }

    @Test
    fun paddingAddsToWhatTheContentAsksFor() {
        // Without the override an intrinsic query walks past the link and reports the inner size as
        // if the padding were not there.
        val node = LayoutNode(BoxMeasurePolicy())
        composeInto(node) { item(20, 20) }
        node.modifier = Modifier.padding(6.dp)

        assertEquals(32, node.minIntrinsicWidth(500), "20 + 6 + 6")
    }

    @Test
    fun aFixedSizeIsTheAnswerWhateverTheContentWanted() {
        val node = LayoutNode(BoxMeasurePolicy())
        composeInto(node) { item(200, 200) }
        node.modifier = Modifier.size(40.dp)

        assertEquals(40, node.minIntrinsicWidth(500))
    }

    @Test
    fun widthIntrinsicMinSizesToTheWidestChild() {
        // The dropdown case: a menu is as wide as its widest item and nobody can write that down.
        val root = laidOut {
            Layout(
                ItemType,
                modifier = Modifier.width(IntrinsicSize.Min),
                measurePolicy = ColumnMeasurePolicy(),
            ) {
                item(30, 10)
                item(70, 10)
                item(50, 10)
            }
        }

        assertEquals(70, root.children[0].width)
    }

    @Test
    fun intrinsicWidthIgnoresTheIncomingMaximum() {
        // It sizes to content, not to the space offered -- that is the whole difference from
        // fillMaxWidth.
        val root = laidOut {
            Layout(
                ItemType,
                modifier = Modifier.width(IntrinsicSize.Min),
                measurePolicy = ColumnMeasurePolicy(),
            ) {
                item(25, 10)
            }
        }

        assertEquals(25, root.children[0].width, "not the 500 the viewport offered")
    }

    @Test
    fun queriesAreCountedWhenStatsAreArmed() {
        // An intrinsic costs an extra subtree walk, and Compose never says when you paid for one.
        LayoutStats.enabled = true
        LayoutStats.reset()
        laidOut {
            Layout(
                ItemType,
                modifier = Modifier.width(IntrinsicSize.Min),
                measurePolicy = ColumnMeasurePolicy(),
            ) {
                item(30, 10)
            }
        }

        assertTrue(LayoutStats.intrinsicQueries > 0)
    }

    @Test
    fun countingIsFreeWhenStatsAreOff() {
        LayoutStats.enabled = false
        LayoutStats.reset()
        laidOut {
            Layout(
                ItemType,
                modifier = Modifier.width(IntrinsicSize.Min),
                measurePolicy = ColumnMeasurePolicy(),
            ) {
                item(30, 10)
            }
        }

        assertEquals(0, LayoutStats.intrinsicQueries)
    }
}
