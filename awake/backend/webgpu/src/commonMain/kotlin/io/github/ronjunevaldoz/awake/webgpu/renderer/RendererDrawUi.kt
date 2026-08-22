// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.webgpu.renderer

import io.github.ronjunevaldoz.awake.render.passes2d.uploadUiRuns
import io.github.ronjunevaldoz.awake.render.passes2d.UiMeshUploader
import io.github.ronjunevaldoz.awake.render.passes2d.UiRunCoalescer
import io.github.ronjunevaldoz.awake.render.passes2d.UiRun
import io.github.ronjunevaldoz.awake.core.graphics2d.UiDrawPrimitive
import io.github.ronjunevaldoz.awake.ui.font.UiFont
import io.github.ronjunevaldoz.awake.webgpu.ui.DynamicMesh

/**
 * UI primitive staging for the WebGPU backend.
 */
internal fun Renderer.performDrawUi(primitives: List<UiDrawPrimitive>, font: UiFont?) {
    ensureUiQuadPipeline()
    if (font != null) ensureGlyphPipeline(font)
    if (primitives.any { it is UiDrawPrimitive.Texture }) ensureTextureQuadPipeline()
    if (primitives.any { it is UiDrawPrimitive.RoundedQuad || it is UiDrawPrimitive.ShadowQuad }) ensureRoundedQuadPipeline()

    uiRuns = uploadUiRuns(
        UiRunCoalescer.coalesce(primitives, Renderer.MAX_UI_QUADS),
        WebGpuUiMeshUploader(this),
    )
}

/** WebGPU's mesh allocation for [uploadUiRuns]. Single-buffered, so no frame index. */
private class WebGpuUiMeshUploader(private val renderer: Renderer) : UiMeshUploader<DynamicMesh> {
    override fun quadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray): DynamicMesh =
        renderer.quadMeshForRun(runIndex).also { it.update(vertices, indices) }

    override fun roundedQuadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray): DynamicMesh =
        renderer.roundedQuadMeshForRun(runIndex).also { it.update(vertices, indices) }

    override fun glyphMesh(runIndex: Int, vertices: FloatArray, indices: IntArray): DynamicMesh =
        renderer.glyphMeshForRun(runIndex).also { it.update(vertices, indices) }
}
