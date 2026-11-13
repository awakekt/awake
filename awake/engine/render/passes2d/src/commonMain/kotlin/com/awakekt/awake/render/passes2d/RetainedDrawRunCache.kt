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
    internal val capacity: Int = DEFAULT_CAPACITY,
) {
    private class Entry(
        val maxQuadsPerRun: Int,
        val primitives: List<DrawCommand>,
        val pathClips: List<DrawPath>,
        val safeInteriorRect: Rectangle?,
        val runs: List<StagedDrawRun>,
        var frame: Long,
    ) {
        fun matches(
            maxQuadsPerRun: Int,
            primitives: List<DrawCommand>,
            pathClips: List<DrawPath>,
            safeInteriorRect: Rectangle?,
        ): Boolean =
            this.maxQuadsPerRun == maxQuadsPerRun &&
                this.primitives.retainedEquals(primitives) &&
                (this.pathClips == pathClips && this.safeInteriorRect == safeInteriorRect)
    }

    /** Spans by retained hash, oldest stored first. A hash collision keeps the newer span. */
    private val entries = LinkedHashMap<Int, Entry>()

    /** The frame [beginFrame] opened; an entry used in it is live and is not evicted. 0 until a
     * caller opens frames, and then nothing is live: a cache used without frames stays LRU-bounded. */
    private var frame = 0L

    /**
     * The most spans held, live or not. [capacity] bounds only the spans no frame uses any more:
     * the spans of the frame being drawn are never evicted for each other, because a frame with
     * more spans than [capacity] would then evict each span before the next frame reached it, and
     * re-stage and re-upload it forever (#1545).
     */
    internal val maxCapacity: Int = capacity * MAX_LIVE_SPANS_PER_CAPACITY

    /** The hash of the span the last [find] missed, so a miss can be recognised next frame without
     * hashing it again; see [StableSpanTracker]. */
    internal var lastMissHash: Int = 0
        private set

    /** Opens a frame: spans not found or stored from now on become evictable once over [capacity]. */
    internal fun beginFrame() {
        frame += 1
    }

    /** Returns a retained span and marks it used in this frame, or `null` on a miss. A hit moves
     * nothing, so a still frame allocates no map nodes. */
    fun find(
        primitives: List<DrawCommand>,
        maxQuadsPerRun: Int,
        pathClips: List<DrawPath> = emptyList(),
        safeInteriorRect: Rectangle? = null,
    ): List<StagedDrawRun>? {
        val hash = retainedHash(primitives, pathClips, safeInteriorRect)
        val entry = entries[hash]
        if (entry == null || !entry.matches(maxQuadsPerRun, primitives, pathClips, safeInteriorRect)) {
            lastMissHash = hash
            return null
        }
        entry.frame = frame
        return entry.runs
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
        entries.remove(hash)
        entries[hash] = Entry(maxQuadsPerRun, snapshot, clipSnapshot, safeInteriorRect, runs.toList(), frame)
        evict()
    }

    /**
     * Past [capacity], drops the oldest spans the current frame has not used, then, only past
     * [maxCapacity], live ones too. Without frames ([beginFrame] never called), it drops the oldest.
     */
    private fun evict() {
        if (entries.size <= capacity) return
        val unused = entries.values.iterator()
        while (entries.size > capacity && unused.hasNext()) {
            val entry = unused.next()
            if (frame == 0L || entry.frame != frame) unused.remove()
        }
        val oldest = entries.values.iterator()
        while (entries.size > maxCapacity) {
            oldest.next()
            oldest.remove()
        }
    }

    /** Clears all retained entries from the cache. */
    fun clear() = entries.clear()

    private fun retainedHash(
        primitives: List<DrawCommand>,
        pathClips: List<DrawPath>,
        safeInteriorRect: Rectangle?,
    ): Int = 31 * (31 * primitives.retainedHashCode() + pathClips.hashCode()) + (safeInteriorRect?.hashCode() ?: 0)

    /** Cache size and configuration constants. */
    companion object {
        /** Keeps the common case bounded without making large Studio screens churn the cache. */
        const val DEFAULT_CAPACITY = 256

        /** [maxCapacity] over [capacity]: a frame of up to 16,384 spans by default stays retained. */
        private const val MAX_LIVE_SPANS_PER_CAPACITY = 64
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

/** Index loop rather than `indices.all`, which allocates an `IntRange` on every lookup. */
private fun List<DrawCommand>.retainedEquals(other: List<DrawCommand>): Boolean {
    var index = if (size == other.size) 0 else -1
    while (index in 0 until size) index = if (get(index).retainedEquals(other[index])) index + 1 else -1
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
