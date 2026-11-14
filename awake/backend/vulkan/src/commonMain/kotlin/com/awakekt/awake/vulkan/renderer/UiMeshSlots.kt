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
 * Slots of destroyed targets, kept until every frame in flight that could still read them has
 * finished: each is destroyed once the fence of every frame slot has been waited on since.
 */
internal class RetiredUiMeshSlots(private val framesInFlight: Int) {
    private class Retired(val slots: UiMeshSlots, val idle: BooleanArray)

    private val retired = ArrayList<Retired>()

    /** Meshes waiting for their frames, for tests. */
    val pending: Int get() = retired.size

    fun retire(slots: UiMeshSlots) {
        retired += Retired(slots, BooleanArray(framesInFlight))
    }

    /** The GPU is done with frame slot [frame]; destroys every retired set no frame can still read. */
    fun frameSlotIdle(frame: Int) {
        if (retired.isEmpty()) return
        val each = retired.iterator()
        while (each.hasNext()) {
            val entry = each.next()
            if (frame in entry.idle.indices) entry.idle[frame] = true
            if (entry.idle.all { it }) {
                entry.slots.destroy()
                each.remove()
            }
        }
    }

    fun destroyAll() {
        retired.forEach { it.slots.destroy() }
        retired.clear()
    }
}
