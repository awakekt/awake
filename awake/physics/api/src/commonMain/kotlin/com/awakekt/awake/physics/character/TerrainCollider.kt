/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.character

import com.awakekt.awake.core.math.GridOrigin
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.CollisionLayers
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.physics.PhysicsWorld

/**
 * A heightfield body and where it lies: what [KinematicCharacterController.liftOutOf] needs to
 * find a character that ended up under the terrain and put it back on top (#1209).
 *
 * A heightfield is solid only from above, so a capsule under it falls, or rests on something
 * buried, and nothing brings it back. A heightfield also has one height per x and z, with no
 * overhangs, so "under the terrain" is well defined once it's known which body is the terrain: the
 * capsule's centre is below the surface at its x and z. A character controller can't decide that
 * on its own. Seen from below, the underside of terrain and a one-sided roof look the same.
 *
 * Built once per terrain body, after it's created: the bounds and the top come from [shape] and
 * [position], and don't follow the body if it moves.
 *
 * @property handle the heightfield's body.
 * @param shape the heightfield the body was built with.
 * @param position the body's position, where the heightfield is laid.
 */
class TerrainCollider(val handle: BodyHandle, shape: HeightFieldShape, position: Vec3f) {
    private val minX: Float
    private val minZ: Float
    private val maxX: Float
    private val maxZ: Float

    /** The highest point of the terrain, in world units: where a search for its surface starts. */
    val top: Float

    /** The lowest point of the terrain, in world units: where a search for its surface ends. */
    private val bottom: Float

    init {
        val spanX = (shape.sampleCount - 1) * shape.scale.x
        val spanZ = (shape.sampleCount - 1) * shape.scale.z
        val centred = shape.origin == GridOrigin.Centered
        minX = position.x - if (centred) spanX * 0.5f else 0f
        minZ = position.z - if (centred) spanZ * 0.5f else 0f
        maxX = minX + spanX
        maxZ = minZ + spanZ
        top = position.y + (shape.heights.maxOrNull() ?: 0f) * shape.scale.y
        bottom = position.y + (shape.heights.minOrNull() ?: 0f) * shape.scale.y
    }

    /** Whether the terrain covers [x], [z]. */
    fun covers(x: Float, z: Float): Boolean = x in minX..maxX && z in minZ..maxZ

    /**
     * The height of this terrain's surface at [x], [z], or `null` where it isn't there.
     *
     * One ray down from above [top], against static world geometry. A tree or a rock standing on
     * the terrain is hit first, so the search goes on from just under each hit that isn't this
     * terrain, up to [MAX_LAYERS_ABOVE] of them. Under a convex body standing on the terrain, such as
     * a crate, there's no answer: a ray from inside it stops at once. That spot gives `null`, and
     * a character there isn't lifted.
     */
    internal fun surfaceAt(world: PhysicsWorld, x: Float, z: Float, origin: Vec3f): Float? {
        origin.set(x, top + MARGIN, z)
        var reach = top - bottom + 2f * MARGIN
        var surface: Float? = null
        var layers = 0
        while (surface == null && reach > 0f && layers <= MAX_LAYERS_ABOVE) {
            val hit = world.raycast(origin, DOWN, reach, CollisionLayers.World) ?: break
            if (hit.handle == handle) {
                surface = origin.y - hit.distance
            } else {
                val skip = hit.distance + PAST_HIT
                origin.y -= skip
                reach -= skip
                layers++
            }
        }
        return surface
    }
}

private val DOWN = Vec3f(0f, -1f, 0f)

/** A search runs this far past [TerrainCollider.top] and its bottom, so a surface at either is still hit. */
private const val MARGIN = 1f

/** How far under a hit on something else the search goes on, so it doesn't hit that again. */
private const val PAST_HIT = 0.01f

/** How many bodies standing on the terrain at one spot a search goes through. */
private const val MAX_LAYERS_ABOVE = 4
