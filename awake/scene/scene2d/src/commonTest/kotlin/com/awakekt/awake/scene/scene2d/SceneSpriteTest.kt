/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SceneSpriteTest {
    init {
        SceneComponentRegistry.registerGlobal(SpriteBinding)
    }

    private val hero = SceneSprite(
        texture = "hero.png",
        columns = 4,
        rows = 2,
        frame = 5,
        pixelsPerUnit = 16f,
        flipX = true,
        flipY = true,
        tint = SceneColor(r = 0.5f, g = 0.25f, b = 0.75f, a = 0.5f),
        sortOrder = 3,
    )

    private fun problems(sprite: SceneSprite): List<String> = sprite.validate("node").map { it.message }

    @Test
    fun aPlainSpriteNeedsOnlyItsImage() {
        val sprite = SceneSprite(texture = "tree.png")

        assertEquals(emptyList(), problems(sprite))
        assertEquals(1, sprite.cellCount)
        assertEquals(100f, sprite.pixelsPerUnit)
    }

    @Test
    fun aSpriteWithEverythingSetIsValid() {
        assertEquals(emptyList(), problems(hero))
        assertEquals(8, hero.cellCount)
    }

    @Test
    fun aBlankImageIsRejected() {
        assertEquals(listOf("sprite.texture must name an image"), problems(SceneSprite(texture = " ")))
    }

    @Test
    fun aSheetNeedsAColumnAndARow() {
        val issues = problems(SceneSprite(texture = "a.png", columns = 0, rows = 0))

        assertTrue("sprite.columns must be at least 1" in issues)
        assertTrue("sprite.rows must be at least 1" in issues)
        assertEquals(2, issues.size, "an invalid sheet must not also report its frame")
    }

    @Test
    fun theFrameStaysInsideTheSheet() {
        assertEquals(emptyList(), problems(hero.copy(frame = 7)))
        assertEquals(
            listOf("sprite.frame must stay within the sheet's 8 cells: it is 8"),
            problems(hero.copy(frame = 8)),
        )
        assertEquals(listOf("sprite.frame must not be negative"), problems(hero.copy(frame = -1)))
    }

    @Test
    fun theScaleMustBeFiniteAndPositive() {
        for (bad in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(
                listOf("sprite.pixelsPerUnit must be finite and above 0"),
                problems(hero.copy(pixelsPerUnit = bad)),
                "pixelsPerUnit = $bad",
            )
        }
    }

    @Test
    fun theIssuesNameTheNodeTheyBelongTo() {
        assertEquals("nodes[2]", SceneSprite(texture = "").validate("nodes[2]").single().path)
    }

    @Test
    fun aSceneFileDecodesToASprite() {
        val decoded = SceneLoader.decode(
            """
            {"version": 1, "nodes": [{"components": [{
              "component": "sprite", "texture": "hero.png", "columns": 4, "rows": 2, "frame": 5,
              "pixelsPerUnit": 16, "flipX": true, "sortOrder": 3
            }]}]}
            """.trimIndent(),
        ).nodes.single().components.single()

        assertEquals(hero.copy(flipY = false, tint = SceneColor()), decoded)
    }

    @Test
    fun aSceneFileWithEverythingSetSurvivesAnEncodeAndDecode() {
        val document = SceneDocument(nodes = listOf(SceneNode("hero", components = listOf(hero))))

        val back = SceneLoader.decode(SceneLoader.encode(document))

        assertEquals(hero, back.nodes.single().components.single())
    }

    @Test
    fun theBindingAttachesALiveSpriteThatExportsBackUnchanged() {
        val world = World()
        val entity = world.create()

        SpriteBinding.attachTyped(world, entity, hero, Context(world))

        val live = assertNotNull(world.get<Sprite>(entity))
        assertEquals(5, live.frame)
        assertEquals(8, live.cellCount)
        assertEquals(hero, SpriteBinding.export(world, entity, live))
    }

    @Test
    fun aGameChangesWhatShowsAndTheSceneExportsIt() {
        val world = World()
        val entity = world.create()
        SpriteBinding.attachTyped(world, entity, hero, Context(world))
        val live = assertNotNull(world.get<Sprite>(entity))

        live.frame = 0
        live.flipX = false
        live.sortOrder = -1

        assertEquals(
            hero.copy(frame = 0, flipX = false, sortOrder = -1),
            SpriteBinding.export(world, entity, live),
        )
    }

    @Test
    fun aLiveSpriteRefusesAFrameOutsideItsSheet() {
        val live = sprite(columns = 2, rows = 2, frame = 0)

        assertFailsWith<IllegalArgumentException> { live.frame = 4 }
        assertFailsWith<IllegalArgumentException> { live.frame = -1 }
        assertEquals(0, live.frame, "a refused frame must leave the old one showing")
    }

    @Test
    fun aLiveSpriteCannotBeBuiltOutsideItsSheet() {
        assertFailsWith<IllegalArgumentException> { sprite(columns = 1, rows = 1, frame = 1) }
    }

    private fun sprite(columns: Int, rows: Int, frame: Int) = Sprite(
        texture = "a.png",
        columns = columns,
        rows = rows,
        pixelsPerUnit = 16f,
        frame = frame,
        flipX = false,
        flipY = false,
        tint = SceneColor(),
        sortOrder = 0,
    )

    private class Context(override val world: World) : SceneResolutionContext {
        override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) = Unit

        override fun recordRequest(request: Any) = Unit
    }
}
