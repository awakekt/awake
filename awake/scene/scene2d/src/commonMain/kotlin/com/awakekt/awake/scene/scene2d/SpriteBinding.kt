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
import kotlin.reflect.KClass

/**
 * A [SceneSprite] on a live entity. A game changes what shows by setting these, for example
 * `sprite.frame = 3` or `sprite.flipX = facingLeft`; the scene exports what they are then.
 *
 * @property texture The image file the sprite shows. It is fixed for the life of the entity.
 * @property columns Frame-sheet columns.
 * @property rows Frame-sheet rows.
 * @property pixelsPerUnit Image pixels that make one scene unit.
 */
class Sprite(
    val texture: String,
    val columns: Int,
    val rows: Int,
    val pixelsPerUnit: Float,
    frame: Int,
    var flipX: Boolean,
    var flipY: Boolean,
    var tint: SceneColor,
    var sortOrder: Int,
) {
    /** Cells in the sheet. */
    val cellCount: Int get() = columns * rows

    /** The cell showing, counted in reading order from 0 at the top left. */
    var frame: Int = frame
        set(value) {
            require(value in 0 until cellCount) { "Sprite frame $value is outside the sheet's $cellCount cells." }
            field = value
        }

    init {
        require(this.frame in 0 until cellCount) { "Sprite frame $frame is outside the sheet's $cellCount cells." }
    }

    /** This sprite as a scene file describes it. */
    internal fun toScene(): SceneSprite = SceneSprite(
        texture = texture,
        columns = columns,
        rows = rows,
        frame = frame,
        pixelsPerUnit = pixelsPerUnit,
        flipX = flipX,
        flipY = flipY,
        tint = tint,
        sortOrder = sortOrder,
    )
}

/** Loads [SceneSprite] as the entity's [Sprite], and exports it back as it is then. */
object SpriteBinding : SceneComponentBinding<Sprite, SceneSprite> {
    override val componentClass: KClass<Sprite> = Sprite::class
    override val schemaClass: KClass<SceneSprite> = SceneSprite::class
    override val serializer = SceneSprite.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneSprite,
        context: SceneResolutionContext,
    ) {
        world.add(
            entity,
            Sprite(
                texture = component.texture,
                columns = component.columns,
                rows = component.rows,
                pixelsPerUnit = component.pixelsPerUnit,
                frame = component.frame,
                flipX = component.flipX,
                flipY = component.flipY,
                tint = component.tint,
                sortOrder = component.sortOrder,
            ),
        )
    }

    override fun export(world: World, entity: Entity, component: Sprite): SceneSprite = component.toScene()
}
