/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.geometry.VertexFormats2D
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.DrawPath
import com.awakekt.awake.core.graphics2d.isTranslatable
import com.awakekt.awake.core.graphics2d.movedClip

/**
 * Checks that a span inside a translate range can be staged and then moved: a kind whose every
 * vertex starts with its pixel position, and no path clip, which clips in place rather than moved.
 */
internal fun checkTranslatable(first: DrawCommand, activePathClips: List<DrawPath>) {
    check(first.isTranslatable) { "$first cannot sit inside a translate range" }
    check(activePathClips.isEmpty()) { "a path clip is active inside a translate range" }
}

/**
 * Moves the runs staged from index [from] on by ([dx], [dy]): the position at the start of each
 * vertex, and a clip run's rect. Only runs staged this frame from a translated span reach here,
 * fresh or pooled, never one the retained cache holds, so moving them in place moves nothing else.
 */
internal fun shiftStagedRuns(runs: MutableList<StagedDrawRun>, from: Int, dx: Float, dy: Float) {
    for (index in from until runs.size) {
        when (val run = runs[index]) {
            is StagedDrawRun.QuadRun -> shiftPositions(run.vertices, VertexFormats2D.FLOATS_PER_VERTEX, dx, dy)
            is StagedDrawRun.RoundedQuadRun -> shiftPositions(run.vertices, VertexFormats2D.ROUNDED_QUAD_FLOATS_PER_VERTEX, dx, dy)
            is StagedDrawRun.GlyphRun -> {
                check(!run.decorated) { "a decorated glyph cannot sit inside a translate range" }
                shiftPositions(run.vertices, VertexFormats2D.GLYPH_FLOATS_PER_VERTEX, dx, dy)
            }
            is StagedDrawRun.ClipRun -> runs[index] = StagedDrawRun.ClipRun(run.rect.movedClip(dx, dy))
            else -> error("$run cannot sit inside a translate range")
        }
    }
}

private fun shiftPositions(vertices: FloatArray, stride: Int, dx: Float, dy: Float) {
    var at = 0
    while (at + 1 < vertices.size) {
        vertices[at] += dx
        vertices[at + 1] += dy
        at += stride
    }
}
