/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.layout.Layer
import com.awakekt.awake.compose.ui.layout.LayerKind
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.SemanticsRole
import com.awakekt.awake.compose.ui.semantics.SemanticsTreeBuilder
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private object SemType

private val stack = MeasurePolicy { measurables, constraints ->
    val placeables = measurables.map { it.measure(constraints) }
    val w = placeables.maxOfOrNull { it.width } ?: constraints.minWidth
    val h = placeables.maxOfOrNull { it.height } ?: constraints.minHeight
    layout(constraints.constrainWidth(w), constraints.constrainHeight(h)) {
        placeables.forEach { it.placeAt(0, 0) }
    }
}

context(_: Composer)
private fun sem(
    modifier: Modifier,
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) =
    Layout(SemType, modifier = modifier, measurePolicy = stack, content = content)

private fun semanticsOf(content: context(Composer) () -> Unit): List<SemanticsNode> {
    val root = LayoutNode(stack)
    composeInto(root, content)
    root.layoutTree(Constraints.of(0, 200, 0, 200))
    return SemanticsTreeBuilder().build(root)
}

class SemanticsTest {

    @Test
    fun aNodeWithNoSemanticsIsNotInTheTree() {
        // The accessibility tree is not the layout tree. A padding wrapper is not a thing a screen
        // reader should announce.
        val nodes = semanticsOf { sem(Modifier.size(20.dp)) }

        assertEquals(0, nodes.size)
    }

    @Test
    fun aTaggedNodeAppearsWithItsPlacedBounds() {
        val nodes = semanticsOf {
            sem(Modifier.padding(10.dp)) {
                sem(Modifier.size(30.dp).testTag("target"))
            }
        }

        assertEquals(1, nodes.size)
        assertEquals("target", nodes[0].testTag)
        assertEquals(10, nodes[0].x, "bounds come from resolved positions, not local ones")
        assertEquals(30, nodes[0].width)
    }

    @Test
    fun nestingIsPreservedForNonMergingNodes() {
        val nodes = semanticsOf {
            sem(Modifier.size(60.dp).testTag("outer")) {
                sem(Modifier.size(20.dp).testTag("inner"))
            }
        }

        assertEquals(1, nodes.size)
        assertEquals("outer", nodes[0].testTag)
        assertEquals(listOf("inner"), nodes[0].children.map { it.testTag })
    }

    @Test
    fun aMergingNodeCollapsesItsSubtreeIntoOne() {
        // Every ui-headless control has this shape: a button wrapping a text. It must announce as
        // one button, not a button containing a text.
        val nodes = semanticsOf {
            sem(
                Modifier.size(60.dp).semantics(mergeDescendants = true) {
                    this[SemanticsProperties.Role] = SemanticsRole.Button
                },
            ) {
                sem(Modifier.size(20.dp).semantics { this[SemanticsProperties.Label] = "Save" })
            }
        }

        assertEquals(1, nodes.size)
        assertEquals(0, nodes[0].children.size, "the text was absorbed, not nested")
        assertEquals(SemanticsRole.Button, nodes[0].role)
        assertEquals("Save", nodes[0].label, "the descendant's label came up with it")
    }

    @Test
    fun theMergingNodeWinsOnConflict() {
        // An ancestor states intent; a descendant only fills gaps. A button labelled "Save" that
        // contains a text reading "Save now" announces "Save".
        val nodes = semanticsOf {
            sem(
                Modifier.size(60.dp).semantics(mergeDescendants = true) {
                    this[SemanticsProperties.Label] = "Save"
                },
            ) {
                sem(Modifier.size(20.dp).semantics { this[SemanticsProperties.Label] = "Save now" })
            }
        }

        assertEquals("Save", nodes[0].label)
    }

    @Test
    fun mergingReachesThroughUnannotatedWrappers() {
        // The label sits two levels down behind a plain padding node, which is what a real recipe
        // looks like.
        val nodes = semanticsOf {
            sem(
                Modifier.semantics(mergeDescendants = true) {
                    this[SemanticsProperties.Role] = SemanticsRole.Checkbox
                },
            ) {
                sem(Modifier.padding(4.dp)) {
                    sem(Modifier.size(10.dp).semantics { this[SemanticsProperties.Label] = "Agree" })
                }
            }
        }

        assertEquals("Agree", nodes[0].label)
    }

    @Test
    fun siblingsAppearInTreeOrderNotEmissionOrder() {
        val nodes = semanticsOf {
            sem(Modifier.size(10.dp).testTag("first"))
            sem(Modifier.size(10.dp).testTag("second"))
        }

        assertEquals(listOf("first", "second"), nodes.map { it.testTag })
    }

    @Test
    fun aLayersContentIsReachable() {
        // A dialog's contents have to be announceable, or the overlay is invisible to a screen
        // reader and to every test that looks for it.
        val nodes = semanticsOf {
            sem(Modifier.size(20.dp).testTag("content"))
            Layer(
                LayerKind.Dialog,
                modifier = Modifier.size(20.dp).testTag("dialog"),
                measurePolicy = stack,
            )
        }

        assertEquals(listOf("content", "dialog"), nodes.map { it.testTag })
    }

    @Test
    fun severalSemanticsLinksOnOneChainMerge() {
        val nodes = semanticsOf {
            sem(
                Modifier.size(20.dp)
                    .testTag("tagged")
                    .semantics { this[SemanticsProperties.Role] = SemanticsRole.Switch },
            )
        }

        assertEquals(1, nodes.size)
        assertEquals("tagged", nodes[0].testTag)
        assertEquals(SemanticsRole.Switch, nodes[0].role)
    }

    @Test
    fun theOutermostLinkWinsOnConflict() {
        // Matches how a chain reads left to right, so a wrapper can override what it wraps.
        val nodes = semanticsOf {
            sem(Modifier.size(20.dp).testTag("outer").testTag("inner"))
        }

        assertEquals("outer", nodes[0].testTag)
    }

    @Test
    fun anAbsentPropertyReadsNullRatherThanADefault() {
        // Tri-state matters: absent means the widget has no indeterminate concept at all, which is
        // different from having one that is false.
        val nodes = semanticsOf { sem(Modifier.size(20.dp).testTag("plain")) }

        assertNull(nodes[0].config[SemanticsProperties.Indeterminate])
        assertNull(nodes[0].role)
    }
}
