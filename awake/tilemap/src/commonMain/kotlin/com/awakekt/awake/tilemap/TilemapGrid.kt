/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.tilemap

/**
 * A finite orthogonal grid. Rows run down from the top left; -1 means an empty cell.
 * The input array is copied. Edits invalidate only the containing chunk.
 *
 * @property width Columns of tiles.
 * @property height Rows of tiles.
 * @param tiles Initial frames in row order, copied on construction.
 * @property chunkSize Maximum cells on either edge of a chunk.
 */
class TilemapGrid(val width: Int, val height: Int, tiles: IntArray, val chunkSize: Int = 16) {
    private val cells: IntArray
    private val chunkColumns: Int

    /** Fixed chunk descriptors in row order, including partial edge chunks. */
    val chunks: List<TilemapChunk>

    init {
        require(width > 0 && height > 0 && width.toLong() * height == tiles.size.toLong()) { "Tilemap dimensions must match its cells." }
        require(chunkSize > 0) { "Tilemap chunkSize must be positive." }
        require(tiles.all { it >= EMPTY }) { "Tile values must be -1 or an atlas frame." }
        cells = tiles.copyOf()
        chunkColumns = (width - 1) / chunkSize + 1
        val chunkRows = (height - 1) / chunkSize + 1
        chunks = List(chunkColumns * chunkRows) { index ->
            val x = index % chunkColumns * chunkSize
            val y = index / chunkColumns * chunkSize
            TilemapChunk(x, y, minOf(chunkSize, width - x), minOf(chunkSize, height - y))
        }
    }

    /** Reads an atlas frame, or -1 for empty. Coordinates must be inside the grid. */
    operator fun get(x: Int, y: Int): Int = cells[index(x, y)]

    /** Changes a cell and advances its chunk revision only when the value differs. */
    operator fun set(x: Int, y: Int, frame: Int) {
        require(frame >= EMPTY) { "Tile values must be -1 or an atlas frame." }
        val index = index(x, y)
        if (cells[index] == frame) return
        cells[index] = frame
        chunks[y / chunkSize * chunkColumns + x / chunkSize].revision++
    }

    /** Returns a snapshot in row order, isolated from future edits. */
    fun copyTiles(): IntArray = cells.copyOf()

    private fun index(x: Int, y: Int): Int {
        require(x in 0 until width && y in 0 until height) { "Tile coordinate ($x, $y) is outside ${width}x$height." }
        return y * width + x
    }

    /** Empty cells contain no geometry. */
    companion object {
        const val EMPTY = -1
    }
}

/**
 * A grid-owned chunk in tile coordinates.
 * @property x First column.
 * @property y First row.
 * @property width Columns in this chunk.
 * @property height Rows in this chunk.
 */
class TilemapChunk internal constructor(val x: Int, val y: Int, val width: Int, val height: Int) {
    /** Advances whenever a cell in the chunk changes. */
    var revision: Long = 0
        internal set
}
