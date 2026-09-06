/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.focusable
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.layout.Layer
import com.awakekt.awake.compose.ui.layout.LayerKind
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.input.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `Layer(modal = true)` owns the frame: the page behind it takes neither clicks nor focus.
 *
 * The machinery for this shipped with the layer system and no component has ever passed
 * `modal = true`, so it has only ever run in its own unit tests. Four overlays are about to depend
 * on it; this is that dependency written down before they do.
 */
class ModalLayerTest {

    @Test
    fun aClickCannotReachThePageBehindAModal() {
        var behind = 0
        var inside = 0
        val session = composeTestSession(VIEWPORT, VIEWPORT) {
            Column {
                Box(Modifier.size(VIEWPORT.dp, VIEWPORT.dp).clickable { behind++ }.testTag("behind"))
            }
            Layer(kind = LayerKind.Dialog, modal = true, measurePolicy = BoxMeasurePolicy()) {
                Box(Modifier.size(PANEL.dp, PANEL.dp).clickable { inside++ }.testTag("panel"))
            }
        }
        session.frame()

        session.click("panel")
        assertEquals(1, inside, "the modal's own content must still take clicks")

        // The backdrop: inside the page's bounds, outside the panel's.
        session.clickAt(VIEWPORT - 1, VIEWPORT - 1)
        assertEquals(0, behind, "a click on the backdrop reached the page behind the modal")
    }

    @Test
    fun tabCannotMoveFocusToThePageBehindAModal() {
        // Asserted through the InteractionSource a component would own, rather than by reading the
        // focus owner: this is the surface a dialog actually styles itself from.
        val behind = InteractionSource()
        val first = InteractionSource()
        val session = composeTestSession(VIEWPORT, VIEWPORT) {
            Column {
                Box(Modifier.size(PANEL.dp, PANEL.dp).focusable(interactionSource = behind).testTag("behind"))
            }
            Layer(kind = LayerKind.Dialog, modal = true, measurePolicy = BoxMeasurePolicy()) {
                Box(Modifier.size(PANEL.dp, PANEL.dp).focusable(interactionSource = first).testTag("first"))
            }
        }
        session.frame()

        var reachedInside = false
        repeat(TAB_PRESSES) {
            session.pressKey(Key.Tab)
            if (first.isFocused) reachedInside = true
            assertFalse(behind.isFocused, "Tab escaped the modal onto the page behind it")
        }
        println("PROBE reachedInside=$reachedInside behindFocused=${behind.isFocused}")
        assertTrue(reachedInside, "Tab never reached the modal's own content")
    }

    @Test
    fun escapeAsksTheModalToClose() {
        var dismissed = 0
        val session = composeTestSession(VIEWPORT, VIEWPORT) {
            Layer(
                kind = LayerKind.Dialog,
                modal = true,
                dismissOnOutsideClick = true,
                onDismissRequest = { dismissed++ },
                measurePolicy = BoxMeasurePolicy(),
            ) {
                Box(Modifier.size(PANEL.dp, PANEL.dp).testTag("panel"))
            }
        }
        session.frame()
        session.pressKey(Key.Escape)

        assertEquals(1, dismissed, "Escape did not reach the modal")
    }

    private companion object {
        const val VIEWPORT = 400
        const val PANEL = 100
        const val TAB_PRESSES = 6
    }
}
