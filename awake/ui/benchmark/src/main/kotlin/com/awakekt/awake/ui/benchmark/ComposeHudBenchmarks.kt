/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.benchmark

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.zIndex
import com.awakekt.awake.compose.ui.layout.Layer
import com.awakekt.awake.compose.ui.layout.LayerKind
import com.awakekt.awake.compose.ui.layout.LayerPosition
import com.awakekt.awake.compose.ui.layout.LayerPositionProvider
import com.awakekt.awake.compose.ui.layout.onPlaced
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit

/**
 * Per-frame Compose HUD hot-path benchmarks:
 * - [placementOnlyHudFrame]: placement-only frame over a 256-node HUD tree with overlay layers
 *   and `onPlaced` listeners (exercises single-pass `resolveAbsolutePositions` and idle
 *   `currentInputOwnership`).
 * - [zIndexContainerPaintFrame]: `Painter.paint` over 16 containers x 16 z-ordered children
 *   (exercises the zero-allocation re-entrant `IntArray` sort path).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
open class ComposeHudBenchmarks {
    private val host = ComposeHost().apply {
        skipUnchangedComposition = true
        skipUnchangedMeasure = true
        retainUnchangedDraw = true
    }
    private val zHost = ComposeHost().apply {
        skipUnchangedComposition = true
        skipUnchangedMeasure = true
        retainUnchangedDraw = false
    }
    private val layerPolicy = BoxMeasurePolicy()
    private val input = FrameInput(viewportWidth = FRAME_WIDTH, viewportHeight = FRAME_HEIGHT)
    private var placedCount = 0

    private val hudContent: context(Composer)
    () -> Unit = {
        Column {
            repeat(ROWS) { row ->
                Row {
                    repeat(COLS) { col ->
                        Box(Modifier.size(CELL_SIZE, CELL_SIZE).onPlaced { _, _, _, _ -> placedCount++ }) {
                            if (row == 0 && col == 0) {
                                Layer(
                                    kind = LayerKind.Popup,
                                    measurePolicy = layerPolicy,
                                    positionProvider = POPUP_PROVIDER,
                                ) {
                                    Box(Modifier.size(POPUP_SIZE, POPUP_SIZE).onPlaced { _, _, _, _ -> placedCount++ })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private val zContent: context(Composer)
    () -> Unit = {
        Column {
            repeat(Z_GROUPS) {
                Box {
                    repeat(Z_CHILDREN) { i ->
                        Box(Modifier.size(CELL_SIZE, CELL_SIZE).zIndex(((Z_CHILDREN - i) % 5).toFloat()))
                    }
                }
            }
        }
    }

    /** Warms both hosts so steady-state placement and paint paths are measured cleanly. */
    @Setup
    fun setup() {
        repeat(2) {
            host.frame(input, hudContent)
            zHost.frame(input, zContent)
        }
        placedCount = 0
    }

    /** Placement-only steady-state HUD frame with 256 cells and an anchored popup layer. */
    @Benchmark
    fun placementOnlyHudFrame(): Int {
        val output = host.frame(input, hudContent)
        return output.primitives.size + placedCount
    }

    /** Full paint pass over 16 containers x 16 z-ordered children (256 z-sorted children). */
    @Benchmark
    fun zIndexContainerPaintFrame(): Int {
        val output = zHost.frame(input, zContent)
        return output.primitives.size
    }

    private companion object {
        const val FRAME_WIDTH = 1280
        const val FRAME_HEIGHT = 720
        const val ROWS = 16
        const val COLS = 16
        const val Z_GROUPS = 16
        const val Z_CHILDREN = 16
        val CELL_SIZE = 16.dp
        val POPUP_SIZE = 64.dp
        val POPUP_PROVIDER = LayerPositionProvider { _, _, _, _, _, _ -> LayerPosition(8, 8) }
    }
}
