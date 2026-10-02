/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SceneFrameStats
import com.awakekt.awake.scene.runtime.frameStats
import kotlin.math.roundToInt

/** Frames between two `PERF` lines: a few seconds at the frame rates worth logging. */
internal const val PERF_LOG_INTERVAL_FRAMES = 240

/**
 * Prints one line of frame statistics every [PERF_LOG_INTERVAL_FRAMES] frames.
 *
 * For comparing two runs from a terminal, where the stats card cannot be diffed: same build,
 * same machine, one change between them. The line is stable `key=value` pairs so it greps.
 */
internal class ShowcasePerfLog(
    private val runtime: SceneAppLifecycleRuntime,
    private val currentShowcase: () -> String,
) : System {
    private var frames = 0

    override fun update(world: World, delta: Float) {
        frames += 1
        if (frames < PERF_LOG_INTERVAL_FRAMES) return
        frames = 0
        println(perfLine(currentShowcase(), world.family<MeshRenderer>().size, runtime.frameStats()))
    }
}

/** One `PERF` line; times in milliseconds. */
internal fun perfLine(showcase: String, renderables: Int, stats: SceneFrameStats): String {
    val phases = stats.phases
    val render = stats.render
    return buildString {
        append("PERF ").append(showcase)
        append(" renderables=").append(renderables)
        append(" fps=").append(stats.fps.tenths())
        append(" avg=").append(stats.frameTimeMs.tenths())
        append(" p99=").append(stats.p99FrameTimeMs.tenths())
        append(" max=").append(stats.maxFrameTimeMs.tenths())
        if (phases.isMeasured) {
            append(" game=").append(phases.gameMs.tenths())
            append(" render=").append(phases.renderMs.tenths())
            append(" ui=").append((phases.uiBuildMs + phases.uiStageMs).tenths())
            append(" wait=").append(phases.uiWaitMs.tenths())
        }
        if (render != null) {
            append(" draws=").append(render.drawCalls)
            append(" instances=").append(render.instances)
            append(" tris=").append(render.triangles)
            append(" gpu=").append(render.gpuTimeMs?.tenths() ?: "na")
        }
    }
}

private fun Float.tenths(): Float = (this * 10f).roundToInt() / 10f
