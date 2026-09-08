/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.testing.ComposeComponentFrame
import com.awakekt.awake.compose.testing.ComposeTestSession
import com.awakekt.awake.compose.testing.composeTestSession
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.ui.shadcn.components.MAX_VISIBLE_TOASTS
import com.awakekt.awake.ui.shadcn.components.ShadcnToastState
import com.awakekt.awake.ui.shadcn.components.ShadcnToaster
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A toast appears, dwells for the time it asked for, and leaves on its own.
 *
 * The dwell is driven by the frame clock rather than a wall clock, so the test advances frames
 * instead of sleeping and the timing is exact rather than flaky.
 */
class ShadcnToasterTest {

    @Test
    fun aToastAppearsAndLeavesOnItsOwn() {
        val state = ShadcnToastState()
        val session = toaster(state)
        session.frame(tick())
        assertEquals(0, toastCount(session.frame(tick())), "nothing was queued yet")

        state.show("Saved.", durationSeconds = 1f)
        assertTrue(toastCount(session.frame(tick())) > 0, "a queued toast did not appear")

        // One second of frames at the tick this session runs.
        repeat(FRAMES_PER_SECOND) { session.frame(tick()) }
        assertEquals(0, toastCount(session.frame(tick())), "the toast outstayed its duration")
    }

    @Test
    fun aToastSurvivesUntilItsDurationElapses() {
        val state = ShadcnToastState()
        val session = toaster(state)
        session.frame(tick())
        state.show("Still here.", durationSeconds = 1f)

        repeat(FRAMES_PER_SECOND / 2) { session.frame(tick()) }
        assertTrue(toastCount(session.frame(tick())) > 0, "the toast left before its time was up")
    }

    @Test
    fun toastsStackAndTheQueueIsCapped() {
        val state = ShadcnToastState()
        val session = toaster(state)
        session.frame(tick())
        repeat(MAX_VISIBLE_TOASTS + 2) { state.show("Toast $it") }
        session.frame(tick())

        assertEquals(MAX_VISIBLE_TOASTS, state.visible.size, "the queue grew past its cap")
        assertEquals(
            "Toast ${MAX_VISIBLE_TOASTS + 1}",
            state.visible.last().message,
            "the newest toast was the one dropped",
        )
    }

    @Test
    fun dismissingRemovesJustThatToast() {
        val state = ShadcnToastState()
        val session = toaster(state)
        session.frame(tick())
        val first = state.show("First")
        state.show("Second")
        state.dismiss(first)
        session.frame(tick())

        assertEquals(1, state.visible.size)
        assertEquals("Second", state.visible.single().message)
    }

    /** A toast reports; it must not take the frame the way a dialog does. */
    @Test
    fun anOpenToastDoesNotBlockThePageBehindIt() {
        val state = ShadcnToastState()
        var clicks = 0
        val session = composeTestSession(VIEWPORT, VIEWPORT) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
                Box(Modifier.fillMaxSize().clickable { clicks++ }.testTag("page"))
                ShadcnToaster(state, id = TOASTER)
            }
        }
        session.frame(tick())
        state.show("Saved.")
        session.frame(tick())
        // Top-left, far from a bottom-trailing toaster.
        session.clickAt(EDGE, EDGE)

        assertEquals(1, clicks, "a toast swallowed a click meant for the page")
    }

    /**
     * When a toaster is nested inside an offset parent (e.g. an EditorScaffold body below a header),
     * its positionProvider must subtract the parent coordinates so the floating layer remains
     * correctly anchored to the bottom-trailing corner of the viewport rather than pushed offscreen.
     */
    @Test
    fun toasterNestedInOffsetContainerPlacesCorrectlyAtViewportBottomRight() {
        val state = ShadcnToastState()
        val session = composeTestSession(VIEWPORT, VIEWPORT) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
                Box(Modifier.padding(top = 100.dp, start = 80.dp)) {
                    ShadcnToaster(state, id = TOASTER)
                }
            }
        }
        session.frame(tick())
        val key = state.show("Nested test")
        val frame = session.frame(tick())

        val toastNode = frame.flatSemantics().first { it.testTag == "$TOASTER.$key" }
        assertTrue(
            toastNode.x + toastNode.width <= VIEWPORT,
            "Toast exceeded viewport width: x=${toastNode.x}, w=${toastNode.width}, viewport=$VIEWPORT",
        )
        assertTrue(
            toastNode.y + toastNode.height <= VIEWPORT,
            "Toast exceeded viewport height: y=${toastNode.y}, h=${toastNode.height}, viewport=$VIEWPORT",
        )
        assertTrue(toastNode.x >= 0, "Toast was placed off-screen to the left: x=${toastNode.x}")
        assertTrue(toastNode.y >= 0, "Toast was placed off-screen to the top: y=${toastNode.y}")
    }

    /** The semantics tree is nested, so a toast is a child of the toaster rather than a root. */
    private fun toastCount(frame: ComposeComponentFrame): Int =
        frame.semantics.sumOf { it.countTagged() }

    private fun SemanticsNode.countTagged(): Int =
        (if (testTag?.startsWith("$TOASTER.") == true) 1 else 0) + children.sumOf { it.countTagged() }

    private fun toaster(state: ShadcnToastState): ComposeTestSession =
        composeTestSession(VIEWPORT, VIEWPORT) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
                ShadcnToaster(state, id = TOASTER)
            }
        }

    private fun tick() = FrameInput(VIEWPORT, VIEWPORT, deltaSeconds = TICK_SECONDS)

    private companion object {
        const val VIEWPORT = 600
        const val EDGE = 4
        const val TOASTER = "toaster"
        const val TICK_SECONDS = 1f / 60f
        const val FRAMES_PER_SECOND = 60
    }
}
