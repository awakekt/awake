/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.asset.sprite.SpriteSheet
import com.awakekt.awake.core.animation.FrameClip
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.scene2d.SceneSprite
import com.awakekt.awake.scene.scene2d.SceneSpriteClips
import com.awakekt.awake.scene.scene2d.Sprite
import com.awakekt.awake.scene.scene2d.SpriteClipSystem
import com.awakekt.awake.scene.scene2d.toSceneSpriteClips
import com.awakekt.awake.scene.scene2d.withSpriteSheets
import kotlin.test.Test
import kotlin.test.assertEquals

class SpriteSheetLoadingTest {
    init {
        DefaultSceneComponentResolvers.install()
    }

    private val sheet = SpriteSheet("sheet.png", 64, 32, 16, 16, mapOf("idle" to FrameClip(5, 2, 5f, true)))

    @Test
    fun bakedRunsInstantiateAdvanceAndRoundTripWithoutTheImporter() {
        val document = SceneDocument(nodes = listOf(SceneNode(name = "hero", components = listOf(SceneSprite("hero")))))
            .withSpriteSheets(mapOf("hero" to sheet))
        val world = World()
        SceneLoader.decode(SceneLoader.encode(document)).instantiate(world = world)
        world.queryEach(Sprite::class) { _, sprite -> assertEquals(5, sprite.frame) }
        SpriteClipSystem().update(world, 0.2f)
        world.queryEach(Sprite::class) { _, sprite -> assertEquals(6, sprite.frame) }
        val saved = SceneLoader.fromWorld(world)
        assertEquals(saved, SceneLoader.decode(SceneLoader.encode(saved)))
        assertEquals(sheet.toSceneSpriteClips("idle"), saved.nodes.single().components.filterIsInstance<SceneSpriteClips>().single())
    }
}
