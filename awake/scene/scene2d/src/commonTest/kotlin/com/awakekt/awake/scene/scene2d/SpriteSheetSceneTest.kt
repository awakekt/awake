/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.asset.sprite.SpriteSheet
import com.awakekt.awake.core.animation.FrameClip
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SpriteSheetSceneTest {
    private val sheet = SpriteSheet("sheet.png", 64, 32, 16, 16, mapOf("idle" to FrameClip(5, 2, 5f, true)))

    @Test
    fun mapsAllGridAndClipFieldsWithCallerControlledTextureResolution() {
        assertEquals(SceneSprite("sheet.png", 4, 2), sheet.toSceneSprite())
        assertEquals(SceneSprite("hero", 4, 2), sheet.toSceneSprite("hero"))
        assertEquals(SceneSpriteClips(mapOf("idle" to SceneSpriteClip(5, 2, 5f, true)), "idle"), sheet.toSceneSpriteClips("idle"))
    }

    @Test
    fun bakesNestedSpritesPreservingStylingSelectionAndLocalRuns() {
        val original = SceneSprite("hero", columns = 99, pixelsPerUnit = 16f, flipX = true, tint = SceneColor(0.2f, 0.3f, 0.4f), sortOrder = 7)
        val localRuns = SceneSpriteClips(mapOf("hold" to SceneSpriteClip(7, 1, 0f, false)), clip = "idle")
        val child = SceneNode(name = "child", components = listOf(localRuns, original))
        val untouched = SceneNode(name = "unrelated", components = listOf(SceneSprite("other")))
        val document = SceneDocument(nodes = listOf(SceneNode(name = "parent", children = listOf(child)), untouched))
        val baked = document.withSpriteSheets(mapOf("hero" to sheet))
        val importedChild = baked.nodes.first().children.single()
        assertSame(child.transform, importedChild.transform)
        assertEquals(original.copy(columns = 4, rows = 2), importedChild.components.filterIsInstance<SceneSprite>().single())
        val runs = importedChild.components.filterIsInstance<SceneSpriteClips>().single()
        assertEquals(listOf("idle", "hold"), runs.clips.keys.toList())
        assertEquals("idle", runs.clip)
        assertEquals(localRuns.clips.getValue("hold"), runs.clips.getValue("hold"))
        assertEquals(untouched, baked.nodes.last())
        assertEquals(99, original.columns, "import must not mutate the source document")
        SceneValidator.requireValid(baked)
    }

    @Test
    fun explicitLocalRunsOverrideImportedRunsAndInvalidSelectionsAreReported() {
        val local = SceneSpriteClips(mapOf("idle" to SceneSpriteClip(2, 1, 0f, false)), "idle")
        val node = SceneNode(components = listOf(SceneSprite("hero"), local))
        val baked = SceneDocument(nodes = listOf(node)).withSpriteSheets(mapOf("hero" to sheet))
        assertEquals(local, baked.nodes.single().components.filterIsInstance<SceneSpriteClips>().single())
        val invalid = SceneDocument(nodes = listOf(node.copy(components = listOf(SceneSprite("hero"), local.copy(clip = "missing")))))
            .withSpriteSheets(mapOf("hero" to sheet))
        assertTrue(invalid.nodes.single().components.filterIsInstance<SceneSpriteClips>().single().validate("hero").isNotEmpty())
    }

    @Test
    fun ambiguousComponentListsAreRejected() {
        val sprite = SceneSprite("hero")
        val duplicateSprites = SceneDocument(nodes = listOf(SceneNode(components = listOf(sprite, sprite))))
        assertFailsWith<IllegalArgumentException> { duplicateSprites.withSpriteSheets(mapOf("hero" to sheet)) }
        val runs = SceneSpriteClips()
        val duplicateClips = SceneDocument(nodes = listOf(SceneNode(components = listOf(sprite, runs, runs))))
        assertFailsWith<IllegalArgumentException> { duplicateClips.withSpriteSheets(mapOf("hero" to sheet)) }
    }
}
