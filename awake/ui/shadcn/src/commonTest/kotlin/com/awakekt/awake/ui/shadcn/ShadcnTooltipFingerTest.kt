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
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.PointerFrame
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.ui.shadcn.components.ShadcnTooltipped
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** A touch screen has no hover, so a held finger opens a tooltip, and only while it's held. */
class ShadcnTooltipFingerTest {
    private var clicks = 0
    private val content: context(Composer) () -> Unit = {
        provideShadcnTheme(shadcnThemeValues(dark = true)) {
            Column {
                Spacer(Modifier.size(60.dp))
                ShadcnTooltipped("Wireframe") {
                    Box(Modifier.size(40.dp).clickable { clicks++ }) {}
                }
            }
        }
    }

    private fun finger(down: Boolean, seconds: Float = 1f / 60f) =
        FrameInput(300, 200, pointers = listOf(PointerFrame(1, 20, 80, down)), deltaSeconds = seconds)

    private fun List<SemanticsNode>.tooltip(): SemanticsNode? = firstNotNullOfOrNull { find(it) }

    private fun find(node: SemanticsNode): SemanticsNode? =
        if (node.label == "Wireframe") node else node.children.firstNotNullOfOrNull { find(it) }

    @Test
    fun holdingAFingerOpensTheTooltipUntilItLiftsWithoutClicking() {
        val host = ComposeHost()
        host.frame(FrameInput(300, 200), content)
        host.frame(finger(down = true), content)
        assertNull(host.frame(finger(down = true), content).semantics.tooltip(), "a touch alone is no hover")

        host.frame(finger(down = true, seconds = 0.6f), content)
        assertNotNull(host.frame(finger(down = true), content).semantics.tooltip(), "held, it opens")

        host.frame(finger(down = false), content)
        assertNull(host.frame(FrameInput(300, 200), content).semantics.tooltip(), "lifted, it closes")
        assertEquals(0, clicks, "the lift that closes the tooltip is not a click")
    }

    @Test
    fun aTapStillClicksAndShowsNoTooltip() {
        val host = ComposeHost()
        host.frame(FrameInput(300, 200), content)
        host.frame(finger(down = true), content)
        host.frame(finger(down = false), content)

        assertEquals(1, clicks)
        assertNull(host.frame(FrameInput(300, 200), content).semantics.tooltip(), "a lifted finger leaves nothing hovered")
    }
}
