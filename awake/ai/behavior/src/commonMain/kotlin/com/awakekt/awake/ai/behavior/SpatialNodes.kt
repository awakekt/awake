/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior

import com.awakekt.awake.ai.AiContext
import com.awakekt.awake.ai.Snapshottable
import com.awakekt.awake.ai.btree.BehaviorNode
import com.awakekt.awake.ai.btree.BehaviorStatus
import com.awakekt.awake.ai.btree.CompareOp
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.navigation.PathRequest
import com.awakekt.awake.navigation.PathStatus
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * What a spatial node keeps between ticks: its route, and for wandering and fleeing, where it is
 * headed and how.
 *
 * @property path The route's waypoints.
 * @property waypoint The waypoint it steers toward.
 * @property sinceRepath Seconds since it last asked for a route.
 * @property home Where wandering started.
 * @property goal The point it picked to wander to.
 * @property random The wander generator's state.
 * @property attempt How many escape directions fleeing has tried.
 */
@Serializable
data class RouteState(
    val path: List<Point> = emptyList(),
    val waypoint: Int = 0,
    val sinceRepath: Float = Float.MAX_VALUE,
    val home: Point? = null,
    val goal: Point? = null,
    val random: Long = 0L,
    val attempt: Int = 0,
)

/** Writes where [this] target is into [into]; false when its key is unset, or names nothing with a place. */
internal fun Target.resolve(placement: AgentPlacement, context: AiContext, into: Vec3f): Boolean {
    point?.let {
        into.set(it.x, it.y, it.z)
        return true
    }
    return when (val value = context.blackboard.get<Any>(key!!)) {
        is Entity -> placement.position(context.world, value, into)
        is Vec3f -> {
            into.set(value)
            true
        }
        else -> false
    }
}

internal fun groundDistance(a: Vec3f, b: Vec3f): Float {
    val dx = a.x - b.x
    val dz = a.z - b.z
    return sqrt(dx * dx + dz * dz)
}

/** A node that walks a route over the entity's [PathRequest], sharing [RouteFollower]'s steps. */
internal abstract class RouteNode(
    protected val placement: AgentPlacement,
    override var speed: Float,
    override var repathInterval: Float,
    override var waypointRadius: Float,
) : BehaviorNode,
    RouteFollower,
    Snapshottable<RouteState> {
    override var path: List<Vec3f> = emptyList()
    override var waypointIndex: Int = 0
    override var timeSinceRepath: Float = Float.MAX_VALUE
    protected val position = Vec3f(0f, 0f, 0f)
    protected val goal = Vec3f(0f, 0f, 0f)
    private var request: PathRequest? = null

    /** The entity's request and position, read into [position]; null when it has either missing. */
    protected fun begin(context: AiContext): PathRequest? {
        val found = context.world.get<PathRequest>(context.entity)
        request = found
        return found?.takeIf { placement.position(context.world, context.entity, position) }
    }

    /** One step toward [goal]; false when the last route answer was that it cannot be reached. */
    protected fun walk(context: AiContext, request: PathRequest): Boolean {
        if (request.status == PathStatus.Unreachable) {
            stop()
            return false
        }
        adoptArrivedRoute(request)
        requestRouteWhenDue(position, goal, request, context.delta)
        steer(placement, context.world, context.entity, position, context.delta)
        return true
    }

    /** Drops the route and withdraws the question, so nothing walks it after this node lets go. */
    protected fun stop() {
        path = emptyList()
        waypointIndex = 0
        timeSinceRepath = Float.MAX_VALUE
        request?.takeIf { it.status != PathStatus.Idle }?.cancel()
    }

    override fun reset() {
        stop()
    }

    override val stateSerializer: KSerializer<RouteState> get() = RouteState.serializer()

    override fun captureState(): RouteState =
        RouteState(path.map { Point(it.x, it.y, it.z) }, waypointIndex, timeSinceRepath)

    override fun restoreState(state: RouteState) {
        require(state.waypoint in 0..state.path.size) { "Waypoint ${state.waypoint} is outside the route of ${state.path.size}." }
        path = state.path.map { Vec3f(it.x, it.y, it.z) }
        waypointIndex = state.waypoint
        timeSinceRepath = state.sinceRepath
    }
}

/** `ai.move-to`: walks to a target, then succeeds. */
internal class MoveToNode(placement: AgentPlacement, private val params: MoveToParams) : RouteNode(placement, params.speed, params.repathInterval, params.waypointRadius) {
    override fun tick(context: AiContext): BehaviorStatus {
        val request = begin(context)
        val known = request != null && params.target.resolve(placement, context, goal)
        return when {
            !known -> BehaviorStatus.FAILURE.also { stop() }
            groundDistance(position, goal) <= maxOf(params.arriveRadius, params.waypointRadius) -> BehaviorStatus.SUCCESS.also { stop() }
            walk(context, request!!) -> BehaviorStatus.RUNNING
            else -> BehaviorStatus.FAILURE
        }
    }
}

/** `ai.follow`: keeps within a distance of a target, running for as long as the target has a place. */
internal class FollowNode(placement: AgentPlacement, private val params: FollowParams) : RouteNode(placement, params.speed, params.repathInterval, params.waypointRadius) {
    override fun tick(context: AiContext): BehaviorStatus {
        val request = begin(context)
        val known = request != null && params.target.resolve(placement, context, goal)
        return when {
            !known -> BehaviorStatus.FAILURE.also { stop() }
            groundDistance(position, goal) <= params.distance -> BehaviorStatus.RUNNING.also { stop() }
            walk(context, request!!) -> BehaviorStatus.RUNNING
            else -> BehaviorStatus.FAILURE
        }
    }
}

/**
 * `ai.flee`: runs from a threat until [FleeParams.safeDistance] away. When the way straight out
 * cannot be reached it tries directions further off it, and fails when none can.
 */
internal class FleeNode(placement: AgentPlacement, private val params: FleeParams) : RouteNode(placement, params.speed, params.repathInterval, params.waypointRadius) {
    private val threat = Vec3f(0f, 0f, 0f)
    private var attempt = 0

    override fun tick(context: AiContext): BehaviorStatus {
        val request = begin(context)
        val known = request != null && params.from.resolve(placement, context, threat)
        return when {
            !known -> BehaviorStatus.FAILURE.also { stop() }
            groundDistance(position, threat) >= params.safeDistance -> BehaviorStatus.SUCCESS.also { reset() }
            else -> run(context, request!!)
        }
    }

    private fun run(context: AiContext, request: PathRequest): BehaviorStatus {
        val length = groundDistance(position, threat)
        // Standing on the threat, any way is away; +X is as good as another.
        val awayX = if (length == 0f) 1f else (position.x - threat.x) / length
        val awayZ = if (length == 0f) 0f else (position.z - threat.z) / length
        val angle = ESCAPE_ANGLES[attempt]
        val dirX = awayX * cos(angle) - awayZ * sin(angle)
        val dirZ = awayX * sin(angle) + awayZ * cos(angle)
        goal.set(position.x + dirX * params.fleeDistance, position.y, position.z + dirZ * params.fleeDistance)
        if (!walk(context, request)) attempt++
        val exhausted = attempt >= ESCAPE_ANGLES.size
        if (exhausted) attempt = 0
        return if (exhausted) BehaviorStatus.FAILURE else BehaviorStatus.RUNNING
    }

    override fun reset() {
        super.reset()
        attempt = 0
    }

    override fun captureState(): RouteState = super.captureState().copy(attempt = attempt)

    override fun restoreState(state: RouteState) {
        require(state.attempt in ESCAPE_ANGLES.indices) { "Escape attempt ${state.attempt} is outside ${ESCAPE_ANGLES.indices}." }
        super.restoreState(state)
        attempt = state.attempt
    }

    private companion object {
        private const val QUARTER = (PI / 4).toFloat()
        val ESCAPE_ANGLES = floatArrayOf(0f, QUARTER, -QUARTER, 2 * QUARTER, -2 * QUARTER)
    }
}

/**
 * `ai.wander`: picks a seeded random point within [WanderParams.radius] of where it first ran and
 * walks there, then succeeds. Repeat it, with a wait, to wander for ever.
 */
internal class WanderNode(placement: AgentPlacement, private val params: WanderParams, seed: Long) : RouteNode(placement, params.speed, Float.MAX_VALUE, params.waypointRadius) {
    private var random = seed
    private var home: Point? = null
    private var target: Point? = null

    override fun tick(context: AiContext): BehaviorStatus {
        val request = begin(context) ?: return BehaviorStatus.FAILURE.also { stop() }
        val origin = home ?: Point(position.x, position.y, position.z).also { home = it }
        val picked = target ?: pick(origin).also {
            target = it
            timeSinceRepath = Float.MAX_VALUE
        }
        goal.set(picked.x, picked.y, picked.z)
        return when {
            groundDistance(position, goal) <= maxOf(params.arriveRadius, params.waypointRadius) -> BehaviorStatus.SUCCESS.also { arrive() }
            walk(context, request) -> BehaviorStatus.RUNNING
            else -> BehaviorStatus.FAILURE.also { target = null }
        }
    }

    private fun arrive() {
        stop()
        target = null
    }

    // Uniform over the disc: the square root keeps points from bunching at the centre.
    private fun pick(origin: Point): Point {
        val angle = nextUnit() * 2.0 * PI
        val distance = sqrt(nextUnit()) * params.radius
        return Point(origin.x + (cos(angle) * distance).toFloat(), origin.y, origin.z + (sin(angle) * distance).toFloat())
    }

    // SplitMix64, so a snapshot holds the whole generator.
    private fun nextUnit(): Double {
        random += GOLDEN_GAMMA
        var z = random
        z = (z xor (z ushr 30)) * MIX_1
        z = (z xor (z ushr 27)) * MIX_2
        z = z xor (z ushr 31)
        return (z ushr 11).toDouble() / UNIT
    }

    override fun reset() {
        super.reset()
        target = null
    }

    override fun captureState(): RouteState = super.captureState().copy(home = home, goal = target, random = random)

    override fun restoreState(state: RouteState) {
        super.restoreState(state)
        home = state.home
        target = state.goal
        random = state.random
    }

    private companion object {
        const val GOLDEN_GAMMA = -0x61c8864680b583ebL
        const val MIX_1 = -0x40a7b892e31b1a47L
        const val MIX_2 = -0x6b2fb644ecceee15L
        const val UNIT = 9_007_199_254_740_992.0
    }
}

/** `ai.face`: turns toward a target through [AgentPlacement.face], and succeeds. */
internal class FaceNode(private val placement: AgentPlacement, private val params: FaceParams) : BehaviorNode {
    private val position = Vec3f(0f, 0f, 0f)
    private val target = Vec3f(0f, 0f, 0f)

    override fun tick(context: AiContext): BehaviorStatus {
        val known = placement.position(context.world, context.entity, position) && params.target.resolve(placement, context, target)
        if (known) placement.face(context.world, context.entity, target.x - position.x, target.z - position.z)
        return if (known) BehaviorStatus.SUCCESS else BehaviorStatus.FAILURE
    }
}

/** `ai.distance`: compares the ground distance to a target. */
internal class DistanceNode(private val placement: AgentPlacement, private val params: DistanceParams) : BehaviorNode {
    private val position = Vec3f(0f, 0f, 0f)
    private val target = Vec3f(0f, 0f, 0f)

    override fun tick(context: AiContext): BehaviorStatus {
        val known = placement.position(context.world, context.entity, position) && params.target.resolve(placement, context, target)
        val holds = known && params.op.compare(groundDistance(position, target), params.distance)
        return if (holds) BehaviorStatus.SUCCESS else BehaviorStatus.FAILURE
    }
}

/** `ai.in-area`: tests whether the agent, or [AreaParams.who], stands in a circle or a box. */
internal class InAreaNode(private val placement: AgentPlacement, private val params: AreaParams) : BehaviorNode {
    private val position = Vec3f(0f, 0f, 0f)

    override fun tick(context: AiContext): BehaviorStatus {
        val known = params.who?.resolve(placement, context, position)
            ?: placement.position(context.world, context.entity, position)
        val inside = known && params.contains(position)
        return if (inside) BehaviorStatus.SUCCESS else BehaviorStatus.FAILURE
    }

    private fun AreaParams.contains(at: Vec3f): Boolean {
        val c = center
        val r = radius
        val low = min
        val high = max
        return when {
            c != null && r != null -> (at.x - c.x) * (at.x - c.x) + (at.z - c.z) * (at.z - c.z) <= r * r
            low != null && high != null -> at.x in low.x..high.x && at.z in low.z..high.z
            else -> false
        }
    }
}

private fun CompareOp.compare(left: Float, right: Float): Boolean =
    when (this) {
        CompareOp.Equal -> left == right
        CompareOp.NotEqual -> left != right
        CompareOp.Less -> left < right
        CompareOp.LessOrEqual -> left <= right
        CompareOp.Greater -> left > right
        CompareOp.GreaterOrEqual -> left >= right
    }
