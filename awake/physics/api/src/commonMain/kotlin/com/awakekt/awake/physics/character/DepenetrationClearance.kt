/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.character

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.CollisionLayers
import com.awakekt.awake.physics.PhysicsWorld

/**
 * How far a character's centre may be pushed from [from] along the unit [direction], at most
 * [distance], before the capsule would reach static world geometry in front of it (#1209).
 *
 * A push out of an overlap is not swept, because the sweep would start inside what the character
 * is leaving and stop at once. Unchecked, a push away from a mesh or another character can carry
 * the capsule through the ground beside it. Under a heightfield, which is solid only from above,
 * nothing brings it back.
 *
 * So one ray runs from the centre along the push, [clearance] beyond it: as far as the capsule's
 * leading side would reach, plus a gap. A surface it meets from the front is one the push would cross
 * or end inside. The push stops that gap short of it, or, when that leaves no room, does not happen
 * at all. A ray sees front faces
 * only, so a mesh being left, which faces back along the push, never stops it. A convex body is
 * solid to a ray from within, so when the centre is inside the body being left, such as a crate, the
 * ray stops on [leaving] at once. That hit doesn't count, and the push goes unguarded, as it did
 * before. A hit on [leaving] further along does count: a heightfield the push would cross is the
 * same body as the one being left when the overlap is with the terrain itself.
 *
 * Limits:
 * - The ray asks only the [CollisionLayers.World] layer, where static bodies go by default. A ray
 *   starting inside the character's own body, or a neighbour's, would otherwise stop at once.
 *   Static geometry a game puts on its own layer isn't guarded.
 * - Only the centre line is checked. A capsule whose side clips a surface the line misses ends
 *   overlapping that surface with its centre still in front, and the next push-out leaves the
 *   right way.
 * - A ray allocates its hit. That happens only on a push, not on every move.
 *
 * @param from the capsule's centre.
 * @param direction the push's unit direction.
 * @param distance how far the push would go unguarded.
 * @param leaving the body the push moves the character out of.
 * @param clearance the capsule's reach from its centre along [direction], plus the gap to keep from
 *   the surface the push stops at.
 * @return the distance to push, between zero and [distance].
 */
internal fun PhysicsWorld.clearPushDistance(
    from: Vec3f,
    direction: Vec3f,
    distance: Float,
    leaving: BodyHandle,
    clearance: Float,
): Float {
    val hit = raycast(from, direction, distance + clearance, CollisionLayers.World)
    val fromInside = hit != null && hit.handle == leaving && hit.distance <= FROM_INSIDE
    if (hit == null || fromInside) return distance
    return (hit.distance - clearance).coerceIn(0f, distance)
}

/** A ray hit this close to its origin started inside the body it hit. */
private const val FROM_INSIDE = 1e-4f
