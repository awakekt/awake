/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OccluderFaderTest {
    private val settings = OccluderFade(opacity = 0.35f, fadeSeconds = 0.2f)
    private val frame = 1f / 60f

    @Test
    fun aBlockingOccluderStartsFading() {
        val fader = OccluderFader<String>()
        fader.blocking("wall")
        fader.step(settings, frame)

        val opacity = fader.opacityOf("wall")
        assertTrue(opacity < 1f && opacity > 0.35f, "one frame in, it is on its way down, not there: $opacity")
        assertEquals(1f, fader.opacityOf("tree"), "what was never hit is not faded")
    }

    @Test
    fun theFadeEasesToTheConfiguredOpacity() {
        val fader = OccluderFader<String>()
        var previous = 1f
        repeat(12) {
            fader.blocking("wall")
            fader.step(settings, frame)
            assertTrue(fader.opacityOf("wall") < previous, "it keeps fading while it blocks")
            previous = fader.opacityOf("wall")
        }
        repeat(108) {
            fader.blocking("wall")
            fader.step(settings, frame)
        }

        assertEquals(0.35f, fader.opacityOf("wall"), 1e-3f, "two seconds later it rests at the configured opacity")
    }

    @Test
    fun anOccluderThatStopsBlockingEasesBackAndIsReleased() {
        val released = mutableListOf<String>()
        val fader = OccluderFader<String> { released += it }
        repeat(60) {
            fader.blocking("wall")
            fader.step(settings, frame)
        }

        fader.step(settings, frame)
        assertTrue(fader.opacityOf("wall") in 0.35f..0.99f, "it eases back, not snaps: ${fader.opacityOf("wall")}")
        assertTrue(released.isEmpty())

        repeat(120) { fader.step(settings, frame) }
        assertEquals(1f, fader.opacityOf("wall"))
        assertEquals(listOf("wall"), released, "once opaque it is forgotten and released once")
        assertEquals(0, fader.size)
    }

    @Test
    fun clearReleasesEveryOccluder() {
        val released = mutableListOf<String>()
        val fader = OccluderFader<String> { released += it }
        fader.blocking("wall")
        fader.blocking("tree")
        fader.step(settings, frame)

        fader.clear()

        assertEquals(setOf("wall", "tree"), released.toSet())
        assertEquals(1f, fader.opacityOf("wall"))
    }
}
