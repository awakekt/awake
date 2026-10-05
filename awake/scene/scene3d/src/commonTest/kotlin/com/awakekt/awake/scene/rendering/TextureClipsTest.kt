/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.render.passes.uniforms.TextureAnimation
import com.awakekt.awake.scene.rendering.mesh.SceneTextureClip
import com.awakekt.awake.scene.rendering.mesh.SceneTextureClips
import com.awakekt.awake.scene.rendering.mesh.TextureClips
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextureClipsTest {
    /** A 4 x 4 sheet: idle is cells 0-1 at 2 a second, walk 8-11 at 10, attack 12-14 once at 10, still holds cell 5. */
    private val sheet = SceneTextureClips(
        columns = 4,
        rows = 4,
        clips = linkedMapOf(
            "idle" to SceneTextureClip(firstFrame = 0, frameCount = 2, framesPerSecond = 2f),
            "walk" to SceneTextureClip(firstFrame = 8, frameCount = 4, framesPerSecond = 10f),
            "attack" to SceneTextureClip(firstFrame = 12, frameCount = 3, framesPerSecond = 10f, loop = false),
            "still" to SceneTextureClip(firstFrame = 5, frameCount = 3, framesPerSecond = 0f),
        ),
        clip = "walk",
    )

    private fun TextureClips.step(seconds: Float, times: Int = 1): TextureClips {
        repeat(times) { advance(seconds) }
        return this
    }

    @Test
    fun aLoopingClipWalksItsCellsAndStartsOver() {
        val clips = TextureClips(sheet)

        val shown = buildList {
            add(clips.frame)
            repeat(5) { add(clips.step(0.1f).frame) }
        }

        // Ten a second: a cell every tenth. Four cells, so the fifth step is back at the first.
        assertEquals(listOf(8, 9, 10, 11, 8, 9), shown)
    }

    @Test
    fun aCellChangesAtTheBoundaryNotJustBefore() {
        val clips = TextureClips(sheet)

        assertEquals(8, clips.step(0.099f).frame)
        assertEquals(9, clips.step(0.002f).frame)
    }

    @Test
    fun aClipThatDoesNotLoopHoldsItsLastCellAndSaysItHasFinished() {
        val clips = TextureClips(sheet).apply { play("attack") }

        assertEquals(12, clips.frame)
        assertEquals(13, clips.step(0.1f).frame)
        assertEquals(14, clips.step(0.1f).frame)
        assertFalse(clips.isFinished, "the last cell has only just come up")
        clips.step(0.1f)
        assertTrue(clips.isFinished)
        assertEquals(14, clips.frame)

        clips.step(5f)
        assertEquals(14, clips.frame, "it holds the last cell")
        assertTrue(clips.isFinished)
    }

    @Test
    fun aLoopingClipNeverFinishes() {
        val clips = TextureClips(sheet).step(1f, times = 10)

        assertFalse(clips.isFinished)
    }

    @Test
    fun aClipAtZeroFramesPerSecondHoldsItsFirstCellAndNeverFinishes() {
        val clips = TextureClips(sheet).apply { play("still") }

        clips.step(100f)

        assertEquals(5, clips.frame)
        assertFalse(clips.isFinished)
    }

    @Test
    fun playingAnotherClipStartsItFromItsFirstCell() {
        val clips = TextureClips(sheet).step(0.25f)
        assertEquals(10, clips.frame)

        clips.play("idle")

        assertEquals("idle", clips.activeClipId)
        assertEquals(0, clips.frame)
        assertEquals(0f, clips.elapsedSeconds)
    }

    /** A game asks for the clip it wants every frame; that must not hold the clip on its first cell. */
    @Test
    fun askingForTheClipThatIsPlayingLeavesItAlone() {
        val clips = TextureClips(sheet).step(0.25f)

        clips.play("walk")

        assertEquals(10, clips.frame)
    }

    @Test
    fun askingToRestartTheClipThatIsPlayingStartsItAgain() {
        val clips = TextureClips(sheet).step(0.25f)

        clips.play("walk", restart = true)

        assertEquals(8, clips.frame)
    }

    @Test
    fun aClipTheSheetLacksIsRefusedAndTheMessageNamesWhatItHas() {
        val clips = TextureClips(sheet)

        val failure = assertFailsWith<IllegalArgumentException> { clips.play("run") }

        assertTrue("run" in failure.message.orEmpty(), failure.message)
        assertTrue("walk" in failure.message.orEmpty(), failure.message)
        assertEquals("walk", clips.activeClipId, "a refused request changes nothing")
    }

    @Test
    fun speedScalesTheClockAndZeroHolds() {
        val fast = TextureClips(sheet).apply { speed = 2f }
        val held = TextureClips(sheet).apply { speed = 0f }

        assertEquals(9, fast.step(0.05f).frame, "twice as fast: a cell in a twentieth")
        assertEquals(8, held.step(10f).frame)
        assertFailsWith<IllegalArgumentException> { held.speed = -1f }
        assertFailsWith<IllegalArgumentException> { held.speed = Float.NaN }
    }

    @Test
    fun theFirstListedClipPlaysWhenNoneIsNamed() {
        val unnamed = TextureClips(sheet.copy(clip = null))

        assertEquals("idle", unnamed.activeClipId)
        assertEquals(0, unnamed.frame)
    }

    @Test
    fun aSheetWithNoClipsShowsItsFirstCellAndSteppingChangesNothing() {
        val bare = TextureClips(SceneTextureClips(columns = 2, rows = 2))

        bare.step(5f)

        assertNull(bare.activeClipId)
        assertEquals(0, bare.frame)
        assertFalse(bare.isFinished)
    }

    @Test
    fun aClipThatLeavesTheSheetIsRefusedWhenItIsMade() {
        val leaving = SceneTextureClips(
            columns = 2,
            rows = 2,
            clips = mapOf("run" to SceneTextureClip(firstFrame = 3, frameCount = 2)),
        )

        assertFailsWith<IllegalArgumentException> { TextureClips(leaving) }
    }

    /** A loop's clock wraps, so an hour of frames leaves it as exact as the first second. */
    @Test
    fun aLoopingClockStaysInsideOneLoopHoweverLongItRuns() {
        val clips = TextureClips(sheet)
        val loop = 4f / 10f

        repeat(60 * 60 * 60) { clips.advance(1f / 60f) }

        assertTrue(clips.elapsedSeconds >= 0f && clips.elapsedSeconds < loop, "elapsed ${clips.elapsedSeconds}")
        assertTrue(clips.frame in 8..11)
    }

    @Test
    fun theHeldAnimationShowsTheCellNow() {
        val clips = TextureClips(sheet).step(0.25f)

        assertEquals(TextureAnimation(columns = 4, rows = 4, frameCount = 1, framesPerSecond = 0f, firstFrame = 10), clips.held())
    }
}
