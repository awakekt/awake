/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.world

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.diagnostics.DebugLines
import com.awakekt.awake.core.diagnostics.DebugRows
import com.awakekt.awake.core.diagnostics.DebugSource
import com.awakekt.awake.core.math.Vec3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A world partition's streaming on the debug panel and over the ground, read from its [probe]. Its id is
 * [ID], `cells`: `--debug=cells` on desktop, `?debug=cells` on the web.
 *
 * - **Rows**: *Cell*, the observer's cell as `x, z`, or `--` with no observer; *Reading*, *Shown* and *Leaving*, how
 *   many active cells are in each [WorldCellState].
 * - **Lines**: each active cell's square outline, coloured by its state ([READING], [SHOWN], [LEAVING]), and the
 *   loading and unloading circles around the observer ([LOAD], [UNLOAD]) in [CIRCLE_SEGMENTS] lines each. They lie
 *   flat [LIFT] metres over the observer's height, so tag the player rather than the camera; a camera's own height
 *   would show them edge on.
 *
 * It measures nothing: the partition keeps [probe] whether or not this source is on. Writing its lines allocates
 * nothing, as it reuses two points of its own; its rows allocate the cell's text only when the observer changes cell.
 */
class WorldPartitionDebugSource(private val probe: WorldPartitionProbe) : DebugSource {
    override val id: String = ID
    override val title: String = "Cells"

    // The pen: where the next line starts and ends, at this frame's height. Reused, so drawing allocates nothing.
    private val a = Vec3f(0f, 0f, 0f)
    private val b = Vec3f(0f, 0f, 0f)
    private var height = 0f

    private var cellText = NONE
    private var textX = 0
    private var textZ = 0

    override fun rows(out: DebugRows) {
        out.row("Cell", cellText())
        out.row("Reading", probe.reading)
        out.row("Shown", probe.shown)
        out.row("Leaving", probe.leaving)
    }

    override fun lines(out: DebugLines) {
        if (!probe.hasObserver) return
        val config = probe.config
        val size = config.cellSize
        // Cells are numbered in absolute space; drawing happens where a floating origin has put the world.
        val shiftX = probe.observer.x - probe.observerLocal.x
        val shiftZ = probe.observer.z - probe.observerLocal.z
        height = probe.observerLocal.y + LIFT
        for (i in 0 until probe.cellCount) {
            val x0 = probe.cellXAt(i) * size - shiftX
            val z0 = probe.cellZAt(i) * size - shiftZ
            val x1 = x0 + size
            val z1 = z0 + size
            val color = colorOf(probe.stateAt(i))
            moveTo(x0, z0)
            lineTo(out, x1, z0, color)
            lineTo(out, x1, z1, color)
            lineTo(out, x0, z1, color)
            lineTo(out, x0, z0, color)
        }
        circle(out, probe.observerLocal.x, probe.observerLocal.z, config.loadingRadius, LOAD)
        circle(out, probe.observerLocal.x, probe.observerLocal.z, config.unloadRadius, UNLOAD)
    }

    private fun cellText(): String {
        if (!probe.hasObserver) return NONE
        if (cellText === NONE || probe.observerCellX != textX || probe.observerCellZ != textZ) {
            textX = probe.observerCellX
            textZ = probe.observerCellZ
            cellText = "$textX, $textZ"
        }
        return cellText
    }

    /** A circle of [radius] around ([cx], [cz]), in [CIRCLE_SEGMENTS] lines, each starting where the last ended. */
    private fun circle(out: DebugLines, cx: Float, cz: Float, radius: Float, color: Color) {
        moveTo(cx + radius, cz)
        for (i in 1..CIRCLE_SEGMENTS) {
            val angle = TURN * i / CIRCLE_SEGMENTS
            lineTo(out, cx + radius * cos(angle), cz + radius * sin(angle), color)
        }
    }

    private fun moveTo(x: Float, z: Float) {
        a.set(x, height, z)
    }

    /** A line from where the last one ended to ([x], [z]). */
    private fun lineTo(out: DebugLines, x: Float, z: Float, color: Color) {
        b.set(x, height, z)
        out.line(a, b, color)
        a.set(b)
    }

    /** The source's id, and its colours and sizes. */
    companion object {
        /** What turns it on: `--debug=cells`, `?debug=cells`. */
        const val ID: String = "cells"

        /** Lines a cell's outline takes. */
        const val CELL_LINES: Int = 4

        /** Lines each radius circle takes. */
        const val CIRCLE_SEGMENTS: Int = 48

        /** How far over the observer's height the lines lie, in metres, so they don't sink into the ground. */
        const val LIFT: Float = 0.15f

        /** A cell still [WorldCellState.Reading]: yellow. */
        val READING: Color = Color(1f, 0.9f, 0.1f)

        /** A cell [WorldCellState.Shown]: green. */
        val SHOWN: Color = Color(0.2f, 0.9f, 0.3f)

        /** A cell [WorldCellState.Leaving]: red. */
        val LEAVING: Color = Color(1f, 0.2f, 0.15f)

        /** The loading radius circle: blue. */
        val LOAD: Color = Color(0.3f, 0.7f, 1f)

        /** The unloading radius circle: violet. */
        val UNLOAD: Color = Color(0.7f, 0.4f, 1f)

        /** The colour a cell in [state] is outlined in. */
        fun colorOf(state: WorldCellState): Color = when (state) {
            WorldCellState.Reading -> READING
            WorldCellState.Shown -> SHOWN
            WorldCellState.Leaving -> LEAVING
        }

        private const val NONE = "--"
        private const val TURN = (2 * PI).toFloat()
    }
}
