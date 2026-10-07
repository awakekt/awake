/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.navigation

import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.navigation.grid.NavGrid
import com.awakekt.awake.navigation.grid.NavGridTile
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where the scene's agents can walk: a grid of cells, authored with the scene.
 *
 * The `patrol`, `chase` and `flee` behaviours ask for routes, and something has to answer. A scene
 * with one of them and a `navigation` component is played with the answering system already running,
 * with nothing for the host to wire.
 *
 * [rows] is one string per row of cells, north to south along z, and each character is one cell
 * across x: `.` is a cell an agent can stand on and `#` is one it cannot. Every row is the same
 * length. A small level is easy to write by hand and to read in a diff:
 * ```
 * "rows": [ "......", ".####.", "......" ]
 * ```
 * [cellSize] is the world size of one cell; cell (0, 0) is centred on ([originX], [originZ]). A tool
 * that bakes a grid from terrain or colliders writes the same component, so the runtime has one way
 * in. Put it on any node; only the first one in a scene is used.
 */
@Serializable
@SerialName("navigation")
data class SceneNavigation(
    val rows: List<String> = emptyList(),
    @PropertyRange(min = 0.0, exclusiveMin = true) val cellSize: Float = DEFAULT_CELL_SIZE,
    val originX: Float = 0f,
    val originZ: Float = 0f,
) : SceneComponent {

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (!(cellSize > 0f && cellSize.isFinite())) {
            add(SceneValidationIssue(path, "navigation.cellSize must be a positive number"))
        }
        if (rows.isEmpty()) add(SceneValidationIssue(path, "navigation.rows must have at least one row"))
        val width = rows.firstOrNull()?.length ?: 0
        rows.forEachIndexed { index, row ->
            if (row.length != width) {
                add(SceneValidationIssue(path, "navigation.rows[$index] is ${row.length} cells wide, but the first row is $width"))
            }
            val stray = row.firstOrNull { it != WALKABLE && it != BLOCKED }
            if (stray != null) {
                add(SceneValidationIssue(path, "navigation.rows[$index] has '$stray'; use '$WALKABLE' for walkable and '$BLOCKED' for blocked"))
            }
        }
        if (width == 0 && rows.isNotEmpty()) add(SceneValidationIssue(path, "navigation.rows must not be empty strings"))
    }

    /**
     * The grid agents route over.
     *
     * @throws IllegalArgumentException when [validate] would report a problem, naming the first.
     */
    fun toGrid(): NavGrid {
        validate("navigation").firstOrNull()?.let { throw IllegalArgumentException(it.message) }
        val width = rows.first().length
        val bits = LongArray((width * rows.size + NavGridTile.LONG_MASK) ushr NavGridTile.LONG_SHIFT)
        for (z in rows.indices) {
            for (x in 0 until width) {
                if (rows[z][x] != WALKABLE) continue
                val bit = z * width + x
                bits[bit ushr NavGridTile.LONG_SHIFT] = bits[bit ushr NavGridTile.LONG_SHIFT] or (1L shl (bit and NavGridTile.LONG_MASK))
            }
        }
        return NavGrid(NavGridTile(width, rows.size, cellSize, bits, originX, originZ))
    }

    /** The defaults, and the characters [rows] is written in. */
    companion object {
        /** A cell of a world unit: coarse enough to be cheap, fine enough for a character-sized agent. */
        const val DEFAULT_CELL_SIZE: Float = 1f

        /** A cell an agent can stand on. */
        const val WALKABLE: Char = '.'

        /** A cell an agent cannot stand on. */
        const val BLOCKED: Char = '#'
    }
}
