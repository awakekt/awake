/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw

/**
 * Records this frame's transparent draws after the opaque pass, in the order given.
 *
 * Separate from [SharedOpaqueRenderFeature] because the two want opposite things. Opaque groups by
 * pipeline so consecutive draws reuse bound state; transparent must not reorder at all, since
 * back-to-front order is the only thing making the blend correct. One loop with a flag would
 * disable the very optimisation the loop exists for.
 *
 * The per-draw recording itself is [SharedOpaqueRenderFeature.recordDraws] -- binding vertex and
 * index buffers, material sets and instance data is identical whether or not the result blends.
 * Only the grouping and the pipeline state differ, and the pipeline came from the prepare step.
 *
 * The sort belongs to that prepare step too, which already holds the camera position. This walks
 * the result.
 */
class SharedTransparentRenderFeature(
    private val opaque: SharedOpaqueRenderFeature = SharedOpaqueRenderFeature(),
) {
    /**
     * @param draws This frame's transparent draws, back-to-front. Empty is the common case.
     * @param recorder The backend's command recorder.
     */
    fun recordCommands(draws: List<PreparedDraw>, recorder: CommandRecorder) {
        if (draws.isEmpty()) return
        var bound: PipelineHandle? = null
        // Rebound per run of same-pipeline draws rather than once for the list: sorted order can
        // interleave pipelines where the opaque pass would not. That thrash is the cost of correct
        // blending, and it is paid per switch, not per draw.
        var runStart = 0
        draws.forEachIndexed { index, draw ->
            if (draw.pipeline !== bound) {
                if (index > runStart) opaque.recordDraws(recorder, draws.subList(runStart, index))
                recorder.bindPipeline(draw.pipeline)
                bound = draw.pipeline
                runStart = index
            }
        }
        opaque.recordDraws(recorder, draws.subList(runStart, draws.size))
    }
}
