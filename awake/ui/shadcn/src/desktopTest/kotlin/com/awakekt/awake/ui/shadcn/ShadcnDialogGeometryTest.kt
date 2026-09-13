/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.testing.composeTestSession
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.ui.shadcn.components.ShadcnDialog
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** Modal dialogs are centered in the window, including when a preview declares them deeply. */
class ShadcnDialogGeometryTest {

    @Test
    fun aDialogIsCenteredInTheWindowWhenDeclaredInsideNestedContent() {
        val session = composeTestSession(VIEWPORT, VIEWPORT) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
                Column {
                    Spacer(Modifier.size(VIEWPORT.dp, NESTED_CONTENT_TOP.dp))
                    Box(Modifier.size(VIEWPORT.dp, 100.dp)) {
                        ShadcnDialog(
                            visible = true,
                            onDismissRequest = {},
                            modifier = Modifier.testTag(ID),
                            id = ID,
                        )
                    }
                }
            }
        }

        val bounds = session.frame().onNodeWithTag(ID).getBoundsInRoot()
        assertEquals(VIEWPORT / 2, bounds.left + bounds.width / 2)
        assertEquals(VIEWPORT / 2, bounds.top + bounds.height / 2)
        assertEquals(DIALOG_WIDTH, bounds.width)
    }

    private companion object {
        const val VIEWPORT = 600
        const val NESTED_CONTENT_TOP = 120
        const val DIALOG_WIDTH = 512
        const val ID = "dialog"
    }
}
