/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.testing

import io.github.awakelab.awake.core.graphics2d.UiPrimitiveTransform
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

internal inline fun rasterizeTrianglePixels(
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float,
    cx: Float,
    cy: Float,
    clipX0: Float,
    clipY0: Float,
    clipX1: Float,
    clipY1: Float,
    width: Int,
    height: Int,
    pathClip: (Float, Float) -> Boolean,
    drawPixel: (x: Int, y: Int, w0: Float, w1: Float, w2: Float, area: Float) -> Unit,
) {
    val area = triangleEdge(ax, ay, bx, by, cx, cy)
    if (area == 0f) return
    val minX = max(min(ax, min(bx, cx)), clipX0).toInt().coerceIn(0, width)
    val minY = max(min(ay, min(by, cy)), clipY0).toInt().coerceIn(0, height)
    val maxX = ceil(min(max(ax, max(bx, cx)), clipX1)).toInt().coerceIn(0, width)
    val maxY = ceil(min(max(ay, max(by, cy)), clipY1)).toInt().coerceIn(0, height)
    var y = minY
    while (y < maxY) {
        var x = minX
        while (x < maxX) {
            val sampleX = x + 0.5f
            val sampleY = y + 0.5f
            val w0 = triangleEdge(ax, ay, bx, by, sampleX, sampleY)
            val w1 = triangleEdge(bx, by, cx, cy, sampleX, sampleY)
            val w2 = triangleEdge(cx, cy, ax, ay, sampleX, sampleY)
            if (pointIsInsideTriangle(w0, w1, w2) && pathClip(sampleX, sampleY)) {
                drawPixel(x, y, w0, w1, w2, area)
            }
            x += 1
        }
        y += 1
    }
}

internal fun triangleEdge(
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    px: Float,
    py: Float,
): Float = (px - x0) * (y1 - y0) - (py - y0) * (x1 - x0)

internal fun pointIsInsideTriangle(w0: Float, w1: Float, w2: Float): Boolean =
    (w0 >= 0f && w1 >= 0f && w2 >= 0f) || (w0 <= 0f && w1 <= 0f && w2 <= 0f)

internal inline fun withScaledRect(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    transform: UiPrimitiveTransform?,
    block: (x: Float, y: Float, w: Float, h: Float) -> Unit,
) {
    if (transform == null) {
        block(x, y, w, h)
    } else {
        block(
            transform.pivotX + (x - transform.pivotX) * transform.scaleX,
            transform.pivotY + (y - transform.pivotY) * transform.scaleY,
            w * transform.scaleX,
            h * transform.scaleY,
        )
    }
}
