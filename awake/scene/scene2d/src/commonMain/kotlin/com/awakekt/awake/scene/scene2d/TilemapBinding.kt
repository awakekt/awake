/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.tilemap.TilemapGrid
import kotlin.reflect.KClass

/** Live scene binding. Tile edits invalidate the grid's chunks; export preserves current values. */
class Tilemap(settings: SceneTilemap) {
    init {
        require(settings.validate("tilemap").isEmpty()) { "Invalid tilemap settings." }
    }

    /** Named atlas. */
    val texture: String = settings.texture

    /** Atlas columns. */
    val columns: Int = settings.columns

    /** Atlas rows. */
    val rows: Int = settings.rows

    /** Pixels per local world unit. */
    val pixelsPerUnit: Float = settings.pixelsPerUnit

    /** Editable cells and chunk revisions. */
    val grid = TilemapGrid(settings.width, settings.height, settings.tiles.toIntArray(), settings.chunkSize)

    /** Whole-layer tint. */
    var tint: SceneColor = settings.tint

    /** Paint order, shared with sprites. */
    var sortOrder: Int = settings.sortOrder

    /** Changes a tile after checking that its frame belongs to this layer's atlas. */
    fun setTile(x: Int, y: Int, frame: Int) {
        require(frame >= -1 && frame.toLong() < columns.toLong() * rows) { "Tile frame is outside the atlas." }
        grid[x, y] = frame
    }

    internal fun toScene() = SceneTilemap(
        texture, grid.width, grid.height, grid.copyTiles().toList(), columns, rows, pixelsPerUnit,
        grid.chunkSize, tint, sortOrder,
    )
}

/** Attaches tile data to an entity and exports its current edits. */
object TilemapBinding : SceneComponentBinding<Tilemap, SceneTilemap> {
    override val componentClass: KClass<Tilemap> = Tilemap::class
    override val schemaClass: KClass<SceneTilemap> = SceneTilemap::class
    override val serializer = SceneTilemap.serializer()
    override fun attachTyped(world: World, entity: Entity, component: SceneTilemap, context: SceneResolutionContext) {
        world.add(entity, Tilemap(component))
    }
    override fun export(world: World, entity: Entity, component: Tilemap): SceneTilemap = component.toScene()
}
