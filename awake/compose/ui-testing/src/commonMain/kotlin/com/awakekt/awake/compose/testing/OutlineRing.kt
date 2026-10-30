/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.render.capture.PixelMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * How continuous a text outline is around a glyph (#1061), measured along rays cast in from outside it to its fill.
 * Each ray sums how much darker than the ground it gets on its way, in pixels of full outline: an antialiased ring
 * straddling two pixels counts as the share of each it covers. A ray that reaches the fill with almost none found one of
 * [gaps]; [thinnest] and [thickest] are the outline the other rays crossed. A ray crosses a ring at least as thick as
 * the ring is, less the fill's own antialiased edge over it, so a thinnest well under the outline's width is a scallop,
 * as a thick outline stamped in eight steps shows at its diagonals.
 *
 * @property rays The rays that reached the glyph's fill.
 * @property gaps The rays that reached the fill across less than [GAP_PX] of outline.
 * @property thinnest The least outline any other ray crossed, in pixels.
 * @property thickest The most outline any ray crossed, in pixels.
 */
data class OutlineRing(val rays: Int, val gaps: Int, val thinnest: Float, val thickest: Float)

/**
 * [OutlineRing] for the glyph inside [left], [top], [right], [bottom], drawn with a light fill and a black outline over
 * a mid-grey ground, the frame's first pixel. A ray stops at the first pixel brighter than the ground by [BAND], the
 * fill; one that grazes the outline and never reaches the fill counts for nothing.
 */
fun PixelMap.outlineRing(left: Int, top: Int, right: Int, bottom: Int, rays: Int = DEFAULT_RAYS): OutlineRing {
    val ground = sample(0, 0).r
    val centerX = (left + right) / 2f
    val centerY = (top + bottom) / 2f
    val reach = hypot((right - left).toFloat(), (bottom - top).toFloat()) / 2f
    var met = 0
    var gaps = 0
    var thinnest = Float.POSITIVE_INFINITY
    var thickest = 0f
    for (ray in 0 until rays) {
        val angle = 2.0 * PI * ray / rays
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        var distance = reach
        var outline = 0f
        var reachedFill = false
        while (distance > 0f && !reachedFill) {
            val value = redAt(centerX + dx * distance, centerY + dy * distance)
            reachedFill = value > ground + BAND
            if (value < ground) outline += (ground - value) / ground * STEP
            distance -= STEP
        }
        if (!reachedFill) continue
        met++
        if (outline < GAP_PX) {
            gaps++
        } else {
            thinnest = min(thinnest, outline)
        }
        thickest = max(thickest, outline)
    }
    return OutlineRing(met, gaps, if (thinnest.isFinite()) thinnest else 0f, thickest)
}

/**
 * The red channel at ([x], [y]), bilinear between pixel centres: a nearest pixel would let a ray slip diagonally between
 * two pixels of a one-pixel ring that touch only at their corners.
 */
private fun PixelMap.redAt(x: Float, y: Float): Float {
    val left = floor(x - 0.5f).toInt()
    val top = floor(y - 0.5f).toInt()
    val tx = x - 0.5f - left
    val ty = y - 0.5f - top
    val upper = sample(left, top).r * (1f - tx) + sample(left + 1, top).r * tx
    val lower = sample(left, top + 1).r * (1f - tx) + sample(left + 1, top + 1).r * tx
    return upper * (1f - ty) + lower * ty
}

private const val DEFAULT_RAYS = 180
private const val STEP = 0.25f

/** How much brighter than the ground a pixel is before it counts as the fill. */
private const val BAND = 24

/** The least outline, in pixels, a ray may cross on its way to the fill before it counts as a gap. */
private const val GAP_PX = 0.15f
