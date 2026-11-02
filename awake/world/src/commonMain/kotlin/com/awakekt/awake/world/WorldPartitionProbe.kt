/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.world

import com.awakekt.awake.core.math.Vec3f
import kotlin.math.floor

/** Where an active cell is in its streaming, as [WorldPartitionProbe] records it. */
enum class WorldCellState {
    /** In reach, and its load is still in flight: its content isn't in the world yet. */
    Reading,

    /** In reach and loaded. */
    Shown,

    /**
     * Loaded, its centre past the loading radius but not yet past the unloading radius: kept only by the hysteresis
     * band, and the first to go when the observer moves on.
     */
    Leaving,
}

/**
 * What a world partition streams, as of its last update: the observer, its cell, and each active cell's
 * [WorldCellState]. Plain numbers, with no reference to a cell's content, a listener or a scene, so a test or a debug
 * view reads it without holding the partition's state.
 *
 * The partition writes it every update whether or not anyone reads it; writing it allocates nothing once its arrays
 * have grown to the most cells that were ever active together. Frame thread only.
 *
 * @property config the cell size and radii it was recorded with.
 */
class WorldPartitionProbe(val config: WorldPartitionConfig) {
    /** Whether the partition streamed around an observer in its last update. */
    var hasObserver: Boolean = false
        private set

    /** The observer's absolute position, which cells are measured from. Valid while [hasObserver]. */
    val observer: Vec3f = Vec3f(0f, 0f, 0f)

    /**
     * The observer's position in the world as it is drawn: [observer] less a floating origin's shift. The same as
     * [observer] in a world that never shifts its origin. Valid while [hasObserver].
     */
    val observerLocal: Vec3f = Vec3f(0f, 0f, 0f)

    /** The column of the cell the observer is in. Valid while [hasObserver]. */
    var observerCellX: Int = 0
        private set

    /** The row of the cell the observer is in. Valid while [hasObserver]. */
    var observerCellZ: Int = 0
        private set

    /** Active cells whose load is still in flight. */
    var reading: Int = 0
        private set

    /** Active cells loaded and within the loading radius. */
    var shown: Int = 0
        private set

    /** Active cells loaded and in the band between the loading and unloading radii. */
    var leaving: Int = 0
        private set

    /** How many active cells [cellXAt], [cellZAt] and [stateAt] index: `reading + shown + leaving`. */
    var cellCount: Int = 0
        private set

    private var xs = IntArray(INITIAL_CELLS)
    private var zs = IntArray(INITIAL_CELLS)
    private var states = IntArray(INITIAL_CELLS)

    /** The column of active cell [index], `0 until cellCount`. */
    fun cellXAt(index: Int): Int = xs[checkIndex(index)]

    /** The row of active cell [index], `0 until cellCount`. */
    fun cellZAt(index: Int): Int = zs[checkIndex(index)]

    /** The state of active cell [index], `0 until cellCount`. */
    fun stateAt(index: Int): WorldCellState = WorldCellState.entries[states[checkIndex(index)]]

    /**
     * Starts a new record around an observer at [absolute], drawn at [local]: forgets the last update's cells, so the
     * partition then adds each active cell with [addCell].
     */
    fun begin(absolute: Vec3f, local: Vec3f) {
        hasObserver = true
        observer.set(absolute)
        observerLocal.set(local)
        observerCellX = floor(absolute.x / config.cellSize).toInt()
        observerCellZ = floor(absolute.z / config.cellSize).toInt()
        clearCells()
    }

    /** Records active cell ([x], [z]) in [state]. */
    fun addCell(x: Int, z: Int, state: WorldCellState) {
        if (cellCount == xs.size) {
            val grown = cellCount * 2
            xs = xs.copyOf(grown)
            zs = zs.copyOf(grown)
            states = states.copyOf(grown)
        }
        xs[cellCount] = x
        zs[cellCount] = z
        states[cellCount] = state.ordinal
        cellCount++
        when (state) {
            WorldCellState.Reading -> reading++
            WorldCellState.Shown -> shown++
            WorldCellState.Leaving -> leaving++
        }
    }

    /**
     * Records that the last update found no observer. The cells and counts stay as they were when the partition last
     * streamed, as the partition keeps those cells active.
     */
    fun lostObserver() {
        hasObserver = false
    }

    /** Forgets everything: no observer and no cells, as after the partition is cleared. */
    fun reset() {
        hasObserver = false
        clearCells()
    }

    private fun clearCells() {
        cellCount = 0
        reading = 0
        shown = 0
        leaving = 0
    }

    private fun checkIndex(index: Int): Int {
        if (index !in 0 until cellCount) throw IndexOutOfBoundsException("cell $index of $cellCount")
        return index
    }

    private companion object {
        const val INITIAL_CELLS = 64
    }
}
