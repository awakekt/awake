/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

class SharedRenderFeature2D {

    fun <M> recordCommands(
        runs: List<DrawRun<M>>,
        surfaceWidth: Int,
        surfaceHeight: Int,
        recorder: DrawRunRecorder<M>,
    ) {
        var textureSlotIndex = 0
        var runIndex = 0
        while (runIndex < runs.size) {
            when (val run = runs[runIndex]) {
                is DrawRun.QuadRun ->
                    if (recorder.bindPipeline(DrawPipelineKind.Quad)) recorder.drawMesh(run.mesh)

                is DrawRun.RoundedQuadRun ->
                    if (recorder.bindPipeline(DrawPipelineKind.RoundedQuad)) recorder.drawMesh(run.mesh)

                is DrawRun.GlyphRun ->
                    if (recorder.bindPipeline(DrawPipelineKind.Glyph)) recorder.drawMesh(run.mesh)

                is DrawRun.ClipRun -> {
                    val x = run.rect.x.toInt().coerceIn(0, surfaceWidth)
                    val y = run.rect.y.toInt().coerceIn(0, surfaceHeight)
                    val width = run.rect.width.toInt().coerceAtLeast(0).coerceAtMost(surfaceWidth - x)
                    val height = run.rect.height.toInt().coerceAtLeast(0).coerceAtMost(surfaceHeight - y)
                    recorder.setScissor(x, y, width, height)
                }

                is DrawRun.TextureRun ->
                    textureSlotIndex = recordTextureRun(run, textureSlotIndex, recorder)
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

typealias SharedUiRenderFeature = SharedRenderFeature2D
