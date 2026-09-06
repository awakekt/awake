/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.testing.ComposeTestSession
import com.awakekt.awake.compose.testing.composeTestSession
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnPopover
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A popover opens from its trigger, sits below it, and closes the ways upstream's does.
 *
 * It is the same primitive as the dropdown menu -- only the contents differ -- so the interesting
 * assertions are the layer ones: that the panel is anchored to the trigger and that opening it does
 * not move the trigger.
 */
class ShadcnPopoverTest {

    @Test
    fun theTriggerTogglesIt() {
        val world = popoverWorld()
        world.session.frame()
        assertNull(panelBounds(world.session), "a closed popover rendered its panel")

        world.session.click(TRIGGER)
        assertTrue(world.visible, "the trigger did not open it")
        world.session.frame()
        assertTrue(panelBounds(world.session) != null, "the panel did not appear")

        world.session.click(TRIGGER)
        assertTrue(!world.visible, "pressing the trigger again did not close it")
    }

    @Test
    fun pressingOutsideClosesIt() {
        val world = popoverWorld(open = true)
        world.session.frame()
        world.session.clickAt(VIEWPORT - EDGE, VIEWPORT - EDGE)
        assertTrue(!world.visible, "an outside press did not close the popover")
    }

    @Test
    fun escapeClosesIt() {
        val world = popoverWorld(open = true)
        world.session.frame()
        world.session.pressKey(Key.Escape)
        assertTrue(!world.visible, "Escape did not close the popover")
    }

    @Test
    fun thePanelSitsBelowTheTriggerAndDoesNotMoveIt() {
        val closed = popoverWorld()
        val triggerWhenClosed = closed.session.frame().onNodeWithTag(TRIGGER).getBoundsInRoot()

        val open = popoverWorld(open = true)
        val frame = open.session.frame()
        val trigger = frame.onNodeWithTag(TRIGGER).getBoundsInRoot()
        val panel = frame.onNodeWithTag(PANEL).getBoundsInRoot()
        println("PROBE trigger=$trigger panel=$panel")

        assertEquals(triggerWhenClosed.top, trigger.top, "opening the popover moved its trigger")
        assertEquals(
            triggerWhenClosed.height,
            trigger.height,
            "opening the popover resized its trigger",
        )
        assertTrue(
            panel.top >= trigger.top + trigger.height,
            "the panel did not sit below the trigger",
        )
        assertEquals(PANEL_WIDTH, panel.width, "`w-72` is a fixed width")
    }

    private class PopoverWorld(var visible: Boolean) {
        lateinit var session: ComposeTestSession
    }

    private fun panelBounds(session: ComposeTestSession) =
        session.frame().semantics.firstNotNullOfOrNull { it.findTag(PANEL) }

    private fun com.awakekt.awake.compose.ui.semantics.SemanticsNode.findTag(
        tag: String,
    ): com.awakekt.awake.compose.ui.semantics.SemanticsNode? =
        if (testTag == tag) this else children.firstNotNullOfOrNull { it.findTag(tag) }

    private fun popoverWorld(open: Boolean = false): PopoverWorld {
        val world = PopoverWorld(open)
        world.session = composeTestSession(VIEWPORT, VIEWPORT) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
                Column {
                    Spacer(Modifier.height(TRIGGER_INSET.dp))
                    ShadcnPopover(
                        visible = world.visible,
                        onVisibleChange = { world.visible = it },
                        id = PANEL,
                        trigger = { onClick ->
                            ShadcnButton(
                                "Open",
                                modifier = Modifier.testTag(TRIGGER),
                                onClick = onClick,
                            )
                        },
                    ) {
                        ShadcnText("Dimensions")
                    }
                }
            }
        }
        return world
    }

    private companion object {
        const val VIEWPORT = 600
        const val EDGE = 4
        const val TRIGGER_INSET = 40
        const val PANEL_WIDTH = 288
        const val TRIGGER = "popover.trigger"
        const val PANEL = "popover.panel"
    }
}
