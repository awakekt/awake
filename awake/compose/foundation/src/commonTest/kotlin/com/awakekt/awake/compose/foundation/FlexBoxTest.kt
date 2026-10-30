/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.FlexAlignContent
import com.awakekt.awake.compose.foundation.layout.FlexAlignItems
import com.awakekt.awake.compose.foundation.layout.FlexBox
import com.awakekt.awake.compose.foundation.layout.FlexDirection
import com.awakekt.awake.compose.foundation.layout.FlexWrap
import com.awakekt.awake.compose.foundation.layout.flex
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class FlexBoxTest {
    @Test
    fun wrapBuildsLinesFromTheConfiguredBasisAndGap() {
        val nodes = nodes {
            FlexBox(Modifier.size(100.dp, 40.dp), config = {
                wrap = FlexWrap.Wrap
                gap(10.dp)
            }) {
                item("first", 40.dp)
                item("second", 40.dp)
                item("third", 40.dp)
            }
        }

        assertEquals(0, nodes.getValue("first").x)
        assertEquals(50, nodes.getValue("second").x)
        assertEquals(0, nodes.getValue("third").x)
        assertEquals(20, nodes.getValue("third").y)
    }

    @Test
    fun growConsumesAlineRemainingSpace() {
        val nodes = nodes {
            FlexBox(Modifier.size(100.dp, 20.dp)) {
                item("grow", 20.dp, Modifier.flex { grow(1f) })
                item("fixed", 20.dp)
            }
        }

        assertEquals(80, nodes.getValue("grow").width)
        assertEquals(20, nodes.getValue("fixed").width)
        assertEquals(80, nodes.getValue("fixed").x)
    }

    @Test
    fun orderChangesVisualPlacementButKeepsStableTies() {
        val nodes = nodes {
            FlexBox(Modifier.size(60.dp, 20.dp)) {
                item("declaredFirst", 20.dp)
                item("orderedFirst", 20.dp, Modifier.flex { order(-1) })
                item("declaredLast", 20.dp)
            }
        }

        assertEquals(0, nodes.getValue("orderedFirst").x)
        assertEquals(20, nodes.getValue("declaredFirst").x)
        assertEquals(40, nodes.getValue("declaredLast").x)
    }

    @Test
    fun columnDirectionUsesHeightAsItsMainAxis() {
        val nodes = nodes {
            FlexBox(Modifier.size(40.dp, 100.dp), config = { direction = FlexDirection.Column }) {
                item("grow", 20.dp, Modifier.flex { grow(1f) })
                item("fixed", 20.dp)
            }
        }

        assertEquals(90, nodes.getValue("grow").height)
        assertEquals(90, nodes.getValue("fixed").y)
    }

    @Test
    fun rowReversePlacesTheFirstItemAtTheMainAxisEnd() {
        val nodes = nodes {
            FlexBox(Modifier.size(60.dp, 20.dp), config = { direction = FlexDirection.RowReverse }) {
                item("first", 20.dp)
                item("second", 20.dp)
            }
        }

        assertEquals(40, nodes.getValue("first").x)
        assertEquals(20, nodes.getValue("second").x)
    }

    @Test
    fun wrapReverseAddsTheLaterLineAtTheCrossAxisStart() {
        val nodes = nodes {
            FlexBox(Modifier.size(40.dp, 40.dp), config = { wrap = FlexWrap.WrapReverse }) {
                item("first", 20.dp)
                item("second", 20.dp)
                item("third", 20.dp)
            }
        }

        assertEquals(10, nodes.getValue("first").y)
        assertEquals(0, nodes.getValue("third").y)
    }

    @Test
    fun stretchedLinesShareTheFreeCrossSpaceOnce() {
        val nodes = nodes {
            FlexBox(Modifier.size(100.dp, 40.dp), config = {
                alignContent = FlexAlignContent.Stretch
                alignItems = FlexAlignItems.Center
            }) {
                item("centred", 20.dp)
            }
            FlexBox(Modifier.size(100.dp, 60.dp), config = {
                wrap = FlexWrap.Wrap
                alignContent = FlexAlignContent.Stretch
            }) {
                item("top", 60.dp)
                item("second", 60.dp)
            }
        }

        assertEquals(15, nodes.getValue("centred").y, "centred in the one line, which spans the 40 dp")
        // Two 10-dp lines in 60 dp: each grows by half the 40 dp left, so the second starts 30 down.
        assertEquals(30, nodes.getValue("second").y)
    }

    @Test
    fun aWrappingRowThatFillsItsBoxStaysOnOneLineAtFractionalDensities() {
        // 4 x 5 dp and 3 x 1 dp gaps fill 23 dp exactly; at 1.5 they round to 4 x 8 + 3 x 2 = 38 px in 35.
        for (density in listOf(0.75f, 0.83f, 1.25f, 1.5f, 1.75f)) {
            val nodes = nodes(density) { slotRow(23.dp) }

            assertEquals(listOf(0, 0, 0, 0), slotTags.map { nodes.getValue(it).y }, "one line at density $density")
        }
    }

    @Test
    fun aWrappingRowStillWrapsWhenItOverflowsByMoreThanRounding() {
        // 3 dp short is 4.5 px at 1.5: past the 4 px the four children's rounding can account for.
        val nodes = nodes(1.5f) { slotRow(20.dp) }

        // The first line is 8 px tall (5 dp at 1.5, rounded), and the 1 dp gap rounds to 2 px.
        assertEquals(0, nodes.getValue("slot3").x, "the last slot starts the second line")
        assertEquals(10, nodes.getValue("slot3").y)
    }

    @Test
    fun aWholeDensityGivesNoRoundingSlack() {
        // 1 dp short at density 2 is 2 px over, which whole pixels cannot explain: the last slot wraps.
        val nodes = nodes(2f) { slotRow(22.dp) }

        assertEquals(12, nodes.getValue("slot3").y)
    }

    context(_: Composer)
    private fun slotRow(width: com.awakekt.awake.compose.ui.unit.Dp) {
        FlexBox(Modifier.size(width, 20.dp), config = {
            wrap = FlexWrap.Wrap
            gap(1.dp)
        }) {
            slotTags.forEach { Box(Modifier.size(5.dp, 5.dp).testTag(it)) }
        }
    }

    private fun nodes(density: Float = 1f, content: context(Composer) () -> Unit): Map<String, SemanticsNode> {
        val frame = ComposeHost(density = density).frame(FrameInput(viewportWidth = 120, viewportHeight = 120), content)
        val all = mutableListOf<SemanticsNode>()
        fun collect(values: List<SemanticsNode>) {
            values.forEach {
                all += it
                collect(it.children)
            }
        }
        collect(frame.semantics)
        return all.filter { it.testTag != null }.associateBy { it.testTag!! }
    }

    context(_: Composer)
    private fun com.awakekt.awake.compose.foundation.layout.FlexBoxScope.item(
        tag: String,
        main: com.awakekt.awake.compose.ui.unit.Dp,
        modifier: Modifier = Modifier,
    ) {
        Box(modifier.then(Modifier.size(main, 10.dp)).testTag(tag))
    }

    private val slotTags = listOf("slot0", "slot1", "slot2", "slot3")
}
