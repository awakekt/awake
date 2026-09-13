/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.graphics2d.DrawCommand

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
        val runs: List<StagedDrawRun>,
    )

    private val entries = ArrayList<Entry>(capacity)

    /** Returns a retained span and promotes it to most-recently-used, or `null` on a miss. */
    fun find(primitives: List<DrawCommand>, maxQuadsPerRun: Int): List<StagedDrawRun>? {
        val hash = primitives.hashCode()
        var index = entries.lastIndex
        while (index >= 0) {
            val entry = entries[index]
            if (
                entry.hash == hash &&
                entry.maxQuadsPerRun == maxQuadsPerRun &&
                entry.primitives == primitives
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
        runs: List<StagedDrawRun>,
    ) {
        if (capacity == 0 || primitives.isEmpty()) return
        val snapshot = primitives.map(DrawCommand::retentionSnapshot)
        val hash = snapshot.hashCode()
        entries.removeAll {
            it.hash == hash &&
                it.maxQuadsPerRun == maxQuadsPerRun &&
                it.primitives == snapshot
        }
        entries.add(
            Entry(
                hash = hash,
                maxQuadsPerRun = maxQuadsPerRun,
                primitives = snapshot,
                runs = runs.toList(),
            ),
        )
        if (entries.size > capacity) entries.removeAt(0)
    }

    fun clear() = entries.clear()

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
    is DrawCommand.Mesh -> copy(
        mesh = mesh.copy(
            vertices = mesh.vertices.toList(),
            indices = mesh.indices.copyOf(),
        ),
    )
    is DrawCommand.FilledPath -> copy(path = path.copy(commands = path.commands.toList()))
    is DrawCommand.StrokedPath -> copy(path = path.copy(commands = path.commands.toList()))
    is DrawCommand.ClipPathPush -> copy(path = path.copy(commands = path.commands.toList()))
    else -> this
}
