/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.ecs.World
import com.awakekt.awake.particles.ParticleEmitter2D
import com.awakekt.awake.particles.appendSpriteDraws
import com.awakekt.awake.render.passes.sprites.SpriteDrawInput
import com.awakekt.awake.scene.core.transform.Transform

/**
 * Maps live [Sprite] and [ParticleEmitter2D] components to renderer-independent [SpriteDrawInput]
 * quads, a particle's through [appendSpriteDraws] (an emitter without a `Transform`, such as a
 * `BurstEmitter2DPool` slot, is drawn too). Each sprite's or 2D emitter's `sortingLayer` is placed
 * by [layers], the project's; with none, every quad is on [SortingLayers.DEFAULT] and draws by its
 * sort order alone.
 */
fun World.collectSpriteDraws(layers: SortingLayers = SortingLayers.None): List<SpriteDrawInput> = buildList {
    collectEntitySpriteDraws(this@collectSpriteDraws, layers)
    collectParticleSpriteDraws(this@collectSpriteDraws, layers)
}

private fun MutableList<SpriteDrawInput>.collectEntitySpriteDraws(world: World, layers: SortingLayers) {
    world.queryEach<Transform, Sprite> { _, transform, sprite ->
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

private fun MutableList<SpriteDrawInput>.collectParticleSpriteDraws(world: World, layers: SortingLayers) {
    world.queryEach(ParticleEmitter2DType) { entity, emitter ->
        if (!emitter.enabled || emitter.liveParticleCount == 0) return@queryEach
        val transform = world.get(entity, TransformType)
        val parent: Mat4?
        val depth: Float
        if (transform == null) {
            parent = null
            depth = 0f
        } else if (transform.usesWorldMatrix()) {
            parent = transform.worldMatrix
            depth = parent.m23
        } else {
            parent = transform.localMatrix()
            depth = transform.position.z
        }
        emitter.appendSpriteDraws(this, layers.rank(emitter.sortingLayer), parent, depth)
    }
}

// Held once, so a frame's component reads build no KClass.
private val ParticleEmitter2DType = ParticleEmitter2D::class
private val TransformType = Transform::class
