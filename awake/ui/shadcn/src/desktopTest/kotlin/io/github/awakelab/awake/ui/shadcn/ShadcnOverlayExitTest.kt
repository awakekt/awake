/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.compose.testing.ComposeTestSession
import io.github.awakelab.awake.compose.testing.composeTestSession
import io.github.awakelab.awake.compose.ui.platform.FrameInput
import io.github.awakelab.awake.compose.ui.semantics.SemanticsNode
import io.github.awakelab.awake.compose.ui.semantics.testTag
import io.github.awakelab.awake.ui.shadcn.components.shadcnAlertDialog
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A dismissed overlay stays on screen while it fades, then leaves.
 *
 * Without this the subtree is gone between two frames and there is nothing left to animate -- which
 * is the state every overlay here shipped in.
 */
class ShadcnOverlayExitTest {

    @Test
    fun aDismissedDialogOutlivesTheFlagAndThenLeaves() {
        var visible = true
        val session = dialog { visible }
        session.frame(tick())
        assertNotNull(panel(session), "the dialog was not shown while visible")

        visible = false
        // Still there on the very next frame: this is the frame it used to disappear on.
        assertNotNull(panel(session.frame(tick())), "the dialog vanished the instant it was dismissed")

        repeat(FADE_FRAMES) { session.frame(tick()) }
        assertNull(panel(session.frame(tick())), "the dialog never finished leaving")
    }

    @Test
    fun theFadeIsBoundedSoNothingLingersInvisibly() {
        var visible = true
        val session = dialog { visible }
        session.frame(tick())
        visible = false

        var frames = 0
        while (panel(session.frame(tick())) != null && frames < RUNAWAY) frames += 1
        println("PROBE frames to leave=$frames (a ${FADE_SECONDS}s fade at 60fps)")

        assertTrue(frames < RUNAWAY, "the overlay never went away")
        assertTrue(frames >= 2, "there was no fade at all, just a delayed removal: $frames frames")
    }

    private fun panel(session: ComposeTestSession): SemanticsNode? = panel(session.frame(tick()))

    private fun panel(frame: io.github.awakelab.awake.compose.testing.ComposeComponentFrame): SemanticsNode? =
        frame.semantics.firstNotNullOfOrNull { it.find(ID) }

    private fun SemanticsNode.find(tag: String): SemanticsNode? =
        if (testTag == tag) this else children.firstNotNullOfOrNull { it.find(tag) }

    private fun dialog(visible: () -> Boolean): ComposeTestSession =
        composeTestSession(VIEWPORT, VIEWPORT) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
                shadcnAlertDialog(
                    visible = visible(),
                    title = "Delete this scene?",
                    onDismissRequest = {},
                    id = ID,
                )
            }
        }

    private fun tick() = FrameInput(VIEWPORT, VIEWPORT, deltaSeconds = 1f / 60f)

    private companion object {
        const val VIEWPORT = 400
        const val ID = "alert"
        const val FADE_SECONDS = 0.15f
        const val FADE_FRAMES = 12
        const val RUNAWAY = 120
    }
}
