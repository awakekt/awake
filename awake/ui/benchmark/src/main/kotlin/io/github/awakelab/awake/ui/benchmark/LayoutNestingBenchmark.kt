/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.text.benchmark

import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.ColumnScope
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.platform.ComposeHost
import io.github.awakelab.awake.compose.ui.platform.FrameInput
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import java.util.concurrent.TimeUnit

/**
 * Frame cost of nested row/column layout on the compose engine, parameterised by nesting depth.
 *
 * The old `ui-core` version of this benchmark existed because trial-measure re-ran every row/column's
 * content lambda to pick its measurement branch, so cost compounded with depth (see
 * docs/tasks/2026-08-02-trial-measure-double-execution.md). The compose engine measures each child
 * exactly once -- no trial pass -- so this now measures plain recursive layout-tree cost instead, and
 * the depth parameter stays to show that curve is linear rather than the old exponential-ish one.
 *
 * Deliberately widget-free -- no theme, no text, no design system -- for the same reason the old
 * version was: a regression here cannot be blamed on a recipe, and a recipe regression cannot hide
 * in here.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class LayoutNestingBenchmark {

    @State(Scope.Benchmark)
    open class HostState {
        @Param("2", "4", "6", "8")
        var depth: Int = 0

        lateinit var host: ComposeHost

        @Setup
        fun setUp() {
            host = ComposeHost()
        }
    }

    @Benchmark
    fun nestedColumnsAndRows(state: HostState): Int {
        val depth = state.depth
        val frame = state.host.frame(FrameInput(viewportWidth = FRAME_WIDTH, viewportHeight = FRAME_HEIGHT)) {
            Column {
                nest(depth)
            }
        }
        return frame.primitives.size
    }

    private companion object {
        const val FRAME_WIDTH = 1280
        const val FRAME_HEIGHT = 900
    }
}

/** Alternating column/row so both measure policies are exercised at every level. */
context(_: Composer)
private fun ColumnScope.nest(remaining: Int) {
    if (remaining == 0) return
    Column {
        Row {
            Column {
                nest(remaining - 1)
            }
        }
    }
}
