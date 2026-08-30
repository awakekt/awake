/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.testing.ComposeComponentFrame
import io.github.awakelab.awake.compose.testing.ComposeTestBounds
import io.github.awakelab.awake.compose.testing.composeTestSession
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.platform.FrameInput
import io.github.awakelab.awake.core.graphics2d.UiDrawPrimitive
import io.github.awakelab.awake.compose.ui.semantics.testTag
import io.github.awakelab.awake.ui.shadcn.components.ShadcnRangeSlider
import io.github.awakelab.awake.ui.shadcn.components.ShadcnResizablePanel
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSlider
import io.github.awakelab.awake.ui.shadcn.components.shadcnResizablePanelGroup
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drags a slider the way the frame loop does, rather than by calling the dispatcher directly.
 *
 * The session had `click` and `hover` and no drag, so nothing exercised a press-move-release across
 * frames -- which is the sequence `Modifier.draggable` exists for and the one its own comment says
 * it once failed in while a dispatcher-level test passed.
 */
class ShadcnDragTest {

    @Test
    fun draggingTheSliderChangesItsValue() = dragSliderAt(density = 1f)

    /** The app runs at 2x; a delta scaled once too often or not at all only shows up here. */
    @Test
    fun draggingTheSliderChangesItsValueAtTwoTimesDensity() = dragSliderAt(density = 2f)

    private fun dragSliderAt(density: Float) {
        var value = 0.5f
        val session = composeTestSession(WIDTH, HEIGHT, density = density) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
                ShadcnSlider(
                    value = value,
                    modifier = Modifier.fillMaxWidth().testTag("slider"),
                    onValueChange = { value = it },
                )
            }
        }
        val first = session.frame()
        // Guessed coordinates silently miss: the control is only as tall as its thumb, so the
        // viewport centre is below it and every event lands on nothing.
        val bounds = first.onNodeWithTag("slider").getBoundsInRoot()
        println("PROBE slider density=$density bounds=$bounds")

        val startX = bounds.left + bounds.width / 2
        val y = bounds.top + bounds.height / 2
        session.frame(input(startX, y, down = false))
        session.frame(input(startX, y, down = true))
        val before = value
        for (step in 1..STEPS) {
            session.frame(input(startX + step * STEP_PX, y, down = true))
        }
        session.frame(input(startX + STEPS * STEP_PX, y, down = false))

        assertTrue(
            value > before,
            "at density $density, dragging right by ${STEPS * STEP_PX}px left the value at $value (was $before)",
        )
    }

    @Test
    fun draggingTheRangeSliderEndThumbChangesIt() {
        var start = 0.25f
        var end = 0.6f
        val session = composeTestSession(WIDTH, HEIGHT) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
                ShadcnRangeSlider(
                    start = start,
                    end = end,
                    modifier = Modifier.fillMaxWidth().testTag("range"),
                    onStartChange = { start = it },
                    onEndChange = { end = it },
                )
            }
        }
        val bounds = session.frame().onNodeWithTag("range").getBoundsInRoot()
        println("PROBE range bounds=$bounds")
        val y = bounds.top + bounds.height / 2
        val from = bounds.left + (bounds.width * end).toInt()

        session.frame(input(from, y, down = false))
        session.frame(input(from, y, down = true))
        val before = end
        for (step in 1..STEPS) session.frame(input(from + step * STEP_PX, y, down = true))
        session.frame(input(from + STEPS * STEP_PX, y, down = false))

        assertTrue(end > before, "dragging the end thumb right left it at $end (was $before), start=$start")
    }

    @Test
    fun draggingTheResizableHandleMovesTheSplit() {
        val session = composeTestSession(WIDTH, HEIGHT) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
                shadcnResizablePanelGroup(
                    panels = listOf(
                        ShadcnResizablePanel(initialSize = 0.5f, tag = "left") {},
                        ShadcnResizablePanel(initialSize = 0.5f, tag = "right") {},
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("group"),
                    handleTags = listOf("handle"),
                )
            }
        }
        val first = session.frame()
        val handle = first.onNodeWithTag("handle").getBoundsInRoot()
        val before = first.onNodeWithTag("left").getBoundsInRoot().width
        println("PROBE handle bounds=$handle leftWidth=$before")

        val x = handle.left + handle.width / 2
        val y = handle.top + handle.height / 2
        session.frame(input(x, y, down = false))
        session.frame(input(x, y, down = true))
        for (step in 1..STEPS) session.frame(input(x + step * STEP_PX, y, down = true))
        val after = session.frame(input(x + STEPS * STEP_PX, y, down = false))
            .onNodeWithTag("left").getBoundsInRoot().width

        assertTrue(after > before, "dragging the handle right left the left panel at $after (was $before)")
    }

    /** The reported symptom: the divider is one pixel wide, so a real pointer misses it. */
    @Test
    fun theResizableHandleIsGrabbableFromBesideIt() {
        val session = composeTestSession(WIDTH, HEIGHT) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
                shadcnResizablePanelGroup(
                    panels = listOf(
                        ShadcnResizablePanel(initialSize = 0.5f, tag = "left") {},
                        ShadcnResizablePanel(initialSize = 0.5f, tag = "right") {},
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("group"),
                    handleTags = listOf("handle"),
                )
            }
        }
        val first = session.frame()
        val handle = first.onNodeWithTag("handle").getBoundsInRoot()
        val before = first.onNodeWithTag("left").getBoundsInRoot().width
        assertEquals(1, handle.width, "handle should stay one pixel wide; only its reach grows")

        // Beside the line, not on it. Inside the grab margin, outside the box.
        val x = handle.left - NEAR_MISS_PX
        val y = handle.top + handle.height / 2
        session.frame(input(x, y, down = false))
        session.frame(input(x, y, down = true))
        for (step in 1..STEPS) session.frame(input(x + step * STEP_PX, y, down = true))
        val after = session.frame(input(x + STEPS * STEP_PX, y, down = false))
            .onNodeWithTag("left").getBoundsInRoot().width

        assertTrue(
            after > before,
            "pressing ${NEAR_MISS_PX}px beside the divider did not grab it: left panel $after (was $before)",
        )
    }

    /**
     * The pointer must light the divider up from inside the grab margin, not only from the one
     * pixel it can barely land on -- otherwise the only feedback that it is grabbable is the cursor.
     */
    @Test
    fun theResizableHandleHighlightsFromBesideIt() {
        val session = composeTestSession(WIDTH, HEIGHT) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
                shadcnResizablePanelGroup(
                    panels = listOf(
                        ShadcnResizablePanel(initialSize = 0.5f, tag = "left") {},
                        ShadcnResizablePanel(initialSize = 0.5f, tag = "right") {},
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("group"),
                    handleTags = listOf("handle"),
                )
            }
        }
        val handle = session.frame().onNodeWithTag("handle").getBoundsInRoot()
        val y = handle.top + handle.height / 2

        val away = handleColorAt(session.frame(input(0, y, down = false)), handle)
        val beside = handleColorAt(session.frame(input(handle.left - NEAR_MISS_PX, y, down = false)), handle)
        println("PROBE handle away=$away beside=$beside")

        assertTrue(beside != away, "the divider looks identical from ${NEAR_MISS_PX}px away: $beside")
    }

    /** The quad covering the divider column, which is the only thing painted at that x. */
    private fun handleColorAt(frame: ComposeComponentFrame, handle: ComposeTestBounds) =
        frame.primitivesOf<UiDrawPrimitive.Quad>()
            .firstOrNull { it.x.toInt() == handle.left && it.w.toInt() == handle.width }
            ?.color

    private fun input(x: Int, y: Int, down: Boolean) =
        FrameInput(WIDTH, HEIGHT, pointerX = x, pointerY = y, pointerDown = down)

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 60
        const val STEPS = 5
        const val STEP_PX = 10

        /** Inside the 5dp grab margin, outside the 1px handle. */
        const val NEAR_MISS_PX = 3
    }
}
