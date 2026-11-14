/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.geometry.VertexFormats2D
import com.awakekt.awake.render.passes.GpuResourcePool
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.ui.DynamicMesh

/**
 * The UI meshes one UI pass stages its runs into: the overlay's, or one offscreen target's.
 *
 * A retained run skips its upload only while its slot still holds its arrays. Shared by the
 * overlay and a layer drawn every frame, a slot alternated between the two passes' arrays, so both
 * re-uploaded every frame although neither changed (#1545). Each pass keeps its own.
 */
internal class UiMeshSlots(graphicsDevice: GraphicsDevice) {
    private val quads = GpuResourcePool { DynamicMesh(graphicsDevice, Renderer.MAX_UI_QUADS) }
    private val glyphs = GpuResourcePool { DynamicMesh(graphicsDevice, Renderer.MAX_UI_QUADS, DynamicMesh.GLYPH_FLOATS_PER_VERTEX) }
    private val decoratedGlyphs = GpuResourcePool {
        DynamicMesh(graphicsDevice, Renderer.MAX_UI_QUADS, VertexFormats2D.DECORATED_GLYPH_FLOATS_PER_VERTEX)
    }
    private val roundedQuads = GpuResourcePool {
        DynamicMesh(graphicsDevice, Renderer.MAX_UI_QUADS, DynamicMesh.ROUNDED_QUAD_FLOATS_PER_VERTEX)
    }

    fun quadMesh(index: Int): DynamicMesh = quads[index]
    fun roundedQuadMesh(index: Int): DynamicMesh = roundedQuads[index]

    /** The [index]th glyph run's mesh, from the decorated glyph layout's pool when [decorated] (#1061). */
    fun glyphMesh(index: Int, decorated: Boolean = false): DynamicMesh = if (decorated) decoratedGlyphs[index] else glyphs[index]

    fun destroy() {
        quads.forEach { it.destroy() }
        glyphs.forEach { it.destroy() }
        decoratedGlyphs.forEach { it.destroy() }
        roundedQuads.forEach { it.destroy() }
    }
}
