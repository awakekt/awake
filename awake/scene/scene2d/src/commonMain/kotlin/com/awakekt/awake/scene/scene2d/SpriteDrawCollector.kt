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

/** Maps live scene components to the renderer-independent atlas capability. */
fun World.collectSpriteDraws(): List<SpriteDrawInput> = buildList {
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
            ),
        )
    }
}
