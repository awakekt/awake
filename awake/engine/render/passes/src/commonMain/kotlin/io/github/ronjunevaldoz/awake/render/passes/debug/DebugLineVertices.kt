// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.render.passes.debug

import io.github.ronjunevaldoz.awake.render.passes.debug.DebugLineLayout
import io.github.ronjunevaldoz.awake.core.color.Color
import io.github.ronjunevaldoz.awake.render.renderer.LineSegment

/** Writes a 3D debug line vertex (7 floats). */
fun writeLineVertex(
    out: FloatArray,
    offset: Int,
    x: Float,
    y: Float,
    z: Float,
    color: Color,
) {
    out[offset] = x
    out[offset + 1] = y
    out[offset + 2] = z
    out[offset + 3] = color.r
    out[offset + 4] = color.g
    out[offset + 5] = color.b
    out[offset + 6] = color.a
}

/**
 * Packs [lines] into one interleaved `LINE_LIST` vertex array -- two vertices per segment, no
 * index buffer.
 *
 * Backend-neutral on purpose: both backends staged this identical loop around the already-shared
 * [writeLineVertex] until it was hoisted here. The caller uploads the result into its own mesh.
 *
 * @param lines This frame's world-space debug segments.
 * @return `lines.size * VERTICES_PER_LINE * LINE_FLOATS_PER_VERTEX` floats.
 */
fun lineSegmentVertices(lines: List<LineSegment>): FloatArray {
    val stride = DebugLineLayout.FLOATS_PER_VERTEX
    val vertices = FloatArray(lines.size * DebugLineLayout.VERTICES_PER_LINE * stride)
    var lineIndex = 0
    while (lineIndex < lines.size) {
        val line = lines[lineIndex]
        val vertexBase = lineIndex * DebugLineLayout.VERTICES_PER_LINE * stride
        writeLineVertex(vertices, vertexBase, line.start.x, line.start.y, line.start.z, line.color)
        writeLineVertex(
            vertices,
            vertexBase + stride,
            line.end.x,
            line.end.y,
            line.end.z,
            line.color,
        )
        lineIndex += 1
    }
    return vertices
}
