/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.sprites.SpriteDrawInput
import com.awakekt.awake.scene.core.transform.Transform

/**
 * Maps live [Sprite] and [ParticleEmitter2D] scene components to renderer-independent
 * [SpriteDrawInput] quads. Each sprite's or 2D emitter's `sortingLayer` is placed by [layers], the
 * project's; with none, every quad is on [SortingLayers.DEFAULT] and draws by its sort order alone.
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
    var localScratch: Mat4? = null
    world.queryEach<Transform, ParticleEmitter2D> { _, transform, emitter ->
        val sim = emitter.simulation
        if (!emitter.enabled || sim.liveParticleCount == 0) return@queryEach
        val useWorldMatrix = transform.parent != null || transform.worldVersion > 0
        val parentMatrix = if (useWorldMatrix) transform.worldMatrix else transform.localMatrix()
        val worldZ = if (useWorldMatrix) transform.worldMatrix.m23 else transform.position.z
        val sortLayerRank = layers.rank(emitter.sortingLayer)
        for (slot in 0 until sim.maxParticles) {
            if (!sim.alive[slot]) continue
            val model = if (emitter.localSpace) {
                val local = (localScratch ?: Mat4().also { localScratch = it })
                    .setEulerTRS(sim.posX[slot], sim.posY[slot], 0f, 0f, 0f, sim.rotation[slot], 1f, 1f, 1f)
                Mat4.multiplyColumnMajor(parentMatrix, local)
            } else {
                Mat4().setEulerTRS(sim.posX[slot], sim.posY[slot], worldZ, 0f, 0f, sim.rotation[slot], 1f, 1f, 1f)
            }
            add(
                SpriteDrawInput(
                    texture = emitter.texture,
                    model = model,
                    columns = emitter.columns,
                    rows = emitter.rows,
                    frame = sim.frameAt(slot),
                    tint = emitter.particleTintAt(slot),
                    sortOrder = emitter.sortOrder,
                    sortLayer = sortLayerRank,
                    worldSize = sim.scaleAt(slot),
                    additive = emitter.additive,
                ),
            )
        }
    }
}

private fun ParticleEmitter2D.particleTintAt(slot: Int): Color {
    val sim = simulation
    val targetTint = endTint ?: return Color(tint.r, tint.g, tint.b, tint.a * sim.alphaAt(slot))
    val life = sim.lifetime[slot]
    val fraction = if (life > 0f) (sim.age[slot] / life).coerceIn(0f, 1f) else 1f
    val r = tint.r + (targetTint.r - tint.r) * fraction
    val g = tint.g + (targetTint.g - tint.g) * fraction
    val b = tint.b + (targetTint.b - tint.b) * fraction
    val baseA = tint.a + (targetTint.a - tint.a) * fraction
    return Color(r, g, b, baseA * sim.alphaAt(slot))
}
