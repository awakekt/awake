/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.testing.ComposeComponentFrame
import com.awakekt.awake.compose.testing.assertMatchesBaseline
import com.awakekt.awake.compose.testing.composeFrame
import com.awakekt.awake.compose.testing.composeTestSession
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.ui.shadcn.components.ShadcnAlertDialog
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnDropdownMenu
import com.awakekt.awake.ui.shadcn.components.ShadcnInputOtp
import com.awakekt.awake.ui.shadcn.components.ShadcnMenuItem
import com.awakekt.awake.ui.shadcn.components.ShadcnPopover
import com.awakekt.awake.ui.shadcn.components.ShadcnRangeSlider
import com.awakekt.awake.ui.shadcn.components.ShadcnResizablePanel
import com.awakekt.awake.ui.shadcn.components.ShadcnSheet
import com.awakekt.awake.ui.shadcn.components.ShadcnSheetSide
import com.awakekt.awake.ui.shadcn.components.ShadcnSlider
import com.awakekt.awake.ui.shadcn.components.ShadcnSpinner
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnToastState
import com.awakekt.awake.ui.shadcn.components.ShadcnToaster
import com.awakekt.awake.ui.shadcn.components.shadcnContextMenu
import com.awakekt.awake.ui.shadcn.components.shadcnDrawer
import com.awakekt.awake.ui.shadcn.components.shadcnResizablePanelGroup
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test

/**
 * Pins how these five components render, so a fix to one of them shows exactly what it changed.
 *
 * Every component on the open defect list is here at its current -- in several cases wrong --
 * appearance. That is the point: a baseline records what is, so the diff produced by a fix is
 * reviewable. It is not an assertion that any of these look correct today.
 */
class ShadcnVisualBaselineTest {

    @Test
    fun spinner() = baseline("spinner", 48, 48) {
        ShadcnSpinner()
    }

    @Test
    fun slider() = baseline("slider", 320, 48) {
        ShadcnSlider(value = 0.4f, modifier = Modifier.fillMaxWidth().padding(all = 12.dp))
    }

    @Test
    fun rangeSlider() = baseline("slider-range", 320, 48) {
        ShadcnRangeSlider(
            start = 0.25f,
            end = 0.75f,
            modifier = Modifier.fillMaxWidth().padding(all = 12.dp),
        )
    }

    @Test
    fun inputOtp() = baseline("input-otp", 320, 64) {
        ShadcnInputOtp(value = "123", modifier = Modifier.fillMaxWidth().padding(all = 12.dp))
    }

    /** `withHandle`, the state the grip only appears in. */
    @Test
    fun resizablePanelGroupWithHandle() = baseline("resizable-panel-with-handle", 360, 160) {
        shadcnResizablePanelGroup(
            panels = listOf(
                ShadcnResizablePanel(initialSize = 0.5f, tag = "left") { ShadcnText("Left") },
                ShadcnResizablePanel(initialSize = 0.5f, tag = "right") { ShadcnText("Right") },
            ),
            modifier = Modifier.fillMaxWidth().padding(all = 12.dp),
            withHandle = true,
        )
    }

    @Test
    fun resizablePanelGroup() = baseline("resizable-panel-group", 360, 160) {
        shadcnResizablePanelGroup(
            panels = listOf(
                ShadcnResizablePanel(initialSize = 0.5f, tag = "left") { ShadcnText("Left") },
                ShadcnResizablePanel(initialSize = 0.5f, tag = "right") { ShadcnText("Right") },
            ),
            modifier = Modifier.fillMaxWidth().padding(all = 12.dp),
        )
    }

    /** Pins the reported full-width menu: the surface's own width is what the baseline records. */
    @Test
    fun dropdownMenu() = baseline("dropdown-menu", 360, 160) {
        ShadcnDropdownMenu(
            entries = listOf("Profile", "Billing", "Settings").map(::ShadcnMenuItem),
            modifier = Modifier.padding(all = 12.dp),
        )
    }

    /** Its own layer, so the fixture is the whole viewport rather than a wrapped panel. */
    @Test
    fun alertDialog() {
        val session = composeTestSession(360, 220) {
            provideShadcnTheme(Theme) {
                ShadcnAlertDialog(
                    visible = true,
                    title = "Delete this scene?",
                    description = "This cannot be undone.",
                    onDismissRequest = {},
                    destructive = true,
                )
            }
        }
        session.frame().assertMatchesBaseline(
            "alert-dialog",
            width = 360,
            height = 220,
            background = Theme.palette.background,
            font = UiFonts.default(),
        )
    }

    @Test
    fun sheet() = overlayBaseline("sheet") {
        ShadcnSheet(
            visible = true,
            onDismissRequest = {},
            side = ShadcnSheetSide.Right,
            title = "Filters",
            description = "Narrow the list.",
        )
    }

    @Test
    fun drawer() = overlayBaseline("drawer") {
        shadcnDrawer(
            visible = true,
            onDismissRequest = {},
            title = "Move to",
            description = "Pick a destination.",
        )
    }

    @Test
    fun toaster() {
        val state = ShadcnToastState()
        state.show("Scene saved.", title = "Saved")
        overlayBaseline("toaster") { ShadcnToaster(state) }
    }

    @Test
    fun popover() = overlayBaseline("popover") {
        ShadcnPopover(
            visible = true,
            onVisibleChange = {},
            trigger = { onClick -> ShadcnButton("Open", onClick = onClick) },
        ) {
            ShadcnText("Set the dimensions for the layer.")
        }
    }

    /** Only visible after a right-click, so this fixture drives one. */
    @Test
    fun contextMenu() {
        val session = composeTestSession(OVERLAY_WIDTH, OVERLAY_HEIGHT) {
            provideShadcnTheme(Theme) {
                shadcnContextMenu(
                    entries = listOf("Back", "Reload", "Save as").map(::ShadcnMenuItem),
                    onItemSelected = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        session.frame()
        session.frame(
            FrameInput(
                OVERLAY_WIDTH,
                OVERLAY_HEIGHT,
                pointerX = 40,
                pointerY = 30,
                secondaryPointerPressed = true,
            ),
        )
        // Settled, not mid fade-in: a menu opened by a flag animates up from zero, and a baseline
        // caught part-way through would be a timing measurement wearing an appearance test's name.
        repeat(SETTLE_FRAMES) { session.frame() }
        session.frame().assertMatchesBaseline(
            "context-menu",
            width = OVERLAY_WIDTH,
            height = OVERLAY_HEIGHT,
            background = Theme.palette.background,
            font = UiFonts.default(),
        )
    }

    /** Overlays own the viewport, so their fixture is the viewport rather than a wrapped panel. */
    private fun overlayBaseline(name: String, content: context(Composer) () -> Unit) {
        val session = composeTestSession(OVERLAY_WIDTH, OVERLAY_HEIGHT) {
            provideShadcnTheme(Theme) { content() }
        }
        session.frame().assertMatchesBaseline(
            name,
            width = OVERLAY_WIDTH,
            height = OVERLAY_HEIGHT,
            background = Theme.palette.background,
            font = UiFonts.default(),
        )
    }

    private fun baseline(
        name: String,
        width: Int,
        height: Int,
        content: context(Composer) () -> Unit,
    ) {
        frame(width, height, content).assertMatchesBaseline(
            name,
            width = width,
            height = height,
            // Not optional for anything with text: a glyph with no font rasterizes as a magenta
            // block, which would bake a missing font into the baseline as though it were a design.
            background = Theme.palette.background,
            font = UiFonts.default(),
        )
    }

    private val Theme = ShadcnThemeValues(ShadcnTheme)

    private val OVERLAY_WIDTH = 360
    private val OVERLAY_HEIGHT = 220

    /** Long enough for a toggled overlay to reach full opacity before it is captured. */
    private val SETTLE_FRAMES = 20

    private fun frame(
        width: Int,
        height: Int,
        content: context(Composer) () -> Unit,
    ): ComposeComponentFrame = composeFrame(width, height) {
        provideShadcnTheme(Theme) {
            Column { content() }
        }
    }
}
