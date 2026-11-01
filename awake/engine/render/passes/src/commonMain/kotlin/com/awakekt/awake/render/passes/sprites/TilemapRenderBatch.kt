/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.sprites

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Plane
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.SpriteExtraUniformLayout
import com.awakekt.awake.render.passes.uniforms.SpriteFields
import com.awakekt.awake.render.passes.uniforms.SpriteUniformLayout
import com.awakekt.awake.render.pipeline.createImageMaterial
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.UniformWriter
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.render.texture.asGpuImageSource
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
 * @param texture The atlas's name, if it has one, for the log line that reports an atlas that does not divide.
 * @param undivided The atlases already logged as not dividing, by [texture] name. Layers given the same
 * one log an atlas once between them; by default each layer logs its own. An atlas without a name is
 * logged once by each layer.
 * @param resolveTexture Supplies decoded pixels on first collection; null while they are not
 * available, such as a file still loading or one that failed to. The layer then draws nothing, and
 * the pixels are asked for again on the next collection. An atlas that does not divide into
 * [columns] by [rows] cells of whole pixels, an authoring mistake a file-backed atlas first shows at
 * run time, is logged once for its name (see [undivided]), and the layer draws nothing and asks no
 * more until it is [destroy]ed.
 * @param resolveImage Supplies the atlas as a decoded image, asked before [resolveTexture], with the
 * same null meaning: uploaded with `createImageMaterial`, so a browser's WebGPU copies it on the GPU
 * (awakekt/awake#1184).
 */
@Suppress("LongParameterList") // A layer's grid, cell layout, name, shared log and two texture sources.
class TilemapRenderBatch(
    private val renderer: Renderer,
    private val grid: TilemapGrid,
    private val columns: Int,
    private val rows: Int,
    private val pixelsPerUnit: Float,
    private val texture: String? = null,
    private val undivided: UndividedTextures = UndividedTextures(),
    private val resolveTexture: () -> TextureAsset?,
    private val resolveImage: () -> GpuImageSource?,
) {
    /** A layer whose atlas is only [resolveTexture]'s CPU pixels. */
    constructor(
        renderer: Renderer,
        grid: TilemapGrid,
        columns: Int,
        rows: Int,
        pixelsPerUnit: Float,
        texture: String? = null,
        undivided: UndividedTextures = UndividedTextures(),
        resolveTexture: () -> TextureAsset?,
    ) : this(renderer, grid, columns, rows, pixelsPerUnit, texture, undivided, resolveTexture, { null })

    private var atlas: TilemapAtlas? = null
    private var material: Material? = null
    private val cache = Array(grid.chunks.size) { CachedChunk() }
    private var lastTint: Color? = null
    private var extra = FloatArray(0)

    // Whether the atlas did not divide into its cells; the layer then draws nothing until it is destroyed.
    private var atlasUndivided = false

    /**
     * Appends borrowed requests to [out]. Unchanged chunks reuse meshes and requests; hidden
     * chunks are not uploaded. Requests remain valid until this batch's next collection. [sortOrder]
     * orders the layer's chunks among other transparent draws within their [sortLayer].
     */
    fun collect(
        model: Mat4,
        tint: Color,
        sortOrder: Int,
        planes: List<Plane>,
        out: MutableList<RenderDrawCommand>,
        sortLayer: Int = 0,
    ) {
        if (atlasUndivided) return
        val sheet = atlas ?: upload() ?: return
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
                it.sortLayer = sortLayer
                out.add(it)
            }
        }
    }

    /**
     * The atlas, uploaded with its material and kept, or null when its pixels are not available yet or
     * do not divide into its cells.
     */
    private fun upload(): TilemapAtlas? {
        val image = (resolveImage() ?: resolveTexture()?.also(::requireSingleImage)?.asGpuImageSource())?.takeIf(::fitsCells) ?: return null
        // The atlas checks the image's cells before the material is made, so a bad image leaves nothing to free.
        return TilemapAtlas(image.width, image.height, columns, rows, pixelsPerUnit).also {
            material = renderer.createImageMaterial(image, TextureFiltering.Nearest, SpriteUniformLayout.total)
            atlas = it
        }
    }

    private fun requireSingleImage(image: TextureAsset) = require(image.layerCount == 1 && !image.isCubemap) { "Tile atlas must be a single 2D image." }

    /** Whether [image] divides into this layer's cells; one that does not is logged once for its name, and not asked for again. */
    private fun fitsCells(image: GpuImageSource): Boolean {
        atlasUndivided = !dividesIntoCells(image.width, image.height, columns, rows)
        if (atlasUndivided && (texture == null || undivided.firstReport(texture))) {
            log.error {
                "Tile atlas${texture?.let { " '$it'" }.orEmpty()} is ${image.width}x${image.height} pixels, which cannot be " +
                    "divided into ${columns}x$rows equal cells. Its tilemap layer is not drawn."
            }
        }
        return !atlasUndivided
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
        atlasUndivided = false
    }

    private class CachedChunk {
        var revision = -1L
        var mesh: Mesh? = null
        var draw: RenderDrawCommand? = null
    }
}

private val log = Logger("sprites")
