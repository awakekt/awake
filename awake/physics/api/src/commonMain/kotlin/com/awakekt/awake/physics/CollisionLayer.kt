/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics

import kotlin.jvm.JvmInline

/**
 * Which group a body belongs to, for deciding what it collides with.
 *
 * An index rather than an enum: what the groups *mean* -- player, enemy, water, trigger -- is the
 * game's, and an enum here would be the engine authoring world policy. The engine only needs to
 * know that two layers differ and whether they collide.
 */
@JvmInline
value class CollisionLayer(val index: Int) {
    init {
        require(index >= 0) { "a collision layer index cannot be negative: $index" }
    }
}

/**
 * The layers a [PhysicsWorld] is built with, and which of them collide.
 *
 * Fixed at construction because that is where the backend needs it: Jolt builds its layer tables
 * when the physics system is created, and a matrix that changed afterwards would not reach them.
 *
 * [movingLayers] exists for the broadphase rather than for gameplay. Jolt keeps a separate
 * acceleration structure for things that move, and putting static world geometry in it costs a
 * rebuild every time anything else moves -- so a layer says once whether its bodies can move,
 * instead of every body saying so and the two disagreeing.
 */
class CollisionLayers(
    /** How many layers this world has. Layer indices run from zero to one below it. */
    val count: Int,
    /** The layers whose bodies can move; see the note above on why the broadphase cares. */
    val movingLayers: Set<CollisionLayer>,
    /** Consulted once per pair at construction, then never again -- the answer is a table. */
    collides: (CollisionLayer, CollisionLayer) -> Boolean,
) {
    init {
        require(count > 0) { "a world needs at least one collision layer" }
        require(count <= MAX_LAYERS) { "at most $MAX_LAYERS collision layers are supported: $count" }
        movingLayers.forEach {
            require(it.index < count) { "moving layer ${it.index} is outside a $count-layer world" }
        }
    }

    /** Per layer, a bitmask of the layers it collides with. Built once; queried per body pair. */
    private val matrix: IntArray = IntArray(count) { a ->
        var bits = 0
        for (b in 0 until count) {
            if (collides(CollisionLayer(a), CollisionLayer(b))) bits = bits or (1 shl b)
        }
        bits
    }

    init {
        // Collision is mutual, and a matrix that disagrees with itself produces a body that is
        // solid from one side only -- which reads as a physics bug rather than as a bad table.
        for (a in 0 until count) {
            for (b in 0 until count) {
                require(collides(a, b) == collides(b, a)) {
                    "collision must be symmetric, but layers $a and $b disagree"
                }
            }
        }
    }

    /** Whether bodies in these two layers collide. Symmetric, and checked to be at construction. */
    fun collides(a: CollisionLayer, b: CollisionLayer): Boolean = collides(a.index, b.index)

    private fun collides(a: Int, b: Int): Boolean = (matrix[a] and (1 shl b)) != 0

    /** Whether this layer's bodies can move, which decides the broadphase tree they live in. */
    fun isMoving(layer: CollisionLayer): Boolean = layer in movingLayers

    companion object {
        /**
         * Jolt's object layers are 16-bit, and a mask per layer is held in an `Int` here, so 32 is
         * the honest ceiling for both. Far more than any game has needed.
         */
        const val MAX_LAYERS = 32

        /** Static level geometry: terrain, walls, anything that never moves. */
        val World = CollisionLayer(0)

        /** Everything that moves and collides with the world and with itself. */
        val Moving = CollisionLayer(1)

        /**
         * Two layers, exactly matching what every backend hardcoded before layers existed: moving
         * things collide with the world and with each other, and the static world does not
         * collide with itself.
         */
        val Default: CollisionLayers = CollisionLayers(
            count = 2,
            movingLayers = setOf(Moving),
        ) { a, b -> a == Moving || b == Moving }
    }
}
