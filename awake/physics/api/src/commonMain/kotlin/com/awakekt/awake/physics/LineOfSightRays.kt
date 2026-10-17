/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics

import com.awakekt.awake.core.math.Vec3f
import kotlin.math.sqrt

/**
 * Whether something solid stands between two points, by [PhysicsWorld.raycast], for many askers a
 * frame at a cost that stays bounded: at most [raysPerFrame] rays a frame, and each answer reused
 * for [reuseFrames] frames before it is cast again. Labels over a crowd, each asking whether a wall
 * hides it, are what it is for.
 *
 * Each asker is a key of type [K], such as the entity a label follows. Call [nextFrame] once a
 * frame, then [blocked] for each key. A key's answer younger than [reuseFrames] frames comes back
 * without a ray. Of the keys due again, the ones that have waited longest are cast first, within
 * the frame's budget, and the rest keep their last answer for another frame; so with more keys due
 * than the budget allows, each is still cast again in turn and none waits for ever. A key never
 * cast is clear until its first ray, and goes ahead of the others in the next frame's budget.
 *
 * A key not asked for [FORGET_FRAMES] frames is forgotten, so keys that come and go cost nothing
 * once gone.
 *
 * Not thread-safe: one frame loop asks it.
 *
 * @param K What an answer is kept for: compared by equality, so an entity or a name.
 * @param physics The world the rays are cast in.
 * @param raysPerFrame The most rays cast between two [nextFrame] calls; at least 1.
 * @param reuseFrames How many frames an answer stands before its key is cast again; 1 casts each
 *   key every frame, budget allowing.
 * @property onlyLayer Casts against this one layer only, as [PhysicsWorld.raycast] takes it: the
 *   static world's, so only walls and terrain hide anything. Null casts against every layer.
 * @param ownBody Whether a body belongs to the key itself, such as the capsule around a character
 *   whose label asks. A ray that reaches the key's own body first has found nothing in the way, so
 *   the answer is clear.
 */
class LineOfSightRays<K : Any>(
    private val physics: PhysicsWorld,
    raysPerFrame: Int = DEFAULT_RAYS_PER_FRAME,
    reuseFrames: Int = DEFAULT_REUSE_FRAMES,
    var onlyLayer: CollisionLayer? = null,
    private val ownBody: (key: K, body: BodyHandle) -> Boolean = { _, _ -> false },
) {
    /** The most rays cast between two [nextFrame] calls; at least 1. */
    var raysPerFrame: Int = raysPerFrame
        set(value) {
            require(value >= 1) { "raysPerFrame must be at least 1: $value" }
            field = value
        }

    /** How many frames an answer stands before its key is cast again; at least 1. */
    var reuseFrames: Int = reuseFrames
        set(value) {
            require(value >= 1) { "reuseFrames must be at least 1: $value" }
            field = value
        }

    init {
        // Through the setters, so the constructor checks what they check.
        this.raysPerFrame = raysPerFrame
        this.reuseFrames = reuseFrames
    }

    /** A key's last answer, the frame it was cast in and the frame it was last asked in. */
    private class Sight(var blocked: Boolean, var castFrame: Long, var askedFrame: Long)

    private val sights = HashMap<K, Sight>()
    private var frame = 0L

    // How old a due answer must be to be cast this frame: those waiting longest fill the budget.
    private var castAge = 0
    private var ages = IntArray(0)

    /** Rays cast since the last [nextFrame]. */
    var raysCast: Int = 0
        private set

    /** How many keys it holds an answer, or a place for one, for. */
    val size: Int get() = sights.size

    /**
     * Starts a frame: forgets the keys not asked for [FORGET_FRAMES] frames and settles which of the
     * answers due are cast this frame, those waiting longest first.
     */
    fun nextFrame() {
        frame++
        raysCast = 0
        val each = sights.values.iterator()
        while (each.hasNext()) if (frame - each.next().askedFrame > FORGET_FRAMES) each.remove()
        if (ages.size < sights.size) ages = IntArray(sights.size * 2)
        var due = 0
        for (sight in sights.values) {
            val age = ageOf(sight)
            if (age >= reuseFrames) ages[due++] = age
        }
        castAge = if (due <= raysPerFrame) {
            reuseFrames
        } else {
            ages.sort(0, due)
            // The budget's oldest answers are those at or above the raysPerFrame-th oldest age.
            ages[due - raysPerFrame]
        }
    }

    /**
     * Whether something solid stands on the straight line from [from] to [to], for [key]: its
     * answer from an earlier frame while that still stands or the frame's rays are spent, or a ray
     * cast now. A body the ray reaches at [to] or past it, or that is the key's [ownBody], hides
     * nothing; nor does one [from] is inside, which a ray cannot see past.
     */
    fun blocked(key: K, from: Vec3f, to: Vec3f): Boolean {
        val sight = sights.getOrPut(key) { Sight(blocked = false, castFrame = NEVER, askedFrame = frame) }
        sight.askedFrame = frame
        val age = ageOf(sight)
        val stands = age < reuseFrames || age < castAge
        if (stands || raysCast >= raysPerFrame) return sight.blocked
        raysCast++
        sight.blocked = cast(key, from, to)
        sight.castFrame = frame
        return sight.blocked
    }

    /** Forgets [key]'s answer, so its next [blocked] casts again as soon as the budget allows. */
    fun forget(key: K) {
        sights.remove(key)
    }

    /** Forgets every answer. */
    fun clear() {
        sights.clear()
    }

    private fun ageOf(sight: Sight): Int = (frame - sight.castFrame).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun cast(key: K, from: Vec3f, to: Vec3f): Boolean {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        val hit = if (length < MIN_LENGTH) null else physics.raycast(from, Vec3f(dx / length, dy / length, dz / length), length, onlyLayer)
        // A surface right at the point, such as the wall a marker is set on, does not hide it. A hit
        // right at the start is a body [from] is inside, such as a camera pushed into a wall or a
        // first-person eye in its own capsule: a solid ray reports it and nothing past it, so it
        // tells nothing about the line, and hiding every key behind it is the worse mistake.
        return hit != null && hit.distance > ARRIVAL && hit.distance < length - ARRIVAL && !ownBody(key, hit.handle)
    }

    /** The defaults. */
    companion object {
        /** Rays a frame unless set: enough to keep a few hundred keys within [DEFAULT_REUSE_FRAMES]-odd frames. */
        const val DEFAULT_RAYS_PER_FRAME: Int = 32

        /** Frames an answer stands unless set: a tenth of a second at 60 frames a second. */
        const val DEFAULT_REUSE_FRAMES: Int = 6

        /** Frames a key may go unasked before its answer is forgotten. */
        const val FORGET_FRAMES: Int = 120

        // Long before frame 0, so a key never cast is older than any that was.
        private const val NEVER = Long.MIN_VALUE / 2
        private const val MIN_LENGTH = 1e-4f
        private const val ARRIVAL = 1e-3f
    }
}
