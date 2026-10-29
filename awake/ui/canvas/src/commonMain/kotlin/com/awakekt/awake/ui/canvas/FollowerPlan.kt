/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.math2d.Rectangle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Where a follower is drawn this frame, in [density] dp: its box, and the [scale] and [alpha] its
 * [CanvasFollowRules] give it at its distance. A [CanvasElement.followBounds] box is the node's own,
 * so it is never scaled.
 */
internal class FollowerPlan(val box: Rectangle, val scale: Float, val alpha: Float, val density: Float = 0f)

/** A follower that would be drawn, before the [CanvasFollowLimit]s choose among them; [angle] is set while it is pinned. */
private class FollowerCandidate(val element: CanvasElement, val rules: CanvasFollowRules, val plan: FollowerPlan, val angle: Float?)

/** The size and opacity a follower's curves give it at its distance. */
private class FollowerLook(val scale: Float, val alpha: Float)

/** The canvas a frame's followers are placed on: [width] by [height] dp, 0 when unknown, at [density] pixels a dp. */
private class FollowerCanvas(val width: Float, val height: Float, val density: Float) {
    val sized: Boolean get() = width > 0f && height > 0f

    /** Whether [box] shares any of the canvas; any box does while the canvas's size is unknown. */
    fun overlaps(box: Rectangle): Boolean {
        val across = box.x < width && box.x + box.width > 0f
        val down = box.y < height && box.y + box.height > 0f
        return !sized || (across && down)
    }

    /** Whether ([x], [y]) is on the canvas, [margin] in from its edges. */
    fun holds(x: Float, y: Float, margin: Float): Boolean = x in margin..(width - margin) && y in margin..(height - margin)
}

/**
 * Works out, once a frame and before any follower is composed, where each visible follower among
 * the roots goes, into [CanvasTree.followerPlans]: projected through the resolver, culled behind the
 * camera, off a canvas [width] by [height] dp, beyond its [CanvasFollowRules.maxDistance] or while
 * occluded, scaled and faded by its curves, held to the edge when it pins, and then thinned by
 * [limits]. [density] is the canvas's pixels per dp. A canvas whose size is unknown, 0, culls and pins
 * nothing off screen.
 *
 * Each follower is projected once, so the cost is one resolver call a follower and a sort of each
 * limited group, whatever is drawn.
 */
internal fun CanvasTree.planFollowers(width: Float, height: Float, density: Float, limits: List<CanvasFollowLimit>) {
    followerPlans.clear()
    val canvas = FollowerCanvas(width, height, density)
    val candidates = ArrayList<FollowerCandidate>()
    for (item in roots) {
        val element = item.element
        element.pinned = false
        if (element.follow.isNotEmpty() && shows(element)) {
            val itemDensity = densityOf(element).takeIf { it > 0f } ?: density
            val itemCanvas = if (itemDensity == density) {
                canvas
            } else {
                FollowerCanvas(width * density / itemDensity, height * density / itemDensity, itemDensity)
            }
            candidateFor(element, itemCanvas)?.let(candidates::add)
        }
    }
    val kept = if (limits.any { it.max > 0 }) withinLimits(candidates, limits) else candidates
    for (candidate in kept) {
        followerPlans[candidate.element] = candidate.plan
        candidate.angle?.let { angle ->
            candidate.element.pinned = true
            candidate.element.pinAngle = angle
        }
    }
}

/** Where [element] goes this frame, or null when it is not drawn. */
private fun CanvasTree.candidateFor(element: CanvasElement, canvas: FollowerCanvas): FollowerCandidate? {
    val rules = element.followRules ?: CanvasFollowRules.Default
    val projection = resolver.project(element.follow, element.followOffset)
    val frameBox = if (element.followBounds) resolver.bounds(element.follow) else null
    element.followDistance = projection?.distance ?: 0f
    val look = if (projection == null && frameBox == null) null else lookOf(element, rules, projection)
    return when {
        look == null -> null
        frameBox != null -> onBox(element, rules, frameBox, look, canvas)
        projection != null -> onPoint(element, rules, projection, look, canvas)
        else -> null
    }
}

/** [element]'s size and opacity at its distance, or null when its distance or an occluder hides it. */
private fun CanvasTree.lookOf(element: CanvasElement, rules: CanvasFollowRules, projection: CanvasProjection?): FollowerLook? {
    val distance = element.followDistance
    val tooFar = rules.maxDistance > 0f && distance > rules.maxDistance
    // Asked last and only on screen: an occlusion query is the dearest thing a host answers.
    val onScreen = projection != null && !projection.behind
    val occluded = !tooFar && rules.occlusion && onScreen && resolver.occluded(element.follow, element.followOffset)
    val scale = rules.scale?.valueAt(distance) ?: 1f
    val alpha = (rules.fade?.valueAt(distance) ?: 1f).coerceAtMost(1f)
    val seen = scale > 0f && alpha > 0f
    return if (tooFar || occluded || !seen) null else FollowerLook(scale, alpha)
}

/** [element] over the node's frame rectangle [frameBox], grown by its outset. */
private fun CanvasTree.onBox(element: CanvasElement, rules: CanvasFollowRules, frameBox: Rectangle, look: FollowerLook, canvas: FollowerCanvas): FollowerCandidate? {
    val density = canvas.density
    val box = Rectangle((frameBox.x - origin.x) / density, (frameBox.y - origin.y) / density, frameBox.width / density, frameBox.height / density)
        .outset(element.followOutset)
    val culled = rules.cullOffScreen && !canvas.overlaps(box)
    return if (culled) null else FollowerCandidate(element, rules, FollowerPlan(box, 1f, look.alpha, density), null)
}

/**
 * [element] with its anchor point on [projection]'s point, nudged by its offsets, at its look's
 * scale; or held to the edge when its rules pin it and the point is off the canvas or behind the
 * camera. Null when it is behind the camera unpinned, or culled off screen.
 */
private fun CanvasTree.onPoint(
    element: CanvasElement,
    rules: CanvasFollowRules,
    projection: CanvasProjection,
    look: FollowerLook,
    canvas: FollowerCanvas,
): FollowerCandidate? {
    val x = (projection.x - origin.x) / canvas.density
    val y = (projection.y - origin.y) / canvas.density
    val scale = look.scale
    val boxWidth = element.width * scale
    val boxHeight = element.height * scale
    val margin = rules.edgeMargin.coerceAtLeast(0f)
    val pins = rules.pinToEdge && canvas.sized && (projection.behind || !canvas.holds(x, y, margin))
    val column = element.anchor.column
    val row = element.anchor.row
    val left = x - column / 2f * boxWidth + (if (column == 2) -element.offsetX else element.offsetX) * scale
    val top = y - row / 2f * boxHeight + (if (row == 2) -element.offsetY else element.offsetY) * scale
    val box = Rectangle(left, top, boxWidth, boxHeight)
    return when {
        pins -> pinned(element, rules, x, y, canvas, margin, boxWidth, boxHeight, look)
        projection.behind -> null
        rules.cullOffScreen && !canvas.overlaps(box) -> null
        else -> FollowerCandidate(element, rules, FollowerPlan(box, scale, look.alpha, canvas.density), null)
    }
}

/**
 * [element] held inside [canvas], [margin] in from its edges, where the line from the canvas's centre
 * toward ([x], [y]) leaves that inset box, and the angle from the centre to the point. A point
 * straight behind the camera, with no way to go, is taken as below.
 */
@Suppress("LongParameterList") // The point, the canvas, the margin, and the box's size and look.
private fun pinned(
    element: CanvasElement,
    rules: CanvasFollowRules,
    x: Float,
    y: Float,
    canvas: FollowerCanvas,
    margin: Float,
    boxWidth: Float,
    boxHeight: Float,
    look: FollowerLook,
): FollowerCandidate {
    val centreX = canvas.width / 2f
    val centreY = canvas.height / 2f
    var dx = x - centreX
    var dy = y - centreY
    val length = sqrt(dx * dx + dy * dy)
    if (length < MIN_DIRECTION) {
        dx = 0f
        dy = 1f
    } else {
        dx /= length
        dy /= length
    }
    // How far the box's centre may go from the canvas's centre on each axis, never below none.
    val reachX = (centreX - margin - boxWidth / 2f).coerceAtLeast(0f)
    val reachY = (centreY - margin - boxHeight / 2f).coerceAtLeast(0f)
    val alongX = if (abs(dx) > MIN_DIRECTION) reachX / abs(dx) else Float.MAX_VALUE
    val alongY = if (abs(dy) > MIN_DIRECTION) reachY / abs(dy) else Float.MAX_VALUE
    val along = minOf(alongX, alongY)
    val boxCentreX = centreX + dx * along
    val boxCentreY = centreY + dy * along
    val box = Rectangle(boxCentreX - boxWidth / 2f, boxCentreY - boxHeight / 2f, boxWidth, boxHeight)
    val angle = atan2(dy, dx) * DEGREES_PER_RADIAN
    return FollowerCandidate(element, rules, FollowerPlan(box, look.scale, look.alpha, canvas.density), angle)
}

/** [candidates] less those each [limits] entry leaves out of its group, keeping their order. */
private fun withinLimits(candidates: List<FollowerCandidate>, limits: List<CanvasFollowLimit>): List<FollowerCandidate> {
    val dropped = HashSet<CanvasElement>()
    for (limit in limits.filter { it.max > 0 }) {
        val group = candidates.filter { it.rules.group == limit.group }
        // Stable, so of two that rank the same the earlier in the canvas's items stays.
        if (group.size > limit.max) group.sortedWith(rankOf(limit.rankBy)).drop(limit.max).mapTo(dropped) { it.element }
    }
    return if (dropped.isEmpty()) candidates else candidates.filter { it.element !in dropped }
}

private val nearestFirst = compareBy<FollowerCandidate> { it.element.followDistance }

private fun rankOf(rank: CanvasFollowRank): Comparator<FollowerCandidate> = when (rank) {
    CanvasFollowRank.Distance -> nearestFirst
    CanvasFollowRank.Priority -> compareByDescending<FollowerCandidate> { it.rules.priority }.then(nearestFirst)
    CanvasFollowRank.Score -> compareByDescending<FollowerCandidate> { it.element.followScore }.then(nearestFirst)
}

/** This rectangle grown by [by] on each side, or shrunk for a negative [by], about its centre and never below nothing. */
private fun Rectangle.outset(by: Float): Rectangle {
    val grownWidth = (width + 2 * by).coerceAtLeast(0f)
    val grownHeight = (height + 2 * by).coerceAtLeast(0f)
    return Rectangle(x + (width - grownWidth) / 2, y + (height - grownHeight) / 2, grownWidth, grownHeight)
}

/** Shorter than this, a direction has no way to point. */
private const val MIN_DIRECTION = 1e-4f

private const val DEGREES_PER_RADIAN = (180.0 / PI).toFloat()
