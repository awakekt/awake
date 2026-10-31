/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.meshlod

/**
 * A min-heap of candidate collapses (point `from` onto point `to`), in parallel arrays. Entries go stale
 * instead of being removed: each carries both points' versions when it was pushed, and the caller
 * drops an entry whose versions no longer match.
 */
internal class CollapseHeap {
    private var cost = DoubleArray(INITIAL)
    private var from = IntArray(INITIAL)
    private var to = IntArray(INITIAL)
    private var fromVersion = IntArray(INITIAL)
    private var toVersion = IntArray(INITIAL)
    var size = 0
        private set

    var topCost = 0.0
        private set
    var topFrom = 0
        private set
    var topTo = 0
        private set
    var topFromVersion = 0
        private set
    var topToVersion = 0
        private set

    fun push(entryCost: Double, entryFrom: Int, entryTo: Int, versionFrom: Int, versionTo: Int) {
        if (size == cost.size) grow()
        var i = size++
        while (i > 0) {
            val parent = (i - 1) / 2
            if (cost[parent] <= entryCost) break
            move(parent, i)
            i = parent
        }
        cost[i] = entryCost
        from[i] = entryFrom
        to[i] = entryTo
        fromVersion[i] = versionFrom
        toVersion[i] = versionTo
    }

    /** Removes the cheapest entry into the `top*` properties. */
    @Suppress("LoopWithTooManyJumpStatements")
    fun pop() {
        topCost = cost[0]
        topFrom = from[0]
        topTo = to[0]
        topFromVersion = fromVersion[0]
        topToVersion = toVersion[0]
        size--
        if (size == 0) return
        val lastCost = cost[size]
        val lastFrom = from[size]
        val lastTo = to[size]
        val lastFromVersion = fromVersion[size]
        val lastToVersion = toVersion[size]
        var i = 0
        while (true) {
            val left = i * 2 + 1
            if (left >= size) break
            val child = if (left + 1 < size && cost[left + 1] < cost[left]) left + 1 else left
            if (cost[child] >= lastCost) break
            move(child, i)
            i = child
        }
        cost[i] = lastCost
        from[i] = lastFrom
        to[i] = lastTo
        fromVersion[i] = lastFromVersion
        toVersion[i] = lastToVersion
    }

    private fun move(source: Int, target: Int) {
        cost[target] = cost[source]
        from[target] = from[source]
        to[target] = to[source]
        fromVersion[target] = fromVersion[source]
        toVersion[target] = toVersion[source]
    }

    private fun grow() {
        val capacity = cost.size * 2
        cost = cost.copyOf(capacity)
        from = from.copyOf(capacity)
        to = to.copyOf(capacity)
        fromVersion = fromVersion.copyOf(capacity)
        toVersion = toVersion.copyOf(capacity)
    }

    private companion object {
        const val INITIAL = 64
    }
}
