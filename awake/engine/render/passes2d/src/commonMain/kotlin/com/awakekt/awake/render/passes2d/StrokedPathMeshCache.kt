/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.ColoredTriangleMesh
import com.awakekt.awake.core.graphics2d.DrawPath
import com.awakekt.awake.core.graphics2d.DrawStroke
import com.awakekt.awake.core.graphics2d.tessellateStrokeAa

/**
 * Stroked-path triangles, keyed by the geometry that produced them.
 *
 * Stroking is the most expensive tessellation this package runs: it flattens every curve, offsets
 * both sides of every contour with per-join arc trig, and scanline-fills the resulting ring. An
 * outline icon pays all of that, and a UI redraws the same icons every frame at the same place --
 * the frame ratchet's own scene went 41,016 to 56,184 vertices when the icon set became real
 * outlines, and every one of those was being recomputed per frame.
 *
 * Its own object rather than more fields on [DrawRunCoalescer], which detekt already calls too
 * large.
 */
internal object StrokedPathMeshCache {

    /** Everything a stroked path's triangles depend on. `DrawPath`, `DrawStroke` and `Color` are
     * all data classes, so this compares by value -- which is the point: the path handed in is
     * rebuilt every frame by `DrawScope.drawStrokedPath`, so identity would never hit. */
    private data class Key(val path: DrawPath, val stroke: DrawStroke, val color: Color)

    /** Eldest-evicted past [CAPACITY]; `linkedMapOf` iteration order is insertion order, and
     * re-inserting on hit makes it least-recently-used -- the same pattern the rounded-rect cache
     * next door uses. */
    private val meshes = linkedMapOf<Key, ColoredTriangleMesh>()
    private const val CAPACITY = 256

    /** Tessellations actually performed, for a test that a repeated frame does none. */
    internal var tessellations: Int = 0
        private set

    /** Drops everything cached. For a test that wants a cold start. */
    internal fun reset() {
        meshes.clear()
        tessellations = 0
    }

    /**
     * [path]'s stroked triangles, tessellated once per distinct geometry.
     *
     * Keyed on the *placed* path, so a stroked path that moves misses every frame. That is the
     * honest limit: making position irrelevant needs the path normalised to its origin first, which
     * costs a full walk of its commands -- worth doing only if something that moves turns out to
     * stroke every frame.
     */
    internal fun mesh(path: DrawPath, stroke: DrawStroke, color: Color): ColoredTriangleMesh {
        val key = Key(path, stroke, color)
        meshes.remove(key)?.let { cached ->
            meshes[key] = cached
            return cached
        }
        val mesh = path.tessellateStrokeAa(stroke, color)
        tessellations++
        meshes[key] = mesh
        if (meshes.size > CAPACITY) meshes.remove(meshes.keys.first())
        return mesh
    }
}
