/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.animation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrameClipPlayerTest {
    private val runs = linkedMapOf(
        "idle" to FrameClip(0, 2, 2f, true),
        "blink" to FrameClip(2, 2, 4f, false),
        "held" to FrameClip(5, 1, 0f, false),
    )

    @Test
    fun loopsSwitchesAndRestartsUseTheSimulationClock() {
        val player = FrameClipPlayer(runs)
        player.advance(0.499f)
        assertEquals(0, player.frame)
        player.advance(0.001f)
        assertEquals(1, player.frame)
        player.play("idle")
        assertEquals(1, player.frame)
        player.advance(0.5f)
        assertEquals(0, player.frame)
        player.play("blink")
        player.advance(0.25f)
        assertEquals(3, player.frame)
        player.play("blink", restart = true)
        assertEquals(2, player.frame)
        assertFalse(player.isFinished)
    }

    @Test
    fun oneShotsHoldTheirLastCellAndReportCompletion() {
        val player = FrameClipPlayer(runs, "blink")
        player.advance(0.25f)
        assertEquals(3, player.frame)
        assertFalse(player.isFinished)
        player.advance(0.25f)
        assertTrue(player.isFinished)
        player.advance(100f)
        assertEquals(3, player.frame)
        player.play("held")
        player.advance(100f)
        assertEquals(5, player.frame)
        assertFalse(player.isFinished)
    }

    @Test
    fun playersHaveIndependentPauseAndSpeedOverSharedRuns() {
        val paused = FrameClipPlayer(runs).apply { speed = 0f }
        val fast = FrameClipPlayer(runs).apply { speed = 2f }
        paused.advance(0.25f)
        fast.advance(0.25f)
        assertEquals(0, paused.frame)
        assertEquals(1, fast.frame)
        fast.advance(-10f)
        assertEquals(1, fast.frame)
        assertFailsWith<IllegalArgumentException> { fast.speed = Float.NaN }
        assertFailsWith<IllegalArgumentException> { fast.advance(Float.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { fast.play("missing") }
        assertEquals("idle", fast.activeClipId)
    }

    @Test
    fun aLongRunningLoopAndAHugeFiniteStepKeepValidFrameIndices() {
        val player = FrameClipPlayer(runs)
        repeat(60 * 60 * 60) { player.advance(1f / 60f) }
        assertTrue(player.elapsedSeconds in 0f..1f)
        player.speed = Float.MAX_VALUE
        player.advance(Float.MAX_VALUE)
        assertTrue(player.frame in 0..1)
    }

    @Test
    fun invalidRangesAndExternalMapChangesCannotCorruptPlayback() {
        assertFailsWith<IllegalArgumentException> { FrameClip(Int.MAX_VALUE, 2, 1f, true) }
        assertFailsWith<IllegalArgumentException> { FrameClip(0, 0, 1f, true) }
        assertFailsWith<IllegalArgumentException> { FrameClip(0, 1, Float.NaN, true) }
        assertFailsWith<IllegalArgumentException> { FrameClipPlayer(runs, "missing") }
        val editable = runs.toMutableMap()
        val player = FrameClipPlayer(editable)
        editable.clear()
        player.advance(0.5f)
        assertEquals(1, player.frame)
        assertEquals(0, FrameClipPlayer(emptyMap()).frame)
    }
}
