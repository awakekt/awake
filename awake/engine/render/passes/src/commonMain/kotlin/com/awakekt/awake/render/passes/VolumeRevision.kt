/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

/**
 * Counts the times a volume's content differs from when it was last read. It compares what the
 * volume covers, not how it was written, so a volume rebuilt every frame to the same content keeps
 * its revision. Allocates only when the content grows past what it has seen.
 */
internal class VolumeRevision {
    private var counter = 0
    private var planes = FloatArray(0)
    private var boxes = FloatArray(0)
    private var cascades = -1
    private var points = -1

    fun read(cascadeCount: Int, livePlanes: FloatArray, planeFloats: Int, pointCount: Int, liveBoxes: FloatArray, boxFloats: Int): Int {
        val changed = cascadeCount != cascades || pointCount != points ||
            differs(livePlanes, planes, planeFloats) || differs(liveBoxes, boxes, boxFloats)
        if (changed) {
            cascades = cascadeCount
            points = pointCount
            planes = livePlanes.copyInto(planes.sizedFor(planeFloats), endIndex = planeFloats)
            boxes = liveBoxes.copyInto(boxes.sizedFor(boxFloats), endIndex = boxFloats)
            counter++
        }
        return counter
    }

    private fun differs(live: FloatArray, snapshot: FloatArray, count: Int): Boolean {
        for (i in 0 until count) if (live[i] != snapshot[i]) return true
        return false
    }

    private fun FloatArray.sizedFor(count: Int): FloatArray = if (size >= count) this else FloatArray(count)
}
