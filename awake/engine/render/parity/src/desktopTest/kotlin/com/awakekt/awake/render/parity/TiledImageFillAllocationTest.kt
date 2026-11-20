/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.ImageFill
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.graphics2d.FilterQuality
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.render.passes2d.StagedDrawRun
import com.awakekt.awake.render.passes2d.UiRunCoalescer
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A tiled frame allocates no more per frame the longer its edges are (#1795): its 1-px striped
 * border repeats as one piece per edge, not a piece per stripe. One frame is what the engine does
 * with it: compose and draw, swap the bitmap for its material, and stage the runs.
 */
class TiledImageFillAllocationTest {

    @Test
    fun aTiledFramesBytesPerFrameDoNotGrowWithItsTiles() {
        val tiled = SIZES.associateWith { bytesPerFrame(it, repeat = true) }
        val stretched = SIZES.associateWith { bytesPerFrame(it, repeat = false) }
        SIZES.forEach { size ->
            println("frame $size dp, ${tilesAt(size)} tiles: tiled ${tiled.getValue(size)} B/frame, stretched ${stretched.getValue(size)} B/frame")
        }

        val growth = tiled.getValue(SIZES.last()) - tiled.getValue(SIZES.first())
        assertTrue(growth <= FLAT_BYTES, "tiled bytes grew $growth B from ${tilesAt(SIZES.first())} to ${tilesAt(SIZES.last())} tiles: $tiled")
        val overStretched = tiled.getValue(SIZES.last()) - stretched.getValue(SIZES.last())
        assertTrue(overStretched <= FLAT_BYTES, "tiling costs $overStretched B/frame over stretching: tiled $tiled, stretched $stretched")
    }

    private fun bytesPerFrame(size: Int, repeat: Boolean): Long {
        val fill = ImageFill(SHEET, sliceLeft = 3, sliceTop = 3, sliceRight = 3, sliceBottom = 3, repeatEdges = repeat, filterQuality = FilterQuality.None, hollow = true)
        val host = ComposeHost()
        val material = Any()
        var staged = 0
        fun frame() {
            val primitives = host.frame(FrameInput(size, size * 3 / 4)) {
                Box(Modifier.size(size.dp, (size * 3 / 4).dp).background(fill))
            }.primitives
            // The engine host swaps each drawn bitmap for its material, a copy per textured primitive.
            val bound = ArrayList<UiDrawPrimitive>(primitives.size)
            for (i in primitives.indices) {
                val primitive = primitives[i]
                bound += if (primitive is UiDrawPrimitive.Texture) primitive.copy(material = material) else primitive
            }
            staged += UiRunCoalescer.coalesce(bound).count { it is StagedDrawRun.TextureRun }
        }
        repeat(WARMUP) { frame() }
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val before = threads.currentThreadAllocatedBytes
        repeat(FRAMES) { frame() }
        val bytes = (threads.currentThreadAllocatedBytes - before) / FRAMES
        check(staged > 0) { "the frame staged no texture run" }
        return bytes
    }

    /** Tiles, two stripes each, along the frame's four edges at one source pixel per dp. */
    private fun tilesAt(size: Int): Int = (size - 6) + (size * 3 / 4 - 6)

    private companion object {
        val SIZES = listOf(120, 240, 480)
        const val WARMUP = 50
        const val FRAMES = 200

        /** What "flat" allows: well under one tile's former cost per stripe, hundreds of stripes. */
        const val FLAT_BYTES = 1024L

        /** 8 x 8: 3-px corners, a 2-px edge of 1-px black and white stripes between them, a centre left out. */
        val SHEET: ImageBitmap = run {
            val n = 8
            val pixels = ByteArray(n * n * 4)
            for (y in 0 until n) {
                for (x in 0 until n) {
                    val corner = (x < 3 || x > 4) && (y < 3 || y > 4)
                    val value = when {
                        corner -> 0x80
                        (x + y) % 2 == 0 -> 0
                        else -> 0xFF
                    }
                    val at = (y * n + x) * 4
                    pixels[at] = value.toByte()
                    pixels[at + 1] = value.toByte()
                    pixels[at + 2] = value.toByte()
                    pixels[at + 3] = -1
                }
            }
            ImageBitmap(n, n, pixels)
        }
    }
}
