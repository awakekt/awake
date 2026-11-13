/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.DrawCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #1545: the retained cache has no cliff. However many spans a frame has, a still frame re-stages
 * only what does not fit, a changed span costs only itself, and a UI that shrinks gives its spans
 * back. Spans here alternate a rounded rect and a glyph, so each command is a span of its own.
 */
class RetainedSpanScalingTest {
    private val grey = Color(0.2f, 0.2f, 0.25f, 1f)

    /** [spans] spans, built fresh as composition builds them; [changed] differs by one pixel. */
    private fun scene(spans: Int, changed: Int = -1, shift: Float = 0f): List<DrawCommand> = List(spans) { i ->
        val x = (i % 64) * 30f + if (i == changed) shift else 0f
        val y = (i / 64) * 30f
        if (i % 2 == 0) DrawCommand.RoundedQuad(x, y, 24f, 24f, grey, radius = 4f) else DrawCommand.Glyph(x, y, 8f, 14f, 0f, 0f, 0.1f, 0.1f, grey)
    }

    private class Stage(capacity: Int = RetainedDrawRunCache.DEFAULT_CAPACITY) {
        val cache = RetainedDrawRunCache(capacity)
        private val pool = DynamicRunArrayPool()

        /** Stages one frame; returns the runs staged afresh rather than found in the cache. */
        fun frame(commands: List<DrawCommand>): Int {
            val before = pool.stableSpans.promotions
            val staged = DrawRunCoalescer.coalesce(commands, retained = cache, pool = pool)
            return staged.count { it.requiresUpload } + (pool.stableSpans.promotions - before) + firstSights(staged)
        }

        private var seen = HashSet<Any>()

        /** Runs neither pooled nor promoted but new to this cache: first sights, stored on the way. */
        private fun firstSights(staged: List<StagedDrawRun>): Int {
            val now = staged.filter { !it.requiresUpload }.mapTo(HashSet<Any>()) { it }
            val fresh = now.count { it !in seen }
            seen = now
            return fresh
        }
    }

    private fun stillCost(spans: Int): List<Int> = Stage().let { stage -> List(4) { stage.frame(scene(spans)) } }

    @Test
    fun twoThousandAndTenThousandStillSpansReStageNothingAfterTheFirstFrame() {
        for (spans in listOf(2_000, 10_000)) {
            val costs = stillCost(spans)
            println("PERF retainedSpans spans=$spans restaged per frame=$costs")
            assertEquals(spans, costs.first(), "$spans spans: the first frame stages each once")
            assertEquals(listOf(0, 0, 0), costs.drop(1), "$spans spans: still frames re-stage nothing")
        }
    }

    @Test
    fun pastTheHardBoundTheSpansThatFitStayCachedAndOnlyTheExcessReStages() {
        val stage = Stage(capacity = 4)
        val bound = stage.cache.maxCapacity
        val spans = bound + OVER_BOUND
        val costs = List(6) { stage.frame(scene(spans)) }
        println("PERF retainedSpans spans=$spans bound=$bound restaged per frame=$costs")
        assertEquals(List(4) { OVER_BOUND }, costs.drop(2), "only the $OVER_BOUND spans past the bound re-stage, every frame")
        assertEquals(bound, stage.cache.size)
    }

    @Test
    fun aChangedSpanCostsOnlyItself() {
        val stage = Stage()
        repeat(2) { stage.frame(scene(2_000)) }
        val costs = List(6) { frame -> stage.frame(scene(2_000, changed = 777, shift = frame + 1f)) }
        assertEquals(List(6) { 1 }, costs, "one moving span re-stages one run a frame, never the rest")
    }

    @Test
    fun aUiThatShrinksReleasesItsSpansAndOneThatGrowsBackSettlesInAFrame() {
        val stage = Stage()
        repeat(2) { stage.frame(scene(2_000)) }
        repeat(2) { stage.frame(scene(300)) }
        assertTrue(stage.cache.size <= 300 + RetainedDrawRunCache.DEFAULT_CAPACITY, "kept ${stage.cache.size} after shrinking")
        val regrown = List(4) { stage.frame(scene(2_000)) }
        println("PERF retainedSpans regrow 300->2000 restaged per frame=$regrown")
        assertEquals(listOf(0, 0), regrown.drop(2), "a grown-back UI is still again two frames later")
    }

    private companion object {
        const val OVER_BOUND = 44
    }
}
