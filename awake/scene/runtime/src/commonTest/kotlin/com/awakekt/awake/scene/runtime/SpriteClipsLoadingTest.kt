/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.scene2d.SceneSprite
import com.awakekt.awake.scene.scene2d.SceneSpriteClip
import com.awakekt.awake.scene.scene2d.SceneSpriteClips
import com.awakekt.awake.scene.scene2d.Sprite
import com.awakekt.awake.scene.scene2d.SpriteClipSystem
import com.awakekt.awake.scene.scene2d.SpriteClips
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SpriteClipsLoadingTest {
    init { DefaultSceneComponentResolvers.install() }

    private val sprite = SceneSprite("sheet", columns = 4, frame = 1)
    private val clips = SceneSpriteClips(
        clips = linkedMapOf(
            "idle" to SceneSpriteClip(frameCount = 2, framesPerSecond = 2f),
            "blink" to SceneSpriteClip(firstFrame = 2, frameCount = 2, framesPerSecond = 4f, loop = false),
        ),
        clip = "blink",
    )

    @Test
    fun eitherComponentOrderLoadsTheInitialCellAndExportsSelectedClip() {
        for (components in listOf(listOf(sprite, clips), listOf(clips, sprite))) {
            val world = World()
            val document = SceneDocument(nodes = listOf(SceneNode(name = "hero", components = components)))
            SceneLoader.decode(SceneLoader.encode(document)).instantiate(world = world)
            var liveSprite: Sprite? = null
            var liveClips: SpriteClips? = null
            world.queryEach<Sprite, SpriteClips> { _, s, c -> liveSprite = s; liveClips = c }
            assertEquals(2, assertNotNull(liveSprite).frame, "initial cell must be ready before the first system update")
            val playback = assertNotNull(liveClips)
            playback.play("idle")
            SpriteClipSystem().update(world, 0.5f)
            assertEquals(1, liveSprite.frame)
            val exported = SceneLoader.fromWorld(world).nodes.single().components
            assertEquals(clips.copy(clip = "idle"), exported.filterIsInstance<SceneSpriteClips>().single())
        }
    }

    @Test
    fun invalidPeerRangesAreRejectedBeforeAnyEntityIsCreated() {
        val document = SceneDocument(nodes = listOf(SceneNode(name = "hero", components = listOf(sprite.copy(columns = 1), clips))))
        assertFailsWith<IllegalArgumentException> { document.instantiate(world = World()) }
    }
}
