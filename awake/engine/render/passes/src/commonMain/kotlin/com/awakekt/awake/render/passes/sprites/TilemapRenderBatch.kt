/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.sprites

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Plane
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.SpriteExtraUniformLayout
import com.awakekt.awake.render.passes.uniforms.SpriteFields
import com.awakekt.awake.render.passes.uniforms.SpriteUniformLayout
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.UniformWriter
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.tilemap.TilemapAtlas
import com.awakekt.awake.tilemap.TilemapGrid

/**
 * One atlas draw per visible non-empty chunk, using the existing sprite pipeline.
 * Owns its meshes and material; call [destroy] before destroying the renderer.
 * @param renderer Resource factory and GPU lifetime owner.
 * @param grid Editable tile data, independent of the scene graph.
 * @param columns Atlas columns.
 * @param rows Atlas rows.
 * @param pixelsPerUnit Image pixels per local world unit.
 * @param resolveTexture Supplies decoded pixels on first collection.
 */
class TilemapRenderBatch(
    private val renderer: Renderer,
    private val grid: TilemapGrid,
    private val columns: Int,
    private val rows: Int,
    private val pixelsPerUnit: Float,
    private val resolveTexture: () -> TextureAsset,
) {
    private var atlas: TilemapAtlas? = null
    private var material: Material? = null
    private val cache = Array(grid.chunks.size) { CachedChunk() }
    private var lastTint: Color? = null
    private var extra = FloatArray(0)

    /**
     * Appends borrowed requests to [out]. Unchanged chunks reuse meshes and requests; hidden
     * chunks are not uploaded. Requests remain valid until this batch's next collection.
     */
    fun collect(model: Mat4, tint: Color, sortOrder: Int, planes: List<Plane>, out: MutableList<RenderDrawCommand>) {
        val sheet = atlas ?: resolveTexture().let { image ->
            require(image.layerCount == 1 && !image.isCubemap) { "Tile atlas must be a single 2D image." }
            TilemapAtlas(image.width, image.height, columns, rows, pixelsPerUnit).also {
                material = renderer.createMaterial(SpriteUniformLayout, texture = image.copy(filtering = TextureFiltering.Nearest))
                atlas = it
            }
        }
        if (lastTint != tint) {
            extra = UniformWriter(SpriteExtraUniformLayout)
                .put(SpriteFields.UvTransform, 1f, 1f, 0f, 0f)
                .put(SpriteFields.Tint, tint.r, tint.g, tint.b, tint.a)
                .build()
            lastTint = tint
        }
        var waitedForEdits = false
        for (index in grid.chunks.indices) {
            val chunk = grid.chunks[index]
            if (!sheet.isVisible(chunk, model, planes)) continue
            val cached = cache[index]
            if (cached.revision != chunk.revision) {
                val geometry = sheet.geometry(grid, chunk)
                if (cached.mesh != null && !waitedForEdits) {
                    renderer.waitIdle()
                    waitedForEdits = true
                }
                cached.mesh?.destroy()
                cached.mesh = geometry?.let(renderer::createMesh)
                cached.draw = cached.mesh?.let { RenderDrawCommand(it, requireNotNull(material), model, transparent = true) }
                cached.revision = chunk.revision
            }
            cached.draw?.let {
                it.model = model
                it.extraUniformFloats = extra
                it.sortOrder = sortOrder
                out.add(it)
            }
        }
    }

    /** Idempotent teardown, including chunks currently outside the view. */
    fun destroy() {
        if (material != null) renderer.waitIdle()
        cache.forEach {
            it.mesh?.destroy()
            it.mesh = null
            it.draw = null
            it.revision = -1
        }
        material?.destroy()
        material = null
        atlas = null
    }

    private class CachedChunk {
        var revision = -1L
        var mesh: Mesh? = null
        var draw: RenderDrawCommand? = null
    }
}
