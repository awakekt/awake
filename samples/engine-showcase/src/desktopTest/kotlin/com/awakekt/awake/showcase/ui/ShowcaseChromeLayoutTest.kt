/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.ui

import com.awakekt.awake.compose.testing.ComposeTestSession
import com.awakekt.awake.compose.testing.captureImage
import com.awakekt.awake.compose.testing.composeTestSession
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.showcase.EngineShowcases
import com.awakekt.awake.showcase.ShowcaseSelection
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShowcaseChromeLayoutTest {
    @Test
    fun chromeFitsPhoneLandscapeDesktopAndRetinaViewports() {
        listOf(
            Viewport("desktop", 1280, 720, 1f),
            Viewport("phone", 390, 844, 1f),
            Viewport("small-phone", 320, 568, 1f),
            Viewport("landscape", 667, 375, 1f),
            Viewport("retina-phone", 780, 1688, 2f),
        ).forEach { viewport ->
            val selection = ShowcaseSelection("heightfield-terrain")
            val session = composeTestSession(viewport.width, viewport.height, viewport.density) {
                ShowcaseOverlay(selection, EngineShowcases)
            }
            capture(session, viewport, "scene")
            val compact = viewport.width / viewport.density < 768
            if (compact) session.click(ShowcaseChromeTags.MENU)
            val navigation = capture(session, viewport, "navigation")
            val panel = navigation.onNodeWithTag(ShowcaseSwitcherTags.PANEL).getBoundsInRoot()
            assertTrue(panel.left >= 0 && panel.right <= viewport.width, "navigation escapes $viewport: $panel")
            val first = navigation.onNodeWithTag(ShowcaseSwitcherTags.entry(EngineShowcases.first().id)).getBoundsInRoot()
            assertTrue(first.height >= 44 * viewport.density, "navigation target is too small: $first")
            if (compact) {
                session.clickAt(viewport.width - 2, viewport.height / 2)
                repeat(30) { session.frame() }
                assertNull(session.frame().semanticNodeWithTagOrNull(ShowcaseSwitcherTags.PANEL))
            }
            session.click(ShowcaseChromeTags.DEBUG)
            val debug = capture(session, viewport, "debug")
            val close = debug.onNodeWithTag(ShowcaseChromeTags.CLOSE_DEBUG).getBoundsInRoot()
            assertTrue(close.left >= 0 && close.right <= viewport.width && close.bottom <= viewport.height, "debug close escapes $viewport: $close")
            assertNotNull(debug.semanticNodeWithTagOrNull(ShowcaseDebugTags.TERRAIN_DIAGNOSTICS))
            session.click(ShowcaseDebugTags.TAB_STATS)
            capture(session, viewport, "stats")
            session.pressKey(Key.Escape)
            repeat(30) { session.frame() }
            assertNull(session.frame().semanticNodeWithTagOrNull(ShowcaseChromeTags.DEBUG_SHEET))
        }
    }

    @Test
    fun aScrolledMobileMenuCanSelectItsLastSceneAndKeepsItsCloseButtonPinned() {
        val selection = ShowcaseSelection("point-lights")
        val session = composeTestSession(390, 568) { ShowcaseOverlay(selection, EngineShowcases) }
        session.frame()
        session.click(ShowcaseChromeTags.MENU)
        repeat(30) { session.frame() }
        val before = session.frame().onNodeWithTag(ShowcaseChromeTags.CLOSE_NAV).getBoundsInRoot()
        session.frame(FrameInput(390, 568, pointerX = 120, pointerY = 250, scrollDeltaY = -1000f))
        val after = session.frame()
        assertEquals(before, after.onNodeWithTag(ShowcaseChromeTags.CLOSE_NAV).getBoundsInRoot())
        val last = EngineShowcases.last()
        val target = after.onNodeWithTag(ShowcaseSwitcherTags.entry(last.id)).getBoundsInRoot()
        assertTrue(target.top > before.bottom && target.bottom < 568, "last scene is unreachable: $target")
        session.click(ShowcaseSwitcherTags.entry(last.id))
        assertEquals(last.id, selection.consumeRequest())
    }

    private fun capture(session: ComposeTestSession, viewport: Viewport, state: String) = session.run {
        repeat(30) { frame() }
        frame().also {
            it.captureImage(
                File("build/reports/showcase-ui/${viewport.name}-$state.png"),
                width = viewport.width,
                height = viewport.height,
                font = UiFonts.default(),
            )
        }
    }

    private data class Viewport(val name: String, val width: Int, val height: Int, val density: Float)
}
