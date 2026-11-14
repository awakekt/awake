/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.sortForRecording
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What every draw of a frame asks of its vertex format, and the frame's sort, allocate nothing per
 * draw with escape analysis off (#1555). Kotlin/Wasm has no escape analysis: a list iterator in
 * `VertexFormat.equals` or a boxed key in `sortedBy` is garbage on every draw there, about 40% of a
 * 3,000-draw frame's on the JVM with escape analysis off.
 */
class PerDrawAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Test
    fun askingAFormatAboutItselfAllocatesNothing() {
        assertEscapeAnalysisOff()
        val format = VertexFormat.PositionNormalColorUv
        // An equal format built separately: the comparison a pipeline lookup makes.
        val twin = VertexFormat(format.attributes.toList())
        var seen = 0
        val bytes = allocatedBy {
            repeat(CALLS) {
                if (format == twin) seen++
                seen += format.hashCode() and 1
                if (format.isSkinned) seen++
                seen += format.floatOffsetOf(VertexSemantic.Uv)
            }
        }
        assertTrue(seen != 0)
        // The measurement's own few hundred bytes aside: nothing per question.
        assertTrue(bytes < CALLS, "$CALLS format questions allocated $bytes B")
    }

    @Test
    fun sortingAFramesDrawsAllocatesOnlyTheSortedLists() {
        assertEscapeAnalysisOff()
        val pipelines = List(PIPELINES) { object : PipelineHandle {} }
        val handle = object : MaterialBinding, BufferHandle {}
        // Keys past the JVM's small-integer cache, as material keys are: boxing one allocates.
        val draws = List(DRAWS) { index ->
            GpuResolvedDraw(
                pipeline = pipelines[index % PIPELINES],
                materialBinding = handle,
                vertexBuffer = handle,
                indexBuffer = null,
                elementCount = 3,
                batchKey = 1_000 + (index * 7919) % 100_003,
                transparent = index % 10 == 0,
                depthSortKey = (index % 13).toFloat(),
            )
        }
        repeat(WARM_UP) { sortForRecording(draws) }
        val bytes = allocatedBy { sortForRecording(draws) }
        assertTrue(bytes <= SORT_BUDGET, "sorting $DRAWS draws allocated $bytes B, over $SORT_BUDGET B")
    }

    private inline fun allocatedBy(block: () -> Unit): Long {
        val thread = Thread.currentThread().id
        val before = threads.getThreadAllocatedBytes(thread)
        block()
        return threads.getThreadAllocatedBytes(thread) - before
    }

    private fun assertEscapeAnalysisOff() {
        val diagnostics = ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean::class.java)
        assertEquals("false", diagnostics.getVMOption("DoEscapeAnalysis").value, "these gates need -XX:-DoEscapeAnalysis")
    }

    private companion object {
        const val CALLS = 10_000
        const val DRAWS = 3_000
        const val PIPELINES = 8
        const val WARM_UP = 20

        /**
         * The partition, the per-pipeline map and its sorted lists and their arrays: 104 KB at 3,000
         * draws. Boxing the keys on every comparison made it 789 KB.
         */
        const val SORT_BUDGET = 200_000L
    }
}
