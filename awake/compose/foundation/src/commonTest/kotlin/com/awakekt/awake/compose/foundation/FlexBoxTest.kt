/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Box
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

    private fun nodes(content: context(Composer) () -> Unit): Map<String, SemanticsNode> {
        val frame = ComposeHost().frame(FrameInput(viewportWidth = 120, viewportHeight = 120), content)
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
}
