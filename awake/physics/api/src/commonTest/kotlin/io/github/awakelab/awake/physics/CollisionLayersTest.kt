/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.physics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollisionLayersTest {

    @Test
    fun theDefaultReproducesWhatTheBackendsHardcodedBeforeLayersExisted() {
        val layers = CollisionLayers.Default

        // Moving things hit the world and each other; the static world does not hit itself. That
        // last one is the whole point: terrain against terrain is work with no possible outcome.
        assertTrue(layers.collides(CollisionLayers.Moving, CollisionLayers.World))
        assertTrue(layers.collides(CollisionLayers.Moving, CollisionLayers.Moving))
        assertFalse(layers.collides(CollisionLayers.World, CollisionLayers.World))
    }

    @Test
    fun onlyMovingLayersAreInTheMovingSet() {
        assertTrue(CollisionLayers.Default.isMoving(CollisionLayers.Moving))
        assertFalse(CollisionLayers.Default.isMoving(CollisionLayers.World))
    }

    @Test
    fun aStaticBodyIsWorldAndEverythingElseMoves() {
        assertEquals(CollisionLayers.World, defaultLayerFor(MotionType.STATIC))
        assertEquals(CollisionLayers.Moving, defaultLayerFor(MotionType.DYNAMIC))
        assertEquals(CollisionLayers.Moving, defaultLayerFor(MotionType.KINEMATIC))
    }

    @Test
    fun anAsymmetricMatrixIsRefusedRatherThanBuilt() {
        // A body solid from one side only is a physics bug that takes hours to find, and the table
        // that produced it takes seconds to reject.
        val failure = assertFailsWith<IllegalArgumentException> {
            CollisionLayers(count = 2, movingLayers = setOf(CollisionLayer(1))) { a, b ->
                a.index == 0 && b.index == 1
            }
        }
        assertTrue(failure.message.orEmpty().contains("symmetric"), failure.message.orEmpty())
    }

    @Test
    fun aMovingLayerOutsideTheWorldIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            CollisionLayers(count = 2, movingLayers = setOf(CollisionLayer(5))) { _, _ -> true }
        }
    }

    @Test
    fun aNegativeLayerIndexIsRefused() {
        assertFailsWith<IllegalArgumentException> { CollisionLayer(-1) }
    }

    @Test
    fun aCustomMatrixIsHonouredInBothDirections() {
        val world = CollisionLayer(0)
        val player = CollisionLayer(1)
        val debris = CollisionLayer(2)
        // Debris falls through the player but still lands on the ground -- the classic reason to
        // want layers at all.
        val layers = CollisionLayers(count = 3, movingLayers = setOf(player, debris)) { a, b ->
            !(a == player && b == debris || a == debris && b == player)
        }

        assertFalse(layers.collides(player, debris))
        assertFalse(layers.collides(debris, player))
        assertTrue(layers.collides(debris, world))
        assertTrue(layers.collides(player, world))
    }
}
