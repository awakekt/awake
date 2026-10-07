/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpriteClipsTest {
    private val spriteSchema = SceneSprite("sheet", columns = 4, rows = 2, frame = 7)
    private val sheet = SceneSpriteClips(
        clips = linkedMapOf(
            "idle" to SceneSpriteClip(firstFrame = 1, frameCount = 2, framesPerSecond = 2f),
            "blink" to SceneSpriteClip(firstFrame = 5, frameCount = 2, framesPerSecond = 4f, loop = false),
        ),
    )

    @Test
    fun namedRunsDriveSpritesAndPreserveOtherSpriteProperties() {
        val world = World()
        val entity = world.create()
        val sprite = Sprite(spriteSchema.copy(flipX = true, sortOrder = 3))
        val clips = SpriteClips(sheet)
        world.add(entity, sprite)
        world.add(entity, clips)
        val system = SpriteClipSystem()
        system.update(world, 0f)
        assertEquals(1, sprite.frame)
        system.update(world, 0.5f)
        assertEquals(2, sprite.frame)
        clips.speed = 0f
        system.update(world, 1f)
        assertEquals(2, sprite.frame)
        clips.play("blink")
        system.update(world, 0f)
        assertEquals(5, sprite.frame)
        clips.speed = 1f
        system.update(world, 0.25f)
        assertEquals(6, sprite.frame)
        assertFalse(clips.isFinished)
        system.update(world, 0.25f)
        assertTrue(clips.isFinished)
        assertTrue(sprite.flipX)
        assertEquals(3, sprite.sortOrder)
    }

    @Test
    fun emptyLibrariesAndSpritesWithoutClipsKeepTheirManualFrame() {
        val world = World()
        val entity = world.create()
        val sprite = Sprite(spriteSchema)
        world.add(entity, sprite)
        SpriteClipSystem().update(world, 1f)
        assertEquals(7, sprite.frame)
        world.add(entity, SpriteClips(SceneSpriteClips()))
        SpriteClipSystem().update(world, 1f)
        assertEquals(7, sprite.frame)
    }

    @Test
    fun aHostThatIsNotPlayingPreservesTheClockAndTheCurrentCell() {
        val world = World()
        val entity = world.create()
        val sprite = Sprite(spriteSchema)
        val clips = SpriteClips(sheet)
        world.add(entity, sprite)
        world.add(entity, clips)
        SpriteClipSystem(isPlaying = { false }).update(world, 1f)
        assertEquals(7, sprite.frame)
        assertEquals(0f, clips.elapsedSeconds)
    }

    @Test
    fun validationRequiresASpriteAndRejectsInvalidRunsBeforeLoading() {
        assertTrue(sheet.validate("hero", listOf(sheet)).any { "requires a sprite" in it.message })
        assertEquals(emptyList(), sheet.validate("hero", listOf(sheet, spriteSchema)))
        val overflow = sheet.copy(clips = mapOf("bad" to SceneSpriteClip(firstFrame = Int.MAX_VALUE, frameCount = 2)))
        assertTrue(overflow.validate("hero").any { "fit in an Int" in it.message })
        val outside = sheet.copy(clips = mapOf("bad" to SceneSpriteClip(firstFrame = 7, frameCount = 2)))
        assertTrue(outside.validate("hero", listOf(spriteSchema)).any { "8 cells" in it.message })
        assertTrue(sheet.copy(clip = "missing").validate("hero").isNotEmpty())
        assertTrue(sheet.copy(clips = mapOf("bad" to SceneSpriteClip(framesPerSecond = Float.NaN))).validate("hero").isNotEmpty())
    }

    @Test
    fun allSceneRunFieldsMapToTheIndependentCapability() {
        val authored = SceneSpriteClip(firstFrame = 3, frameCount = 4, framesPerSecond = 6f, loop = false)
        val run = authored.toFrameClip()
        assertEquals(authored.firstFrame, run.firstFrame)
        assertEquals(authored.frameCount, run.frameCount)
        assertEquals(authored.framesPerSecond, run.framesPerSecond)
        assertEquals(authored.loop, run.loop)
    }
}
