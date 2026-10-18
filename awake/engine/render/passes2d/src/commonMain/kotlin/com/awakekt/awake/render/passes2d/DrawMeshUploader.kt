/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.text.font.UiFont

/**
 * Uploads one coalesced run's geometry into a backend mesh, for [uploadDrawRuns].
 *
 * @param M The backend's 2D mesh type.
 */
interface DrawMeshUploader<M> {
    /** Builds or updates a backend mesh for solid quad primitives in [runIndex]. */
    fun quadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray): M

    /** Builds or updates a backend mesh for rounded quad primitives in [runIndex]. */
    fun roundedQuadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray): M

    /** Builds or updates a backend mesh for text glyph primitives in [runIndex]. */
    fun glyphMesh(runIndex: Int, vertices: FloatArray, indices: IntArray): M

    /**
     * Builds or updates a backend mesh for the frosted-glass panel [runIndex] or, when
     * [blurPass] is true, for blur pass [runIndex] of the frame. Null when this backend draws no
     * backdrop blur, which drops the panels (their staging already chose the fallback when the
     * renderer's quality is off).
     */
    fun backdropMesh(runIndex: Int, vertices: FloatArray, indices: IntArray, blurPass: Boolean): M? = null
}

typealias UiMeshUploader<M> = DrawMeshUploader<M>

/**
 * Turns this frame's coalesced [StagedDrawRun]s into GPU-backed [DrawRun]s, in paint order.
 *
 * @param M The backend's 2D mesh type.
 * @param staged Output of [DrawRunCoalescer.coalesce].
 * @param uploader The backend's mesh fetch-and-fill.
 * @param defaultFont The font a glyph run without its own font samples. A glyph run with neither
 * is dropped: it has no atlas to draw from.
 * @return Runs ready for [SharedRenderFeature2D].
 */
fun <M> uploadDrawRuns(
    staged: List<StagedDrawRun>,
    uploader: DrawMeshUploader<M>,
    defaultFont: UiFont? = null,
): List<DrawRun<M>> {
    val runs = ArrayList<DrawRun<M>>(staged.size)
    var quadRunCount = 0
    var roundedQuadRunCount = 0
    var glyphRunCount = 0
    var backdropRunCount = 0
    var blurPassCount = 0
    var blurMissing = false
    var index = 0
    while (index < staged.size) {
        when (val run = staged[index]) {
            is StagedDrawRun.QuadRun ->
                runs += DrawRun.QuadRun(
                    uploader.quadMesh(
                        quadRunCount++,
                        run.vertices,
                        run.indices,
                    ),
                )

            is StagedDrawRun.RoundedQuadRun ->
                runs += DrawRun.RoundedQuadRun(
                    uploader.roundedQuadMesh(roundedQuadRunCount++, run.vertices, run.indices),
                )

            is StagedDrawRun.GlyphRun -> {
                val font = run.font ?: defaultFont
                if (font != null) {
                    runs += DrawRun.GlyphRun(uploader.glyphMesh(glyphRunCount++, run.vertices, run.indices), font)
                }
            }

            is StagedDrawRun.TextureRun -> runs += DrawRun.TextureRun(run.primitives)

            is StagedDrawRun.ClipRun -> runs += DrawRun.ClipRun(run.rect)

            is StagedDrawRun.BackdropRun -> {
                val work = run.blur
                var recording: BackdropBlurRecording<M>? = null
                if (work != null) {
                    val passMeshes = ArrayList<M>(work.passes.size)
                    for (pass in work.passes) {
                        passMeshes += uploader.backdropMesh(blurPassCount++, pass.vertices, pass.indices, blurPass = true) ?: break
                    }
                    if (passMeshes.size == work.passes.size) recording = BackdropBlurRecording(work, passMeshes)
                    // The panels sharing a blur that never runs have nothing to sample.
                    blurMissing = recording == null
                }
                val mesh = if (blurMissing) null else uploader.backdropMesh(backdropRunCount++, run.vertices, run.indices, blurPass = false)
                if (mesh != null) runs += DrawRun.BackdropRun(mesh, recording)
            }
        }
        index += 1
    }
    return runs
}

/** Legacy alias for [uploadDrawRuns]. */
fun <M> uploadUiRuns(
    staged: List<StagedDrawRun>,
    uploader: DrawMeshUploader<M>,
    defaultFont: UiFont? = null,
): List<DrawRun<M>> = uploadDrawRuns(staged, uploader, defaultFont)
