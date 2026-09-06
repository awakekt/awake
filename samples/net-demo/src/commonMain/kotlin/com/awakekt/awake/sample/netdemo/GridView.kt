/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.netdemo

import com.awakekt.awake.net.NetId
import kotlin.math.roundToInt

/**
 * The demo's whole renderer: an ASCII grid of world-absolute positions. Common so the terminal
 * client and the browser client draw the same thing from the same snapshot -- if they ever
 * disagree it is a replication bug, not a rendering one.
 */
object GridView {
    const val WIDTH = 41
    const val HEIGHT = 17

    private const val WORLD_HALF_EXTENT = 6f
    private const val EMPTY = '.'
    private const val SELF = '@'
    private const val OTHER = 'o'

    fun render(snapshot: SnapshotBuffer, self: NetId?): String {
        val grid = Array(HEIGHT) { CharArray(WIDTH) { EMPTY } }
        for (index in 0 until snapshot.count) {
            val column = project(snapshot.x[index], WIDTH)
            val row = project(snapshot.y[index], HEIGHT)
            grid[row][column] = if (snapshot.netIds[index] == self?.value) SELF else OTHER
        }
        return grid.joinToString("\n") { it.concatToString() }
    }

    fun status(snapshot: SnapshotBuffer): String =
        "tick ${snapshot.tick}  players ${snapshot.count}   $SELF = you"

    /** Maps a world-absolute coordinate onto a grid cell, clamped to the visible window. */
    private fun project(value: Float, cells: Int): Int {
        val normalized = (value + WORLD_HALF_EXTENT) / (2 * WORLD_HALF_EXTENT)
        return (normalized * (cells - 1)).roundToInt().coerceIn(0, cells - 1)
    }
}

/**
 * The circular walk both clients perform. Shared so neither platform's demo drifts into a
 * different motion and makes a comparison meaningless.
 */
class OrbitInput(phaseOffset: Float = 0f, private val radiansPerTick: Float = DEFAULT_STEP) {
    private var angle = phaseOffset
    var tick: Long = 0
        private set

    fun advance(): Pair<Float, Float> {
        val current = angle
        angle += radiansPerTick
        tick += 1
        return kotlin.math.cos(current) to kotlin.math.sin(current)
    }

    companion object {
        const val SEND_HZ = 20
        private const val RADIANS_PER_SECOND = 1.2f
        private const val DEFAULT_STEP = RADIANS_PER_SECOND / SEND_HZ
    }
}
