/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.tilemap

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Plane
import kotlin.math.abs

/**
 * Equal pixel cells in a decoded bottom-up texture. Frame indices run across rows from the top.
 * @property imageWidth Image width in pixels.
 * @property imageHeight Image height in pixels.
 * @property columns Atlas columns.
 * @property rows Atlas rows.
 * @property pixelsPerUnit Pixels per local world unit.
 */
class TilemapAtlas(
    val imageWidth: Int,
    val imageHeight: Int,
    val columns: Int,
    val rows: Int,
    val pixelsPerUnit: Float = 100f,
) {
    init {
        require(columns > 0 && rows > 0 && columns.toLong() * rows <= Int.MAX_VALUE) { "Invalid tile atlas grid." }
        require(imageWidth > 0 && imageHeight > 0 && imageWidth % columns == 0 && imageHeight % rows == 0) { "Tile atlas must divide into whole pixel cells." }
        require(pixelsPerUnit.isFinite() && pixelsPerUnit > 0f) { "Tile pixelsPerUnit must be finite and positive." }
    }

    /** Cell width in local world units. */
    val tileWidth: Float = imageWidth.toFloat() / columns / pixelsPerUnit

    /** Cell height in local world units. */
    val tileHeight: Float = imageHeight.toFloat() / rows / pixelsPerUnit
    init {
        require(tileWidth.isFinite() && tileHeight.isFinite() && tileWidth > 0f && tileHeight > 0f) { "Tile dimensions must be finite and positive." }
    }

    /** Builds a single XY mesh, or null for an empty chunk. The local origin is the grid's top left. */
    fun geometry(grid: TilemapGrid, chunk: TilemapChunk): MeshGeometry? {
        require(chunk in grid.chunks) { "Chunk belongs to another tilemap." }
        require((grid.width * tileWidth).isFinite() && (grid.height * tileHeight).isFinite()) { "Tilemap dimensions must be finite." }
        var count = 0
        for (y in chunk.y until chunk.y + chunk.height) {
            for (x in chunk.x until chunk.x + chunk.width) {
                if (grid[x, y] != TilemapGrid.EMPTY) count++
            }
        }
        if (count == 0) return null
        require(count.toLong() * FLOATS_PER_TILE <= Int.MAX_VALUE) { "Tile chunk is too large." }
        val vertices = FloatArray(count * FLOATS_PER_TILE)
        val indices = IntArray(count * INDICES_PER_TILE)
        var tile = 0
        for (y in chunk.y until chunk.y + chunk.height) {
            for (x in chunk.x until chunk.x + chunk.width) {
                val frame = grid[x, y]
                if (frame == TilemapGrid.EMPTY) continue
                require(frame.toLong() < columns.toLong() * rows) { "Tile frame $frame is outside the atlas." }
                val left = x * tileWidth
                val right = (x + 1) * tileWidth
                val top = -y * tileHeight
                val bottom = -(y + 1) * tileHeight
                val u0 = (frame % columns).toFloat() / columns
                val u1 = (frame % columns + 1).toFloat() / columns
                val v0 = (rows - 1 - frame / columns).toFloat() / rows
                val v1 = (rows - frame / columns).toFloat() / rows
                val offset = tile * FLOATS_PER_TILE
                vertices.putVertex(offset, left, bottom, u0, v0)
                vertices.putVertex(offset + FLOATS_PER_VERTEX, right, bottom, u1, v0)
                vertices.putVertex(offset + FLOATS_PER_VERTEX * 2, right, top, u1, v1)
                vertices.putVertex(offset + FLOATS_PER_VERTEX * 3, left, top, u0, v1)
                val base = tile * VERTICES_PER_TILE
                val i = tile * INDICES_PER_TILE
                indices[i] = base
                indices[i + 1] = base + 1
                indices[i + 2] = base + 2
                indices[i + 3] = base + 2
                indices[i + 4] = base + 3
                indices[i + 5] = base
                tile++
            }
        }
        return MeshGeometry(vertices, indices, VertexFormat.PositionUv)
    }

    /** Conservative frustum test for the transformed chunk rectangle, with no temporary vectors. */
    fun isVisible(chunk: TilemapChunk, model: Mat4, planes: List<Plane>): Boolean {
        val cx = (chunk.x + chunk.width * 0.5f) * tileWidth
        val cy = -(chunk.y + chunk.height * 0.5f) * tileHeight
        val ex = chunk.width * tileWidth * 0.5f
        val ey = chunk.height * tileHeight * 0.5f
        val wx = model.m00 * cx + model.m01 * cy + model.m03
        val wy = model.m10 * cx + model.m11 * cy + model.m13
        val wz = model.m20 * cx + model.m21 * cy + model.m23
        for (plane in planes) {
            val n = plane.normal
            val radius = abs(n.x * model.m00 + n.y * model.m10 + n.z * model.m20) * ex +
                abs(n.x * model.m01 + n.y * model.m11 + n.z * model.m21) * ey
            if (n.x * wx + n.y * wy + n.z * wz + plane.distance < -radius) return false
        }
        return true
    }

    private fun FloatArray.putVertex(offset: Int, x: Float, y: Float, u: Float, v: Float) {
        this[offset] = x
        this[offset + 1] = y
        this[offset + 2] = 0f
        this[offset + 3] = u
        this[offset + 4] = v
    }

    private companion object {
        private const val FLOATS_PER_VERTEX = 5
        private const val VERTICES_PER_TILE = 4
        private const val FLOATS_PER_TILE = FLOATS_PER_VERTEX * VERTICES_PER_TILE
        private const val INDICES_PER_TILE = 6
    }
}
