/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.input.pointer.PointerInputDispatcher
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.semantics.SemanticsTreeBuilder
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.ui.shadcn.components.ShadcnCheckbox
import com.awakekt.awake.ui.shadcn.components.ShadcnPopover
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Several checkboxes in one popover each deliver their own click.
 *
 * `StudioPills.kt` carried a note that they did not -- "a single checkbox alone in a popover works,
 * this doesn't" -- and the viewport controls were reverted from a popover to an always-visible strip
 * because of it. That was the `ui-core` era, where a popover's content ran unsuppressed during
 * trial passes and the recipes returned a polled `Boolean` that the *next* build read.
 *
 * Both causes are gone: there are no trial passes on this engine, and `ShadcnCheckbox` takes a
 * callback. This is the test that says so, so the next person to want a popover of toggles does not
 * inherit a warning that no longer applies.
 */
class ShadcnPopoverSiblingCheckboxTest {

    @Test
    fun everySiblingCheckboxInAPopoverTogglesItself() {
        val checked = booleanArrayOf(false, false, false)
        val root = LayoutNode(ColumnMeasurePolicy())
        val content: context(Composer)
        () -> Unit = {
            provideShadcnTheme(shadcnThemeValues(dark = true)) {
                ShadcnPopover {
                    Column(Modifier) {
                        repeat(checked.size) { index ->
                            ShadcnCheckbox(
                                checked[index],
                                Modifier.testTag("box-$index"),
                                onCheckedChange = { checked[index] = it },
                            )
                        }
                    }
                }
            }
        }

        fun frame(): List<SemanticsNode> {
            composeInto(root, content)
            root.layoutTree(Constraints.of(0, 400, 0, 400))
            return SemanticsTreeBuilder().build(root)
        }

        val dispatcher = PointerInputDispatcher()
        repeat(checked.size) { index ->
            val box = frame().find("box-$index") ?: error("no checkbox tagged 'box-$index'")
            val x = box.x + box.width / 2
            val y = box.y + box.height / 2
            dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), x, y)
            dispatcher.dispatch(root, PointerEvent(PointerEventType.Release), x, y)
        }

        assertEquals(
            listOf(true, true, true),
            checked.toList(),
            "a checkbox in a popover did not receive its own click",
        )
    }

    private fun List<SemanticsNode>.find(tag: String): SemanticsNode? =
        firstNotNullOfOrNull { search(it, tag) }

    private fun search(node: SemanticsNode, tag: String): SemanticsNode? =
        if (node.testTag == tag) node else node.children.firstNotNullOfOrNull { search(it, tag) }
}
