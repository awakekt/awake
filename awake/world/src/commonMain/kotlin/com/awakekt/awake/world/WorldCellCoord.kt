/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.world

import kotlin.math.floor

/**
 * Integer coordinate identifying a 2D spatial grid cell $(X, Z)$ in world partition space.
 *
 * @property x Grid cell column index along the world X axis.
 * @property z Grid cell row index along the world Z axis.
 */
data class WorldCellCoord(
    val x: Int,
    val z: Int,
) {
    override fun toString(): String = "[$x, $z]"

    /**
     * Factory methods for creating [WorldCellCoord] instances.
     */
    companion object {
        /**
         * Resolves the cell coordinate containing the world $(X, Z)$ position for a given [cellSize].
         *
         * @param worldX World X coordinate in metres.
         * @param worldZ World Z coordinate in metres.
         * @param cellSize Length of a square cell edge in metres.
         * @return The [WorldCellCoord] containing the specified position.
         */
        fun fromWorldPosition(worldX: Float, worldZ: Float, cellSize: Float): WorldCellCoord {
            val cx = floor(worldX / cellSize).toInt()
            val cz = floor(worldZ / cellSize).toInt()
            return WorldCellCoord(cx, cz)
        }
    }
}
