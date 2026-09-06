/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.FlowColumn
import com.awakekt.awake.compose.foundation.layout.FlowRow
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class FlowLayoutTest {
    @Test fun flowRowWrapsAtBoundedWidth() = assertEquals(listOf(0 to 0, 20 to 0, 0 to 10), positions { FlowRow(Modifier.size(40.dp, 40.dp)) { items() } })

    @Test fun flowColumnWrapsAtBoundedHeight() = assertEquals(listOf(0 to 0, 0 to 10, 20 to 0), positions { FlowColumn(Modifier.size(40.dp, 20.dp)) { items() } })

    @Test
    fun flowRowAppliesItemAndLineSpacing() = assertEquals(
        listOf(0 to 0, 25 to 0, 0 to 13),
        positions {
            FlowRow(
                Modifier.size(45.dp, 40.dp),
                horizontalArrangement = Arrangement.spacedByHorizontal(5.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) { items() }
        },
    )

    @Test
    fun flowColumnAppliesItemAndColumnSpacing() = assertEquals(
        listOf(0 to 0, 0 to 12, 24 to 0),
        positions {
            FlowColumn(
                Modifier.size(50.dp, 25.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalArrangement = Arrangement.spacedByHorizontal(4.dp),
            ) { items() }
        },
    )

    @Test
    fun flowRowHonorsItsItemLimitBeforeItNeedsToWrap() = assertEquals(
        listOf(0 to 0, 20 to 0, 0 to 10),
        positions {
            FlowRow(
                modifier = Modifier.size(60.dp, 40.dp),
                maxItemsInEachRow = 2,
            ) { items() }
        },
    )

    @Test
    fun flowColumnHonorsItsItemLimitBeforeItNeedsToWrap() = assertEquals(
        listOf(0 to 0, 0 to 10, 20 to 0),
        positions {
            FlowColumn(
                modifier = Modifier.size(60.dp, 40.dp),
                maxItemsInEachColumn = 2,
            ) { items() }
        },
    )

    @Test
    fun flowRowArrangesItemsWithinEachLine() = assertEquals(
        listOf(0 to 0, 25 to 0, 50 to 0),
        positions {
            FlowRow(
                modifier = Modifier.size(60.dp, 20.dp),
                horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
            ) {
                repeat(3) { Box(Modifier.size(10.dp, 10.dp).testTag("item$it")) }
            }
        },
    )

    @Test
    fun flowRowAlignsItemsWithinTheirLine() = assertEquals(
        5,
        tagPositions {
            FlowRow(
                modifier = Modifier.size(60.dp, 30.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(20.dp, 10.dp).testTag("small"))
                Box(Modifier.size(20.dp, 20.dp).testTag("tall"))
            }
        }.getValue("small").second,
    )

    @Test
    fun flowRowAllocatesWeightWithinEachLine() = assertEquals(
        40,
        taggedNodes {
            FlowRow(Modifier.size(60.dp, 20.dp)) {
                Box(Modifier.size(20.dp, 10.dp).testTag("fixed"))
                Box(Modifier.size(10.dp, 10.dp).weight(1f).testTag("weighted"))
            }
        }.getValue("weighted").width,
    )

    @Test
    fun flowColumnAllocatesWeightWithinEachColumn() = assertEquals(
        40,
        taggedNodes {
            FlowColumn(Modifier.size(20.dp, 60.dp)) {
                Box(Modifier.size(10.dp, 20.dp).testTag("fixed"))
                Box(Modifier.size(10.dp, 10.dp).weight(1f).testTag("weighted"))
            }
        }.getValue("weighted").height,
    )

    @Test
    fun flowRowCollapsesItemsPastItsMaximumLineCount() = assertEquals(
        0,
        taggedNodes {
            FlowRow(
                modifier = Modifier.size(20.dp, 30.dp),
                maxLines = 1,
            ) { items() }
        }.getValue("item2").width,
    )

    private fun positions(content: context(Composer) () -> Unit): List<Pair<Int, Int>> {
        val nodes = tagPositions(content)
        return (0..2).map { nodes.getValue("item$it") }
    }

    private fun tagPositions(content: context(Composer) () -> Unit): Map<String, Pair<Int, Int>> = taggedNodes(content).mapValues { (_, node) -> node.x to node.y }

    private fun taggedNodes(content: context(Composer) () -> Unit): Map<String, SemanticsNode> {
        val frame = ComposeHost().frame(FrameInput(viewportWidth = 60, viewportHeight = 60), content)
        val nodes = mutableListOf<SemanticsNode>()
        fun collect(values: List<SemanticsNode>) {
            values.forEach {
                nodes += it
                collect(it.children)
            }
        }
        collect(frame.semantics)
        return nodes.filter { it.testTag != null }.associateBy { node -> node.testTag!! }
    }

    context(_: Composer)
    private fun com.awakekt.awake.compose.foundation.layout.RowScope.items() {
        repeat(3) { Box(Modifier.size(20.dp, 10.dp).testTag("item$it")) }
    }
    context(_: Composer)
    private fun com.awakekt.awake.compose.foundation.layout.ColumnScope.items() {
        repeat(3) { Box(Modifier.size(20.dp, 10.dp).testTag("item$it")) }
    }
}
