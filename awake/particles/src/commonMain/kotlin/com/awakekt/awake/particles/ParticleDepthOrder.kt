/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.particles

/** Runs this long are insertion-sorted before merging, where insertion sort beats merging. */
private const val INSERTION_RUN = 16

/**
 * One emitter's visible particles and their squared distances from the eye, and the far-to-near
 * sort [ParticleDrawBuilder] draws them in.
 *
 * The sort is a bottom-up merge sort over [INSERTION_RUN]-long insertion-sorted runs: `O(n log n)`,
 * and stable, so particles at equal distance keep their slot order exactly as the insertion sort it
 * replaced did. It merges into scratch arrays sized once to [capacity], so sorting allocates nothing.
 */
internal class ParticleDepthOrder(capacity: Int) {
    /** Slot indices, in the order written and then, after [sortFarToNear], far to near. */
    val indices: IntArray = IntArray(capacity)

    /** Squared eye distances, parallel to [indices]. */
    val distances: FloatArray = FloatArray(capacity)

    private val scratchIndices = IntArray(capacity)
    private val scratchDistances = FloatArray(capacity)

    /** Sorts the first [count] entries of [indices] and [distances] by descending distance, stably. */
    fun sortFarToNear(count: Int) {
        var runStart = 0
        while (runStart < count) {
            insertionSort(runStart, minOf(runStart + INSERTION_RUN, count))
            runStart += INSERTION_RUN
        }
        var inPrimary = true
        var width = INSERTION_RUN
        while (width < count) {
            var left = 0
            while (left < count) {
                val mid = minOf(left + width, count)
                val right = minOf(mid + width, count)
                merge(inPrimary, left, mid, right)
                left = right
            }
            inPrimary = !inPrimary
            width *= 2
        }
        if (!inPrimary) {
            scratchIndices.copyInto(indices, 0, 0, count)
            scratchDistances.copyInto(distances, 0, 0, count)
        }
    }

    /** Stable descending insertion sort of `[from, until)` in the primary arrays. */
    private fun insertionSort(from: Int, until: Int) {
        for (i in from + 1 until until) {
            val keySlot = indices[i]
            val keyDistance = distances[i]
            var j = i - 1
            while (j >= from && distances[j] < keyDistance) {
                indices[j + 1] = indices[j]
                distances[j + 1] = distances[j]
                j -= 1
            }
            indices[j + 1] = keySlot
            distances[j + 1] = keyDistance
        }
    }

    /**
     * Merges the sorted runs `[left, mid)` and `[mid, right)` of the primary arrays into the scratch
     * ones when [fromPrimary], or back the other way. A right-run entry goes first only when it is
     * strictly farther, so ties keep their order.
     */
    private fun merge(fromPrimary: Boolean, left: Int, mid: Int, right: Int) {
        val fromIndices = if (fromPrimary) indices else scratchIndices
        val fromDistances = if (fromPrimary) distances else scratchDistances
        val toIndices = if (fromPrimary) scratchIndices else indices
        val toDistances = if (fromPrimary) scratchDistances else distances
        var i = left
        var j = mid
        var k = left
        while (i < mid && j < right) {
            if (fromDistances[i] < fromDistances[j]) {
                toIndices[k] = fromIndices[j]
                toDistances[k] = fromDistances[j]
                j += 1
            } else {
                toIndices[k] = fromIndices[i]
                toDistances[k] = fromDistances[i]
                i += 1
            }
            k += 1
        }
        if (i < mid) {
            fromIndices.copyInto(toIndices, k, i, mid)
            fromDistances.copyInto(toDistances, k, i, mid)
        } else if (j < right) {
            fromIndices.copyInto(toIndices, k, j, right)
            fromDistances.copyInto(toDistances, k, j, right)
        }
    }
}
