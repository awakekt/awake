/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.ui

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.showcase.EngineShowcases
import com.awakekt.awake.showcase.ShowcaseDebugToggles
import com.awakekt.awake.showcase.ShowcaseSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Clicks the panel the way the runtime does, because the failure worth catching is a button that
 * renders and does nothing — a switcher nobody can switch with looks identical in a screenshot.
 */
class ShowcaseSwitcherTest {

    private val host = ComposeHost()
    private val selection = ShowcaseSelection("point-lights")

    private fun frame(x: Int = FrameInput.UNKNOWN_POINTER, y: Int = FrameInput.UNKNOWN_POINTER, down: Boolean = false): FrameOutput =
        host.frame(
            FrameInput(viewportWidth = 1280, viewportHeight = 720, pointerX = x, pointerY = y, pointerDown = down),
        ) {
            ShowcaseOverlay(selection, EngineShowcases)
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
        selection.request("heightfield-terrain")
        assertEquals("heightfield-terrain", selection.consumeRequest())
        val heightfield = frame()
        assertNotNull(heightfield.find(ShowcaseDebugTags.TERRAIN_DIAGNOSTICS))
        assertNotNull(heightfield.find(ShowcaseDebugTags.COLLIDERS))
        assertNotNull(heightfield.find(ShowcaseDebugTags.LIGHTS))
        assertNotNull(heightfield.find(ShowcaseDebugTags.WIREFRAME))
        assertNotNull(heightfield.find(ShowcaseDebugTags.OCCLUSION))

        selection.request("skinned-mesh")
        assertEquals("skinned-mesh", selection.consumeRequest())
        val skinned = frame()
        assertNotNull(skinned.find(ShowcaseDebugTags.WIREFRAME))
        assertNull(skinned.find(ShowcaseDebugTags.TERRAIN_DIAGNOSTICS))
        assertNull(skinned.find(ShowcaseDebugTags.COLLIDERS))

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
}

/** The tree is nested, so a flat scan of the roots finds the panel and none of its buttons. */
private fun FrameOutput.find(tag: String): SemanticsNode? = semantics.firstNotNullOfOrNull { it.find(tag) }

private fun SemanticsNode.find(tag: String): SemanticsNode? =
    if (testTag == tag) this else children.firstNotNullOfOrNull { it.find(tag) }
