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
