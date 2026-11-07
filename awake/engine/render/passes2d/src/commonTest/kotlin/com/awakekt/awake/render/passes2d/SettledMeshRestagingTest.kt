/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.ColoredTriangleMesh
import com.awakekt.awake.core.graphics2d.ColoredVertex
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.DrawPoint
import com.awakekt.awake.core.math2d.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #1408: dynamic UI meshes that have stopped changing must stop being restaged and re-uploaded.
 *
 * The scene is [PLATES] name-plate meshes, each its own span (a clip between them, as the text
 * between real name plates splits them), that move for [MOVING_FRAMES] frames and then stand still.
 * [CountingUploader] models both backends' `DynamicMesh.update`: an upload happens when it is forced
 * or the arrays are not the instances the slot last received, and each upload copies two arrays
 * (vertices and indices) into GPU memory -- on the web, before this fix, two `ArrayBuffer`s sliced
 * out of linear memory.
 *
 * Measured over the [WINDOW] frames after the plates settle, with 22 plates:
 * - before: **22 restaged runs, 22 uploads, 44 array copies per frame** -- every plate pooled forever,
 *   because a span position seen before was always pooled on a miss and a pooled span never stored.
 * - after: **0 restaged runs, 0 uploads, 0 copies per frame**; each plate is promoted once.
 *
 * Negative control: making [StableSpanTracker.shouldPromote] always return `false` measured the
 * before figures above in [settledMeshesStopRestagingAndUploading].
 */
class SettledMeshRestagingTest {

    private class CountingUploader : DrawMeshUploader<Int> {
        private val last = HashMap<Int, FloatArray>()
        var uploads = 0

        private fun record(slot: Int, vertices: FloatArray, forceUpload: Boolean): Int {
            if (forceUpload || last[slot] !== vertices) uploads += 1
            last[slot] = vertices
            return slot
        }

        override fun quadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray) = record(runIndex, vertices, false)
        override fun roundedQuadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray) = record(-1 - runIndex, vertices, false)
        override fun glyphMesh(runIndex: Int, vertices: FloatArray, indices: IntArray) = record(1 shl 24 or runIndex, vertices, false)
        override fun quadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray, forceUpload: Boolean) =
            record(runIndex, vertices, forceUpload)
    }

    /** Per-frame averages over the measured window. */
    private data class Window(val restagedRuns: Int, val uploads: Int, val copies: Int, val promotions: Int)

    private val white = Color(1f, 1f, 1f, 1f)
    private val plateMesh = ColoredTriangleMesh(
        listOf(DrawPoint(0f, 0f), DrawPoint(80f, 0f), DrawPoint(80f, 14f), DrawPoint(0f, 14f)).map { ColoredVertex(it, white) },
        intArrayOf(0, 1, 2, 2, 3, 0),
    )
    private val geometryKey = Any()

    private fun plate(index: Int, dx: Float) =
        DrawCommand.Mesh(plateMesh, offsetX = index * 90f + dx, offsetY = 40f).also { it.retainedGeometryKey = geometryKey }

    private fun scene(plates: Int, dx: (plate: Int) -> Float): List<DrawCommand> = buildList {
        repeat(plates) { i ->
            add(plate(i, dx(i)))
            add(DrawCommand.ClipPush(Rectangle(0f, 0f, 4000f, 4000f)))
        }
    }

    private fun measure(frames: Int, sceneAt: (frame: Int) -> List<DrawCommand>): Window {
        val cache = RetainedDrawRunCache()
        val pool = DynamicRunArrayPool()
        val uploader = CountingUploader()
        var restaged = 0
        var uploadsBefore = 0
        var promotionsBefore = 0
        for (frame in 0 until frames) {
            if (frame == frames - WINDOW) {
                uploadsBefore = uploader.uploads
                promotionsBefore = pool.stableSpans.promotions
            }
            val staged = DrawRunCoalescer.coalesce(sceneAt(frame), retained = cache, pool = pool)
            if (frame >= frames - WINDOW) restaged += staged.count { it.requiresUpload }
            uploadDrawRuns(staged, uploader)
        }
        val uploads = uploader.uploads - uploadsBefore
        return Window(restaged / WINDOW, uploads / WINDOW, 2 * uploads / WINDOW, pool.stableSpans.promotions - promotionsBefore)
    }

    @Test
    fun settledMeshesStopRestagingAndUploading() {
        val window = measure(MOVING_FRAMES + SETTLE_FRAMES + WINDOW) { frame ->
            scene(PLATES) { minOf(frame, MOVING_FRAMES).toFloat() }
        }
        println("PERF settledMeshes plates=$PLATES perFrame restaged=${window.restagedRuns} uploads=${window.uploads} copies=${window.copies}")
        assertEquals(Window(0, 0, 0, 0), window, "settled plates must hit the retained cache: nothing restaged or copied")
    }

    /** One plate keeps walking; the other plates settle and stop costing anything. */
    @Test
    fun onlyTheMovingMeshKeepsUploading() {
        val window = measure(MOVING_FRAMES + SETTLE_FRAMES + WINDOW) { frame ->
            scene(PLATES) { i -> if (i == 0) frame.toFloat() else minOf(frame, MOVING_FRAMES).toFloat() }
        }
        assertEquals(Window(1, 1, 2, 0), window, "only the walking plate restages and uploads")
    }

    /** A span whose content alternates never repeats on consecutive frames, so it is never promoted. */
    @Test
    fun aBlinkingMeshStaysPooledWithoutStoringEveryFrame() {
        // Frame 0's first sight is stored; from then on the plate alternates between two new places.
        val window = measure(MOVING_FRAMES + WINDOW) { frame -> scene(1) { if (frame == 0) 0f else 1f + frame % 2 } }
        assertEquals(Window(1, 1, 2, 0), window)
    }

    /**
     * More cacheable spans than the cache holds evict each other before they are reached again, so
     * promoting them would store, and allocate, every frame without ever hitting. They stay pooled.
     *
     * Negative control: dropping the capacity check from [StableSpanTracker.shouldPromote] measured
     * 396 promotions over the 10-frame window here (300 plates), a store and its allocation for
     * about 40 spans every frame, none of which ever hit.
     */
    @Test
    fun spansBeyondTheCacheCapacityAreNotPromotedEveryFrame() {
        val plates = RetainedDrawRunCache.DEFAULT_CAPACITY + OVER_CAPACITY
        val window = measure(MOVING_FRAMES + SETTLE_FRAMES + WINDOW) { frame ->
            scene(plates) { minOf(frame, MOVING_FRAMES).toFloat() }
        }
        assertEquals(0, window.promotions, "a thrashing cache must not be fed a store per span per frame")
    }

    private companion object {
        const val PLATES = 22
        const val MOVING_FRAMES = 6
        const val SETTLE_FRAMES = 4
        const val WINDOW = 10
        const val OVER_CAPACITY = 44
    }
}
