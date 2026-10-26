/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity.textclip

import java.awt.image.BufferedImage
import kotlin.math.ceil

/** A rectangle in frame pixels. */
internal data class Rect(val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * One frame as drawn ([render]) and, when there is something to compare against, as it would draw
 * with no clip or layer in its way ([reference]). A case with no reference is measured on the edge
 * of its glyph [quads] instead: the distance-field fringe that ought to fade out inside the quad.
 */
internal class Captured(
    val render: BufferedImage,
    val reference: BufferedImage?,
    val clips: List<Rect> = emptyList(),
    val layers: List<Rect> = emptyList(),
    val quads: List<Rect> = emptyList(),
) {
    val metric: String get() = if (reference != null) "lost" else "edge"

    /** Pixels whose ink the render lost against the reference, or the quad-edge ink with no reference. */
    fun count(): Int = if (reference != null) Ink.lost(reference, render) else Ink.onQuadEdge(render, quads)

    /** Pixels whose ink the render added against the reference. */
    fun extra(): Int = if (reference != null) Ink.extra(reference, render) else 0

    fun geometryJson(): String {
        fun list(rects: List<Rect>) = rects.joinToString(",", "[", "]") { "[${it.x},${it.y},${it.w},${it.h}]" }
        return """{"clips":${list(clips)},"layers":${list(layers)},"quads":${list(quads)}}"""
    }
}

/**
 * Ink is `255 - red` of a black-on-white frame. A pixel loses ink when the reference has at least
 * [THRESHOLD]/255 more than the render; the same bar tells a quad-edge pixel from rounding. The
 * composer in `tools/fonts-tooling/text-evidence` recounts from the PNGs and refuses a mismatch, so
 * keep the two in step.
 */
internal object Ink {
    const val THRESHOLD = 8

    fun at(image: BufferedImage, x: Int, y: Int): Int = 255 - ((image.getRGB(x, y) shr RED_SHIFT) and CHANNEL)

    fun lost(reference: BufferedImage, render: BufferedImage): Int = difference(reference, render) { it >= THRESHOLD }

    fun extra(reference: BufferedImage, render: BufferedImage): Int = difference(reference, render) { -it >= THRESHOLD }

    private inline fun difference(reference: BufferedImage, render: BufferedImage, counts: (Int) -> Boolean): Int {
        require(reference.width == render.width && reference.height == render.height) { "a reference and its render differ in size" }
        var n = 0
        for (y in 0 until render.height) {
            for (x in 0 until render.width) {
                if (counts(at(reference, x, y) - at(render, x, y))) n++
            }
        }
        return n
    }

    /** Pixels on the outermost ring of each quad (centres inside it) that carry ink. */
    fun onQuadEdge(render: BufferedImage, quads: List<Rect>): Int {
        var n = 0
        for (q in quads) {
            val x0 = ceil(q.x - HALF).toInt()
            val x1 = ceil(q.x + q.w - HALF).toInt() - 1
            val y0 = ceil(q.y - HALF).toInt()
            val y1 = ceil(q.y + q.h - HALF).toInt() - 1
            val ring = HashSet<Long>()
            for (x in x0..x1) {
                ring += key(x, y0)
                ring += key(x, y1)
            }
            for (y in y0..y1) {
                ring += key(x0, y)
                ring += key(x1, y)
            }
            for (k in ring) {
                val x = (k shr KEY_SHIFT).toInt()
                val y = (k and KEY_MASK).toInt()
                if (x in 0 until render.width && y in 0 until render.height && at(render, x, y) >= THRESHOLD) n++
            }
        }
        return n
    }

    private fun key(x: Int, y: Int): Long = (x.toLong() shl KEY_SHIFT) or (y.toLong() and KEY_MASK)

    private const val RED_SHIFT = 16
    private const val CHANNEL = 0xFF
    private const val HALF = 0.5f
    private const val KEY_SHIFT = 32
    private const val KEY_MASK = 0xFFFFFFFFL
}
