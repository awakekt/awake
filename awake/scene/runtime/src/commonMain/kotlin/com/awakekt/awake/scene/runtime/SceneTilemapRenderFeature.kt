/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Frustum
import com.awakekt.awake.core.math.planes
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.sprites.TilemapRenderBatch
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.RenderContribution
import com.awakekt.awake.scene.rendering.RenderFeature3D
import com.awakekt.awake.scene.rendering.RenderFeatureContext3D
import com.awakekt.awake.scene.scene2d.Tilemap

/** GPU resources are owned by the scene runtime, never by the serialized tile component. */
internal class SceneTilemapRenderFeature(
    private val renderer: Renderer,
    private val resolveTexture: (String) -> TextureAsset,
) : RenderFeature3D {
    private val batches = mutableMapOf<Tilemap, TilemapRenderBatch>()
    private val live = mutableSetOf<Tilemap>()
    private val draws = ArrayList<RenderDrawCommand>()

    override fun collect(world: World, context: RenderFeatureContext3D): RenderContribution {
        live.clear()
        draws.clear()
        val planes = Frustum.planes(context.camera.lens, context.viewportAspect)
        world.queryEach<Transform, Tilemap> { _, transform, tilemap ->
            live.add(tilemap)
            val batch = batches.getOrPut(tilemap) {
                TilemapRenderBatch(renderer, tilemap.grid, tilemap.columns, tilemap.rows, tilemap.pixelsPerUnit) {
                    resolveTexture(tilemap.texture)
                }
            }
            val tint = tilemap.tint
            batch.collect(transform.worldMatrix, Color(tint.r, tint.g, tint.b, tint.a), tilemap.sortOrder, planes, draws)
        }
        val iterator = batches.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in live) {
                entry.value.destroy()
                iterator.remove()
            }
        }
        return RenderContribution(draws = draws)
    }

    fun destroy() {
        batches.values.forEach { it.destroy() }
        batches.clear()
        draws.clear()
        live.clear()
    }
}
