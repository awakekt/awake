/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

/**
 * Leaves out of [candidates] the followers of [limit]'s group past its [CanvasFollowLimit.max], by
 * its [CanvasFollowLimit.rankBy], marking them not drawn. [scratch] is reused, so a frame allocates
 * nothing: the group is ranked by a stable insertion sort, so of two that rank the same the earlier
 * in the canvas's items stays. A crowd's group is tens to a few hundred, where that sort is cheap.
 */
internal fun applyLimit(limit: CanvasFollowLimit, candidates: List<FollowerPlan>, scratch: ArrayList<FollowerPlan>) {
    scratch.clear()
    for (i in candidates.indices) {
        val plan = candidates[i]
        if (plan.drawn && plan.rules.group == limit.group) scratch.add(plan)
    }
    if (scratch.size <= limit.max) return
    val rank = rankOf(limit.rankBy)
    for (i in 1 until scratch.size) {
        val moving = scratch[i]
        var at = i - 1
        while (at >= 0 && rank.compare(scratch[at], moving) > 0) {
            scratch[at + 1] = scratch[at]
            at--
        }
        scratch[at + 1] = moving
    }
    for (i in limit.max until scratch.size) scratch[i].drawn = false
}

private val nearestFirst = Comparator<FollowerPlan> { a, b -> a.element.followDistance.compareTo(b.element.followDistance) }

private val byPriority = Comparator<FollowerPlan> { a, b ->
    val order = b.rules.priority.compareTo(a.rules.priority)
    if (order != 0) order else nearestFirst.compare(a, b)
}

private val byScore = Comparator<FollowerPlan> { a, b ->
    val order = b.element.followScore.compareTo(a.element.followScore)
    if (order != 0) order else nearestFirst.compare(a, b)
}

private fun rankOf(rank: CanvasFollowRank): Comparator<FollowerPlan> = when (rank) {
    CanvasFollowRank.Distance -> nearestFirst
    CanvasFollowRank.Priority -> byPriority
    CanvasFollowRank.Score -> byScore
}
