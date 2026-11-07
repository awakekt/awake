/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.trail

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.core.math.Vec3f

/**
 * A strip of up to [capacity] point pairs, drawn as one ribbon from the first pair to the [visible]th.
 *
 * Pair `i` is two points, `a` and `b`, each with its own straight-alpha colour. The ribbon runs from
 * pair to pair; a texture's `u` runs along it and its `v` across it, 0 at `a` and 1 at `b`. The points
 * are in the space of whatever places the strip: an entity's local space when a scene draws it.
 *
 * The strip keeps its vertices in [vertices], laid out in [VertexFormat.PositionRgbaUv], two vertices
 * a pair, and remembers which pairs changed since [markUploaded], so the GPU copy is brought up to date
 * one range at a time. Changing [visible] changes no vertex. Nothing here allocates after construction.
 *
 * @property capacity The most pairs the strip holds, at least 2.
 */
class TrailStrip(val capacity: Int) {
    init {
        require(capacity >= 2) { "A trail needs at least 2 pairs to draw anything, not $capacity." }
    }

    /** Every pair's two vertices, interleaved in [VertexFormat.PositionRgbaUv]: pair `i` starts at `i * FLOATS_PER_PAIR`. */
    val vertices: FloatArray = FloatArray(capacity * FLOATS_PER_PAIR)

    /** How many pairs, from the first, are drawn: 0 or 1 draws nothing. Setting it uploads nothing. */
    var visible: Int = 0
        set(value) {
            require(value in 0..capacity) { "visible must be in 0..$capacity, not $value." }
            field = value
        }

    /** The first pair changed since [markUploaded], or [capacity] when none has. */
    var firstChanged: Int = capacity
        private set

    /** One past the last pair changed since [markUploaded], or 0 when none has. */
    var changedEnd: Int = 0
        private set

    /** Whether a pair changed since [markUploaded]. */
    val hasChanges: Boolean get() = changedEnd > firstChanged

    /** Counts every change to a pair's points, so a cached box knows when it is stale. */
    private var revision = 0
    private var boundsRevision = -1
    private var boundsVisible = -1
    private val boundsMin = Vec3f(0f, 0f, 0f)
    private val boundsMax = Vec3f(0f, 0f, 0f)

    /**
     * Sets pair [index]: its points [a] and [b], their colours, and how far along the strip a texture
     * reads it, [u], which by default spreads the texture once over the whole [capacity].
     */
    fun set(index: Int, a: Vec3f, b: Vec3f, colorA: Color, colorB: Color, u: Float = defaultU(index)) {
        setPoints(index, a, b)
        setColors(index, colorA, colorB)
        val base = pairBase(index)
        vertices.writeUv(base, u, 0f)
        vertices.writeUv(base + STRIDE, u, 1f)
    }

    /** Moves pair [index]'s points to [a] and [b], keeping their colours. */
    fun setPoints(index: Int, a: Vec3f, b: Vec3f) {
        val base = pairBase(index)
        vertices.writePosition(base, a)
        vertices.writePosition(base + STRIDE, b)
        revision++
        touch(index)
    }

    /** Sets pair [index]'s colours, keeping its points: how a trail fades. */
    fun setColors(index: Int, colorA: Color, colorB: Color) {
        val base = pairBase(index)
        vertices.writeColor(base, colorA.r, colorA.g, colorA.b, colorA.a)
        vertices.writeColor(base + STRIDE, colorB.r, colorB.g, colorB.b, colorB.a)
        touch(index)
    }

    /** Sets both of pair [index]'s colours to one straight-alpha colour, without a [Color] to build. */
    fun setColor(index: Int, r: Float, g: Float, b: Float, a: Float) {
        val base = pairBase(index)
        vertices.writeColor(base, r, g, b, a)
        vertices.writeColor(base + STRIDE, r, g, b, a)
        touch(index)
    }

    /** Forgets every change: the caller has copied pairs [firstChanged] until [changedEnd] to the GPU. */
    fun markUploaded() {
        firstChanged = capacity
        changedEnd = 0
    }

    /** Marks every pair changed, so the next upload sends the whole strip, as a new GPU copy needs. */
    fun markAllChanged() {
        firstChanged = 0
        changedEnd = capacity
    }

    /**
     * Writes the box around the [visible] pairs' points into [min] and [max], in the strip's own
     * space, and returns true; returns false, writing nothing, when fewer than 2 pairs are visible.
     * Cached until a point or [visible] changes.
     */
    fun visibleBounds(min: Vec3f, max: Vec3f): Boolean {
        if (visible < 2) return false
        if (boundsRevision != revision || boundsVisible != visible) measureVisible()
        min.set(boundsMin)
        max.set(boundsMax)
        return true
    }

    private fun measureVisible() {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        var offset = POSITION
        val end = visible * FLOATS_PER_PAIR
        while (offset < end) {
            val x = vertices[offset]
            val y = vertices[offset + 1]
            val z = vertices[offset + 2]
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (z < minZ) minZ = z
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
            if (z > maxZ) maxZ = z
            offset += STRIDE
        }
        boundsMin.set(minX, minY, minZ)
        boundsMax.set(maxX, maxY, maxZ)
        boundsRevision = revision
        boundsVisible = visible
    }

    private fun defaultU(index: Int): Float = index.toFloat() / (capacity - 1)

    private fun pairBase(index: Int): Int {
        if (index !in 0 until capacity) throw IndexOutOfBoundsException("Pair $index is outside the trail's $capacity.")
        return index * FLOATS_PER_PAIR
    }

    private fun touch(index: Int) {
        if (index < firstChanged) firstChanged = index
        if (index + 1 > changedEnd) changedEnd = index + 1
    }

    /** The layout every strip's [vertices] are in, derived from [VertexFormat.PositionRgbaUv]. */
    companion object {
        /** The vertex layout a strip is drawn in. */
        val FORMAT: VertexFormat = VertexFormat.PositionRgbaUv

        /** Vertices a pair: `a`, then `b`. */
        const val VERTICES_PER_PAIR: Int = 2

        internal val STRIDE = FORMAT.strideFloats
        internal val POSITION = FORMAT.floatOffsetOf(VertexSemantic.Position)
        internal val COLOR = FORMAT.floatOffsetOf(VertexSemantic.Color)
        internal val UV = FORMAT.floatOffsetOf(VertexSemantic.Uv)

        /** Floats a pair occupies in [vertices]. */
        val FLOATS_PER_PAIR: Int = STRIDE * VERTICES_PER_PAIR
    }
}

private fun FloatArray.writePosition(vertex: Int, point: Vec3f) {
    this[vertex + TrailStrip.POSITION] = point.x
    this[vertex + TrailStrip.POSITION + 1] = point.y
    this[vertex + TrailStrip.POSITION + 2] = point.z
}

private fun FloatArray.writeColor(vertex: Int, r: Float, g: Float, b: Float, a: Float) {
    this[vertex + TrailStrip.COLOR] = r
    this[vertex + TrailStrip.COLOR + 1] = g
    this[vertex + TrailStrip.COLOR + 2] = b
    this[vertex + TrailStrip.COLOR + 3] = a
}

private fun FloatArray.writeUv(vertex: Int, u: Float, v: Float) {
    this[vertex + TrailStrip.UV] = u
    this[vertex + TrailStrip.UV + 1] = v
}
