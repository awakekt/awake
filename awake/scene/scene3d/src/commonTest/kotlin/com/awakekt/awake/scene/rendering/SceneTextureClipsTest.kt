/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.scene.rendering.mesh.SceneTextureAnimation
import com.awakekt.awake.scene.rendering.mesh.SceneTextureClip
import com.awakekt.awake.scene.rendering.mesh.SceneTextureClips
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneTextureClipsTest {
    private fun sheet(vararg clips: Pair<String, SceneTextureClip>, clip: String? = null) =
        SceneTextureClips(columns = 4, rows = 2, clips = linkedMapOf(*clips), clip = clip)

    private fun messages(sheet: SceneTextureClips): List<String> = sheet.validate("node").map { it.message }

    @Test
    fun aSheetWithClipsThatFitIsValid() {
        val valid = sheet(
            "idle" to SceneTextureClip(firstFrame = 0, frameCount = 2),
            "walk" to SceneTextureClip(firstFrame = 4, frameCount = 4, framesPerSecond = 8f),
            "hit" to SceneTextureClip(firstFrame = 7, frameCount = 1, loop = false),
            clip = "walk",
        )

        assertEquals(emptyList(), valid.validate("node"))
    }

    @Test
    fun aSheetWithNoClipsIsValid() {
        assertEquals(emptyList(), SceneTextureClips(columns = 2, rows = 2).validate("node"))
    }

    @Test
    fun aClipThatLeavesTheSheetIsInvalidAndNamesTheClip() {
        // Cells 6, 7 and 8 of an 8-cell sheet.
        val problems = messages(sheet("run" to SceneTextureClip(firstFrame = 6, frameCount = 3)))

        assertEquals(1, problems.size)
        assertTrue("run" in problems.single(), problems.single())
        assertTrue("8" in problems.single(), "it should say how big the sheet is: ${problems.single()}")
    }

    @Test
    fun aClipMustHaveCellsAndStartInsideTheSheet() {
        assertEquals(1, messages(sheet("none" to SceneTextureClip(frameCount = 0))).size)
        assertEquals(1, messages(sheet("before" to SceneTextureClip(firstFrame = -1, frameCount = 2))).size)
    }

    @Test
    fun aClipsRateMustBeFiniteAndNotNegative() {
        assertEquals(1, messages(sheet("slow" to SceneTextureClip(framesPerSecond = -1f))).size)
        assertEquals(1, messages(sheet("nan" to SceneTextureClip(framesPerSecond = Float.NaN))).size)
        assertEquals(1, messages(sheet("inf" to SceneTextureClip(framesPerSecond = Float.POSITIVE_INFINITY))).size)
        assertEquals(emptyList(), messages(sheet("held" to SceneTextureClip(framesPerSecond = 0f))))
    }

    @Test
    fun aClipNeedsAName() {
        assertEquals(1, messages(sheet("" to SceneTextureClip())).size)
    }

    @Test
    fun theClipThatPlaysFirstMustBeOneOfTheClips() {
        val problems = messages(sheet("idle" to SceneTextureClip(), clip = "dance"))

        assertEquals(1, problems.size)
        assertTrue("dance" in problems.single(), problems.single())
    }

    /** With no sheet to measure against, a clip can only be checked for what does not depend on it. */
    @Test
    fun aSheetWithNoCellsIsInvalidWithoutAlsoBlamingEveryClipForLeavingIt() {
        val broken = SceneTextureClips(columns = 0, rows = 2, clips = mapOf("walk" to SceneTextureClip(firstFrame = 4, frameCount = 4)))

        assertEquals(1, broken.validate("node").size)
    }

    @Test
    fun theFirstListedClipPlaysWhenNoneIsNamed() {
        val clips = sheet("idle" to SceneTextureClip(), "walk" to SceneTextureClip())

        assertEquals("idle", clips.initialClip)
        assertEquals("walk", clips.copy(clip = "walk").initialClip)
        assertNull(SceneTextureClips().initialClip)
    }

    @Test
    fun aNodeCannotHaveBothTextureClipsAndTextureAnimation() {
        val clips = sheet("idle" to SceneTextureClip())
        val animation = SceneTextureAnimation(columns = 4, rows = 2)

        val isolatedIssues = clips.validate("hero", listOf(clips))
        assertEquals(emptyList(), isolatedIssues, "texture_clips alone on a node is valid")

        val conflictIssues = clips.validate("hero", listOf(clips, animation))
        assertEquals(1, conflictIssues.size)
        assertEquals("hero", conflictIssues.single().path)
        assertEquals("node cannot have both texture_clips and texture_animation", conflictIssues.single().message)
    }
}
