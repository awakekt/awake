/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.text.font.UiFont

/**
 * Backend-neutral command recording engine for 2D draw runs.
 * Translates coalesced [DrawRun]s into backend driver calls via [DrawRunRecorder].
 */
class SharedRenderFeature2D {

    /**
     * Records draw and scissor commands for all [runs] onto [recorder] targeting the given surface dimensions.
     */
    fun <M> recordCommands(
        runs: List<DrawRun<M>>,
        surfaceWidth: Int,
        surfaceHeight: Int,
        recorder: DrawRunRecorder<M>,
    ) {
        val bound = BoundState(recorder)
        var textureSlotIndex = 0
        var blursLeft = countBlurs(runs)
        var backdropReady = false
        var runIndex = 0
        while (runIndex < runs.size) {
            when (val run = runs[runIndex]) {
                is DrawRun.QuadRun -> bound.draw(DrawPipelineKind.Quad, run.mesh)
                is DrawRun.RoundedQuadRun -> bound.draw(DrawPipelineKind.RoundedQuad, run.mesh)
                is DrawRun.GlyphRun -> bound.drawGlyphs(run.font, run.mesh)
                is DrawRun.ClipRun -> bound.clip(run.rect, surfaceWidth, surfaceHeight)
                is DrawRun.TextureRun -> {
                    textureSlotIndex = recordTextureRun(run, textureSlotIndex, recorder)
                    // Each texture binds its own pipeline.
                    bound.forgetPipeline()
                }
                is DrawRun.BackdropRun -> {
                    val blur = run.blur
                    if (blur != null) {
                        blursLeft -= 1
                        backdropReady = recorder.blurBackdrop(blur, more = blursLeft > 0)
                        // The pass was broken off and resumed: nothing is bound and the scissor is lost.
                        if (backdropReady) bound.resume(surfaceWidth, surfaceHeight)
                    }
                    // A panel sharing a blur that could not run has nothing behind it to show.
                    if (backdropReady) bound.draw(DrawPipelineKind.BackdropComposite, run.mesh)
                }
            }
            runIndex += 1
        }
    }

    private fun <M> countBlurs(runs: List<DrawRun<M>>): Int {
        var count = 0
        for (index in runs.indices) {
            val run = runs[index]
            if (run is DrawRun.BackdropRun && run.blur != null) count += 1
        }
        return count
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
 * What the previous run left bound. Runs alternate with clips, so the same pipeline, font and rect
 * recur back to back; none is set again while it is already in place.
 */
private class BoundState<M>(private val recorder: DrawRunRecorder<M>) {
    private var kind: DrawPipelineKind? = null
    private var usable = false
    private var font: UiFont? = null
    private var fontUsable = false

    // A clamped rect packs to non-negative values, so -1 never matches and the first clip is set.
    private var scissorOrigin = -1L
    private var scissorSize = -1L
    private var lastClip: Rectangle? = null

    fun draw(next: DrawPipelineKind, mesh: M) {
        if (bind(next)) recorder.drawMesh(mesh)
    }

    /** A font change binds the next atlas into the glyph pipeline already bound; paint order stays. */
    fun drawGlyphs(nextFont: UiFont, mesh: M) {
        if (!bind(DrawPipelineKind.Glyph)) return
        if (nextFont !== font) {
            fontUsable = recorder.bindGlyphFont(nextFont)
            font = nextFont
        }
        if (fontUsable) recorder.drawMesh(mesh)
    }

    private fun bind(next: DrawPipelineKind): Boolean {
        if (next != kind) {
            usable = recorder.bindPipeline(next)
            kind = next
            // Another pipeline's bind replaced group 0, so the next glyph run binds its font again.
            font = null
        }
        return usable
    }

    fun clip(rect: Rectangle, surfaceWidth: Int, surfaceHeight: Int) {
        lastClip = rect
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
        font = null
    }

    /** After the pass was broken off: bind everything again and restore the clip in force. */
    fun resume(surfaceWidth: Int, surfaceHeight: Int) {
        forgetPipeline()
        scissorOrigin = -1L
        scissorSize = -1L
        val clip = lastClip
        if (clip != null) {
            clip(clip, surfaceWidth, surfaceHeight)
        } else {
            recorder.setScissor(0, 0, surfaceWidth, surfaceHeight)
        }
    }

    private companion object {
        const val LOW_BITS = 0xFFFFFFFFL
    }
}

typealias SharedUiRenderFeature = SharedRenderFeature2D
