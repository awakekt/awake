/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.core.geometry.VertexFormats2D
import com.awakekt.awake.render.passes.GpuResourcePool
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.ui.DynamicMesh

/**
 * The UI meshes one UI pass stages its runs into: the overlay's, or one offscreen target's.
 *
 * A retained run skips its upload only while its slot still holds its arrays. Shared by the
 * overlay and a layer drawn every frame, a slot alternated between the two passes' arrays, so both
 * re-uploaded every frame although neither changed (#1545). Each pass keeps its own.
 */
internal class UiMeshSlots(graphicsDevice: GraphicsDevice, framesInFlight: Int) {
    private val quads = GpuResourcePool { DynamicMesh(graphicsDevice, Renderer.MAX_UI_QUADS, framesInFlight = framesInFlight) }
    private val glyphs = GpuResourcePool {
        DynamicMesh(graphicsDevice, Renderer.MAX_UI_QUADS, DynamicMesh.GLYPH_FLOATS_PER_VERTEX, framesInFlight)
    }
    private val decoratedGlyphs = GpuResourcePool {
        DynamicMesh(graphicsDevice, Renderer.MAX_UI_QUADS, VertexFormats2D.DECORATED_GLYPH_FLOATS_PER_VERTEX, framesInFlight)
    }
    private val roundedQuads = GpuResourcePool {
        DynamicMesh(graphicsDevice, Renderer.MAX_UI_QUADS, DynamicMesh.ROUNDED_QUAD_FLOATS_PER_VERTEX, framesInFlight)
    }

    fun quadMesh(index: Int): DynamicMesh = quads[index]
    fun roundedQuadMesh(index: Int): DynamicMesh = roundedQuads[index]

    /** The [index]th glyph run's mesh, from the decorated glyph layout's pool when [decorated] (#1061). */
    fun glyphMesh(index: Int, decorated: Boolean = false): DynamicMesh = if (decorated) decoratedGlyphs[index] else glyphs[index]

    /** Buffer reallocations across these meshes; see `GpuBufferPoolManager.uiMeshGrowthCount`. */
    val growthCount: Int
        get() {
            var total = 0
            listOf(quads, glyphs, decoratedGlyphs, roundedQuads).forEach { pool -> pool.forEach { total += it.growthCount } }
            return total
        }

    fun destroy() {
        quads.forEach { it.destroy() }
        glyphs.forEach { it.destroy() }
        decoratedGlyphs.forEach { it.destroy() }
        roundedQuads.forEach { it.destroy() }
    }
}

/**
 * Resources of destroyed targets, kept until no frame that could still read them is in flight.
 *
 * Counted in frame submissions, not fence waits: a fence is waited on again mid-frame (draw
 * preparation, UI recording), before the frame being recorded is submitted, so "every slot waited
 * on since the retire" freed a target's meshes while the frame that drew it was still executing.
 * A resource retired while frame `n` records is freed once a fence wait shows submission `n` done;
 * frames on one queue complete in submission order, so any slot's fence covering a later
 * submission covers it too.
 */
internal class RetiredResources<T>(framesInFlight: Int, private val free: (T) -> Unit) {
    private class Retired<T>(val resource: T, val lastReader: Long)

    private val retired = ArrayList<Retired<T>>()
    private val submittedOn = LongArray(framesInFlight)
    private var submitted = 0L
    private var completed = 0L

    /** Resources waiting for their frames, for tests. */
    val pending: Int get() = retired.size

    /** [resource] is no longer used; the frame being recorded is the last that may read it. */
    fun retire(resource: T) {
        retired += Retired(resource, submitted + 1)
    }

    /** The frame recorded into slot [frame] was submitted. */
    fun frameSubmitted(frame: Int) {
        submitted += 1
        if (frame in submittedOn.indices) submittedOn[frame] = submitted
    }

    /** Slot [frame]'s fence was waited on: its last submission, and every earlier one, is done. */
    fun frameSlotIdle(frame: Int) {
        if (frame in submittedOn.indices) completed = maxOf(completed, submittedOn[frame])
        if (retired.isEmpty()) return
        val each = retired.iterator()
        while (each.hasNext()) {
            val entry = each.next()
            if (entry.lastReader <= completed) {
                free(entry.resource)
                each.remove()
            }
        }
    }

    fun destroyAll() {
        retired.forEach { free(it.resource) }
        retired.clear()
    }
}
