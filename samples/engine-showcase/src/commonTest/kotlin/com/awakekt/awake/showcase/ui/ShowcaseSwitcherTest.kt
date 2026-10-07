/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.ui

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.platform.PointerFrame
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.showcase.EngineShowcases
import com.awakekt.awake.showcase.ShowcaseDebugToggles
import com.awakekt.awake.showcase.ShowcaseSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Clicks the panel the way the runtime does, because the failure worth catching is a button that
 * renders and does nothing — a switcher nobody can switch with looks identical in a screenshot.
 */
class ShowcaseSwitcherTest {

    private val host = ComposeHost()
    private val selection = ShowcaseSelection("point-lights")
    private var viewportWidth = 1280
    private var viewportHeight = 720
    private var phaseTimings = false

    private fun frame(
        x: Int = FrameInput.UNKNOWN_POINTER,
        y: Int = FrameInput.UNKNOWN_POINTER,
        down: Boolean = false,
        pointers: List<PointerFrame> = emptyList(),
    ): FrameOutput =
        host.frame(
            FrameInput(viewportWidth = viewportWidth, viewportHeight = viewportHeight, pointerX = x, pointerY = y, pointerDown = down, pointers = pointers),
        ) {
            ShowcaseOverlay(selection, EngineShowcases, phaseTimingsEnabled = phaseTimings, onPhaseTimingsChange = { phaseTimings = it })
        }

    /** Input is dispatched against the previous frame's placed tree, so the layout frame comes first. */
    private fun click(tag: String) {
        val node = assertNotNull(frame().find(tag), "no node tagged '$tag'")
        val cx = node.x + node.width / 2
        val cy = node.y + node.height / 2
        frame(cx, cy, down = true)
        frame(cx, cy, down = false)
    }

    @Test
    fun everyShowcaseGetsAnEntry() {
        val output = frame()

        assertNotNull(output.find(ShowcaseSwitcherTags.PANEL))
        EngineShowcases.forEach { showcase ->
            assertNotNull(
                output.find(ShowcaseSwitcherTags.entry(showcase.id)),
                "${showcase.id} has no button, so it cannot be reached.",
            )
        }
    }

    @Test
    fun clickingAnEntryRequestsThatShowcase() {
        click(ShowcaseSwitcherTags.entry("nav-chase"))

        assertEquals("nav-chase", selection.consumeRequest())
    }

    @Test
    fun debugCardOnlyShowsTheActiveShowcasesDiagnostics() {
        click(ShowcaseChromeTags.DEBUG)
        selection.request("heightfield-terrain")
        assertEquals("heightfield-terrain", selection.consumeRequest())
        val heightfield = frame()
        assertNotNull(heightfield.find(ShowcaseDebugTags.TERRAIN_DIAGNOSTICS))
        assertNotNull(heightfield.find(ShowcaseDebugTags.COLLIDERS))
        assertNotNull(heightfield.find(ShowcaseDebugTags.LIGHTS))
        assertNotNull(heightfield.find(ShowcaseDebugTags.WIREFRAME))
        assertNotNull(heightfield.find(ShowcaseDebugTags.OCCLUSION))
        assertNotNull(heightfield.find(ShowcaseDebugTags.PROP_SPAWN_BOX))
        assertNotNull(heightfield.find(ShowcaseDebugTags.PROP_SPAWN_SPHERE))
        assertNotNull(heightfield.find(ShowcaseDebugTags.PROP_SPAWN_WEDGE))
        assertNotNull(heightfield.find(ShowcaseDebugTags.PROP_RESET))

        selection.request("skinned-mesh")
        assertEquals("skinned-mesh", selection.consumeRequest())
        val skinned = frame()
        assertNotNull(skinned.find(ShowcaseDebugTags.WIREFRAME))
        assertNull(skinned.find(ShowcaseDebugTags.TERRAIN_DIAGNOSTICS))
        assertNull(skinned.find(ShowcaseDebugTags.COLLIDERS))
        assertNull(skinned.find(ShowcaseDebugTags.PROP_SPAWN_BOX))

        selection.request("instanced-cubes")
        assertEquals("instanced-cubes", selection.consumeRequest())
        val instanced = frame()
        assertNotNull(instanced.find(ShowcaseDebugTags.INSTANCE_BOUNDS))
        assertNotNull(instanced.find(ShowcaseDebugTags.BOUNDS))
        assertNull(instanced.find(ShowcaseDebugTags.TERRAIN_DIAGNOSTICS))
    }

    @Test
    fun everyGlobalDebugControlIsVisibleOnceAndChangesItsSetting() {
        try {
            click(ShowcaseChromeTags.DEBUG)
            val output = frame()
            val globalTags = listOf(
                ShowcaseDebugTags.BOUNDS,
                ShowcaseDebugTags.INSTANCE_BOUNDS,
                ShowcaseDebugTags.SHADOW_CASCADES,
                ShowcaseDebugTags.CASCADED_SHADOWS,
                ShowcaseDebugTags.SHADOWS,
                ShowcaseDebugTags.OCCLUSION,
                ShowcaseDebugTags.LIGHTS,
                ShowcaseDebugTags.WIREFRAME,
            )
            globalTags.forEach { tag ->
                assertNotNull(output.find(tag), "global control '$tag' is missing")
            }
            assertEquals(globalTags.size, globalTags.distinct().size, "Global debug controls must not duplicate a capability.")

            click(ShowcaseDebugTags.BOUNDS)
            click(ShowcaseDebugTags.INSTANCE_BOUNDS)
            click(ShowcaseDebugTags.SHADOW_CASCADES)
            click(ShowcaseDebugTags.CASCADED_SHADOWS)
            click(ShowcaseDebugTags.SHADOWS)
            click(ShowcaseDebugTags.OCCLUSION)
            click(ShowcaseDebugTags.LIGHTS)
            click(ShowcaseDebugTags.WIREFRAME)

            assertEquals(true, ShowcaseDebugToggles.showBounds)
            assertEquals(true, ShowcaseDebugToggles.showInstanceBounds)
            assertEquals(true, ShowcaseDebugToggles.showShadowCascades)
            assertEquals(false, ShowcaseDebugToggles.cascadedShadows)
            assertEquals(false, ShowcaseDebugToggles.shadows)
            assertEquals(true, ShowcaseDebugToggles.showOcclusion)
            assertEquals(true, ShowcaseDebugToggles.showLights)
            assertEquals(true, ShowcaseDebugToggles.wireframe)
        } finally {
            ShowcaseDebugToggles.showBounds = false
            ShowcaseDebugToggles.showInstanceBounds = false
            ShowcaseDebugToggles.showShadowCascades = false
            ShowcaseDebugToggles.cascadedShadows = true
            ShowcaseDebugToggles.shadows = true
            ShowcaseDebugToggles.showOcclusion = false
            ShowcaseDebugToggles.showLights = false
            ShowcaseDebugToggles.wireframe = false
        }
    }

    @Test
    fun compactNavigationOpensAndClosesAfterSelection() {
        viewportWidth = 390
        viewportHeight = 844
        assertNull(frame().find(ShowcaseSwitcherTags.PANEL))
        assertNull(frame().find(ShowcaseDebugTags.CARD))
        click(ShowcaseChromeTags.MENU)
        assertNotNull(frame().find(ShowcaseChromeTags.NAV_SHEET))
        click(ShowcaseSwitcherTags.entry("heightfield-terrain"))
        assertEquals("heightfield-terrain", selection.consumeRequest())
        repeat(30) { frame() }
        assertNull(frame().find(ShowcaseSwitcherTags.PANEL))
    }

    @Test
    fun retinaPhoneUsesCompactNavigationAndFitsItsToolbar() {
        host.density = 2f
        viewportWidth = 780
        viewportHeight = 1688
        assertNull(frame().find(ShowcaseSwitcherTags.PANEL))
        listOf(ShowcaseChromeTags.MENU, ShowcaseChromeTags.DEBUG, ShowcaseChromeTags.STATS).forEach { tag ->
            val node = assertNotNull(frame().find(tag))
            assertTrue(node.x >= 0 && node.x + node.width <= viewportWidth, "$tag extends outside the phone")
        }
        click(ShowcaseChromeTags.MENU)
        val panel = assertNotNull(frame().find(ShowcaseSwitcherTags.PANEL))
        assertTrue(panel.width < viewportWidth)
    }

    @Test
    fun desktopSidebarCanBeHiddenAndRestored() {
        click(ShowcaseChromeTags.CLOSE_NAV)
        assertNull(frame().find(ShowcaseSwitcherTags.PANEL))
        click(ShowcaseChromeTags.MENU)
        assertNotNull(frame().find(ShowcaseSwitcherTags.PANEL))
    }

    @Test
    fun statsOpensOnDemandAndPhaseTimingsCanBeToggledWithoutAKeyboard() {
        viewportWidth = 320
        viewportHeight = 568
        click(ShowcaseChromeTags.STATS)
        val statsToggle = assertNotNull(frame().find(ShowcaseDebugTags.PHASE_TIMINGS))
        assertTrue(statsToggle.x >= 0 && statsToggle.x + statsToggle.width <= viewportWidth)
        click(ShowcaseDebugTags.PHASE_TIMINGS)
        assertTrue(phaseTimings)
        click(ShowcaseChromeTags.CLOSE_DEBUG)
        repeat(30) { frame() }
        assertNull(frame().find(ShowcaseDebugTags.CARD))
    }

    @Test
    fun touchCanOpenAndScrollTheCompactSceneMenu() {
        viewportWidth = 390
        viewportHeight = 568
        val menu = assertNotNull(frame().find(ShowcaseChromeTags.MENU))
        val x = menu.x + menu.width / 2
        val y = menu.y + menu.height / 2
        frame(pointers = listOf(PointerFrame(1, x, y, down = true, pressed = true)))
        frame(pointers = listOf(PointerFrame(1, x, y, down = false, released = true)))
        repeat(30) { frame() }
        assertNotNull(frame().find(ShowcaseSwitcherTags.PANEL))

        frame(pointers = listOf(PointerFrame(2, 120, 420, down = true, pressed = true)))
        repeat(10) { step -> frame(pointers = listOf(PointerFrame(2, 120, 420 - (step + 1) * 28, down = true))) }
        frame(pointers = listOf(PointerFrame(2, 120, 140, down = false, released = true)))
        val last = assertNotNull(frame().find(ShowcaseSwitcherTags.entry(EngineShowcases.last().id)))
        assertTrue(last.y >= 0 && last.y + last.height < viewportHeight, "last menu item was not scrolled into view")
        assertNull(selection.consumeRequest(), "swiping must not activate a scene")
    }

    @Test
    fun debugToggleLabelAndCheckboxEachToggleExactlyOnce() {
        try {
            ShowcaseDebugToggles.showBounds = false
            viewportWidth = 390
            viewportHeight = 844
            click(ShowcaseChromeTags.DEBUG)
            click("${ShowcaseDebugTags.BOUNDS}-row")
            assertTrue(ShowcaseDebugToggles.showBounds)
            click(ShowcaseDebugTags.BOUNDS)
            assertEquals(false, ShowcaseDebugToggles.showBounds)
        } finally {
            ShowcaseDebugToggles.showBounds = false
        }
    }

    @Test
    fun sceneInputIsAvailableUntilAModalPanelOpens() {
        frame()
        assertFalse(frame(800, 400, down = true).ownership.isCaptured)
        frame(800, 400)
        click(ShowcaseChromeTags.DEBUG)
        assertTrue(frame(800, 400).ownership.isModalOpen)
    }
}

/** The tree is nested, so a flat scan of the roots finds the panel and none of its buttons. */
private fun FrameOutput.find(tag: String): SemanticsNode? = semantics.firstNotNullOfOrNull { it.find(tag) }

private fun SemanticsNode.find(tag: String): SemanticsNode? =
    if (testTag == tag) this else children.firstNotNullOfOrNull { it.find(tag) }
