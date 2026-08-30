/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation

import io.github.awakelab.awake.compose.foundation.interaction.Interaction
import io.github.awakelab.awake.compose.foundation.interaction.InteractionSource
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.ColumnMeasurePolicy
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.size
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun laidOut(content: context(Composer) () -> Unit): LayoutNode {
    val root = LayoutNode(ColumnMeasurePolicy())
    composeInto(root, content)
    root.layoutTree(Constraints.of(0, 200, 0, 200))
    return root
}

class InteractionSourceTest {

    @Test
    fun aSecondPressSurvivesTheFirstRelease() {
        // The whole reason interactions are counted rather than flagged: a boolean clears here and
        // leaves the component looking un-pressed while a finger is still down.
        val source = InteractionSource()
        val first = Interaction.Press.Press
        val second = Interaction.Press.Press
        source.tryEmit(first)
        source.tryEmit(second)

        source.tryEmit(Interaction.Press.Release(first))

        assertTrue(source.isPressed, "one release cleared two presses")
        source.tryEmit(Interaction.Press.Release(second))
        assertFalse(source.isPressed)
    }

    @Test
    fun anUnmatchedReleaseCannotDriveTheCountNegative() {
        // Otherwise a stray release banks a credit and the next real press reads as not pressed.
        val source = InteractionSource()
        source.tryEmit(Interaction.Press.Release(Interaction.Press.Press))
        source.tryEmit(Interaction.Press.Press)

        assertTrue(source.isPressed)
    }

    @Test
    fun theThreeKindsAreIndependent() {
        val source = InteractionSource()
        source.tryEmit(Interaction.Hover.Enter)

        assertTrue(source.isHovered)
        assertFalse(source.isPressed)
        assertFalse(source.isFocused)
    }

    @Test
    fun resetDropsEverythingLive() {
        val source = InteractionSource()
        source.tryEmit(Interaction.Hover.Enter)
        source.tryEmit(Interaction.Press.Press)

        source.reset()

        assertFalse(source.isHovered)
        assertFalse(source.isPressed)
    }
}

class HoverableTest {

    @Test
    fun thePointerArrivingMarksTheNodeHovered() {
        val source = InteractionSource()
        val root = laidOut { Spacer(Modifier.size(50.dp).hoverable(source)) }

        PointerInputDispatcher().dispatch(root, PointerEvent(PointerEventType.Move), 10, 10)

        assertTrue(source.isHovered)
    }

    @Test
    fun thePointerLeavingClearsIt() {
        val source = InteractionSource()
        val root = laidOut { Spacer(Modifier.size(50.dp).hoverable(source)) }
        val dispatcher = PointerInputDispatcher()
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Move), 10, 10)

        dispatcher.dispatch(root, PointerEvent(PointerEventType.Move), 500, 500)

        assertFalse(source.isHovered, "nothing told the node the pointer had gone")
    }

    @Test
    fun stayingInsideDoesNotEnterTwice() {
        // Enter fires on the transition, not on every move -- otherwise the count climbs and one
        // Exit never clears it.
        val source = InteractionSource()
        val root = laidOut { Spacer(Modifier.size(50.dp).hoverable(source)) }
        val dispatcher = PointerInputDispatcher()

        dispatcher.dispatch(root, PointerEvent(PointerEventType.Move), 10, 10)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Move), 20, 20)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Move), 500, 500)

        assertFalse(source.isHovered)
    }

    @Test
    fun anAncestorIsHoveredWhenItsChildIs() {
        // CSS `:hover` semantics: a card containing a hovered button is hovered. A styling layer
        // that wanted only the leaf would have no way to express the card case at all.
        val outer = InteractionSource()
        val inner = InteractionSource()
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Column(Modifier.size(80.dp).hoverable(outer)) {
                Spacer(Modifier.size(40.dp).hoverable(inner))
            }
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        PointerInputDispatcher().dispatch(root, PointerEvent(PointerEventType.Move), 10, 10)

        assertTrue(inner.isHovered)
        assertTrue(outer.isHovered, "the ancestor was skipped")
    }

    @Test
    fun disabledIsNotAChainLink() {
        val source = InteractionSource()
        val root = laidOut { Spacer(Modifier.size(50.dp).hoverable(source, enabled = false)) }

        PointerInputDispatcher().dispatch(root, PointerEvent(PointerEventType.Move), 10, 10)

        assertFalse(source.isHovered)
    }
}

class ClickablePressStateTest {

    @Test
    fun pressAndReleaseBracketThePressedState() {
        val source = InteractionSource()
        var clicks = 0
        val root = laidOut { Spacer(Modifier.size(50.dp).clickable(source) { clicks++ }) }
        val dispatcher = PointerInputDispatcher()

        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), 10, 10)
        assertTrue(source.isPressed)

        dispatcher.dispatch(root, PointerEvent(PointerEventType.Release), 10, 10)
        assertFalse(source.isPressed)
        assertEquals(1, clicks)
    }

    @Test
    fun thePointerLeavingMidPressCancelsRatherThanClicks() {
        // The visible half of this bug is a button left looking held down after the pointer walked
        // away, which is what happens if only Release clears the state.
        val source = InteractionSource()
        var clicks = 0
        val root = laidOut { Spacer(Modifier.size(50.dp).clickable(source) { clicks++ }) }
        val dispatcher = PointerInputDispatcher()

        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), 10, 10)
        // A plain move away, not a hand-fed Exit: the dispatcher has to notice by itself, which it
        // cannot do from the capture path alone.
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Move), 500, 500)

        assertFalse(source.isPressed)
        assertEquals(0, clicks, "leaving mid-press still fired the click")
    }

    @Test
    fun clickableWithoutASourceStillWorks() {
        var clicks = 0
        val root = laidOut { Spacer(Modifier.size(50.dp).clickable { clicks++ }) }
        val dispatcher = PointerInputDispatcher()

        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), 10, 10)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Release), 10, 10)

        assertEquals(1, clicks)
    }
}
