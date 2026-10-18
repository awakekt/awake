/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneVec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A sprite's pivot and sorting layer: in the scene file, on the live sprite, and in what it draws. */
class SpritePivotAndLayersTest {
    init {
        SceneComponentRegistry.registerGlobal(SpriteBinding)
        SceneComponentRegistry.registerGlobal(TilemapBinding)
    }

    private fun problems(sprite: SceneSprite): List<String> = sprite.validate("node").map { it.message }

    // --- the scene file

    @Test
    fun aSpriteSavedBeforePivotsAndLayersLoadsCentredOnTheDefaultLayer() {
        val decoded = SceneLoader.decode(
            """{"version": 1, "nodes": [{"components": [{"component": "sprite", "texture": "hero.png"}]}]}""",
        ).nodes.single().components.single() as SceneSprite

        assertEquals(SceneVec2(0.5f, 0.5f), decoded.pivot)
        assertEquals(SortingLayers.DEFAULT, decoded.sortingLayer)
    }

    @Test
    fun aPivotAndALayerSurviveAnEncodeAndDecode() {
        val sprite = SceneSprite(texture = "hero.png", pivot = SceneVec2(0.5f, 0f), sortingLayer = "Foreground")
        val document = SceneDocument(nodes = listOf(SceneNode("hero", components = listOf(sprite))))

        assertEquals(sprite, SceneLoader.decode(SceneLoader.encode(document)).nodes.single().components.single())
    }

    @Test
    fun aPivotOutsideTheFrameIsAllowedButNotAnUnboundedOne() {
        assertEquals(emptyList(), problems(SceneSprite(texture = "a.png", pivot = SceneVec2(-0.5f, 2f))))
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(listOf("sprite.pivot must be finite"), problems(SceneSprite(texture = "a.png", pivot = SceneVec2(bad, 0f))))
        }
        assertFailsWith<IllegalArgumentException> { Sprite(SceneSprite(texture = "a.png", pivot = SceneVec2(Float.NaN, 0f))) }
    }

    @Test
    fun aBlankLayerIsRejected() {
        assertEquals(listOf("sprite.sortingLayer must name a layer"), problems(SceneSprite(texture = "a.png", sortingLayer = " ")))
        val tilemap = SceneTilemap("tiles.png", 1, 1, listOf(0), sortingLayer = "")
        assertEquals(listOf("tilemap.sortingLayer must name a layer"), tilemap.validate("node").map { it.message })
    }

    @Test
    fun aGameMovesASpriteToAnotherLayerAndTheSceneExportsIt() {
        val world = World()
        val entity = world.create()
        val authored = SceneSprite(texture = "hero.png")
        SpriteBinding.attachTyped(world, entity, authored, NoLinks(world))
        val live = world.get<Sprite>(entity)!!

        live.pivot = SceneVec2(0.5f, 0f)
        live.sortingLayer = "Foreground"

        assertEquals(authored.copy(pivot = SceneVec2(0.5f, 0f), sortingLayer = "Foreground"), SpriteBinding.export(world, entity, live))
        assertFailsWith<IllegalArgumentException> { live.pivot = SceneVec2(0f, Float.NaN) }
        assertEquals(SceneVec2(0.5f, 0f), live.pivot, "a refused pivot leaves the old one")
    }

    @Test
    fun aTilemapsLayerSurvivesItsBinding() {
        val world = World()
        val entity = world.create()
        val authored = SceneTilemap("tiles.png", 1, 1, listOf(0), sortingLayer = "Background")
        TilemapBinding.attachTyped(world, entity, authored, NoLinks(world))

        assertEquals(authored, TilemapBinding.export(world, entity, world.get<Tilemap>(entity)!!))
    }

    // --- the layers

    @Test
    fun layersRankAroundTheDefaultLayer() {
        val layers = SortingLayers(listOf("Sky", "Background", "Default", "Foreground", "Overlay"))

        assertEquals(listOf(-2, -1, 0, 1, 2), layers.names.map(layers::rank))
    }

    @Test
    fun aListWithoutTheDefaultLayerHasItFirst() {
        val layers = SortingLayers(listOf("Background", "Foreground"))

        assertEquals(listOf("Default", "Background", "Foreground"), layers.names)
        assertEquals(0, layers.rank("Default"))
        assertEquals(1, layers.rank("Background"))
        assertEquals(2, layers.rank("Foreground"))
    }

    @Test
    fun aLayerTheProjectDoesNotListDrawsOnTheDefaultLayer() {
        val layers = SortingLayers(listOf("Background", "Default"))

        assertEquals(0, layers.rank("Typo"))
        assertFalse("Typo" in layers)
        assertTrue("Background" in layers)
        assertEquals(0, SortingLayers.None.rank("Anything"))
    }

    @Test
    fun eachLayerIsNamedOnce() {
        assertFailsWith<IllegalArgumentException> { SortingLayers(listOf("Background", "Background")) }
        assertFailsWith<IllegalArgumentException> { SortingLayers(listOf(" ")) }
    }

    // --- what the sprites draw

    @Test
    fun eachSpriteDrawsOnItsLayersRankWithItsPivot() {
        val world = World()
        fun sprite(layer: String, order: Int, pivot: SceneVec2 = SceneVec2(0.5f, 0.5f)) = world.create().also {
            world.add(it, Transform())
            world.add(it, Sprite(SceneSprite(texture = "a.png", sortOrder = order, pivot = pivot, sortingLayer = layer)))
        }
        sprite("Foreground", order = -5, pivot = SceneVec2(0.25f, 0f))
        sprite("Default", order = 7)
        sprite("Background", order = 99)
        val layers = SortingLayers(listOf("Background", "Default", "Foreground"))

        val draws = world.collectSpriteDraws(layers).sortedWith(compareBy({ it.sortLayer }, { it.sortOrder }))

        assertEquals(listOf(-1 to 99, 0 to 7, 1 to -5), draws.map { it.sortLayer to it.sortOrder })
        assertEquals(0.25f to 0f, draws.last().pivotX to draws.last().pivotY)
    }

    @Test
    fun withoutLayersEverySpriteDrawsOnTheDefaultLayerAsBefore() {
        val world = World()
        listOf("Foreground", "Background").forEach { layer ->
            world.create().also {
                world.add(it, Transform())
                world.add(it, Sprite(SceneSprite(texture = "a.png", sortingLayer = layer)))
            }
        }

        assertEquals(listOf(0, 0), world.collectSpriteDraws().map { it.sortLayer })
    }

    private class NoLinks(override val world: World) : com.awakekt.awake.scene.binding.SceneResolutionContext {
        override fun deferNodeLink(targetNodeName: String, onResolved: (target: com.awakekt.awake.ecs.Entity) -> Unit) = Unit

        override fun recordRequest(request: Any) = Unit
    }
}
