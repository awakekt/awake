/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.awakekt.awake.core.math.GridOrigin
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.heightFieldTile
import com.awakekt.awake.physics.heightFieldTileCenter
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * That a world collided with as a grid of heightfield tiles behaves like one field.
 *
 * The failure this exists for is the seam. Tiles cut disjointly meet at two different heights, so
 * the join is a one-sample wall to walk into or a gap to drop through -- and it looks like a
 * physics or terrain-authoring problem rather than an off-by-one in the slicing. Against a real
 * Jolt world on every backend, because that is where a seam actually bites.
 */
class JoltHeightFieldTileTest {

    /** 7x7 samples cut into 2x2 tiles of 4, which is Jolt's minimum and shares one row per seam. */
    private val sourceWidth = 7
    private val tileSamples = 4
    private val scale = Vec3f(1f, 1f, 1f)

    /** A ramp along x, so the two tiles either side of a seam genuinely differ in height. */
    private val ramp = FloatArray(sourceWidth * sourceWidth) { index -> (index % sourceWidth) * 0.5f }

    /** Flat, for the tests that drop something: a body must land, not slide off a hillside. */
    private val flat = FloatArray(sourceWidth * sourceWidth) { 2f }

    private suspend fun tiledWorld(heights: FloatArray): PhysicsWorld {
        val world = createJoltPhysicsWorld()
        for (tileZ in 0 until 2) {
            for (tileX in 0 until 2) {
                val shape = assertNotNull(
                    heightFieldTile(heights, sourceWidth, sourceWidth, scale, tileX, tileZ, tileSamples),
                    "tile ($tileX, $tileZ) should be inside a 7x7 field",
                )
                world.createBody(
                    shape,
                    heightFieldTileCenter(sourceWidth, sourceWidth, scale, tileX, tileZ, tileSamples),
                    Quat.IDENTITY,
                    MotionType.STATIC,
                )
            }
        }
        return world
    }

    /** The last pose reported: a box that settles stops being awake and stops being visited. */
    private fun PhysicsWorld.restingHeightOf(body: BodyHandle, from: Float): Float {
        var y = from
        repeat(180) {
            step(1f / 60f)
            forEachBodyTransform { handle, position, _ -> if (handle == body) y = position.y }
        }
        return y
    }

    private fun PhysicsWorld.dropBoxAt(x: Float, z: Float): Float {
        val box = createBody(
            BoxShape(Vec3f(0.2f, 0.2f, 0.2f)),
            Vec3f(x, 8f, z),
            Quat.IDENTITY,
            MotionType.DYNAMIC,
        )
        return restingHeightOf(box, from = 8f)
    }

    @Test
    fun aBoxLandsOverTheSeamRatherThanFallingBetweenTiles() = runTest {
        val world = tiledWorld(flat)
        try {
            // x = 0, z = 0 is the corner where all four tiles meet -- the worst case, and the one
            // a disjoint cut leaves a hole at.
            val y = world.dropBoxAt(x = 0f, z = 0f)

            // The surface is at 2 and the box is 0.2 deep, so it rests just over 2.2.
            assertTrue(y > 2f, "the box fell through the join between tiles: y=$y")
            assertTrue(y < 3f, "the box did not settle on the surface at 2: y=$y")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun theSurfaceIsContinuousAcrossASeam() = runTest {
        val world = tiledWorld(ramp)
        try {
            // Rays rather than bodies here on purpose: this ramp is 26 degrees, and a box dropped
            // on it lands, slides down, and leaves a six-metre world -- which reads exactly like
            // falling through a seam and is not. A ray asks about the surface itself.
            val heights = listOf(-1f, -0.5f, 0f, 0.5f, 1f).map { x ->
                assertNotNull(
                    world.raycast(Vec3f(x, 20f, 0f), Vec3f(0f, -1f, 0f), 60f),
                    "no surface at x=$x, so the tiles do not meet",
                ).point.y
            }

            // The ramp rises 0.5 per sample and the samples are one apart, so every half-unit step
            // across the seam must rise about 0.25. A gap would miss entirely; a step would jump.
            heights.zipWithNext { lower, higher ->
                assertTrue(
                    abs((higher - lower) - 0.25f) < 0.05f,
                    "the surface steps at the seam: $heights",
                )
            }
        } finally {
            world.destroy()
        }
    }

    @Test
    fun aCornerAnchoredFieldPutsItsFirstTileInThePositiveQuadrant() {
        val centered = heightFieldTileCenter(sourceWidth, sourceWidth, scale, 0, 0, tileSamples)
        val corner = heightFieldTileCenter(
            sourceWidth,
            sourceWidth,
            scale,
            0,
            0,
            tileSamples,
            GridOrigin.Corner,
        )

        // A streamer keyed to positive-quadrant cell coordinates anchors at sample zero; a single
        // authored heightmap is centred. Confusing the two offsets the world by half a map.
        assertTrue(centered.x < 0f, "a centred field's first tile sits before the origin: $centered")
        assertTrue(corner.x > 0f, "a corner-anchored field's first tile sits after it: $corner")
        assertTrue(
            abs(corner.x - centered.x - (sourceWidth - 1) * 0.5f * scale.x) < 1e-4f,
            "the two anchorings differ by exactly half the field: $centered vs $corner",
        )
    }

    @Test
    fun aTilePastTheEdgeOfTheFieldIsNoTileAtAll() {
        // What a streamer gets when it asks about a cell beyond the map: null, meaning no collider,
        // rather than a clamped or wrapped tile that would collide with terrain nobody authored.
        assertNull(heightFieldTile(ramp, sourceWidth, sourceWidth, scale, 2, 0, tileSamples))
        assertNull(heightFieldTile(ramp, sourceWidth, sourceWidth, scale, 0, -1, tileSamples))
    }

    @Test
    fun neighbouringTilesReportTheSameHeightsAlongTheirSharedEdge() {
        val left = assertNotNull(heightFieldTile(ramp, sourceWidth, sourceWidth, scale, 0, 0, tileSamples))
        val right = assertNotNull(heightFieldTile(ramp, sourceWidth, sourceWidth, scale, 1, 0, tileSamples))

        // The property the simulation tests above depend on, asserted directly so a failure says
        // which of the two things broke.
        for (z in 0 until tileSamples) {
            assertTrue(
                left.heightAt(tileSamples - 1, z) == right.heightAt(0, z),
                "row $z disagrees across the seam: ${left.heightAt(tileSamples - 1, z)} " +
                    "vs ${right.heightAt(0, z)}",
            )
        }
    }
}
