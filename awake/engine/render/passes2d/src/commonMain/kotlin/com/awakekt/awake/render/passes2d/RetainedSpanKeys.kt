/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.graphics2d.DrawCommand

/**
 * Protects retention keys from callers that reuse and mutate mesh/path backing collections.
 * Scalar draw commands and texture material handles are immutable for key purposes and can be
 * retained as-is.
 */
internal fun DrawCommand.retentionSnapshot(): DrawCommand = when (this) {
    is DrawCommand.Mesh -> if (retainedGeometryKey == null) {
        copy(
            mesh = mesh.copy(
                vertices = mesh.vertices.toList(),
                indices = mesh.indices.copyOf(),
            ),
        )
    } else {
        copy().also { it.retainedGeometryKey = retainedGeometryKey }
    }
    is DrawCommand.FilledPath -> copy(path = path.copy(commands = path.commands.toList()))
    is DrawCommand.StrokedPath -> copy(path = path.copy(commands = path.commands.toList()))
    is DrawCommand.ClipPathPush -> copy(path = path.copy(commands = path.commands.toList()))
    else -> this
}

/** The retained hash of `this[from until to]`, with no sub-list. */
internal fun List<DrawCommand>.retainedHashCode(from: Int = 0, to: Int = size): Int {
    var result = 1
    for (index in from until to) result = 31 * result + get(index).retainedHashCode()
    return result
}

private fun DrawCommand.retainedHashCode(): Int {
    val meshCommand = this as? DrawCommand.Mesh
    val key = meshCommand?.retainedGeometryKey
    return if (meshCommand == null || key == null) {
        hashCode()
    } else {
        var result = key.hashCode()
        result = 31 * result + meshCommand.mesh.vertices.size
        result = 31 * result + meshCommand.mesh.indices.size
        result = 31 * result + meshCommand.offsetX.hashCode()
        result = 31 * result + meshCommand.offsetY.hashCode()
        result = 31 * result + meshCommand.scaleX.hashCode()
        result = 31 * result + meshCommand.scaleY.hashCode()
        result = 31 * result + meshCommand.alpha.hashCode()
        result = 31 * result + (meshCommand.tokenId?.hashCode() ?: 0)
        result = 31 * result + meshCommand.rotationDegrees.hashCode()
        result = 31 * result + meshCommand.pivotX.hashCode()
        31 * result + meshCommand.pivotY.hashCode()
    }
}

/** Whether this equals `other[from until to]`, by index, with no sub-list or iterator. */
internal fun List<DrawCommand>.retainedEquals(other: List<DrawCommand>, from: Int = 0, to: Int = other.size): Boolean {
    var index = if (size == to - from) 0 else -1
    while (index in 0 until size) index = if (get(index).retainedEquals(other[from + index])) index + 1 else -1
    return index == size
}

private fun DrawCommand.retainedEquals(other: DrawCommand): Boolean =
    when {
        this === other -> true
        this is DrawCommand.Mesh && other is DrawCommand.Mesh && hasSameRetainedGeometry(other) ->
            hasSamePlacement(other)
        else -> this == other
    }

private fun DrawCommand.Mesh.hasSameRetainedGeometry(other: DrawCommand.Mesh): Boolean =
    retainedGeometryKey != null &&
        retainedGeometryKey === other.retainedGeometryKey &&
        mesh === other.mesh

private fun DrawCommand.Mesh.hasSamePlacement(other: DrawCommand.Mesh): Boolean =
    hasSamePosition(other) && hasSameScale(other) && hasSameAppearance(other)

private fun DrawCommand.Mesh.hasSamePosition(other: DrawCommand.Mesh): Boolean =
    offsetX == other.offsetX && offsetY == other.offsetY

private fun DrawCommand.Mesh.hasSameScale(other: DrawCommand.Mesh): Boolean =
    scaleX == other.scaleX && scaleY == other.scaleY

private fun DrawCommand.Mesh.hasSameAppearance(other: DrawCommand.Mesh): Boolean =
    alpha == other.alpha &&
        tokenId == other.tokenId &&
        rotationDegrees == other.rotationDegrees &&
        pivotX == other.pivotX &&
        pivotY == other.pivotY
