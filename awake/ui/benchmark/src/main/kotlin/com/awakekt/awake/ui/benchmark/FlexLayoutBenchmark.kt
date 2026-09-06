/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.benchmark

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.FlexBox
import com.awakekt.awake.compose.foundation.layout.FlowRow
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.flex
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
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
 * Frame cost of `Row`, `FlowRow` and `FlexBox` laying out an identical, non-wrapping child count.
 * Every child is a fixed-size `Box`, one child weighted/grown to absorb leftover space, so all
 * three do the same amount of measurement work -- only the measure-policy overhead differs.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class FlexLayoutBenchmark {

    @State(Scope.Benchmark)
    open class HostState {
        @Param("10", "50", "200")
        var count: Int = 0

        lateinit var host: ComposeHost

        @Setup
        fun setUp() {
            host = ComposeHost()
        }
    }

    @Benchmark
    fun row(state: HostState): Int {
        val count = state.count
        val frame = state.host.frame(FrameInput(viewportWidth = FRAME_WIDTH, viewportHeight = FRAME_HEIGHT)) {
            Row {
                repeat(count) {
                    Box(Modifier.size(CHILD_SIZE, CHILD_SIZE))
                }
            }
        }
        return frame.primitives.size
    }

    @Benchmark
    fun flowRow(state: HostState): Int {
        val count = state.count
        val frame = state.host.frame(FrameInput(viewportWidth = FRAME_WIDTH, viewportHeight = FRAME_HEIGHT)) {
            FlowRow(maxItemsInEachRow = Int.MAX_VALUE) {
                repeat(count) {
                    Box(Modifier.size(CHILD_SIZE, CHILD_SIZE))
                }
            }
        }
        return frame.primitives.size
    }

    @Benchmark
    fun flexBox(state: HostState): Int {
        val count = state.count
        val frame = state.host.frame(FrameInput(viewportWidth = FRAME_WIDTH, viewportHeight = FRAME_HEIGHT)) {
            FlexBox {
                repeat(count) {
                    Box(Modifier.flex { }.then(Modifier.size(CHILD_SIZE, CHILD_SIZE)))
                }
            }
        }
        return frame.primitives.size
    }

    private companion object {
        const val FRAME_WIDTH = 4000
        const val FRAME_HEIGHT = 900
        val CHILD_SIZE = 10.dp
    }
}
