/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.sprites.SpriteDrawInput
import com.awakekt.awake.scene.core.transform.Transform

/**
 * Maps live scene components to the renderer-independent atlas capability. Each sprite's
 * [Sprite.sortingLayer] is placed by [layers], the project's; with none, every sprite is on
 * [SortingLayers.DEFAULT] and draws by its sort order alone.
 */
fun World.collectSpriteDraws(layers: SortingLayers = SortingLayers.None): List<SpriteDrawInput> = buildList {
    queryEach<Transform, Sprite> { _, transform, sprite ->
        add(
            SpriteDrawInput(
                texture = sprite.texture,
                model = transform.worldMatrix,
                columns = sprite.columns,
                rows = sprite.rows,
                frame = sprite.frame,
                pixelsPerUnit = sprite.pixelsPerUnit,
                flipX = sprite.flipX,
                flipY = sprite.flipY,
                tint = Color(sprite.tint.r, sprite.tint.g, sprite.tint.b, sprite.tint.a),
                sortOrder = sprite.sortOrder,
                pivotX = sprite.pivot.x,
                pivotY = sprite.pivot.y,
                sortLayer = layers.rank(sprite.sortingLayer),
            ),
        )
    }
}
