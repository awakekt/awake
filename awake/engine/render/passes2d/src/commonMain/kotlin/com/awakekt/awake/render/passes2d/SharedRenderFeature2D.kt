/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.math2d.Rectangle

class SharedRenderFeature2D {

    fun <M> recordCommands(
        runs: List<DrawRun<M>>,
        surfaceWidth: Int,
        surfaceHeight: Int,
        recorder: DrawRunRecorder<M>,
    ) {
        val bound = BoundState(recorder)
        var textureSlotIndex = 0
        var runIndex = 0
        while (runIndex < runs.size) {
            when (val run = runs[runIndex]) {
                is DrawRun.QuadRun -> bound.draw(DrawPipelineKind.Quad, run.mesh)
                is DrawRun.RoundedQuadRun -> bound.draw(DrawPipelineKind.RoundedQuad, run.mesh)
                is DrawRun.GlyphRun -> bound.draw(DrawPipelineKind.Glyph, run.mesh)
                is DrawRun.ClipRun -> bound.clip(run.rect, surfaceWidth, surfaceHeight)
                is DrawRun.TextureRun -> {
                    textureSlotIndex = recordTextureRun(run, textureSlotIndex, recorder)
                    // Each texture binds its own pipeline.
                    bound.forgetPipeline()
                }
            }
            runIndex += 1
        }
    }

    private fun <M> recordTextureRun(
        run: DrawRun.TextureRun,
        firstSlotIndex: Int,
        recorder: DrawRunRecorder<M>,
    ): Int {
        if (!recorder.bindPipeline(DrawPipelineKind.Texture)) return firstSlotIndex
        var slotIndex = firstSlotIndex
        var primitiveIndex = 0
        while (primitiveIndex < run.primitives.size) {
            recorder.drawTexturePrimitive(run.primitives[primitiveIndex], slotIndex)
            slotIndex += 1
            primitiveIndex += 1
        }
        return slotIndex
    }
}

/**
 * What the previous run left bound. Runs alternate with clips, so the same pipeline and the same
 * rect recur back to back; neither is set again while it is already in place.
 */
private class BoundState<M>(private val recorder: DrawRunRecorder<M>) {
    private var kind: DrawPipelineKind? = null
    private var usable = false
    // A clamped rect packs to non-negative values, so -1 never matches and the first clip is set.
    private var scissorOrigin = -1L
    private var scissorSize = -1L

    fun draw(next: DrawPipelineKind, mesh: M) {
        if (next != kind) {
            usable = recorder.bindPipeline(next)
            kind = next
        }
        if (usable) recorder.drawMesh(mesh)
    }

    fun clip(rect: Rectangle, surfaceWidth: Int, surfaceHeight: Int) {
        val x = rect.x.toInt().coerceIn(0, surfaceWidth)
        val y = rect.y.toInt().coerceIn(0, surfaceHeight)
        val width = rect.width.toInt().coerceAtLeast(0).coerceAtMost(surfaceWidth - x)
        val height = rect.height.toInt().coerceAtLeast(0).coerceAtMost(surfaceHeight - y)
        val origin = pack(x, y)
        val size = pack(width, height)
        if (origin == scissorOrigin && size == scissorSize) return
        recorder.setScissor(x, y, width, height)
        scissorOrigin = origin
        scissorSize = size
    }

    private fun pack(a: Int, b: Int): Long = (a.toLong() shl Int.SIZE_BITS) or (b.toLong() and LOW_BITS)

    fun forgetPipeline() {
        kind = null
    }

    private companion object {
        const val LOW_BITS = 0xFFFFFFFFL
    }
}

typealias SharedUiRenderFeature = SharedRenderFeature2D
