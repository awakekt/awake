/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics.drawscope

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.FilterQuality
import com.awakekt.awake.core.graphics2d.TextureRegion
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.round

/**
 * [DrawScope.drawTextureRepeated] one repeat at a time, for a [DrawScope] that does not draw a
 * repeating quad of its own: one [DrawScope.drawTexture] per repeat, each on whole pixels, the last
 * one cut short and showing only that much of [region].
 */
@Suppress("LongParameterList")
internal fun DrawScope.drawTextureTiles(
    material: Any,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    region: TextureRegion,
    repeatX: Float,
    repeatY: Float,
    tint: Color,
    filterQuality: FilterQuality,
) {
    if (width <= 0f || height <= 0f || repeatX <= 0f || repeatY <= 0f) return
    val tileWidth = width / repeatX
    val tileHeight = height / repeatY
    val regionWidth = region.u1 - region.u0
    val regionHeight = region.v1 - region.v0
    val x1 = x + width
    val y1 = y + height
    for (row in 0 until tiles(height, tileHeight)) {
        val top = y + row * tileHeight
        val bottom = min(top + tileHeight, y1)
        for (column in 0 until tiles(width, tileWidth)) {
            val left = x + column * tileWidth
            val right = min(left + tileWidth, x1)
            val tile = TextureRegion(
                region.u0,
                region.v0,
                region.u0 + regionWidth * (right - left) / tileWidth,
                region.v0 + regionHeight * (bottom - top) / tileHeight,
            )
            val tileX = round(left)
            val tileY = round(top)
            val w = round(right) - tileX
            val h = round(bottom) - tileY
            if (w > 0f && h > 0f) drawTexture(material, tileX, tileY, w, h, tile, tint, filterQuality)
        }
    }
}

/** How many tiles of [tile] cover [span], the last one cut short; rounding left over is not a tile. */
private fun tiles(span: Float, tile: Float): Int = ceil((span - MIN_TILE) / tile).toInt()

/** Less than this of a span is rounding left over from adding tiles, not a tile. */
private const val MIN_TILE = 0.01f
