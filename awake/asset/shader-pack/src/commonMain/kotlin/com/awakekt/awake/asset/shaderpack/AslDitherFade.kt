/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslFragmentBuilder
import com.awakekt.awake.asset.shaderdsl.abs
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.floor
import com.awakekt.awake.asset.shaderdsl.fract
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.lt
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.y

/**
 * A dither fade: discards the share [fade] of an opaque draw's pixels in a 4 x 4 ordered (Bayer)
 * pattern fixed to the screen, so the draw reads as see-through while it stays in the opaque pass,
 * needing no depth sorting and leaving its shadow as it was. [fade] is `1 - opacity`, the packer's
 * `UniformFields.Exposure.y`: at `0`, which every draw that sets no fade has, no pixel is discarded
 * and the draw renders exactly as without it.
 *
 * Call it at the top level of the fragment block, after every derivative the shader takes.
 */
internal fun AslFragmentBuilder.ditherFade(fade: AslExpr) {
    val pixel = position()
    val column = floor(pixel.x)
    val row = floor(pixel.y)
    val x0 = bit(column)
    val y0 = bit(row)
    val x1 = bit(floor(column * HALF.lit))
    val y1 = bit(floor(row * HALF.lit))
    // The 4 x 4 Bayer rank from the bits of the column and row: 0..15, each once per tile.
    val rank = let(
        "ditherRank",
        abs(x0 - y0) * 8f.lit + y0 * 4f.lit + abs(x1 - y1) * 2f.lit + y1,
    )
    discardIf((rank + HALF.lit) / TILE.lit lt fade)
}

/** The lowest bit of a whole number held as a float: `0` or `1`. */
private fun bit(whole: AslExpr): AslExpr = fract(whole * HALF.lit) * 2f.lit

private const val HALF = 0.5f
private const val TILE = 16f
