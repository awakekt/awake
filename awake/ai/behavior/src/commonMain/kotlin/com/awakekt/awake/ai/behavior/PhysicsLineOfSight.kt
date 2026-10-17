/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior

import com.awakekt.awake.ai.perception.LineOfSight
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.CollisionLayer
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.RaycastHit

/** One physics ray query: the first body along a ray, as [PhysicsWorld.raycast] answers it. */
fun interface RayCaster {
    /** The first body from [origin] along [direction], a unit vector, within [maxDistance], or `null`. */
    fun cast(origin: Vec3f, direction: Vec3f, maxDistance: Float): RaycastHit?
}

/**
 * Line of sight through physics: a line is clear when the first body a ray from one end meets is
 * the target's own, or nothing short of the other end. The viewer's own body does not block it:
 * a ray that starts inside or on it is cast again from just past it.
 *
 * [bodyOf] says which body is an entity's own, such as a scene's `PhysicsBody.handle`. An entity it
 * knows no body for blocks nothing of its own, and is blocked by every body in the way.
 *
 * @param rays The ray query.
 * @param bodyOf The body of an entity in a world, or `null`.
 * @param endSlack How far short of the far end a hit may be and still count as reaching it, so a
 *   target point just inside a wall's surface is not hidden by that surface.
 */
class PhysicsLineOfSight(
    private val rays: RayCaster,
    private val bodyOf: (World, Entity) -> BodyHandle?,
    private val endSlack: Float = DEFAULT_END_SLACK,
) : LineOfSight {
    /**
     * Line of sight over [physics]'s raycast, restricted to [onlyLayer] when set, such as a layer for
     * walls only.
     */
    constructor(
        physics: PhysicsWorld,
        bodyOf: (World, Entity) -> BodyHandle?,
        onlyLayer: CollisionLayer? = null,
        endSlack: Float = DEFAULT_END_SLACK,
    ) : this(RayCaster { origin, direction, maxDistance -> physics.raycast(origin, direction, maxDistance, onlyLayer) }, bodyOf, endSlack)

    init {
        require(endSlack.isFinite() && endSlack >= 0f) { "endSlack is $endSlack; it must be 0 or more." }
    }

    private val origin = Vec3f(0f, 0f, 0f)
    private val direction = Vec3f(0f, 0f, 0f)

    override fun isClear(world: World, from: Vec3f, to: Vec3f, viewer: Entity, target: Entity?): Boolean {
        val length = from.distanceTo(to)
        if (length <= endSlack) return true
        direction.set((to.x - from.x) / length, (to.y - from.y) / length, (to.z - from.z) / length)
        origin.set(from)
        return castThrough(length, bodyOf(world, viewer), target?.let { bodyOf(world, it) })
    }

    /** Casts from [origin] along [direction] for [length], stepping past [own] body, until a verdict. */
    private fun castThrough(length: Float, own: BodyHandle?, theirs: BodyHandle?): Boolean {
        var travelled = 0f
        var verdict: Boolean? = null
        var hops = 0
        while (verdict == null) {
            val hit = rays.cast(origin, direction, length - travelled)
            verdict = when {
                hit == null || travelled + hit.distance >= length - endSlack -> true
                theirs != null && hit.handle == theirs -> true
                own != null && hit.handle == own && hops < MAX_SELF_HOPS -> null
                else -> false
            }
            if (verdict == null && hit != null) {
                // Step past the viewer's own surface and look again.
                val step = hit.distance + SELF_STEP
                origin.set(origin.x + direction.x * step, origin.y + direction.y * step, origin.z + direction.z * step)
                travelled += step
                hops++
                if (travelled >= length) verdict = true
            }
        }
        return verdict
    }

    private companion object {
        const val DEFAULT_END_SLACK = 0.05f
        const val SELF_STEP = 0.01f
        const val MAX_SELF_HOPS = 4
    }
}
