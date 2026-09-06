/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.world

import kotlin.math.floor

/**
 * Integer coordinate identifying a 2D spatial grid cell $(X, Z)$ in world partition space.
 */
data class WorldCellCoord(
    val x: Int,
    val z: Int,
) {
    override fun toString(): String = "[$x, $z]"

    companion object {
        /**
         * Resolves the cell coordinate containing the world $(X, Z)$ position for a given [cellSize].
         */
        fun fromWorldPosition(worldX: Float, worldZ: Float, cellSize: Float): WorldCellCoord {
            val cx = floor(worldX / cellSize).toInt()
            val cz = floor(worldZ / cellSize).toInt()
            return WorldCellCoord(cx, cz)
        }
    }
}
