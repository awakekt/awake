/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.passes.ScenePassScratch
import kotlinx.coroutines.test.runTest
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Where a warm moving frame's wasm heap goes, stage by stage (#1621). The JVM, with escape analysis
 * off, puts a planned draw at about 100 B; a consumer's web build pays about 1.3 KB a draw. This
 * measures the browser's own figure for each stage, so a fix is ranked on what wasm pays:
 *
 * - **fold**: the pass compiled with no preparer, which folds, sorts and culls but prepares nothing;
 * - **prepare**: the same frame compiled with the WebGPU preparer, less the fold;
 * - **draw**: the prepared pass drawn, recorded and submitted.
 *
 * Each is the median, over [MovingField.MEASURED] frames, of the JS heap's growth from a collected
 * heap. Per draw is per draw the preparer was asked for.
 */
class MovingCameraStageHeapBrowserTest {

    /** Counts what it is asked to prepare; it allocates nothing itself. */
    private class Counting(private val delegate: GpuDrawPreparer) : GpuDrawPreparer by delegate {
        var draws = 0

        override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: GpuDrawPreparationContext): GpuResolvedDraw? {
            draws++
            return delegate.prepare(request, sourceIndex, context)
        }
    }

    @Test
    fun aWarmMovingFrameIsMeasuredStageByStage() = runTest(timeout = 4.minutes) {
        assertTrue(heapMeasurable(), "needs the launcher's --js-flags=--expose-gc and --enable-precise-memory-info")
        val engine = BundleBrowserEngine(1)
        try {
            val scene = MovingField(engine.boot())
            val preparer = Counting(requireNotNull(scene.defaultPreparer))
            // Its own scratch: a compile without a preparer must not stand in for the prepared one.
            val foldScratch = ScenePassScratch()
            fun frame(index: Int) {
                scene.compile(index, preparer = null, scratch = foldScratch)
                scene.draw(scene.compile(index, preparer))
            }
            repeat(MovingField.WARM_UP) { frame(it) }

            val fold = DoubleArray(MovingField.MEASURED)
            val prepared = DoubleArray(MovingField.MEASURED)
            val draw = DoubleArray(MovingField.MEASURED)
            preparer.draws = 0
            for (i in 0 until MovingField.MEASURED) {
                val index = MovingField.WARM_UP + i
                var before = collectedHeap()
                scene.compile(index, preparer = null, scratch = foldScratch)
                fold[i] = heapNow() - before
                before = collectedHeap()
                val pass = scene.compile(index, preparer)
                prepared[i] = heapNow() - before
                before = collectedHeap()
                scene.draw(pass)
                draw[i] = heapNow() - before
            }
            val draws = preparer.draws / MovingField.MEASURED
            val foldKb = median(fold) / MovingField.KB
            val prepareKb = (median(prepared) - median(fold)) / MovingField.KB
            val drawKb = median(draw) / MovingField.KB
            println(
                "MovingCameraStageHeap: $draws draws prepared a frame; fold ${foldKb.toInt()} KB, " +
                    "prepare ${prepareKb.toInt()} KB (${(prepareKb * MovingField.KB / draws).toInt()} B a draw), " +
                    "draw ${drawKb.toInt()} KB",
            )
            assertTrue(draws > 0, "the preparer is asked for the frame's draws")
            assertTrue(engine.errors.isEmpty(), "WebGPU validation errors: ${engine.errors}")
        } finally {
            engine.destroy()
        }
    }

    private fun median(values: DoubleArray): Double = values.sortedArray()[values.size / 2]
}
