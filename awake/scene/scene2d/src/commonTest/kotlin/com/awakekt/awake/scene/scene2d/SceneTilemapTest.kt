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
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SceneTilemapTest {
    init {
        SceneComponentRegistry.registerGlobal(TilemapBinding)
    }
    private val settings = SceneTilemap("tiles", 2, 2, listOf(0, -1, 2, 3), columns = 2, rows = 2, chunkSize = 1)

    @Test
    fun tileEditsRoundTripThroughTheDocumentBinding() {
        val document = SceneDocument(nodes = listOf(SceneNode(name = "floor", components = listOf(settings))))
        val loaded = SceneLoader.decode(SceneLoader.encode(document)).nodes.single().components.single() as SceneTilemap
        assertEquals(settings, loaded)
        val world = World()
        val entity = world.create()
        TilemapBinding.attachTyped(
            world,
            entity,
            loaded,
            object : SceneResolutionContext {
                override val world = world
                override fun deferNodeLink(targetNodeName: String, onResolved: (Entity) -> Unit) = Unit
                override fun recordRequest(request: Any) = Unit
            },
        )
        val tilemap = requireNotNull(world.get<Tilemap>(entity))
        tilemap.setTile(1, 0, 1)
        tilemap.sortOrder = 7
        val exported = TilemapBinding.export(world, entity, tilemap)
        assertEquals(listOf(0, 1, 2, 3), exported.tiles)
        assertEquals(7, exported.sortOrder)
        assertEquals(1L, tilemap.grid.chunks[1].revision)
        assertFailsWith<IllegalArgumentException> { tilemap.setTile(0, 0, 4) }
    }

    @Test
    fun malformedCellsDimensionsAndAtlasSettingsAreRejected() {
        for (invalid in listOf(
            settings.copy(width = 3),
            settings.copy(tiles = listOf(-2, 0, 0, 0)),
            settings.copy(columns = 0),
            settings.copy(chunkSize = 0),
            settings.copy(pixelsPerUnit = Float.NaN),
            settings.copy(texture = ""),
        )) {
            assertTrue(invalid.validate("node").isNotEmpty())
            assertFailsWith<IllegalArgumentException> { Tilemap(invalid) }
        }
    }
}
