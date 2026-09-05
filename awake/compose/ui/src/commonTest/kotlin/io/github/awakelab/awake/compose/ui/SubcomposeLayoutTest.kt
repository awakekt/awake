/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui

import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.layout.Layout
import io.github.awakelab.awake.compose.ui.layout.MeasurePolicy
import io.github.awakelab.awake.compose.ui.layout.SubcomposeLayout
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.platform.ComposeHost
import io.github.awakelab.awake.compose.ui.platform.FrameInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

private object TestChildType

private fun fixedPolicy(width: Int, height: Int) = MeasurePolicy { _, constraints ->
    layout(constraints.constrainWidth(width), constraints.constrainHeight(height)) {}
}

context(_: Composer)
private fun fixedChild(width: Int, height: Int) =
    Layout(TestChildType, measurePolicy = fixedPolicy(width, height))

class SubcomposeLayoutTest {

    @Test
    fun subcomposeComposesDuringMeasureAndPlacesChildren() {
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            SubcomposeLayout { constraints ->
                val measurables = subcompose("header") {
                    fixedChild(40, 20)
                }
                val placeable = measurables.single().measure(constraints)
                layout(100, 50) {
                    placeable.placeAt(10, 15)
                }
            }
        }

        host.frame(FrameInput(viewportWidth = 100, viewportHeight = 50), content)

        val subcomposeNode = host.root.children[0]
        assertEquals(100, subcomposeNode.width)
        assertEquals(50, subcomposeNode.height)

        val childNode = subcomposeNode.children[0]
        assertEquals(40, childNode.width)
        assertEquals(20, childNode.height)
        assertEquals(10, childNode.absoluteX)
        assertEquals(15, childNode.absoluteY)
    }

    @Test
    fun subcomposeReusesSameSlotNodeAcrossPasses() {
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            SubcomposeLayout { constraints ->
                val measurables = subcompose("slot") {
                    fixedChild(50, 50)
                }
                val placeable = measurables.single().measure(constraints)
                layout(100, 100) {
                    placeable.placeAt(0, 0)
                }
            }
        }

        host.frame(FrameInput(viewportWidth = 100, viewportHeight = 100), content)
        val capturedNode = host.root.children[0].children[0]

        host.frame(FrameInput(viewportWidth = 100, viewportHeight = 100), content)
        val secondPassNode = host.root.children[0].children[0]

        assertSame(capturedNode, secondPassNode, "Subcomposition must reuse retained LayoutNode across passes")
    }

    @Test
    fun subcomposeCanBranchBasedOnIncomingConstraints() {
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            SubcomposeLayout { constraints ->
                val measurables = subcompose("dynamic") {
                    if (constraints.maxWidth > 100) {
                        fixedChild(120, 40)
                    } else {
                        fixedChild(60, 20)
                    }
                }
                val placeable = measurables.single().measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeAt(0, 0)
                }
            }
        }

        host.frame(FrameInput(viewportWidth = 200, viewportHeight = 100), content)
        val wideChild = host.root.children[0].children[0]
        assertEquals(120, wideChild.width)

        host.frame(FrameInput(viewportWidth = 80, viewportHeight = 100), content)
        val narrowChild = host.root.children[0].children[0]
        assertEquals(60, narrowChild.width)
    }
}
