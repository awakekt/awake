/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.compose.foundation.clickable
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.fillMaxSize
import io.github.awakelab.awake.compose.testing.composeTestSession
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.semantics.testTag
import io.github.awakelab.awake.core.input.Key
import io.github.awakelab.awake.ui.shadcn.components.ShadcnAlertDialog
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
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
        lateinit var session: io.github.awakelab.awake.compose.testing.ComposeTestSession
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
        const val EDGE = 4
        const val ID = "alert"
    }
}
