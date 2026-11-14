/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline.bundles

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [RetainedCells] keeps a cell's bundle while the cell binds the same objects in the same order:
 * a frame that changes only instance counts rewrites arguments and records nothing, and a draw
 * entering or leaving re-records only the cell it is in.
 */
class RetainedCellsTest {
    private class Draw(val mesh: Any, val material: Any, val instances: Int)

    /** A bound object with a chosen hash, which decides where a cell may end. */
    private class Ref(private val hash: Int) {
        override fun hashCode() = hash
        override fun equals(other: Any?) = this === other
    }

    private val released = mutableListOf<Int>()
    private var nextBundle = 0
    private val cells = RetainedCells<Int>(record = { nextBundle++ }, writeCounts = { _, _ -> }, release = { released += it })
    private val pipeline = "pipeline"
    private val shadow = "shadow"

    /** One frame the way the shared feature records it: pipeline, then per draw its mesh, material, shadow group and draw. */
    private fun frame(draws: List<Draw>): List<Int> {
        cells.begin()
        cells.bindPipeline(pipeline)
        draws.forEach { draw ->
            cells.bindVertexBuffer(0, draw.mesh)
            cells.bindGroup(0, draw.material)
            cells.bindGroup(2, shadow)
            cells.bindIndexBuffer(draw.mesh)
            cells.draw(indexed = true, elements = 6, instances = draw.instances)
        }
        return cells.end().toList()
    }

    // Meshes whose hash ends a cell at draws 20 and 45 (and nowhere else): cells 0-19, 20-44, 45-69.
    private val meshes = List(70) { Ref(if (it == 20 || it == 45) 16 * it else it * 2 + 1) }
    private val materials = List(70) { Ref(it * 2 + 1) }

    /** [count] distinct draws, as the preparer gives each its own material group. */
    private fun draws(count: Int, instances: (Int) -> Int = { 4 }) = List(count) { Draw(meshes[it], materials[it], instances(it)) }

    @Test
    fun aStillFrameRecordsNothingAndWritesNothing() {
        val first = frame(draws(70))
        assertEquals(3, cells.recorded, "cells end where a mesh's hash says, past 16 draws")
        val again = frame(draws(70))
        assertEquals(first, again)
        assertEquals(0, cells.recorded)
        assertEquals(0, cells.countWrites)
    }

    @Test
    fun changedInstanceCountsWriteArgumentsAndRecordNothing() {
        val first = frame(draws(70))
        // Culling changes counts in the first and third cell, and a draw culled to zero stays.
        val moved = frame(
            draws(70) {
                if (it == 3) {
                    0
                } else if (it == 65) {
                    9
                } else {
                    4
                }
            },
        )
        assertEquals(first, moved)
        assertEquals(0, cells.recorded)
        assertEquals(2, cells.countWrites, "one write per cell whose counts changed")
    }

    @Test
    fun aDrawEnteringReRecordsOnlyItsOwnCell() {
        val base = draws(70)
        val first = frame(base)
        val grown = frame(base.take(30) + Draw(Ref(3), Ref(5), 1) + base.drop(30))
        assertEquals(1, cells.recorded, "only the second cell changed; the third still starts at draw 45's mesh")
        assertEquals(first[0], grown[0])
        assertEquals(first[2], grown[2])
        assertEquals(listOf(first[1]), released)
    }

    @Test
    fun aCellsShapeDoesNotTakeInTheCellBeforeIt() {
        val base = draws(70)
        frame(base)
        // The first cell's last draw changes mesh; the second cell inherits nothing of it.
        frame(base.take(19) + Draw(Ref(7), Ref(9), 4) + base.drop(20))
        assertEquals(1, cells.recorded)
    }

    @Test
    fun castersSharingOneGroupStillEndCellsByTheirMeshes() {
        val base = draws(70).map { Draw(it.mesh, shadow, it.instances) }
        frame(base)
        frame(base.filterIndexed { index, _ -> index != 30 })
        assertEquals(1, cells.recorded, "a caster leaving re-records only its cell")
    }

    @Test
    fun aCellNoLongerDrawnIsReleased() {
        val first = frame(draws(30))
        frame(draws(20))
        assertEquals(listOf(first[1]), released)
        cells.clear()
        assertEquals(listOf(first[1], first[0]), released)
    }
}
