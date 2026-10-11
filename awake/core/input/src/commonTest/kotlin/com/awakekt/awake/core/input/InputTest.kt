/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [Input] is a per-session instance now (no longer a global object) -- each test
 * constructs its own, so there's no shared state to reset between tests. */
class InputTest {
    @Test
    fun keyIsNotDownByDefault() {
        val input = Input()
        assertFalse(input.isKeyDown(Key.Space))
    }

    @Test
    fun settingAKeyDownIsObservable() {
        val input = Input()
        input.setKeyDown(Key.W, down = true)
        assertTrue(input.isKeyDown(Key.W))
    }

    @Test
    fun releasingAKeyClearsIt() {
        val input = Input()
        input.setKeyDown(Key.W, down = true)
        input.setKeyDown(Key.W, down = false)
        assertFalse(input.isKeyDown(Key.W))
    }

    @Test
    fun clearKeysReleasesEveryHeldKey() {
        val input = Input()
        input.setKeyDown(Key.W, down = true)
        input.setKeyDown(Key.Space, down = true)
        input.clearKeys()
        assertFalse(input.isKeyDown(Key.W))
        assertFalse(input.isKeyDown(Key.Space))
    }

    @Test
    fun keyEdgesFireOnceAcrossPressHoldRelease() {
        val input = Input()

        input.setKeyDown(Key.F1, down = true)
        val pressFrame = input.updateSnapshot()
        assertEquals(setOf(Key.F1), pressFrame.keysPressed, "the first frame down is a rising edge")
        assertEquals(emptySet(), pressFrame.keysReleased)
        assertTrue(pressFrame.wasPressed(Key.F1))
        assertTrue(pressFrame.isDown(Key.F1))

        val holdFrame = input.updateSnapshot()
        assertEquals(emptySet(), holdFrame.keysPressed, "holding must not re-fire the rising edge")
        assertEquals(emptySet(), holdFrame.keysReleased)
        assertFalse(holdFrame.wasPressed(Key.F1))
        assertTrue(holdFrame.isDown(Key.F1), "a held key is still down")

        input.setKeyDown(Key.F1, down = false)
        val releaseFrame = input.updateSnapshot()
        assertEquals(emptySet(), releaseFrame.keysPressed)
        assertEquals(setOf(Key.F1), releaseFrame.keysReleased, "letting go is a falling edge")
        assertFalse(releaseFrame.isDown(Key.F1))
    }

    @Test
    fun pressingAgainAfterAReleaseIsAFreshEdge() {
        val input = Input()
        input.setKeyDown(Key.F1, down = true)
        input.updateSnapshot()
        input.setKeyDown(Key.F1, down = false)
        input.updateSnapshot()
        input.setKeyDown(Key.F1, down = true)
        assertTrue(input.updateSnapshot().wasPressed(Key.F1))
    }

    @Test
    fun pointerStateReflectsTheLastSetCall() {
        val input = Input()
        input.setPointer(down = true, x = 12f, y = 34f)
        assertTrue(input.pointerDown)
        assertEquals(12f, input.pointerX)
        assertEquals(34f, input.pointerY)

        input.setPointer(down = false, x = 56f, y = 78f)
        assertFalse(input.pointerDown)
        assertEquals(56f, input.pointerX)
        assertEquals(78f, input.pointerY)
    }

    @Test
    fun aSubFrameDownUpPairIsHeldForOneSnapshotThenReleased() {
        // A fast tap, a trackpad tap, or any synthetic/automation click can deliver both
        // mousedown and mouseup before the frame reads the pointer. Shown as both edges with the
        // pointer up, code that reads only pointerDown or isDown(Primary) never saw the click.
        val input = Input()
        input.setPointer(down = true, x = 10f, y = 10f)
        input.setPointer(down = false, x = 10f, y = 10f)

        val tap = input.updateSnapshot()
        assertTrue(tap.pointerPressed, "the down edge must not be erased by the up call")
        assertTrue(tap.pointerDown, "the tap shows as held for one snapshot")
        assertFalse(tap.pointerReleased, "its release comes in the next")
        assertTrue(tap.isDown(PointerButton.Primary))
        assertTrue(tap.wasPressed(PointerButton.Primary), "the button's edge matches the pointer's")

        val after = input.updateSnapshot()
        assertFalse(after.pointerDown)
        assertFalse(after.pointerPressed)
        assertTrue(after.pointerReleased, "the up edge is not lost either")
        assertTrue(PointerButton.Primary in after.buttonsReleased)

        val idle = input.updateSnapshot()
        assertFalse(idle.pointerPressed || idle.pointerReleased || idle.pointerDown, "and then nothing")
    }

    @Test
    fun aPressThatComesWhileATapsReleaseIsOwedJoinsIt() {
        val input = Input()
        input.setPointer(down = true, x = 10f, y = 10f)
        input.setPointer(down = false, x = 10f, y = 10f)
        input.updateSnapshot()

        input.setPointer(down = true, x = 10f, y = 10f)
        val held = input.updateSnapshot()
        assertTrue(held.pointerDown, "two clicks inside two frames read as one press")
        assertFalse(held.pointerPressed || held.pointerReleased)

        input.setPointer(down = false, x = 10f, y = 10f)
        assertTrue(input.updateSnapshot().pointerReleased)
    }

    @Test
    fun pointerEdgesFireOnceAcrossPressHoldRelease() {
        val input = Input()

        input.setPointer(down = true, x = 1f, y = 1f)
        val pressFrame = input.updateSnapshot()
        assertTrue(pressFrame.pointerPressed)
        assertFalse(pressFrame.pointerReleased)

        val holdFrame = input.updateSnapshot()
        assertFalse(holdFrame.pointerPressed, "holding must not re-fire the rising edge")
        assertFalse(holdFrame.pointerReleased)
        assertTrue(holdFrame.pointerDown, "held state persists across frames with no new event")

        input.setPointer(down = false, x = 1f, y = 1f)
        val releaseFrame = input.updateSnapshot()
        assertFalse(releaseFrame.pointerPressed)
        assertTrue(releaseFrame.pointerReleased)
    }

    @Test
    fun imePreeditPersistsUntilThePlatformCommitsIt() {
        val input = Input()
        val composition = ImeComposition("ka", selectionStart = 1, selectionEnd = 2)

        input.setImeComposition(composition)
        assertEquals(composition, input.updateSnapshot().imeComposition)
        assertEquals(composition, input.updateSnapshot().imeComposition, "pre-edit vanished without a platform update")

        input.commitImeText("か")
        val commit = input.updateSnapshot()
        assertEquals(null, commit.imeComposition)
        assertEquals("か", commit.imeCommit)
        assertEquals(null, input.updateSnapshot().imeCommit, "a commit was replayed on a later frame")
    }
}

class InputClipboardTest {

    @Test
    fun clipboardCommandsLastExactlyOneSnapshot() {
        val input = Input()
        input.pushClipboardCommand(ClipboardCommand.Copy)
        input.pushClipboardCommand(ClipboardCommand.Cut)

        assertEquals(listOf(ClipboardCommand.Copy, ClipboardCommand.Cut), input.updateSnapshot().clipboardCommands)
        assertEquals(emptyList(), input.updateSnapshot().clipboardCommands)
    }

    @Test
    fun aClipboardWriteIsTakenOnce() {
        val input = Input()
        input.clipboardWrite = "copied"

        assertEquals("copied", input.takeClipboardWrite())
        assertEquals(null, input.takeClipboardWrite())
    }
}
