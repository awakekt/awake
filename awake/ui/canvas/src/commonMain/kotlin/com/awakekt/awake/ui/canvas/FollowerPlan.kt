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
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Where a follower is drawn this frame, in [density] dp: its box, and the [scale] and [alpha] its
 * [CanvasFollowRules] give it at its distance. A [CanvasElement.followBounds] box is the node's own,
 * so it is never scaled.
 *
 * One plan is kept per follower for as long as it is in the canvas and rewritten each frame, so
 * planning allocates nothing once warm. It also remembers what [FollowerSizing] needs across
 * frames: the size bucket the follower is in, and where its point was and whether it snapped.
 */
internal class FollowerPlan(val element: CanvasElement) {
    var x: Float = 0f
    var y: Float = 0f
    var width: Float = 0f
    var height: Float = 0f
    var scale: Float = 1f
    var alpha: Float = 1f
    var density: Float = 0f

    /** Whether the follower is drawn this frame; a plan not drawn keeps its memory for the next. */
    var drawn: Boolean = false

    internal var rules: CanvasFollowRules = CanvasFollowRules.Default

    /** While pinned, which way the target lies; NaN while not. */
    internal var angle: Float = Float.NaN
    internal var bucket: Int = FollowerSizing.NO_BUCKET
    internal var lastPointX: Float = Float.NaN
    internal var lastPointY: Float = Float.NaN
    internal var snapping: Boolean = true
    internal var plannedFrame: Long = 0L

    fun place(left: Float, top: Float, boxWidth: Float, boxHeight: Float) {
        x = left
        y = top
        width = boxWidth
        height = boxHeight
    }

    /** Moves the box onto whole device pixels while [snapping], keeping its size. */
    fun snapToPixels() {
        if (!snapping || density <= 0f) return
        x = round(x * density) / density
        y = round(y * density) / density
    }

    /** Records the followed point, in frame pixels, and decides whether this frame snaps. */
    fun track(pointX: Float, pointY: Float) {
        val first = lastPointX.isNaN()
        snapping = first || FollowerSizing.snaps(snapping, pointX - lastPointX, pointY - lastPointY)
        lastPointX = pointX
        lastPointY = pointY
    }
}

/**
 * Every follower's [FollowerPlan], kept from frame to frame by the canvas that draws them, with the
 * lists planning reuses, so planning allocates nothing once each follower has a plan.
 */
internal class FollowerPlans {
    val plans = HashMap<CanvasElement, FollowerPlan>()
    val candidates = ArrayList<FollowerPlan>()
    val rankScratch = ArrayList<FollowerPlan>()
    val canvas = FollowerCanvas()
    val itemCanvas = FollowerCanvas()
    var frame = 0L
}

/** The canvas a frame's followers are placed on: [width] by [height] dp, 0 when unknown, at [density] pixels a dp. */
internal class FollowerCanvas(var width: Float = 0f, var height: Float = 0f, var density: Float = 1f) {
    val sized: Boolean get() = width > 0f && height > 0f

    fun set(width: Float, height: Float, density: Float): FollowerCanvas {
        this.width = width
        this.height = height
        this.density = density
        return this
    }

    /** Whether the box shares any of the canvas; any box does while the canvas's size is unknown. */
    fun overlaps(x: Float, y: Float, w: Float, h: Float): Boolean {
        val across = x < width && x + w > 0f
        val down = y < height && y + h > 0f
        return !sized || (across && down)
    }

    /** Whether ([x], [y]) is on the canvas, [margin] in from its edges. */
    fun holds(x: Float, y: Float, margin: Float): Boolean = x in margin..(width - margin) && y in margin..(height - margin)
}

/**
 * Works out, once a frame and before any follower is composed, where each visible follower among
 * the roots goes, into [CanvasTree.followerPlans]: projected through the resolver, culled behind the
 * camera, off a canvas [width] by [height] dp, beyond its [CanvasFollowRules.maxDistance] or while
 * occluded, sized and faded by its curves as [FollowerSizing] rounds them, held to the edge when it
 * pins, and then thinned by [limits]. [density] is the canvas's pixels per dp. A canvas whose size is
 * unknown, 0, culls and pins nothing off screen.
 *
 * Each follower is projected once, so the cost is one resolver call a follower and a sort of each
 * limited group, whatever is drawn; once every follower has a plan it allocates nothing itself.
 */
internal fun CanvasTree.planFollowers(width: Float, height: Float, density: Float, limits: List<CanvasFollowLimit>) {
    val store = followers
    val frame = ++store.frame
    val canvas = store.canvas.set(width, height, density)
    val candidates = store.candidates
    candidates.clear()
    val roots = roots
    for (i in roots.indices) {
        val element = roots[i].element
        element.pinned = false
        if (element.follow.isEmpty() || !shows(element)) continue
        val plan = followerPlans.getOrPut(element) { FollowerPlan(element) }
        plan.plannedFrame = frame
        // Not takeIf: a nullable Float is boxed, once per follower per frame.
        val ownDensity = densityOf(element)
        val itemDensity = if (ownDensity > 0f) ownDensity else density
        val itemCanvas = if (itemDensity == density) {
            canvas
        } else {
            store.itemCanvas.set(width * density / itemDensity, height * density / itemDensity, itemDensity)
        }
        plan.drawn = plan(plan, itemCanvas)
        if (plan.drawn) candidates.add(plan)
    }
    if (followerPlans.size > candidates.size + roots.size) followerPlans.values.removeAll { it.plannedFrame != frame }
    for (i in limits.indices) if (limits[i].max > 0) applyLimit(limits[i], candidates, store.rankScratch)
    for (i in candidates.indices) {
        val plan = candidates[i]
        if (plan.drawn && !plan.angle.isNaN()) {
            plan.element.pinned = true
            plan.element.pinAngle = plan.angle
        }
    }
}

/** Writes where [plan]'s follower goes this frame, returning whether it is drawn. */
private fun CanvasTree.plan(plan: FollowerPlan, canvas: FollowerCanvas): Boolean {
    val element = plan.element
    val rules = element.followRules ?: CanvasFollowRules.Default
    plan.rules = rules
    plan.angle = Float.NaN
    plan.density = canvas.density
    val projection = resolver.project(element.follow, element.followOffset)
    val frameBox = if (element.followBounds) resolver.bounds(element.follow) else null
    element.followDistance = projection?.distance ?: 0f
    return when {
        projection == null && frameBox == null -> false
        !looks(plan, rules, projection) -> false
        frameBox != null -> onBox(plan, rules, frameBox, canvas)
        else -> projection != null && onPoint(plan, rules, projection, canvas)
    }
}

/** [plan]'s follower over the node's frame rectangle [frameBox], grown by its outset; false when culled off screen. */
private fun CanvasTree.onBox(plan: FollowerPlan, rules: CanvasFollowRules, frameBox: Rectangle, canvas: FollowerCanvas): Boolean {
    val density = canvas.density
    plan.track(frameBox.x, frameBox.y)
    plan.scale = 1f
    plan.place((frameBox.x - origin.x) / density, (frameBox.y - origin.y) / density, frameBox.width / density, frameBox.height / density)
    plan.outset(plan.element.followOutset)
    plan.snapToPixels()
    return !(rules.cullOffScreen && !canvas.overlaps(plan.x, plan.y, plan.width, plan.height))
}

/** Sets [plan]'s size bucket and opacity at its distance, returning false when its distance or an occluder hides it. */
private fun CanvasTree.looks(plan: FollowerPlan, rules: CanvasFollowRules, projection: CanvasProjection?): Boolean {
    val element = plan.element
    val distance = element.followDistance
    val tooFar = rules.maxDistance > 0f && distance > rules.maxDistance
    // Asked last and only on screen: an occlusion query is the dearest thing a host answers.
    val onScreen = projection != null && !projection.behind
    val asksOcclusion = rules.occlusion && onScreen && !tooFar
    val occluded = asksOcclusion && resolver.occluded(element.follow, element.followOffset)
    val scale = rules.scale?.valueAt(distance) ?: 1f
    val alpha = (rules.fade?.valueAt(distance) ?: 1f).coerceAtMost(1f)
    val seen = scale > 0f && alpha > 0f
    val shown = seen && !tooFar && !occluded
    if (shown) {
        plan.bucket = FollowerSizing.bucketOf(scale, plan.bucket)
        plan.scale = FollowerSizing.scaleOf(plan.bucket)
        plan.alpha = alpha
    }
    return shown
}

/**
 * [plan]'s follower with its anchor point on [projection]'s point, nudged by its offsets, at its
 * bucket's scale; or held to the edge when its rules pin it and the point is off the canvas or
 * behind the camera. False when it is behind the camera unpinned, or culled off screen.
 */
private fun CanvasTree.onPoint(plan: FollowerPlan, rules: CanvasFollowRules, projection: CanvasProjection, canvas: FollowerCanvas): Boolean {
    val element = plan.element
    plan.track(projection.x, projection.y)
    val x = (projection.x - origin.x) / canvas.density
    val y = (projection.y - origin.y) / canvas.density
    val scale = plan.scale
    val boxWidth = element.width * scale
    val boxHeight = element.height * scale
    val margin = rules.edgeMargin.coerceAtLeast(0f)
    val offCanvas = projection.behind || !canvas.holds(x, y, margin)
    val pins = rules.pinToEdge && canvas.sized && offCanvas
    if (pins) {
        pinned(plan, x, y, canvas, margin, boxWidth, boxHeight)
    } else {
        val column = element.anchor.column
        val row = element.anchor.row
        val left = x - column / 2f * boxWidth + (if (column == 2) -element.offsetX else element.offsetX) * scale
        val top = y - row / 2f * boxHeight + (if (row == 2) -element.offsetY else element.offsetY) * scale
        plan.place(left, top, boxWidth, boxHeight)
    }
    plan.snapToPixels()
    val culled = rules.cullOffScreen && !canvas.overlaps(plan.x, plan.y, boxWidth, boxHeight)
    return pins || (!projection.behind && !culled)
}

/**
 * [plan]'s box held inside [canvas], [margin] in from its edges, where the line from the canvas's
 * centre toward ([x], [y]) leaves that inset box, and the angle from the centre to the point. A point
 * straight behind the camera, with no way to go, is taken as below.
 */
@Suppress("LongParameterList") // The point, the canvas, the margin and the box's size.
private fun pinned(plan: FollowerPlan, x: Float, y: Float, canvas: FollowerCanvas, margin: Float, boxWidth: Float, boxHeight: Float) {
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
    plan.place(centreX + dx * along - boxWidth / 2f, centreY + dy * along - boxHeight / 2f, boxWidth, boxHeight)
    plan.angle = atan2(dy, dx) * DEGREES_PER_RADIAN
}

/** This plan's box grown by [by] on each side, or shrunk for a negative [by], about its centre and never below nothing. */
private fun FollowerPlan.outset(by: Float) {
    val grownWidth = (width + 2 * by).coerceAtLeast(0f)
    val grownHeight = (height + 2 * by).coerceAtLeast(0f)
    place(x + (width - grownWidth) / 2, y + (height - grownHeight) / 2, grownWidth, grownHeight)
}

/** Shorter than this, a direction has no way to point. */
private const val MIN_DIRECTION = 1e-4f

private const val DEGREES_PER_RADIAN = (180.0 / PI).toFloat()
