/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.DrawPath
import com.awakekt.awake.core.math2d.Rectangle

/**
 * Bounded retained storage for the backend-neutral result of UI draw-run staging.
 *
 * UI composition still emits a fresh command list every frame, but most contiguous spans in a
 * scene are unchanged. Keeping their staged arrays avoids repeating tessellation and array
 * materialisation on the frame-critical path. The cache belongs to one renderer: staged texture
 * runs retain renderer-owned material handles and must not outlive that renderer.
 *
 * A cache hit is only valid while the returned [StagedDrawRun]s remain read-only. Uploaders consume
 * their arrays but do not mutate them; that is the contract that makes sharing them across frames
 * safe.
 */
class RetainedDrawRunCache(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private data class Entry(
        val hash: Int,
        val maxQuadsPerRun: Int,
        val primitives: List<DrawCommand>,
        val pathClips: List<DrawPath>,
        val safeInteriorRect: Rectangle?,
        val runs: List<StagedDrawRun>,
    )

    private val entries = ArrayList<Entry>(capacity)

    /** Returns a retained span and promotes it to most-recently-used, or `null` on a miss. */
    fun find(
        primitives: List<DrawCommand>,
        maxQuadsPerRun: Int,
        pathClips: List<DrawPath> = emptyList(),
        safeInteriorRect: Rectangle? = null,
    ): List<StagedDrawRun>? {
        val hash = retainedHash(primitives, pathClips, safeInteriorRect)
        var index = entries.lastIndex
        while (index >= 0) {
            val entry = entries[index]
            if (
                entry.hash == hash &&
                entry.maxQuadsPerRun == maxQuadsPerRun &&
                entry.primitives.retainedEquals(primitives) &&
                entry.pathClips == pathClips &&
                entry.safeInteriorRect == safeInteriorRect
            ) {
                entries.removeAt(index)
                entries.add(entry)
                return entry.runs
            }
            index -= 1
        }
        return null
    }

    /** Retains a copy of the command values and the staged runs produced for that span. */
    fun store(
        primitives: List<DrawCommand>,
        maxQuadsPerRun: Int,
        pathClips: List<DrawPath> = emptyList(),
        safeInteriorRect: Rectangle? = null,
        runs: List<StagedDrawRun>,
    ) {
        if (capacity == 0 || primitives.isEmpty()) return
        val snapshot = primitives.map(DrawCommand::retentionSnapshot)
        val clipSnapshot = pathClips.map { it.copy(commands = it.commands.toList()) }
        val hash = retainedHash(snapshot, clipSnapshot, safeInteriorRect)
        entries.removeAll {
            it.hash == hash &&
                it.maxQuadsPerRun == maxQuadsPerRun &&
                it.primitives.retainedEquals(snapshot) &&
                it.pathClips == clipSnapshot &&
                it.safeInteriorRect == safeInteriorRect
        }
        entries.add(
            Entry(
                hash = hash,
                maxQuadsPerRun = maxQuadsPerRun,
                primitives = snapshot,
                pathClips = clipSnapshot,
                safeInteriorRect = safeInteriorRect,
                runs = runs.toList(),
            ),
        )
        if (entries.size > capacity) entries.removeAt(0)
    }

    fun clear() = entries.clear()

    private fun retainedHash(
        primitives: List<DrawCommand>,
        pathClips: List<DrawPath>,
        safeInteriorRect: Rectangle?,
    ): Int = 31 * (31 * primitives.retainedHashCode() + pathClips.hashCode()) + (safeInteriorRect?.hashCode() ?: 0)

    companion object {
        /** Keeps the common case bounded without making large Studio screens churn the cache. */
        const val DEFAULT_CAPACITY = 256
    }
}

/**
 * Protects retention keys from callers that reuse and mutate mesh/path backing collections.
 * Scalar draw commands and texture material handles are immutable for key purposes and can be
 * retained as-is.
 */
private fun DrawCommand.retentionSnapshot(): DrawCommand = when (this) {
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

private fun List<DrawCommand>.retainedHashCode(): Int = fold(1) { result, primitive ->
    31 * result + primitive.retainedHashCode()
}

private fun DrawCommand.retainedHashCode(): Int {
    if (this !is DrawCommand.Mesh) return hashCode()
    val key = retainedGeometryKey ?: return hashCode()
    var result = key.hashCode()
    result = 31 * result + mesh.vertices.size
    result = 31 * result + mesh.indices.size
    result = 31 * result + offsetX.hashCode()
    result = 31 * result + offsetY.hashCode()
    result = 31 * result + scaleX.hashCode()
    result = 31 * result + scaleY.hashCode()
    result = 31 * result + alpha.hashCode()
    result = 31 * result + (tokenId?.hashCode() ?: 0)
    result = 31 * result + rotationDegrees.hashCode()
    result = 31 * result + pivotX.hashCode()
    return 31 * result + pivotY.hashCode()
}

private fun List<DrawCommand>.retainedEquals(other: List<DrawCommand>): Boolean {
    if (size != other.size) return false
    for (index in indices) {
        if (!get(index).retainedEquals(other[index])) return false
    }
    return true
}

private fun DrawCommand.retainedEquals(other: DrawCommand): Boolean {
    if (this === other) return true
    if (this is DrawCommand.Mesh && other is DrawCommand.Mesh) {
        val key = retainedGeometryKey
        if (key != null && key === other.retainedGeometryKey && mesh === other.mesh) {
            return offsetX == other.offsetX &&
                offsetY == other.offsetY &&
                scaleX == other.scaleX &&
                scaleY == other.scaleY &&
                alpha == other.alpha &&
                tokenId == other.tokenId &&
                rotationDegrees == other.rotationDegrees &&
                pivotX == other.pivotX &&
                pivotY == other.pivotY
        }
    }
    return this == other
}
