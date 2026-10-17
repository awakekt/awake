/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior

import com.awakekt.awake.ai.btree.CompareOp
import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.core.schema.PropertyUnit
import kotlinx.serialization.Serializable

/**
 * A fixed position in a spatial kind's parameters.
 *
 * @property x X.
 * @property y Y; spatial kinds measure on the ground plane, so it is kept but not used.
 * @property z Z.
 */
@Serializable
data class Point(val x: Float, val y: Float = 0f, val z: Float)

/**
 * Where a spatial kind goes or looks: a blackboard [key] holding an Entity (followed wherever it
 * is) or a Vec3f, such as `perception.target`, or a fixed [point]. Give one.
 *
 * @property key A blackboard key.
 * @property point A fixed position.
 */
@Serializable
data class Target(val key: String? = null, val point: Point? = null) {
    init {
        require((key == null) != (point == null)) { "a target is a key or a point, not both or neither." }
        require(key == null || key.isNotBlank()) { "a target key is not blank." }
    }
}

/**
 * Parameters of `ai.move-to`.
 *
 * @property target Where to go.
 * @property arriveRadius How close counts as there; never less than [waypointRadius], where the route ends.
 * @property speed Metres per second.
 * @property repathInterval Seconds between route queries, while the target moves.
 * @property waypointRadius How close counts as reaching a waypoint.
 */
@Serializable
data class MoveToParams(
    val target: Target,
    @PropertyRange(min = 0.0) @PropertyUnit("m") val arriveRadius: Float = 0.5f,
    @PropertyRange(min = 0.0, exclusiveMin = true) @PropertyUnit("m/s") val speed: Float = 2.5f,
    @PropertyRange(min = 0.0) @PropertyUnit("s") val repathInterval: Float = 0.5f,
    @PropertyRange(min = 0.0) @PropertyUnit("m") val waypointRadius: Float = 0.3f,
) {
    init {
        requireMotion(speed, repathInterval, waypointRadius)
        require(arriveRadius.isFinite() && arriveRadius >= 0f) { "arriveRadius is $arriveRadius; it must be 0 or more." }
    }
}

/**
 * Parameters of `ai.follow`.
 *
 * @property target Whom or what to follow.
 * @property distance How close to keep; it stands still within it.
 * @property speed Metres per second.
 * @property repathInterval Seconds between route queries.
 * @property waypointRadius How close counts as reaching a waypoint.
 */
@Serializable
data class FollowParams(
    val target: Target,
    @PropertyRange(min = 0.0) @PropertyUnit("m") val distance: Float = 2f,
    @PropertyRange(min = 0.0, exclusiveMin = true) @PropertyUnit("m/s") val speed: Float = 2.5f,
    @PropertyRange(min = 0.0) @PropertyUnit("s") val repathInterval: Float = 0.5f,
    @PropertyRange(min = 0.0) @PropertyUnit("m") val waypointRadius: Float = 0.3f,
) {
    init {
        requireMotion(speed, repathInterval, waypointRadius)
        require(distance.isFinite() && distance >= 0f) { "distance is $distance; it must be 0 or more." }
    }
}

/**
 * Parameters of `ai.flee`.
 *
 * @property from What to run from.
 * @property safeDistance It succeeds once this far away.
 * @property fleeDistance How far ahead it aims each time it picks where to run.
 * @property speed Metres per second.
 * @property repathInterval Seconds between route queries.
 * @property waypointRadius How close counts as reaching a waypoint.
 */
@Serializable
data class FleeParams(
    val from: Target,
    @PropertyRange(min = 0.0) @PropertyUnit("m") val safeDistance: Float = 10f,
    @PropertyRange(min = 0.0, exclusiveMin = true) @PropertyUnit("m") val fleeDistance: Float = 8f,
    @PropertyRange(min = 0.0, exclusiveMin = true) @PropertyUnit("m/s") val speed: Float = 3.5f,
    @PropertyRange(min = 0.0) @PropertyUnit("s") val repathInterval: Float = 0.4f,
    @PropertyRange(min = 0.0) @PropertyUnit("m") val waypointRadius: Float = 0.3f,
) {
    init {
        requireMotion(speed, repathInterval, waypointRadius)
        require(safeDistance.isFinite() && safeDistance >= 0f) { "safeDistance is $safeDistance; it must be 0 or more." }
        require(fleeDistance.isFinite() && fleeDistance > 0f) { "fleeDistance is $fleeDistance; it must be more than 0." }
    }
}

/**
 * Parameters of `ai.wander`.
 *
 * @property radius How far from where it started wandering it may go.
 * @property arriveRadius How close to the picked point counts as there; never less than [waypointRadius].
 * @property speed Metres per second.
 * @property waypointRadius How close counts as reaching a waypoint.
 * @property seed Mixed into the tree's seed.
 */
@Serializable
data class WanderParams(
    @PropertyRange(min = 0.0, exclusiveMin = true) @PropertyUnit("m") val radius: Float = 5f,
    @PropertyRange(min = 0.0) @PropertyUnit("m") val arriveRadius: Float = 0.5f,
    @PropertyRange(min = 0.0, exclusiveMin = true) @PropertyUnit("m/s") val speed: Float = 1.5f,
    @PropertyRange(min = 0.0) @PropertyUnit("m") val waypointRadius: Float = 0.3f,
    val seed: Long = 0L,
) {
    init {
        requireMotion(speed, 0f, waypointRadius)
        require(radius.isFinite() && radius > 0f) { "radius is $radius; it must be more than 0." }
        require(arriveRadius.isFinite() && arriveRadius >= 0f) { "arriveRadius is $arriveRadius; it must be 0 or more." }
    }
}

/**
 * Parameters of `ai.face`.
 *
 * @property target What to turn toward.
 */
@Serializable
data class FaceParams(val target: Target)

/**
 * Parameters of `ai.distance`.
 *
 * @property target What to measure to.
 * @property op How the distance compares with [distance]; `<` is "within".
 * @property distance The distance, in metres, on the ground plane.
 */
@Serializable
data class DistanceParams(
    val target: Target,
    val op: CompareOp = CompareOp.Less,
    @PropertyRange(min = 0.0) @PropertyUnit("m") val distance: Float,
) {
    init {
        require(distance.isFinite() && distance >= 0f) { "distance is $distance; it must be 0 or more." }
    }
}

/**
 * Parameters of `ai.in-area`: a circle ([center] and [radius]) or a box ([min] and [max]) on the
 * ground plane. Give one.
 *
 * @property center A circle's centre.
 * @property radius A circle's radius.
 * @property min A box's lowest corner.
 * @property max A box's highest corner.
 * @property who Whose position is tested; the agent's own when left out.
 */
@Serializable
data class AreaParams(
    val center: Point? = null,
    val radius: Float? = null,
    val min: Point? = null,
    val max: Point? = null,
    val who: Target? = null,
) {
    init {
        val circle = center != null && radius != null
        val box = min != null && max != null
        require(circle != box && (circle || (center == null && radius == null)) && (box || (min == null && max == null))) {
            "an area is a circle (center and radius) or a box (min and max)."
        }
        require(radius == null || (radius.isFinite() && radius >= 0f)) { "radius is $radius; it must be 0 or more." }
        require(min == null || max == null || (min.x <= max.x && min.z <= max.z)) { "min is above max." }
    }
}

private fun requireMotion(speed: Float, repathInterval: Float, waypointRadius: Float) {
    require(speed.isFinite() && speed > 0f) { "speed is $speed; it must be more than 0." }
    require(repathInterval.isFinite() && repathInterval >= 0f) { "repathInterval is $repathInterval; it must be 0 or more." }
    require(waypointRadius.isFinite() && waypointRadius >= 0f) { "waypointRadius is $waypointRadius; it must be 0 or more." }
}
