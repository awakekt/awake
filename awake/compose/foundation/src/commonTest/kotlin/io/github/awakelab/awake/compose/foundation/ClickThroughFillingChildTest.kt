/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation

import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.ColumnMeasurePolicy
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEvent
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEventType
import io.github.awakelab.awake.compose.ui.input.pointer.PointerInputDispatcher
import io.github.awakelab.awake.compose.ui.layout.composeInto
import io.github.awakelab.awake.compose.ui.layout.layoutTree
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.unit.Constraints
import io.github.awakelab.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A child that exactly covers its clickable parent must not swallow the parent's click.
 *
 * Capture followed depth: the press was handed to `path.last()`, the innermost node under the
 * pointer. That is the same node as the consumer only while nothing fills the parent, so every
 * plain button passed. Give a Row's only child `weight(1f)` -- which is how a label is made to take
 * the leftover width, and what every sidebar row and list item does -- and the child covers the Row
 * pixel for pixel. The child took a capture it had no handler for, and the Row's release then
 * arrived with `isCaptureHolder = false`, so it highlighted on press and never fired.
 *
 * Studio's hierarchy list is where this showed up: rows lit up under the pointer and selected
 * nothing.
 */
class ClickThroughFillingChildTest {

    @Test
    fun aWeightedChildDoesNotStealItsParentsClick() {
        var clicks = 0
        val content: context(Composer)
        () -> Unit = {
            Row(Modifier.fillMaxWidth().height(20.dp).clickable { clicks++ }) {
                Spacer(Modifier.weight(1f).height(20.dp))
            }
        }
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root, content)
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        val dispatcher = PointerInputDispatcher()
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), 100, 10)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Release), 100, 10)

        assertEquals(1, clicks, "the filling child swallowed the row's click")
    }

    @Test
    fun aChildWithItsOwnHandlerStillWinsOverTheParent() {
        // The other half of the rule: consumption still decides, so an inner button inside a
        // clickable card takes the click rather than the card.
        var outer = 0
        var inner = 0
        val content: context(Composer)
        () -> Unit = {
            Column(Modifier.fillMaxWidth().height(20.dp).clickable { outer++ }) {
                Spacer(Modifier.fillMaxWidth().height(20.dp).clickable { inner++ })
            }
        }
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root, content)
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        val dispatcher = PointerInputDispatcher()
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), 100, 10)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Release), 100, 10)

        assertEquals(1, inner, "the inner handler must take the click")
        assertEquals(0, outer, "the click must not reach the parent as well")
    }
}
