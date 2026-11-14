/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.platform.StaleSkip
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.DrawCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * #1544: a host that skips unchanged composition can still show state the snapshot system does not
 * observe, such as a field an ECS system writes, by being told it changed.
 */
class ExternalInvalidateTest {
    /** A window's colour, as an ECS system would hold it: a plain field, not a `State`. */
    private class Window {
        var hp = 0
    }

    private val red = Color(0.9f, 0.1f, 0.1f)
    private val green = Color(0.1f, 0.9f, 0.1f)

    private fun rig() = ComposeHost().also { it.skipUnchangedComposition = true }

    private fun ComposeHost.draw(window: Window): FrameOutput = frame(FrameInput(200, 100, deltaSeconds = 0f)) { ui(window) }

    context(_: Composer)
    private fun ui(window: Window) {
        Box(Modifier.size(40.dp, 20.dp).background(if (window.hp > 0) green else red))
    }

    private fun FrameOutput.shows(color: Color): Boolean =
        primitives.any { (it as? DrawCommand.Quad)?.color == color || (it as? DrawCommand.RoundedQuad)?.color == color }

    @Test
    fun withoutAnInvalidateASkippingHostKeepsTheLastFrame() {
        val window = Window()
        val host = rig()
        host.draw(window)
        host.draw(window)
        window.hp = 10
        val output = host.draw(window)
        assertTrue(output.shows(red), "documented: an unobserved read stays stale until something invalidates")
        assertEquals(2, host.skippedCompositions)
    }

    @Test
    fun invalidatingShowsTheNewFrameOnceAndThenSkipsAgain() {
        val window = Window()
        val host = rig()
        host.draw(window)
        window.hp = 10
        host.invalidate()
        assertTrue(host.draw(window).shows(green))
        val skippedBefore = host.skippedCompositions
        assertTrue(host.draw(window).shows(green))
        assertEquals(skippedBefore + 1, host.skippedCompositions, "the invalidate is spent by one pass")
    }

    @Test
    fun thePerFrameHookInvalidatesWhenTheConsumersSystemSaysSo() {
        val window = Window()
        val host = rig()
        var lastSeenHp = window.hp
        host.externalChanges = { (window.hp != lastSeenHp).also { lastSeenHp = window.hp } }
        host.draw(window)
        host.draw(window)
        window.hp = 10
        assertTrue(host.draw(window).shows(green))
        host.draw(window)
        assertEquals(2, host.skippedCompositions, "frames with no change still skip")
    }

    @Test
    fun verifyModeReportsAStaleSkipAndShowsTheComposedFrame() {
        val window = Window()
        val host = rig()
        val found = ArrayList<StaleSkip>()
        host.verifySkippedCompositions = true
        host.onStaleSkip = { found += it }
        host.draw(window)
        host.draw(window)
        assertEquals(0, host.staleSkips, "an unchanged frame is not stale")
        window.hp = 10
        assertTrue(host.draw(window).shows(green), "verify mode composes, so the frame is right")
        assertEquals(1, host.staleSkips)
        val stale = assertNotNull(found.singleOrNull())
        assertTrue(stale.composed.toString().contains("0.9"), "the report carries the differing primitive: $stale")
    }
}
