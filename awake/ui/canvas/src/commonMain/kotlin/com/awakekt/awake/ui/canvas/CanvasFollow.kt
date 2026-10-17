/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.schema.PropertyRange
import kotlinx.serialization.Serializable

/**
 * Where a followed point lands, as a [CanvasResolver] reports it: in the frame's pixels, with how far
 * it is from the camera.
 *
 * @property x Pixels from the frame's left. For a point [behind] the camera, a point far off the
 *   view in the direction the target lies, from the view's centre, so an element pinned to the edge
 *   points the right way.
 * @property y Pixels from the frame's top, as [x].
 * @property depth How far the point is in front of the camera along its view direction, in world
 *   units; at or below the camera's near plane for a point [behind] it. 0 when the host cannot tell.
 * @property distance How far the point is from the camera, in world units; 0 when the host cannot
 *   tell, which every distance rule then reads as right at the camera.
 * @property behind Whether the point is behind the camera, by its view depth rather than the clip
 *   space's `w`, so it holds for every backend's projection.
 */
class CanvasProjection(
    val x: Float,
    val y: Float,
    val depth: Float = 0f,
    val distance: Float = 0f,
    val behind: Boolean = false,
)

/** A point on a [CanvasCurve]: its [value] at [at]. */
@Serializable
data class CanvasCurvePoint(val at: Float = 0f, val value: Float = 1f)

/**
 * A value that changes with distance, as data: straight lines between [points], ordered by
 * [CanvasCurvePoint.at], held at the first point's value before it and the last's after it. With no
 * points it is no curve, and the thing it would change stays as it is.
 */
@Serializable
data class CanvasCurve(val points: List<CanvasCurvePoint> = emptyList()) {
    /** The curve's value at [x], or [otherwise] when it has no points. */
    fun valueAt(x: Float, otherwise: Float = 1f): Float {
        if (points.isEmpty()) return otherwise
        val next = points.indexOfFirst { x <= it.at }
        return when (next) {
            0 -> points.first().value
            -1 -> points.last().value
            else -> between(points[next - 1], points[next], x)
        }
    }

    /** The straight line from [from] to [to] at [x], or [to]'s value where the two stand at one distance. */
    private fun between(from: CanvasCurvePoint, to: CanvasCurvePoint, x: Float): Float {
        val span = to.at - from.at
        return if (span <= 0f) to.value else from.value + (to.value - from.value) * (x - from.at) / span
    }

    /** What is wrong with this curve, each as `field message`; empty when nothing is. */
    fun problems(): List<String> = buildList {
        if (points.any { !it.at.isFinite() || !it.value.isFinite() }) add("points must be finite numbers")
        if (points.zipWithNext().any { (a, b) -> b.at < a.at }) add("points must be in order of at")
    }
}

/** What a [CanvasFollowLimit] keeps when more followers want the screen than it allows. */
@Serializable
enum class CanvasFollowRank {
    /** The nearest to the camera. */
    Distance,

    /** The highest [CanvasFollowRules.priority], and of equal ones the nearest. */
    Priority,

    /** The highest [CanvasElement.followScore], which game code sets, and of equal ones the nearest. */
    Score,
}

/**
 * How an element that [CanvasElement.follow]s a node behaves with many of them on screen: when it
 * hides, how it is ranked against the others, how it grows and fades with distance, and whether it
 * holds to the screen's edge when what it follows is off screen. All of it is data, saved with the
 * element as its `followRules`.
 *
 * @property cullOffScreen Whether the element is left out, not just drawn off the canvas, while its
 *   box is wholly outside it. Left out it costs nothing to draw.
 * @property maxDistance How far from the camera, in world units, the followed point may be before
 *   the element hides; 0 for no limit.
 * @property occlusion Whether the element hides while something stands between the camera and the
 *   followed point, as the host's [CanvasResolver.occluded] says. A host that cannot tell hides none.
 * @property group The [CanvasFollowLimit] the element counts against; elements that do not set one
 *   share the unnamed group.
 * @property priority How important the element is when a limit ranks by
 *   [CanvasFollowRank.Priority]: higher stays.
 * @property scale The element's size by the point's distance from the camera: 1 its own size, 0.5
 *   half. Its text, images and children scale with it, about its anchor point. Null keeps its size.
 * @property fade The element's opacity by the point's distance, from 0, gone, to 1. Null keeps it
 *   opaque. An element faded or scaled to nothing is left out.
 * @property pinToEdge Whether, while the followed point is off the canvas or behind the camera, the
 *   element holds to the canvas's edge on the side the point lies, instead of hiding. While it is
 *   held there [CanvasElement.pinned] is true, and a child with [CanvasElement.followArrow] points
 *   to the target.
 * @property edgeMargin How far in from the canvas's edges, in dp, a pinned element is held.
 */
@Serializable
data class CanvasFollowRules(
    val cullOffScreen: Boolean = true,
    @PropertyRange(min = 0.0) val maxDistance: Float = 0f,
    val occlusion: Boolean = false,
    val group: String = "",
    val priority: Float = 0f,
    val scale: CanvasCurve? = null,
    val fade: CanvasCurve? = null,
    val pinToEdge: Boolean = false,
    @PropertyRange(min = 0.0) val edgeMargin: Float = 0f,
) {
    /** What is wrong with these rules, each as `field message`; empty when nothing is. */
    fun problems(): List<String> = buildList {
        if (maxDistance < 0f) add("maxDistance must not be negative")
        if (edgeMargin < 0f) add("edgeMargin must not be negative")
        scale?.problems()?.forEach { add("scale.$it") }
        fade?.problems()?.forEach { add("fade.$it") }
    }

    /** Holds [Default]. */
    companion object {
        /** What an element with no rules does: culled off screen, never pinned, ranked by nothing of its own. */
        val Default: CanvasFollowRules = CanvasFollowRules()
    }
}

/**
 * The most followers of one [group] drawn at once, keeping the most important by [rankBy] and
 * leaving out the rest: name plates over a crowd. Set on the canvas, as
 * `CanvasOverlay(followLimits = …)`, or saved with a scene as a `canvas_follow_limit`.
 *
 * Only followers that would otherwise be drawn count: one hidden, culled, too far or occluded makes
 * room for the next.
 *
 * @property group The [CanvasFollowRules.group] it limits; empty limits the followers that name none.
 * @property max The most of them drawn at once; 0 for no limit.
 * @property rankBy Which are kept.
 */
@Serializable
data class CanvasFollowLimit(
    val group: String = "",
    @PropertyRange(min = 0.0) val max: Int = 0,
    val rankBy: CanvasFollowRank = CanvasFollowRank.Distance,
) {
    /** What is wrong with this limit, each as `field message`; empty when nothing is. */
    fun problems(): List<String> = if (max < 0) listOf("max must not be negative") else emptyList()
}
