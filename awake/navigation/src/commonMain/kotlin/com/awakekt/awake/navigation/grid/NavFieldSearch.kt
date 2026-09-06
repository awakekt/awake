/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.grid

import com.awakekt.awake.core.math.Vec3f
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * One A* run over one [NavField].
 *
 * Nodes are packed sample coordinates in a sparse table rather than dense per-sample arrays. Dense
 * arrays were the earlier single-tile shape and cannot survive streaming: the resident set spans
 * an unbounded coordinate range, and sizing arrays to its bounding box would allocate tens of
 * megabytes per query to visit a corridor. Search cost is proportional to what A* actually expands
 * either way.
 *
 * Determinism survives the change because nothing iterates the table — it is looked up, never
 * walked — and the heap breaks ties on the packed key, which orders by Z then X exactly as the old
 * row-major node index did. Identical inputs still produce an identical path, which an
 * authoritative server needs.
 */
class NavFieldSearch(private val field: NavField) {
    private val nodes = NavNodeTable()
    private val open = NodeHeap(INITIAL_HEAP_CAPACITY)

    /** The goal is fixed for a run, so it lives here rather than being threaded through every step. */
    private var goalX = 0
    private var goalZ = 0

    fun run(startX: Int, startZ: Int, goalX: Int, goalZ: Int): List<Vec3f> {
        this.goalX = goalX
        this.goalZ = goalZ
        val start = pack(startX, startZ)
        val goal = pack(goalX, goalZ)
        nodes.setGScore(nodes.slotOf(start), 0f)
        open.push(start, heuristic(startX, startZ))
        var reached = false
        while (!open.isEmpty && !reached) {
            val current = open.pop()
            val slot = nodes.slotOf(current)
            when {
                current == goal -> reached = true
                nodes.isClosed(slot) -> Unit
                else -> {
                    nodes.close(slot)
                    expand(current, slot)
                }
            }
        }
        return if (reached) reconstruct(start, goal) else emptyList()
    }

    private fun expand(current: Long, currentSlot: Int) {
        val x = unpackX(current)
        val z = unpackZ(current)
        val cost = nodes.gScore(currentSlot)
        for (direction in 0 until DIRECTION_COUNT) {
            val dx = DIRECTION_X[direction]
            val dz = DIRECTION_Z[direction]
            if (!canStep(x, z, dx, dz)) continue
            relax(current, x + dx, z + dz, cost, diagonal = dx != 0 && dz != 0)
        }
    }

    /**
     * A diagonal needs *both* shared orthogonal samples open, not just one. The looser rule lets an
     * agent shave the corner of a single block, which an agent with any width cannot do. The cost
     * is that diagonals are unavailable alongside any obstacle edge, which is the conservative
     * direction to be wrong in. [smoothPath] applies the same rule, or it would undo this one.
     */
    private fun canStep(x: Int, z: Int, dx: Int, dz: Int): Boolean =
        field.isWalkable(x + dx, z + dz) &&
            (dx == 0 || dz == 0 || (field.isWalkable(x + dx, z) && field.isWalkable(x, z + dz)))

    private fun relax(current: Long, x: Int, z: Int, cost: Float, diagonal: Boolean) {
        val neighbour = pack(x, z)
        val slot = nodes.slotOf(neighbour)
        if (nodes.isClosed(slot)) return
        val tentative = cost + if (diagonal) SQRT2 else 1f
        if (tentative >= nodes.gScore(slot)) return
        nodes.setGScore(slot, tentative)
        nodes.setParent(slot, current)
        open.push(neighbour, tentative + heuristic(x, z))
    }

    /** Octile distance: the exact cost of an unobstructed 8-connected walk, so it never overestimates. */
    private fun heuristic(x: Int, z: Int): Float {
        val dx = abs(x - goalX).toFloat()
        val dz = abs(z - goalZ).toFloat()
        return (dx + dz) + (SQRT2 - 2f) * min(dx, dz)
    }

    private fun reconstruct(start: Long, goal: Long): List<Vec3f> {
        val reversed = ArrayList<Vec3f>()
        var node = goal
        while (node != NO_PARENT) {
            reversed.add(
                Vec3f(
                    field.originX + unpackX(node) * field.sampleSize,
                    0f,
                    field.originZ + unpackZ(node) * field.sampleSize,
                ),
            )
            if (node == start) break
            node = nodes.parent(nodes.slotOf(node))
        }
        reversed.reverse()
        return reversed
    }
}

/**
 * Open-addressed table from a packed sample coordinate to that node's search state.
 *
 * Linear probing over primitive arrays: no boxing, and no iteration order to depend on. A slot
 * index stays valid only until the next [slotOf], which may grow and rehash — every caller here
 * looks up and uses immediately.
 */
private class NavNodeTable {
    private var mask = INITIAL_TABLE_CAPACITY - 1
    private var keys = LongArray(INITIAL_TABLE_CAPACITY)
    private var used = BooleanArray(INITIAL_TABLE_CAPACITY)
    private var gScore = FloatArray(INITIAL_TABLE_CAPACITY)
    private var parent = LongArray(INITIAL_TABLE_CAPACITY)
    private var closed = BooleanArray(INITIAL_TABLE_CAPACITY)
    private var size = 0

    /** The slot holding [key]'s state, inserting an unvisited node at infinite cost if new. */
    fun slotOf(key: Long): Int {
        if ((size + 1) * LOAD_FACTOR_DENOMINATOR > (mask + 1) * LOAD_FACTOR_NUMERATOR) grow()
        var slot = probe(key)
        if (!used[slot]) {
            used[slot] = true
            keys[slot] = key
            gScore[slot] = Float.MAX_VALUE
            parent[slot] = NO_PARENT
            closed[slot] = false
            size++
        }
        return slot
    }

    fun gScore(slot: Int): Float = gScore[slot]

    fun setGScore(slot: Int, value: Float) {
        gScore[slot] = value
    }

    fun parent(slot: Int): Long = parent[slot]

    fun setParent(slot: Int, value: Long) {
        parent[slot] = value
    }

    fun isClosed(slot: Int): Boolean = closed[slot]

    fun close(slot: Int) {
        closed[slot] = true
    }

    private fun probe(key: Long): Int {
        var slot = hash(key) and mask
        while (used[slot] && keys[slot] != key) slot = (slot + 1) and mask
        return slot
    }

    private fun grow() {
        val oldKeys = keys
        val oldUsed = used
        val oldG = gScore
        val oldParent = parent
        val oldClosed = closed
        val capacity = (mask + 1) * 2
        mask = capacity - 1
        keys = LongArray(capacity)
        used = BooleanArray(capacity)
        gScore = FloatArray(capacity)
        parent = LongArray(capacity)
        closed = BooleanArray(capacity)
        for (old in oldKeys.indices) {
            if (!oldUsed[old]) continue
            val slot = probe(oldKeys[old])
            used[slot] = true
            keys[slot] = oldKeys[old]
            gScore[slot] = oldG[old]
            parent[slot] = oldParent[old]
            closed[slot] = oldClosed[old]
        }
    }

    /** Fibonacci hashing: neighbouring samples differ by 1 or by one Z step, which the multiply spreads. */
    private fun hash(key: Long): Int = ((key * HASH_MULTIPLIER) ushr HASH_SHIFT).toInt()
}

/**
 * Binary min-heap of packed sample coordinates.
 *
 * `commonMain` has no priority queue — `java.util.PriorityQueue` is JVM-only — and a hand-rolled
 * heap over primitive arrays also avoids boxing every entry.
 *
 * Ties break on the packed key, giving a total order. Without that, two nodes with equal `f` would
 * pop in whatever order sift operations happened to leave them, and the same query could return
 * two different equally-cheap paths on different runs.
 */
private class NodeHeap(capacity: Int) {
    private var nodes = LongArray(capacity)
    private var priorities = FloatArray(capacity)
    private var size = 0

    val isEmpty: Boolean get() = size == 0

    fun push(node: Long, priority: Float) {
        if (size == nodes.size) grow()
        nodes[size] = node
        priorities[size] = priority
        siftUp(size)
        size++
    }

    fun pop(): Long {
        val top = nodes[0]
        size--
        nodes[0] = nodes[size]
        priorities[0] = priorities[size]
        siftDown()
        return top
    }

    private fun grow() {
        nodes = nodes.copyOf(nodes.size * 2)
        priorities = priorities.copyOf(priorities.size * 2)
    }

    private fun siftUp(from: Int) {
        var child = from
        while (child > 0) {
            val parent = (child - 1) / 2
            if (!precedes(child, parent)) break
            swap(child, parent)
            child = parent
        }
    }

    private fun siftDown() {
        var parent = 0
        var sifting = true
        while (sifting) {
            val left = parent * 2 + 1
            val right = left + 1
            // A leaf resolves to itself, which is what ends the loop without a second exit.
            val best = when {
                left >= size -> parent
                right < size && precedes(right, left) -> right
                else -> left
            }
            sifting = best != parent && precedes(best, parent)
            if (sifting) {
                swap(best, parent)
                parent = best
            }
        }
    }

    private fun precedes(a: Int, b: Int): Boolean = when {
        priorities[a] < priorities[b] -> true
        priorities[a] > priorities[b] -> false
        else -> nodes[a] < nodes[b]
    }

    private fun swap(a: Int, b: Int) {
        val node = nodes[a]
        nodes[a] = nodes[b]
        nodes[b] = node
        val priority = priorities[a]
        priorities[a] = priorities[b]
        priorities[b] = priority
    }
}

/**
 * Packs a sample coordinate into one key, biased so the whole `Int` range packs to a positive
 * `Long` ordered by Z then X — the ordering the heap's tie-break inherits.
 */
private fun pack(x: Int, z: Int): Long =
    (((z.toLong() + COORDINATE_BIAS) shl COORDINATE_SHIFT) or (x.toLong() + COORDINATE_BIAS))

private fun unpackX(key: Long): Int = ((key and COORDINATE_MASK) - COORDINATE_BIAS).toInt()

private fun unpackZ(key: Long): Int = ((key ushr COORDINATE_SHIFT) - COORDINATE_BIAS).toInt()

/** No packed key is negative, so a negative sentinel cannot collide with a real node. */
private const val NO_PARENT = -1L
private const val COORDINATE_SHIFT = 32
private const val COORDINATE_BIAS = 0x8000_0000L
private const val COORDINATE_MASK = 0xFFFF_FFFFL
private const val INITIAL_HEAP_CAPACITY = 64
private const val INITIAL_TABLE_CAPACITY = 256
private const val LOAD_FACTOR_NUMERATOR = 3
private const val LOAD_FACTOR_DENOMINATOR = 4
private const val HASH_MULTIPLIER = -7046029254386353131L
private const val HASH_SHIFT = 40
private const val DIRECTION_COUNT = 8
private val SQRT2 = sqrt(2f)
private val DIRECTION_X = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
private val DIRECTION_Z = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)
