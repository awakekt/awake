/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.compose.testing.ComposeTestSession
import io.github.awakelab.awake.compose.testing.composeTestSession
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.platform.FrameInput
import io.github.awakelab.awake.compose.ui.semantics.testTag
import io.github.awakelab.awake.core.input.Key
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSheetSide
import io.github.awakelab.awake.ui.shadcn.components.shadcnDrawer
import io.github.awakelab.awake.ui.shadcn.components.shadcnSheet
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A sheet is pinned to an edge and closes on its backdrop; a drawer adds a draggable handle.
 *
 * The backdrop assertion is the one that matters: an alert dialog must ignore it and these must
 * not, and both go through the same layer.
 */
class ShadcnSheetAndDrawerTest {

    @Test
    fun aSheetPinsToTheEdgeItIsGiven() {
        val right = sheetBounds(ShadcnSheetSide.Right)
        val left = sheetBounds(ShadcnSheetSide.Left)
        val bottom = sheetBounds(ShadcnSheetSide.Bottom)
        println("PROBE right=$right left=$left bottom=$bottom viewport=$VIEWPORT")

        assertEquals(0, left.left, "a left sheet starts at the left edge")
        assertEquals(VIEWPORT, right.left + right.width, "a right sheet ends at the right edge")
        assertEquals(VIEWPORT, bottom.top + bottom.height, "a bottom sheet ends at the bottom edge")

        assertEquals(VIEWPORT, left.height, "`inset-y-0`: a side sheet is full height")
        assertEquals(VIEWPORT, bottom.width, "`inset-x-0`: a bottom sheet is full width")
        assertTrue(bottom.height < VIEWPORT, "`h-auto`: a bottom sheet is only as tall as its content")
    }

    /** `w-3/4 sm:max-w-sm` -- three quarters, but never past 384dp. */
    @Test
    fun aSideSheetIsThreeQuartersWideUpToItsMaximum() {
        assertEquals(NARROW * 3 / 4, sheetBounds(ShadcnSheetSide.Right, viewport = NARROW).width)
        assertEquals(SHEET_MAX_WIDTH, sheetBounds(ShadcnSheetSide.Right, viewport = WIDE).width)
    }

    @Test
    fun aSheetClosesOnItsBackdropAndOnEscape() {
        var dismissed = 0
        val backdrop = sheetSession(ShadcnSheetSide.Right) { dismissed++ }
        backdrop.frame()
        // The far left: over the scrim, nowhere near a right-hand sheet.
        backdrop.clickAt(EDGE, VIEWPORT / 2)
        assertEquals(1, dismissed, "the backdrop did not close the sheet")

        var escaped = 0
        val escape = sheetSession(ShadcnSheetSide.Right) { escaped++ }
        escape.frame()
        escape.pressKey(Key.Escape)
        assertEquals(1, escaped, "Escape did not close the sheet")
    }

    @Test
    fun draggingTheDrawerHandleFarEnoughDismissesIt() {
        var dismissed = 0
        val session = drawerSession { dismissed++ }
        val handle = session.frame().onNodeWithTag("$DRAWER.handle").getBoundsInRoot()
        println("PROBE handle=$handle")

        val x = handle.left + handle.width / 2
        val y = handle.top + handle.height / 2
        session.frame(FrameInput(VIEWPORT, VIEWPORT, pointerX = x, pointerY = y, pointerDown = false))
        session.frame(FrameInput(VIEWPORT, VIEWPORT, pointerX = x, pointerY = y, pointerDown = true))
        for (step in 1..DRAG_STEPS) {
            session.frame(
                FrameInput(VIEWPORT, VIEWPORT, pointerX = x, pointerY = y + step * DRAG_STEP_PX, pointerDown = true),
            )
        }
        session.frame()

        assertEquals(1, dismissed, "dragging the handle ${DRAG_STEPS * DRAG_STEP_PX}px down did not dismiss")
    }

    @Test
    fun aShortDragOnTheDrawerDoesNotDismissIt() {
        var dismissed = 0
        val session = drawerSession { dismissed++ }
        val handle = session.frame().onNodeWithTag("$DRAWER.handle").getBoundsInRoot()
        val x = handle.left + handle.width / 2
        val y = handle.top + handle.height / 2
        session.frame(FrameInput(VIEWPORT, VIEWPORT, pointerX = x, pointerY = y, pointerDown = false))
        session.frame(FrameInput(VIEWPORT, VIEWPORT, pointerX = x, pointerY = y, pointerDown = true))
        session.frame(FrameInput(VIEWPORT, VIEWPORT, pointerX = x, pointerY = y + SHORT_DRAG_PX, pointerDown = true))
        session.frame()

        assertEquals(0, dismissed, "a ${SHORT_DRAG_PX}px nudge closed the drawer")
    }

    private fun sheetBounds(side: ShadcnSheetSide, viewport: Int = VIEWPORT) =
        sheetSession(side, viewport = viewport).frame().onNodeWithTag(SHEET).getBoundsInRoot()

    private fun sheetSession(
        side: ShadcnSheetSide,
        viewport: Int = VIEWPORT,
        onDismissRequest: () -> Unit = {},
    ): ComposeTestSession = composeTestSession(viewport, viewport) {
        provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
            shadcnSheet(
                visible = true,
                onDismissRequest = onDismissRequest,
                side = side,
                title = "Filters",
                description = "Narrow the list.",
                modifier = Modifier.testTag(SHEET),
                id = SHEET,
            )
        }
    }

    private fun drawerSession(onDismissRequest: () -> Unit): ComposeTestSession =
        composeTestSession(VIEWPORT, VIEWPORT) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
                shadcnDrawer(
                    visible = true,
                    onDismissRequest = onDismissRequest,
                    title = "Move to",
                    id = DRAWER,
                )
            }
        }

    private companion object {
        const val VIEWPORT = 600
        const val NARROW = 400
        const val WIDE = 800
        const val SHEET_MAX_WIDTH = 384
        const val EDGE = 4
        const val SHEET = "sheet"
        const val DRAWER = "drawer"
        const val DRAG_STEPS = 6
        const val DRAG_STEP_PX = 20
        const val SHORT_DRAG_PX = 12
    }
}
