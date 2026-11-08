/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.gestures

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DoubleClickTrackerTest {

    @Test
    fun aSecondClickInTimeAndInPlaceIsADoubleClick() {
        val tracker = DoubleClickTracker()
        assertFalse(tracker.onClick(1f, 10, 10))
        assertTrue(tracker.onClick(1.3f, 12, 11))
    }

    @Test
    fun aThirdClickStartsANewPairInsteadOfFiringAgain() {
        val tracker = DoubleClickTracker()
        tracker.onClick(1f, 0, 0)
        assertTrue(tracker.onClick(1.1f, 0, 0))
        assertFalse(tracker.onClick(1.2f, 0, 0))
        assertTrue(tracker.onClick(1.3f, 0, 0))
    }

    @Test
    fun aSlowSecondClickStartsOver() {
        val tracker = DoubleClickTracker(maxIntervalSeconds = 0.4f)
        tracker.onClick(1f, 0, 0)
        assertFalse(tracker.onClick(1.5f, 0, 0))
        assertTrue(tracker.onClick(1.6f, 0, 0), "the slow click is the first of the next pair")
    }

    @Test
    fun aSecondClickOutsideTheSlopStartsOver() {
        val tracker = DoubleClickTracker(slopPx = 8)
        tracker.onClick(1f, 0, 0)
        assertFalse(tracker.onClick(1.1f, 9, 0))
        assertTrue(tracker.onClick(1.2f, 9, 0))
    }

    @Test
    fun aClockThatWentBackwardsIsNotADoubleClick() {
        val tracker = DoubleClickTracker()
        tracker.onClick(5f, 0, 0)
        assertFalse(tracker.onClick(4.9f, 0, 0))
    }

    @Test
    fun resetForgetsTheFirstClick() {
        val tracker = DoubleClickTracker()
        tracker.onClick(1f, 0, 0)
        tracker.reset()
        assertFalse(tracker.onClick(1.1f, 0, 0))
    }
}

class DoubleClickableTest {
    private var clicks = 0
    private var doubleClicks = 0
    private var outerClicks = 0
    private val content: context(Composer)
    () -> Unit = {
        Box(Modifier.size(200.dp)) {
            Spacer(Modifier.size(80.dp).doubleClickable(onClick = { clicks++ }) { doubleClicks++ })
        }
    }

    private fun mouse(host: ComposeHost, x: Int, down: Boolean, seconds: Float = 1f / 60f) =
        host.frame(FrameInput(200, 200, x, 20, pointerDown = down, deltaSeconds = seconds), content)

    private fun click(host: ComposeHost, x: Int = 20, gapSeconds: Float = 1f / 60f) {
        mouse(host, x, down = false, seconds = gapSeconds)
        mouse(host, x, down = true)
        mouse(host, x, down = false)
    }

    @Test
    fun twoQuickClicksFireBothClicksAndOneDoubleClick() {
        val host = ComposeHost()
        click(host)
        click(host)

        assertEquals(2, clicks)
        assertEquals(1, doubleClicks)
    }

    @Test
    fun clicksFurtherApartThanTheIntervalAreNotADoubleClick() {
        val host = ComposeHost()
        click(host)
        // The frame clock clamps one frame to 0.1 s, so the gap is several frames.
        repeat(6) { mouse(host, 20, down = false, seconds = 0.1f) }
        click(host)

        assertEquals(2, clicks)
        assertEquals(0, doubleClicks)
    }

    @Test
    fun aSecondClickFarFromTheFirstIsNotADoubleClick() {
        val host = ComposeHost()
        click(host, x = 10)
        click(host, x = 60)

        assertEquals(0, doubleClicks)
    }

    @Test
    fun aReleaseOutsideTheNodeIsNoClick() {
        val host = ComposeHost()
        click(host)
        mouse(host, 20, down = true)
        mouse(host, 150, down = true)
        mouse(host, 150, down = false)

        assertEquals(1, clicks)
        assertEquals(0, doubleClicks)
    }

    @Test
    fun theDoubleClickIsConsumedSoAnAncestorDoesNotClick() {
        val nested: context(Composer)
        () -> Unit = {
            Box(Modifier.size(200.dp).doubleClickable { outerClicks++ }) {
                Spacer(Modifier.size(80.dp).doubleClickable { doubleClicks++ })
            }
        }
        val host = ComposeHost()
        repeat(2) {
            host.frame(FrameInput(200, 200, 20, 20), nested)
            host.frame(FrameInput(200, 200, 20, 20, pointerDown = true), nested)
            host.frame(FrameInput(200, 200, 20, 20), nested)
        }

        assertEquals(1, doubleClicks)
        assertEquals(0, outerClicks)
    }
}
