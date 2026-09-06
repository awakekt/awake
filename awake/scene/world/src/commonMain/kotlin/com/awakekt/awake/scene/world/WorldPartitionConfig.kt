/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.world

/**
 * Configuration parameters for distance-based spatial world cell streaming.
 *
 * @property cellSize Meter width and depth of each square spatial cell $(cx, cz)$.
 * @property loadingRadius Distance in meters from the camera within which cells are streamed in.
 * @property unloadRadius Distance in meters beyond which active cells are unloaded (hysteresis band).
 */
data class WorldPartitionConfig(
    val cellSize: Float = 512.0f,
    val loadingRadius: Float = 1024.0f,
    val unloadRadius: Float = 1536.0f,
) {
    init {
        require(cellSize > 0f && cellSize.isFinite()) { "cellSize must be a positive finite float; was $cellSize." }
        require(loadingRadius >= cellSize) { "loadingRadius must be >= cellSize ($cellSize); was $loadingRadius." }
        require(unloadRadius > loadingRadius) { "unloadRadius ($unloadRadius) must be > loadingRadius ($loadingRadius)." }
    }
}
