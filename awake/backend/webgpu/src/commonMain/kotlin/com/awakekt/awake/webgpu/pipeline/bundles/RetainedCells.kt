/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline.bundles

/**
 * One retained region of a pass, frame after frame: its commands captured into cells, each cell
 * kept as a [B] (a render bundle) while its [CellStream.sameShape] holds.
 *
 * A cell is a run of draws under one pipeline. A new one starts at a pipeline bind, or at the
 * mesh bind (vertex slot 0) that opens a draw, by that mesh: once the cell holds [MIN_CELL_DRAWS],
 * at a mesh buffer whose hash falls on [BOUNDARY_MASK], and always at [MAX_CELL_DRAWS]. The mesh,
 * not the material group: a shadow cascade's casters all share one depth group. Boundaries go with
 * the draws, not their positions, so a draw entering or leaving re-records only the cell it is in,
 * where boundaries counted in draws would shift every cell after it. A bundle starts with nothing
 * bound, so each cell begins with the pipeline, and before its first draw binds whatever is still
 * in force that it has not bound itself. Cells are matched across frames by their head (the
 * pipeline and the first draw's mesh). A cell whose shape held but whose counts changed rewrites
 * its indirect arguments once ([writeCounts]); one that held both costs nothing.
 *
 * GPU-free: [record], [writeCounts] and [release] are the backend's.
 */
internal class RetainedCells<B : Any>(
    private val record: (CellStream) -> B,
    private val writeCounts: (B, CellStream) -> Unit,
    private val release: (B) -> Unit,
) {
    private class Cell<B : Any>(val stream: CellStream = CellStream(), var bundle: B? = null, var matched: Boolean = false)

    private val kept = ArrayList<Cell<B>>()
    private val scratch = ArrayList<CellStream>()
    private var open = 0
    private val bundles = ArrayList<B>()

    // What is bound now, to seed a cell: a bundle inherits nothing from the one before it.
    private var pipeline: Any? = null
    private val groups = arrayOfNulls<Any>(MAX_SLOTS)
    private val vertexBuffers = arrayOfNulls<Any>(MAX_SLOTS)
    private var indexBuffer: Any? = null

    /** What the open cell has bound itself: groups in bits 0-7, vertex slots in 8-15, the index buffer in 16. */
    private var ownBinds = 0

    /** Cells recorded, and cells whose counts alone were written, by the last [end]: for tests and probes. */
    var recorded = 0
        private set
    var countWrites = 0
        private set

    fun begin() {
        open = 0
        pipeline = null
        groups.fill(null)
        vertexBuffers.fill(null)
        indexBuffer = null
    }

    fun bindPipeline(ref: Any) {
        pipeline = ref
        groups.fill(null)
        startCell()
    }

    fun bindGroup(group: Int, ref: Any) {
        if (group < MAX_SLOTS) {
            groups[group] = ref
            ownBinds = ownBinds or (1 shl group)
        }
        current().bind(CellStream.GROUP, group, ref)
    }

    /** Slot 0 is the draw's mesh, the first bind of a draw, where a cell may end. */
    fun bindVertexBuffer(slot: Int, ref: Any) {
        if (slot == 0 && endsCell(current().drawCount, ref)) startCell()
        if (slot == 0 && current().headMesh == null) current().headMesh = ref
        if (slot < MAX_SLOTS) {
            vertexBuffers[slot] = ref
            ownBinds = ownBinds or (1 shl (slot + MAX_SLOTS))
        }
        current().bind(CellStream.VERTEX, slot, ref)
    }

    fun bindIndexBuffer(ref: Any) {
        indexBuffer = ref
        ownBinds = ownBinds or INDEX_BIT
        current().bind(CellStream.INDEX, 0, ref)
    }

    /** Before a cell's first draw, binds into it what is in force that it has not bound itself. */
    fun draw(indexed: Boolean, elements: Int, instances: Int) {
        val stream = current()
        if (stream.drawCount == 0) {
            groups.forEachIndexed { group, ref -> if (ref != null && ownBinds and (1 shl group) == 0) stream.bind(CellStream.GROUP, group, ref) }
            vertexBuffers.forEachIndexed { slot, ref ->
                if (ref != null && ownBinds and (1 shl (slot + MAX_SLOTS)) == 0) stream.bind(CellStream.VERTEX, slot, ref)
            }
            indexBuffer?.let { if (ownBinds and INDEX_BIT == 0) stream.bind(CellStream.INDEX, 0, it) }
        }
        stream.draw(if (indexed) CellStream.DRAW_INDEXED else CellStream.DRAW, elements, instances)
    }

    /** Settles this frame's cells against the last frame's; the bundles to execute, in order. */
    fun end(): List<B> {
        recorded = 0
        countWrites = 0
        kept.forEach { it.matched = false }
        bundles.clear()
        val next = ArrayList<Cell<B>>(open)
        if (open > 0) scratch[open - 1].endAtLastDraw()
        for (index in 0 until open) {
            val stream = scratch[index]
            if (stream.drawCount == 0) continue
            val cell = kept.firstOrNull { !it.matched && it.stream.headPipeline === stream.headPipeline && it.stream.headMesh === stream.headMesh }
            next += settle(cell, stream)
        }
        kept.forEach { if (!it.matched) it.bundle?.let(release) }
        kept.clear()
        kept.addAll(next)
        return bundles
    }

    /** Forgets every cell, releasing its bundle. */
    fun clear() {
        kept.forEach { cell -> cell.bundle?.let(release) }
        kept.clear()
    }

    private fun settle(found: Cell<B>?, stream: CellStream): Cell<B> {
        val cell = found ?: Cell()
        cell.matched = true
        val bundle = cell.bundle
        when {
            bundle != null && cell.stream.sameShape(stream) -> if (!cell.stream.sameCounts(stream)) {
                cell.stream.copyCountsFrom(stream)
                writeCounts(bundle, cell.stream)
                countWrites += 1
            }
            else -> {
                bundle?.let(release)
                cell.stream.copyFrom(stream)
                cell.bundle = record(cell.stream)
                recorded += 1
            }
        }
        bundles += checkNotNull(cell.bundle)
        return cell
    }

    private fun current(): CellStream {
        if (open == 0) startCell()
        return scratch[open - 1]
    }

    private fun startCell() {
        if (open > 0) scratch[open - 1].endAtLastDraw()
        if (open == scratch.size) scratch += CellStream()
        val stream = scratch[open++].apply { clear() }
        ownBinds = 0
        val pipeline = pipeline ?: return
        stream.headPipeline = pipeline
        stream.bind(CellStream.PIPELINE, 0, pipeline)
    }

    private companion object {
        /** WebGPU's guaranteed `maxVertexBuffers`, at least `maxBindGroups`. */
        const val MAX_SLOTS = 8
        const val INDEX_BIT = 1 shl 16

        fun endsCell(draws: Int, mesh: Any): Boolean =
            draws >= MAX_CELL_DRAWS || draws >= MIN_CELL_DRAWS && mesh.hashCode() and BOUNDARY_MASK == 0

        /** A cell's draws: few enough that a change re-records little, many enough that a pass is a few bundles. */
        const val MIN_CELL_DRAWS = 16
        const val MAX_CELL_DRAWS = 96

        /** One mesh in sixteen ends a cell past [MIN_CELL_DRAWS]: about 32 draws a cell. */
        const val BOUNDARY_MASK = 15
    }
}
