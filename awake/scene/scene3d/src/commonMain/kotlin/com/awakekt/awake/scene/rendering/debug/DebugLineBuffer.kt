/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.debug

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.diagnostics.DebugLines
import com.awakekt.awake.core.diagnostics.DebugRegistry
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.debug.DebugLineLayout
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.render.renderer.Renderer

/**
 * One frame's debug lines, gathered from every contributor so they reach the renderer in a single
 * [Renderer.drawDebugLines] call: that call replaces the frame's whole line buffer, so two callers
 * would each wipe out the other's lines.
 *
 * The buffer is kept from frame to frame. A line written through [line] reuses the [LineSegment]
 * it wrote at the same place last frame and only copies the two points into it, so steady frames
 * allocate nothing. It makes a new segment only where the colour at that place changed, as a
 * segment's colour is fixed. Lines already built elsewhere join through [addAll] as they are.
 *
 * Holds at most [maxLines]; lines past that are dropped and counted in [dropped], because a
 * backend refuses more than [DebugLineLayout.MAX_LINES_CEILING].
 */
class DebugLineBuffer(
    /** Most lines this buffer holds in a frame. */
    val maxLines: Int = DebugLineLayout.MAX_LINES_CEILING,
) : DebugLines {
    init {
        require(maxLines in 0..DebugLineLayout.MAX_LINES_CEILING) {
            "maxLines ($maxLines) must be within 0..${DebugLineLayout.MAX_LINES_CEILING}"
        }
    }

    private val owned = ArrayList<LineSegment>()
    private var ownedUsed = 0
    private val frame = ArrayList<LineSegment>()

    /** This frame's lines, in the order they were added. Valid until the next [clear]. */
    val lines: List<LineSegment> get() = frame

    /** Lines refused this frame because the buffer already held [maxLines]. */
    var dropped: Int = 0
        private set

    /** Starts a new frame: empties [lines] and [dropped], and keeps the segments for reuse. */
    fun clear() {
        frame.clear()
        ownedUsed = 0
        dropped = 0
    }

    override fun line(a: Vec3f, b: Vec3f, color: Color) {
        if (frame.size >= maxLines) {
            dropped++
            return
        }
        val index = ownedUsed++
        val segment = if (index < owned.size && owned[index].color == color) {
            owned[index]
        } else {
            LineSegment(Vec3f(), Vec3f(), color).also {
                if (index < owned.size) owned[index] = it else owned += it
            }
        }
        segment.start.set(a)
        segment.end.set(b)
        frame += segment
    }

    /** Adds lines built elsewhere, such as [debugVisualizationLines], as they are. */
    fun addAll(segments: List<LineSegment>) {
        val room = maxLines - frame.size
        if (segments.size <= room) {
            frame.addAll(segments)
        } else {
            for (i in 0 until room) frame += segments[i]
            dropped += segments.size - room
        }
    }

    /** Asks every source [registry] has on to write its lines here, in the order they registered. */
    fun addSources(registry: DebugRegistry) {
        val sources = registry.active
        for (i in sources.indices) sources[i].lines(this)
    }
}
