/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A finite orthogonal tile layer at its node's transform. Tiles count from the top left in row
 * order, and -1 is empty. Map coordinates start at the node's top left, +X right and -Y down.
 * @property texture Named atlas image.
 * @property width Map columns.
 * @property height Map rows.
 * @property tiles Atlas frames in row order.
 * @property columns Atlas columns.
 * @property rows Atlas rows.
 * @property pixelsPerUnit Pixels per local world unit.
 * @property chunkSize Cells per chunk edge.
 * @property tint Whole-layer straight-alpha multiplier.
 * @property sortOrder Transparent paint order shared with sprites.
 */
@Serializable
@SerialName("tilemap")
data class SceneTilemap(
    val texture: String,
    @PropertyRange(min = 1.0) val width: Int,
    @PropertyRange(min = 1.0) val height: Int,
    val tiles: List<Int>,
    @PropertyRange(min = 1.0) val columns: Int = 1,
    @PropertyRange(min = 1.0) val rows: Int = 1,
    @PropertyRange(min = 0.0, exclusiveMin = true) val pixelsPerUnit: Float = 100f,
    @PropertyRange(min = 1.0) val chunkSize: Int = 16,
    val tint: SceneColor = SceneColor(),
    val sortOrder: Int = 0,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        fun issue(message: String) {
            add(SceneValidationIssue(path, "tilemap.$message"))
        }
        if (texture.isBlank()) issue("texture must name an image")
        if (width <= 0 || height <= 0 || width.toLong() * height != tiles.size.toLong()) issue("dimensions must match tiles")
        val frames = columns.toLong() * rows
        if (columns <= 0 || rows <= 0 || frames > Int.MAX_VALUE) issue("atlas dimensions must be positive and fit in an Int")
        if (tiles.any { it < -1 || it >= frames }) issue("tiles must be -1 or a frame inside the atlas")
        if (!pixelsPerUnit.isFinite() || pixelsPerUnit <= 0f) issue("pixelsPerUnit must be finite and positive")
        if (chunkSize <= 0) issue("chunkSize must be positive")
    }
}
