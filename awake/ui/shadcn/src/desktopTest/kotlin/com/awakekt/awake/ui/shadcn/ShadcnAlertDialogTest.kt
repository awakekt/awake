/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.testing.composeTestSession
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.ui.shadcn.components.ShadcnAlertDialog
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The alert dialog's contract is its dismissal, not its styling.
 *
 * Upstream's alert dialog answers Escape and deliberately ignores its backdrop -- a destructive
 * question must not be answerable by clicking away from it. That is the whole difference from
 * `Dialog`, and it is the reason `Layer` grew `dismissOnEscape` separately.
 */
class ShadcnAlertDialogTest {

    @Test
    fun clickingTheBackdropDoesNotDismissIt() {
        val world = alertWorld()
        world.session.frame()
        world.session.clickAt(EDGE, EDGE)

        assertEquals(0, world.dismissed, "the backdrop dismissed an alert dialog")
        assertNotNull(
            world.session.frame().onNodeWithTag(ID).getBoundsInRoot(),
            "the panel vanished",
        )
    }

    @Test
    fun escapeDismissesIt() {
        val world = alertWorld()
        world.session.frame()
        world.session.pressKey(Key.Escape)

        assertEquals(1, world.dismissed, "Escape did not reach the alert dialog")
    }

    @Test
    fun theCancelAndConfirmActionsReportSeparately() {
        val cancelWorld = alertWorld()
        cancelWorld.session.frame()
        cancelWorld.session.click("$ID.cancel")
        assertEquals(1, cancelWorld.dismissed, "cancel should ask to close")
        assertEquals(0, cancelWorld.confirmed, "cancel must not confirm")

        val confirmWorld = alertWorld()
        confirmWorld.session.frame()
        confirmWorld.session.click("$ID.confirm")
        assertEquals(1, confirmWorld.confirmed, "confirm should fire its own action")
        assertEquals(0, confirmWorld.dismissed, "confirm must not double as a dismissal")
    }

    /**
     * The scrim, not `modal`, is what does this: it covers the viewport, so the hit-test never
     * descends past the layer. Modality is proven separately in `ModalLayerTest`, where no scrim is
     * in the way to give a false pass.
     */
    @Test
    fun thePageBehindTakesNoClicksWhileItIsOpen() {
        val world = alertWorld()
        world.session.frame()
        world.session.clickAt(EDGE, EDGE)

        assertEquals(0, world.behind, "a click reached the page behind an open alert dialog")
    }

    @Test
    fun anAlertDialogIsCenteredInTheWindowWhenDeclaredInsideNestedContent() {
        val session = composeTestSession(VIEWPORT, VIEWPORT) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
                Column {
                    Spacer(Modifier.size(VIEWPORT.dp, NESTED_CONTENT_TOP.dp))
                    Box(Modifier.size(VIEWPORT.dp, 100.dp)) {
                        ShadcnAlertDialog(
                            visible = true,
                            title = "Delete this scene?",
                            description = "This cannot be undone.",
                            onDismissRequest = {},
                            id = ID,
                        )
                    }
                }
            }
        }

        val bounds = session.frame().onNodeWithTag(ID).getBoundsInRoot()
        assertEquals(VIEWPORT / 2, bounds.left + bounds.width / 2, "alert dialog is not centered horizontally")
        assertEquals(VIEWPORT / 2, bounds.top + bounds.height / 2, "alert dialog is positioned from its declaring preview")
        assertEquals(ALERT_MAX_WIDTH, bounds.width, "alert dialog ignored its max-width")
    }

    @Test
    fun nothingIsShownWhenItIsNotVisible() {
        val world = alertWorld(visible = false)
        val frame = world.session.frame()

        assertNull(
            frame.semantics.firstOrNull { it.testTag == ID },
            "a hidden alert dialog rendered",
        )
        world.session.clickAt(EDGE, EDGE)
        assertEquals(1, world.behind, "the page must be interactive with no dialog open")
    }

    private class AlertWorld {
        var dismissed = 0
        var confirmed = 0
        var behind = 0
        lateinit var session: com.awakekt.awake.compose.testing.ComposeTestSession
    }

    private fun alertWorld(visible: Boolean = true): AlertWorld {
        val world = AlertWorld()
        world.session = composeTestSession(VIEWPORT, VIEWPORT) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
                Box(Modifier.fillMaxSize().clickable { world.behind++ }.testTag("page"))
                ShadcnAlertDialog(
                    visible = visible,
                    title = "Delete this scene?",
                    description = "This cannot be undone.",
                    onDismissRequest = { world.dismissed++ },
                    onConfirm = { world.confirmed++ },
                    destructive = true,
                    id = ID,
                )
            }
        }
        return world
    }

    private companion object {
        const val VIEWPORT = 600
        const val NESTED_CONTENT_TOP = 120
        const val ALERT_MAX_WIDTH = 512
        const val EDGE = 4
        const val ID = "alert"
    }
}
