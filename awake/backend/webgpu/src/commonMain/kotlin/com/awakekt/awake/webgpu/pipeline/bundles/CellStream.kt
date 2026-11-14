/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline.bundles

/**
 * One cell's commands as captured this frame: binds by reference and draws by their counts.
 *
 * A cell's identity is its [shape], the op sequence and every object it binds, and not its
 * counts: a draw whose instance count culling changed is the same draw. Counts go to the cell's
 * indirect arguments instead, so a cell whose shape held keeps its bundle and only rewrites them.
 * GPU-free: references are whatever the backend binds, compared by identity.
 */
internal class CellStream {
    var size = 0
        private set

    /** Each entry's op, its slot (a group index, a vertex slot, or a draw's number), and what it binds. */
    var ops = IntArray(INITIAL)
        private set
    var slots = IntArray(INITIAL)
        private set
    var refs = arrayOfNulls<Any>(INITIAL)
        private set

    /** Two ints a draw: its element count, then its instance count. */
    var counts = IntArray(INITIAL)
        private set
    var drawCount = 0
        private set

    /** The pipeline and the first draw's mesh: how cells are matched across frames. */
    var headPipeline: Any? = null
    var headMesh: Any? = null

    fun clear() {
        size = 0
        drawCount = 0
        headPipeline = null
        headMesh = null
        refs.fill(null)
    }

    /** Appends a bind of [ref] at [slot] (a group index or a vertex slot; unused for a pipeline or an index buffer). */
    fun bind(op: Int, slot: Int, ref: Any) = push(op, slot, ref)

    /** Appends a draw; [slot] numbers it within the cell, its counts kept apart from the shape. */
    fun draw(op: Int, elements: Int, instances: Int) {
        if (counts.size < (drawCount + 1) * 2) counts = counts.copyOf(counts.size * 2)
        counts[drawCount * 2] = elements
        counts[drawCount * 2 + 1] = instances
        push(op, drawCount, null)
        drawCount += 1
    }

    /** Drops binds after the last draw: they draw nothing here, and the next cell binds what it needs itself. */
    fun endAtLastDraw() {
        while (size > 0 && ops[size - 1] != DRAW && ops[size - 1] != DRAW_INDEXED) refs[--size] = null
    }

    /** Whether [other] binds the same objects in the same order and draws as many times. */
    fun sameShape(other: CellStream): Boolean {
        if (size != other.size || drawCount != other.drawCount) return false
        var index = 0
        while (index < size && sameAt(other, index)) index++
        return index == size
    }

    /** Whether [other] draws with the same counts; meaningful only when [sameShape]. */
    fun sameCounts(other: CellStream): Boolean {
        var index = 0
        val end = drawCount * 2
        while (index < end && counts[index] == other.counts[index]) index++
        return index == end
    }

    /** Makes this an exact copy of [other], reusing this one's arrays where they are large enough. */
    fun copyFrom(other: CellStream) {
        clear()
        if (ops.size < other.size) grow(other.size)
        other.ops.copyInto(ops, 0, 0, other.size)
        other.slots.copyInto(slots, 0, 0, other.size)
        other.refs.copyInto(refs, 0, 0, other.size)
        size = other.size
        if (counts.size < other.drawCount * 2) counts = IntArray(other.counts.size)
        other.counts.copyInto(counts, 0, 0, other.drawCount * 2)
        drawCount = other.drawCount
        headPipeline = other.headPipeline
        headMesh = other.headMesh
    }

    /** Takes [other]'s counts, the shape already being the same. */
    fun copyCountsFrom(other: CellStream) = other.counts.copyInto(counts, 0, 0, other.drawCount * 2)

    private fun sameAt(other: CellStream, index: Int) =
        ops[index] == other.ops[index] && slots[index] == other.slots[index] && refs[index] === other.refs[index]

    private fun push(op: Int, slot: Int, ref: Any?) {
        if (size == ops.size) grow(size * 2)
        ops[size] = op
        slots[size] = slot
        refs[size] = ref
        size += 1
    }

    private fun grow(capacity: Int) {
        ops = ops.copyOf(capacity)
        slots = slots.copyOf(capacity)
        refs = refs.copyOf(capacity)
    }

    companion object {
        const val PIPELINE = 1
        const val GROUP = 2
        const val VERTEX = 3
        const val INDEX = 4
        const val DRAW = 5
        const val DRAW_INDEXED = 6
        private const val INITIAL = 32
    }
}
