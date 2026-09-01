/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.navigation.grid

/**
 * The walkability a search reads, addressed in world-wide sample coordinates.
 *
 * One baked tile and a streamed set of them differ only in this lookup: where a single tile
 * answers from its own bitset, a streamed set finds the owning tile first and answers from that.
 * Everything above — A*, the corner rule, line-of-sight smoothing — is identical, so it is written
 * once against this interface rather than twice against two grids.
 *
 * Sample `(x, z)` sits at world `(originX + x * sampleSize, _, originZ + z * sampleSize)`, and coordinates may be
 * negative: a streamed world has cells on both sides of its origin.
 *
 * Off-field is walkable-false rather than an error. A search that walks off the resident set must
 * stop there, and a caller asking about a sample nobody has baked is asking a legitimate question
 * with the answer "you cannot stand there".
 */
internal interface NavField {
    /** Metres between adjacent samples. */
    val sampleSize: Float

    /** World X of sample column 0. Zero for a field whose own coordinates are world coordinates. */
    val originX: Float get() = 0f

    /** World Z of sample row 0. */
    val originZ: Float get() = 0f

    /** Whether an agent can stand at sample ([x], [z]); false anywhere the field does not cover. */
    fun isWalkable(x: Int, z: Int): Boolean
}

/** One tile, placed where it was baked — the field a non-streamed [NavGrid] searches. */
internal class TileField(private val tile: NavGridTile) : NavField {
    override val sampleSize: Float get() = tile.cellSize
    override val originX: Float get() = tile.originX
    override val originZ: Float get() = tile.originZ

    override fun isWalkable(x: Int, z: Int): Boolean =
        x in 0 until tile.width && z in 0 until tile.depth && tile.isWalkable(x, z)
}
