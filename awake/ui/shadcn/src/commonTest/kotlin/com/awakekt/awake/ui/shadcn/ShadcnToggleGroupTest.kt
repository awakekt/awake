/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.testing.composeFrame
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.input.pointer.PointerInputDispatcher
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.SemanticsRole
import com.awakekt.awake.compose.ui.semantics.SemanticsTreeBuilder
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.core.graphics2d.ColoredTriangleMesh
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.graphics2d.bounds
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.ui.shadcn.components.ShadcnToggleGroup
import com.awakekt.awake.ui.shadcn.components.ShadcnToggleGroupSelection
import com.awakekt.awake.ui.shadcn.components.ShadcnToggleGroupVariant
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a toggle group is for, in two parts: it reports its kind, and it toggles.
 *
 * The reporting half is the reason this component exists rather than another `ShadcnButtonGroup`.
 * Studio's pills faked pressed state with a button variant, so a screen reader heard thirteen
 * unrelated buttons where the screen shows one modal choice and two sets of independent switches.
 */
class ShadcnToggleGroupTest {

    private class Host {
        val root = LayoutNode(ColumnMeasurePolicy())

        fun frame(content: context(Composer) () -> Unit): List<SemanticsNode> {
            composeInto(root) { provideShadcnTheme(shadcnThemeValues(dark = true)) { content() } }
            root.layoutTree(Constraints.of(0, 400, 0, 400))
            return SemanticsTreeBuilder().build(root)
        }
    }

    private fun List<SemanticsNode>.byLabel(label: String): SemanticsNode =
        firstNotNullOfOrNull { find(it, label) } ?: error("no node labelled '$label'")

    private fun find(node: SemanticsNode, label: String): SemanticsNode? =
        if (node.label == label) node else node.children.firstNotNullOfOrNull { find(it, label) }

    @Test
    fun singleSelectReportsRadioButtons() {
        val host = Host()
        val semantics = host.frame {
            ShadcnToggleGroup(selected = "move", onSelectedChange = {}) {
                item("select", "Select")
                item("move", "Move")
            }
        }

        assertEquals(SemanticsRole.RadioButton, semantics.byLabel("Move").role)
        assertEquals(true, semantics.byLabel("Move").config[SemanticsProperties.Selected])
        assertEquals(false, semantics.byLabel("Select").config[SemanticsProperties.Selected])
    }

    @Test
    fun multiSelectReportsButtons() {
        val host = Host()
        val semantics = host.frame {
            ShadcnToggleGroup(
                selected = setOf("shadows"),
                onSelectedChange = {},
                selection = ShadcnToggleGroupSelection.Multiple,
            ) {
                item("wireframe", "Wireframe")
                item("shadows", "Shadows")
            }
        }

        // Not a radio: these are independent, and calling them radios would tell a reader that
        // turning shadows on turns wireframe off.
        assertEquals(SemanticsRole.Button, semantics.byLabel("Shadows").role)
    }

    @Test
    fun clickingAnItemReportsTheNewSelection() {
        var selected: String? = "select"
        val host = Host()
        val content: context(Composer)
        () -> Unit = {
            ShadcnToggleGroup(selected = selected, onSelectedChange = { selected = it }) {
                item("select", "Select")
                item("move", "Move")
            }
        }
        host.frame(content)
        val move = host.frame(content).byLabel("Move")

        val dispatcher = PointerInputDispatcher()
        val x = move.x + move.width / 2
        val y = move.y + move.height / 2
        dispatcher.dispatch(host.root, PointerEvent(PointerEventType.Press), x, y)
        dispatcher.dispatch(host.root, PointerEvent(PointerEventType.Release), x, y)

        assertEquals("move", selected, "single select must replace, not add")
    }

    @Test
    fun clickingTheActiveItemInASingleGroupClearsIt() {
        // Radix's behaviour, and the reason the single-select callback is nullable. A screen for
        // which empty is meaningless keeps its old value in its own handler.
        var selected: String? = "select"
        val host = Host()
        val content: context(Composer)
        () -> Unit = {
            ShadcnToggleGroup(selected = selected, onSelectedChange = { selected = it }) {
                item("select", "Select")
                item("move", "Move")
            }
        }
        host.frame(content)
        val active = host.frame(content).byLabel("Select")

        val dispatcher = PointerInputDispatcher()
        val x = active.x + active.width / 2
        val y = active.y + active.height / 2
        dispatcher.dispatch(host.root, PointerEvent(PointerEventType.Press), x, y)
        dispatcher.dispatch(host.root, PointerEvent(PointerEventType.Release), x, y)

        assertEquals(null, selected)
    }

    @Test
    fun aMultiSelectItemAddsWithoutClearingTheOthers() {
        var selected = setOf("shadows")
        val host = Host()
        val content: context(Composer)
        () -> Unit = {
            ShadcnToggleGroup(
                selected = selected,
                onSelectedChange = { selected = it },
                selection = ShadcnToggleGroupSelection.Multiple,
            ) {
                item("wireframe", "Wireframe")
                item("shadows", "Shadows")
            }
        }
        host.frame(content)
        val wireframe = host.frame(content).byLabel("Wireframe")

        val dispatcher = PointerInputDispatcher()
        val x = wireframe.x + wireframe.width / 2
        val y = wireframe.y + wireframe.height / 2
        dispatcher.dispatch(host.root, PointerEvent(PointerEventType.Press), x, y)
        dispatcher.dispatch(host.root, PointerEvent(PointerEventType.Release), x, y)

        assertEquals(setOf("shadows", "wireframe"), selected)
    }

    @Test
    fun outlineToggleGroupRendersSeamlessBordersWithoutDuplicates() {
        val paths = composeFrame(200, 50) {
            provideShadcnTheme(shadcnThemeValues(dark = true)) {
                ShadcnToggleGroup(
                    selected = "a",
                    onSelectedChange = {},
                    variant = ShadcnToggleGroupVariant.Outline,
                ) {
                    item("a", "Alpha")
                    item("b", "Beta")
                }
            }
        }.primitives.filterIsInstance<UiDrawPrimitive.Mesh>()

        assertEquals(2, paths.size)
        val first = paths.first().placedMesh()
        val boundsFirst = first.bounds()
        val second = paths.last().placedMesh()
        val boundsSecond = second.bounds()

        fun paintsVerticalEdgeIn(
            mesh: ColoredTriangleMesh,
            bounds: Rectangle,
            from: Float,
            to: Float,
        ): Boolean = mesh.vertices.any { vertex ->
            vertex.color.a > 0f &&
                vertex.position.x >= from && vertex.position.x <= to &&
                vertex.position.y > bounds.y + 4f &&
                vertex.position.y < bounds.y + bounds.height - 4f
        }

        val firstStart = boundsFirst.x to boundsFirst.x + 2f
        val firstEnd = boundsFirst.x + boundsFirst.width - 2f to boundsFirst.x + boundsFirst.width
        val secondStart = boundsSecond.x to boundsSecond.x + 2f
        val secondEnd =
            boundsSecond.x + boundsSecond.width - 2f to boundsSecond.x + boundsSecond.width

        // The first toggle item paints its start border and its shared divider border
        assertTrue(
            paintsVerticalEdgeIn(first, boundsFirst, firstStart.first, firstStart.second),
            "the first toggle item lost its outer start border",
        )
        assertTrue(
            paintsVerticalEdgeIn(first, boundsFirst, firstEnd.first, firstEnd.second),
            "the first toggle item must paint the shared divider border",
        )
        // The second toggle item omits its start border so there is no 2px double border
        assertTrue(
            !paintsVerticalEdgeIn(second, boundsSecond, secondStart.first, secondStart.second),
            "the second toggle item must not paint a duplicate start border",
        )
        assertTrue(
            paintsVerticalEdgeIn(second, boundsSecond, secondEnd.first, secondEnd.second),
            "the second toggle item must paint its outer end border",
        )
    }
}
