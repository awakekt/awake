/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.testing.composeFrame
import com.awakekt.awake.compose.testing.composeTestSession
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.ui.shadcn.components.ShadcnLoadingBar
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The loading bar shows progress, a spinner while it can't measure, and an error with a retry. */
class ShadcnLoadingBarTest {
    private val theme = ShadcnThemeValues(ShadcnTheme)

    @Test
    fun aMeasuredLoadFillsTheTrackByItsFraction() {
        val quads = composeFrame(WIDTH, HEIGHT) {
            provideShadcnTheme(theme) { ShadcnLoadingBar("Loading", fraction = 0.5f) }
        }.primitivesOf<DrawCommand.RoundedQuad>()

        assertEquals(2, quads.size, "a track and its indicator")
        assertEquals(quads[0].w / 2f, quads[1].w, 1f, "half did not fill half")
    }

    @Test
    fun anUnmeasuredLoadShowsASpinnerAndAnEmptyTrack() {
        val unmeasured = frame { ShadcnLoadingBar("Waiting for sign-in", fraction = null) }
        val measured = frame { ShadcnLoadingBar("Loading", fraction = 0.3f) }

        assertEquals(1, unmeasured.filterIsInstance<UiDrawPrimitive.Mesh>().size, "no spinner while unmeasured")
        assertTrue(measured.none { it is UiDrawPrimitive.Mesh }, "a spinner beside a measured bar")
        val track = composeFrame(WIDTH, HEIGHT) {
            provideShadcnTheme(theme) { ShadcnLoadingBar("Waiting", fraction = null) }
        }.primitivesOf<DrawCommand.RoundedQuad>()
        assertEquals(1, track.size, "an unmeasured load drew an indicator")
    }

    @Test
    fun aFailureShowsTheErrorInTheDestructiveColourAndNoTrack() {
        val frame = composeFrame(WIDTH, HEIGHT) {
            provideShadcnTheme(theme) { ShadcnLoadingBar("Loading", fraction = 0.8f, error = "Update the app") }
        }

        assertTrue(frame.primitivesOf<DrawCommand.Glyph>().any { it.color == theme.palette.destructive }, "the error is not destructive")
        assertTrue(frame.primitivesOf<DrawCommand.RoundedQuad>().isEmpty(), "a failed load still drew its track or a button")
    }

    @Test
    fun theRetryButtonRetries() {
        var retries = 0
        val session = composeTestSession(width = WIDTH, height = HEIGHT) {
            provideShadcnTheme(theme) {
                ShadcnLoadingBar("Loading", fraction = null, error = "Offline", retryLabel = "Retry", onRetry = { retries++ })
            }
        }
        val button = session.frame().primitivesOf<DrawCommand.RoundedQuad>().last()

        session.clickAt((button.x + button.w / 2f).toInt(), (button.y + button.h / 2f).toInt())

        assertEquals(1, retries)
    }

    private fun frame(content: context(com.awakekt.awake.compose.runtime.Composer) () -> Unit): List<UiDrawPrimitive> =
        ComposeHost().frame(FrameInput(WIDTH, HEIGHT, deltaSeconds = 0f)) {
            provideShadcnTheme(theme) { content() }
        }.primitives.toList()

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 200
    }
}
