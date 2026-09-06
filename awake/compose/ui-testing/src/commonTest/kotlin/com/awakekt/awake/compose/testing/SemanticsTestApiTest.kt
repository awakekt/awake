/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.style.Style
import com.awakekt.awake.compose.foundation.style.StyleState
import com.awakekt.awake.compose.foundation.style.styleable
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.SemanticsRole
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class SemanticsTestApiTest {

    private fun frame(): ComposeComponentFrame = composeFrame(width = 80, height = 40) {
        Spacer(
            Modifier.size(20.dp).semantics {
                this[SemanticsProperties.TestTag] = "save"
                this[SemanticsProperties.Role] = SemanticsRole.Button
                this[SemanticsProperties.Label] = "Save"
                this[SemanticsProperties.Selected] = true
            },
        )
    }

    @Test
    fun findersAssertSemanticsAndExactRootBounds() {
        frame().onNode(hasTestTag("save") and hasRole(SemanticsRole.Button))
            .assertExists()
            .assert(isSelected())
            .assertBoundsInRoot(ComposeTestBounds(left = 0, top = 0, width = 20, height = 20))
    }

    @Test
    fun snapshotIsStableAndIncludesTheParityFacts() {
        val json = frame().captureSemantics().toJson()

        assertEquals(
            "{\"nodes\":[{\"testTag\":\"save\",\"role\":\"Button\",\"label\":\"Save\",\"bounds\":{\"x\":0,\"y\":0,\"width\":20,\"height\":20},\"selected\":true,\"disabled\":null}]}",
            json,
        )
    }

    @Test
    fun snapshotExportsResolvedContentInsetsForParity() {
        val frame = composeFrame(width = 80, height = 40) {
            Spacer(
                Modifier
                    .size(20.dp)
                    .styleable(
                        StyleState.Default,
                        Style {
                            contentPadding(horizontal = 3.dp, vertical = 2.dp)
                            cornerRadius(4.dp)
                            border(1.dp, com.awakekt.awake.core.color.Color.Black)
                        },
                    )
                    .semantics { this[SemanticsProperties.TestTag] = "inset" },
            )
        }

        val node = frame.captureSemantics().nodes.single()

        assertEquals(ComposeTestInsets(3f, 2f, 3f, 2f), node.contentPadding)
        assertEquals(1f, node.borderWidth)
        assertEquals(4f, node.cornerRadius)
    }

    @Test
    fun debugOverlayPaintsTheSemanticBoundsOverTheHeadlessCapture() {
        val pixels = frame().rasterizeDebugOverlay(width = 80, height = 40)

        assertEquals(51.toByte(), pixels[0], "the overlay did not write its blue bounds line")
        assertEquals(153.toByte(), pixels[1])
        assertEquals(255.toByte(), pixels[2])
    }

    @Test
    fun sessionClicksTheLastFramesSemanticNodeThenReturnsTheUpdatedFrame() {
        var clicks = 0
        val session = composeTestSession(width = 80, height = 40) {
            Spacer(Modifier.size(20.dp).clickable { clicks++ }.semantics { this[SemanticsProperties.TestTag] = "save" })
        }

        session.frame()
        session.click("save")

        assertEquals(1, clicks)
    }
}
