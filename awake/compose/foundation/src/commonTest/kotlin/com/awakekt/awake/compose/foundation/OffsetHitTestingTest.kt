/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.input.pointer.PointerInputDispatcher
import com.awakekt.awake.compose.ui.input.pointer.pointerCursor
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.input.PointerCursor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A node moved by `Modifier.offset(60.dp)` answers the pointer where it is drawn, not where it
 * would have been.
 *
 * Upstream Compose gives a node's pointer input the box of the layout modifiers outside it: what
 * sits inside an offset moves with it, and what sits outside stays put. These pin that against the
 * shapes a screen builds an offset node in. Each one draws a 24 px square and taps the centre of
 * where it is drawn, then the centre of where it would have been without the offset, which must
 * fall through to nothing.
 *
 * Reported as a node offset by 60 dp being hit at its un-offset place. A pointer link was never the
 * culprit: `clickable` already follows the offset (see [OffsetHitTargetTest]), and the first group
 * below pins that. A cursor, a focus target and a scroll area did not: they were judged against the
 * node's own box, not the box where their modifier sits in the chain, so an offset handle showed its
 * cursor, and took a press that belonged to whatever lay under it, at the place it had left.
 */
class OffsetHitTestingTest {

    private fun laidOut(content: context(Composer) () -> Unit): LayoutNode {
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root, content)
        root.layoutTree(Constraints.of(300, 300, 300, 300))
        return root
    }

    /** Taps ([x], [y]) on a tree of [content] in a 300 px square and says how many clicks it got. */
    private fun clicksAt(x: Int, y: Int, content: context(Composer) (onClick: () -> Unit) -> Unit): Int {
        var clicks = 0
        val root = laidOut { content { clicks++ } }

        val dispatcher = PointerInputDispatcher()
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), x, y)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Release), x, y)
        return clicks
    }

    private fun assertHitWhereDrawnOnly(
        drawnAt: Pair<Int, Int>,
        vacated: Pair<Int, Int>,
        content: context(Composer) (onClick: () -> Unit) -> Unit,
    ) {
        assertEquals(1, clicksAt(drawnAt.first, drawnAt.second, content), "hit where it is drawn, $drawnAt")
        assertEquals(0, clicksAt(vacated.first, vacated.second, content), "not hit where it was, $vacated")
    }

    @Test
    fun anOffsetThenTheSizeThenTheClickIsHitWhereItIsDrawn() {
        assertHitWhereDrawnOnly(drawnAt = 72 to 12, vacated = 12 to 12) { onClick ->
            Box(Modifier.offset(x = OFFSET.dp).size(SIZE.dp).clickable(onClick = onClick)) {}
        }
    }

    @Test
    fun aClickInsideTheOffsetIsHitWhereItIsDrawn() {
        assertHitWhereDrawnOnly(drawnAt = 72 to 12, vacated = 12 to 12) { onClick ->
            Box(Modifier.size(SIZE.dp).offset(x = OFFSET.dp).clickable(onClick = onClick)) {}
        }
    }

    @Test
    fun aChildOfAnOffsetBoxIsHitWhereItIsDrawn() {
        assertHitWhereDrawnOnly(drawnAt = 72 to 12, vacated = 12 to 12) { onClick ->
            Box(Modifier.offset(x = OFFSET.dp)) {
                Box(Modifier.size(SIZE.dp).clickable(onClick = onClick)) {}
            }
        }
    }

    @Test
    fun anOffsetNodeInAColumnIsHitWhereItIsDrawn() {
        assertHitWhereDrawnOnly(drawnAt = 72 to 52, vacated = 12 to 52) { onClick ->
            Column(Modifier.size(PARENT.dp)) {
                Spacer(Modifier.size(40.dp))
                Box(Modifier.offset(x = OFFSET.dp).size(SIZE.dp).clickable(onClick = onClick)) {}
            }
        }
    }

    @Test
    fun anOffsetNodeInARowIsHitWhereItIsDrawn() {
        assertHitWhereDrawnOnly(drawnAt = 40 + 72 to 12, vacated = 40 + 12 to 12) { onClick ->
            Row(Modifier.size(PARENT.dp)) {
                Spacer(Modifier.size(40.dp))
                Box(Modifier.offset(x = OFFSET.dp).size(SIZE.dp).clickable(onClick = onClick)) {}
            }
        }
    }

    @Test
    fun anOffsetInsideAnOffsetIsHitWhereBothPutIt() {
        assertHitWhereDrawnOnly(drawnAt = 72 to 72, vacated = 12 to 12) { onClick ->
            Box(Modifier.offset(x = OFFSET.dp).size(PARENT.dp)) {
                Box(Modifier.offset(y = OFFSET.dp).size(SIZE.dp).clickable(onClick = onClick)) {}
            }
        }
    }

    @Test
    fun aNegativeOffsetIsHitWhereItIsDrawn() {
        assertHitWhereDrawnOnly(drawnAt = 12 to 12, vacated = 72 to 72) { onClick ->
            Box(Modifier.offset(x = OFFSET.dp, y = OFFSET.dp).size(PARENT.dp)) {
                Box(Modifier.offset(x = (-OFFSET).dp, y = (-OFFSET).dp).size(SIZE.dp).clickable(onClick = onClick)) {}
            }
        }
    }

    @Test
    fun aClickableOutsideTheOffsetStaysWhereTheParentPutIt() {
        // Upstream's rule, and the reason the order of the two modifiers matters: pointer input
        // declared before an offset sees the unmoved box.
        assertHitWhereDrawnOnly(drawnAt = 12 to 12, vacated = 72 to 12) { onClick ->
            Box(Modifier.clickable(onClick = onClick).offset(x = OFFSET.dp).size(SIZE.dp)) {}
        }
    }

    // What is not a pointer link: a cursor, a focus target.

    private fun cursorAt(x: Int, y: Int, content: context(Composer) () -> Unit): PointerCursor {
        val root = laidOut(content)
        val dispatcher = PointerInputDispatcher()
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Move), x, y)
        return dispatcher.hoveredCursor
    }

    @Test
    fun anOffsetNodesCursorShowsWhereItIsDrawn() {
        val content: context(Composer)
        () -> Unit = {
            Box(Modifier.offset(x = OFFSET.dp).size(SIZE.dp).pointerCursor(PointerCursor.ResizeHorizontal)) {}
        }

        assertEquals(PointerCursor.ResizeHorizontal, cursorAt(72, 12, content))
        assertEquals(PointerCursor.Default, cursorAt(12, 12, content), "shown where it was")
    }

    @Test
    fun aCursorInsideTheOffsetShowsWhereItIsDrawn() {
        val content: context(Composer)
        () -> Unit = {
            Box(Modifier.size(SIZE.dp).offset(x = OFFSET.dp).pointerCursor(PointerCursor.ResizeHorizontal)) {}
        }

        assertEquals(PointerCursor.ResizeHorizontal, cursorAt(72, 12, content))
        assertEquals(PointerCursor.Default, cursorAt(12, 12, content), "shown where it was")
    }

    @Test
    fun aCursorOutsideTheOffsetStaysWhereTheParentPutIt() {
        val content: context(Composer)
        () -> Unit = {
            Box(Modifier.pointerCursor(PointerCursor.Text).offset(x = OFFSET.dp).size(SIZE.dp)) {}
        }

        assertEquals(PointerCursor.Text, cursorAt(12, 12, content))
        assertEquals(PointerCursor.Default, cursorAt(72, 12, content))
    }

    @Test
    fun anOffsetNodesCursorDoesNotCoverWhatItVacated() {
        // The handle is drawn above the panel, so the point it left is the panel's to answer.
        var clicks = 0
        val root = laidOut {
            Box(Modifier.size(PARENT.dp)) {
                Box(Modifier.size(100.dp).clickable { clicks++ }) {}
                Box(Modifier.offset(x = OFFSET.dp).size(SIZE.dp).pointerCursor(PointerCursor.ResizeHorizontal)) {}
            }
        }

        val dispatcher = PointerInputDispatcher()
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), 12, 12)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Release), 12, 12)

        assertEquals(1, clicks, "the press never reached what lies under the vacated place")
    }

    @Test
    fun anOffsetFocusTargetIsFocusedWhereItIsDrawn() {
        fun pressFocuses(x: Int, y: Int): Boolean {
            val source = InteractionSource()
            val root = laidOut {
                Box(Modifier.offset(x = OFFSET.dp).size(SIZE.dp).focusable(interactionSource = source)) {}
            }
            PointerInputDispatcher().dispatch(root, PointerEvent(PointerEventType.Press), x, y)
            return source.isFocused
        }

        assertEquals(true, pressFocuses(72, 12))
        assertEquals(false, pressFocuses(12, 12), "focused from where it was")
    }

    @Test
    fun anOffsetScrollAreaTakesTheWheelWhereItIsDrawn() {
        fun scrolledByWheelAt(x: Int): Int {
            val state = ScrollState()
            val host = ComposeHost()
            val content: context(Composer)
            () -> Unit = {
                Column(Modifier.offset(x = OFFSET.dp).size(100.dp).verticalScroll(state)) {
                    repeat(5) { Spacer(Modifier.size(100.dp, 50.dp)) }
                }
            }
            host.frame(FrameInput(viewportWidth = 300, viewportHeight = 300, pointerX = x, pointerY = 10), content)
            host.frame(
                FrameInput(viewportWidth = 300, viewportHeight = 300, pointerX = x, pointerY = 10, scrollDeltaY = -1f),
                content,
            )
            return state.value
        }

        assertEquals(true, scrolledByWheelAt(OFFSET + 40) > 0, "the wheel did not reach the scroller where it is drawn")
        assertEquals(0, scrolledByWheelAt(40), "the wheel scrolled it from where it was")
    }

    private companion object {
        const val OFFSET = 60
        const val SIZE = 24
        const val PARENT = 200
    }
}
