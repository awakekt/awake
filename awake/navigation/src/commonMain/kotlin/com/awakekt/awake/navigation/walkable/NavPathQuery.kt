/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.walkable

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.navigation.grid.NavGroundHeight
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Finds walkable routes across a [NavSurface]: 8-connected A* with an octile heuristic, then
 * line-of-sight smoothing, written into a caller-owned [NavPath].
 *
 * **Reuse one per thread.** Its search state grows on the first queries and is then reused, so a
 * warm query allocates nothing; it is not safe to share between threads. A server answering many
 * agents keeps one per worker.
 *
 * **Bounded by nodes, not time.** A search expands at most [maxExpandedNodes] samples and then
 * answers [NavPathStatus.Partial] with the best point it reached. A time budget would make the
 * answer depend on the machine, and an authoritative server and its clients must agree on a route;
 * a node budget bounds the time as well, and the same query gives the same route everywhere.
 *
 * **Nearest reachable point.** A target that cannot be reached (inside a building, across water, on
 * an island) or that the budget does not reach still gets a route: to the reached sample nearest
 * the target. An endpoint inside an obstacle first moves to the nearest standable sample within
 * [snapSamples], which is what lets an agent pushed slightly into a wall still find its way out.
 *
 * Deterministic: the heap breaks ties on the sample, nothing iterates a hash table, and the cost
 * model is integer steps, so identical inputs give an identical route on every platform.
 *
 * Waypoint Y comes from [ground], since the surface stores clearance and not height.
 *
 * @param surface Where agents can stand.
 * @param ground World Y for each waypoint.
 * @property maxExpandedNodes The most samples one query expands.
 * @property snapSamples How many samples an endpoint may move to reach standable ground.
 */
class NavPathQuery(
    private val surface: NavSurface,
    private val ground: NavGroundHeight = NavGroundHeight.FLAT,
    val maxExpandedNodes: Int = DEFAULT_MAX_EXPANDED_NODES,
    val snapSamples: Int = DEFAULT_SNAP_SAMPLES,
) {
    private val nodes = NavNodeTable()
    private val open = NavOpenHeap()
    private var required = 1
    private var goalX = 0
    private var goalZ = 0
    private var best = NO_PARENT
    private var bestDistance = Float.MAX_VALUE

    /** Samples the last query expanded, for measuring a budget. */
    var lastExpandedNodes: Int = 0
        private set

    init {
        require(maxExpandedNodes > 0) { "maxExpandedNodes must be positive; was $maxExpandedNodes." }
        require(snapSamples >= 0) { "snapSamples must be non-negative; was $snapSamples." }
    }

    /**
     * Writes a route for [agent] from [from] to [to] into [out], replacing what it held.
     *
     * The first waypoint is [from] and, when [NavPathStatus.Complete], the last is [to]; the corners
     * between sit on sample centres. Only X and Z of [from] and [to] are read.
     *
     * @param from Where the agent stands.
     * @param to Where it wants to go.
     * @param agent The body; its radius is read against the surface's clearance.
     * @param out The path written. Reuse it.
     * @return How the query answered, also left in [NavPath.status].
     */
    fun findPath(from: Vec3f, to: Vec3f, agent: NavAgent, out: NavPath): NavPathStatus {
        out.clear()
        required = agent.requiredClearance(surface.sampleSize)
        val fromX = surface.sampleOf(from.x, surface.originX)
        val fromZ = surface.sampleOf(from.z, surface.originZ)
        val start = snap(fromX, fromZ)
        out.status = if (start == NO_PARENT) NavPathStatus.NoPath else route(start, from, to, out)
        return out.status
    }

    private fun route(start: Long, from: Vec3f, to: Vec3f, out: NavPath): NavPathStatus {
        val target = packSample(surface.sampleOf(to.x, surface.originX), surface.sampleOf(to.z, surface.originZ))
        val snapped = snap(unpackX(target), unpackZ(target))
        val goal = if (snapped == NO_PARENT) target else snapped
        val reached = search(start, goal)
        reconstruct(start, if (reached) goal else best, out)
        surface.smooth(out, required)
        val complete = reached && goal == target
        if (start == packSample(surface.sampleOf(from.x, surface.originX), surface.sampleOf(from.z, surface.originZ))) {
            out.set(0, from.x, ground.heightAt(from.x, from.z), from.z)
        }
        if (complete) out.set(out.size - 1, to.x, ground.heightAt(to.x, to.z), to.z)
        return if (complete) NavPathStatus.Complete else NavPathStatus.Partial
    }

    /** A* from [start] towards [goal]; true when it got there inside the budget. */
    private fun search(start: Long, goal: Long): Boolean {
        nodes.reset()
        open.clear()
        goalX = unpackX(goal)
        goalZ = unpackZ(goal)
        best = start
        bestDistance = heuristic(unpackX(start), unpackZ(start))
        nodes.setCost(nodes.slotOf(start), 0f)
        open.push(start, bestDistance, 0f)
        var expanded = 0
        var reached = false
        while (!reached && !open.isEmpty && expanded < maxExpandedNodes) {
            val current = open.pop()
            val slot = nodes.slotOf(current)
            reached = current == goal
            if (!reached && !nodes.isClosed(slot)) {
                nodes.close(slot)
                expanded++
                remember(current)
                expand(current, slot)
            }
        }
        lastExpandedNodes = expanded
        if (reached) best = goal
        return reached
    }

    /** Keeps the expanded sample nearest the goal; the first of equals wins, in pop order. */
    private fun remember(key: Long) {
        val distance = heuristic(unpackX(key), unpackZ(key))
        if (distance < bestDistance) {
            bestDistance = distance
            best = key
        }
    }

    private fun expand(current: Long, slot: Int) {
        val x = unpackX(current)
        val z = unpackZ(current)
        val cost = nodes.cost(slot)
        for (direction in 0 until DIRECTIONS) {
            val dx = DIRECTION_X[direction]
            val dz = DIRECTION_Z[direction]
            val diagonal = dx != 0 && dz != 0
            val passable = surface.standable(x + dx, z + dz, required) &&
                (!diagonal || (surface.standable(x + dx, z, required) && surface.standable(x, z + dz, required)))
            if (passable) relax(current, x + dx, z + dz, cost + if (diagonal) SQRT2 else 1f)
        }
    }

    private fun relax(parent: Long, x: Int, z: Int, cost: Float) {
        val key = packSample(x, z)
        val slot = nodes.slotOf(key)
        if (nodes.isClosed(slot) || cost >= nodes.cost(slot)) return
        nodes.setCost(slot, cost)
        nodes.setParent(slot, parent)
        open.push(key, cost + heuristic(x, z), cost)
    }

    /** Octile distance to the goal: exact for an open 8-connected walk, so it never overestimates. */
    private fun heuristic(x: Int, z: Int): Float {
        val dx = abs(x - goalX).toFloat()
        val dz = abs(z - goalZ).toFloat()
        return dx + dz + (SQRT2 - 2f) * min(dx, dz)
    }

    private fun reconstruct(start: Long, end: Long, out: NavPath) {
        var node = end
        while (node != NO_PARENT) {
            val worldX = surface.originX + unpackX(node) * surface.sampleSize
            val worldZ = surface.originZ + unpackZ(node) * surface.sampleSize
            out.add(worldX, ground.heightAt(worldX, worldZ), worldZ)
            node = if (node == start) NO_PARENT else nodes.parent(nodes.slotOf(node))
        }
        out.reverse()
    }

    /**
     * The standable sample nearest ([x], [z]) within [snapSamples], or [NO_PARENT]. Rings are
     * searched outward until none can hold anything nearer; ties go to the first in Z-then-X order.
     */
    private fun snap(x: Int, z: Int): Long {
        var found = if (surface.standable(x, z, required)) packSample(x, z) else NO_PARENT
        var foundDistance = if (found == NO_PARENT) Int.MAX_VALUE else 0
        var ring = 1
        while (ring <= snapSamples && ring * ring <= foundDistance) {
            for (dz in -ring..ring) {
                for (dx in -ring..ring) {
                    val onRing = abs(dx) == ring || abs(dz) == ring
                    val distance = dx * dx + dz * dz
                    if (onRing && distance < foundDistance && surface.standable(x + dx, z + dz, required)) {
                        found = packSample(x + dx, z + dz)
                        foundDistance = distance
                    }
                }
            }
            ring++
        }
        return found
    }

    /** The default budgets. */
    companion object {
        /** Samples one query may expand: a 1 km route at 1 m across open ground needs about 1,500. */
        const val DEFAULT_MAX_EXPANDED_NODES: Int = 200_000

        /** Samples an endpoint may move to find standable ground. */
        const val DEFAULT_SNAP_SAMPLES: Int = 8

        private const val DIRECTIONS = 8
        private val SQRT2 = sqrt(2f)
        private val DIRECTION_X = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
        private val DIRECTION_Z = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)
    }
}
