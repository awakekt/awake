/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.CapsuleShape
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.physics.MeshShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsShape
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.SphereShape
import com.awakekt.awake.physics.character.CharacterConfig
import com.awakekt.awake.physics.character.KinematicCharacterController
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Every malformed-normal source the character controller could meet, against a real Jolt world (#989).
 *
 * Each target is cast at by each swept shape from inside, from outside, along the surface exactly,
 * straight at it, and with no displacement at all. A result is either no hit or a finite point, a
 * fraction in `[0, 1]` and a unit normal; the controller then walks the same starts and must keep
 * a finite position. Degenerate geometry that Jolt refuses at creation is a refusal, not a hit.
 */
class JoltShapeCastFiniteResultsTest {

    private val swept = mapOf(
        "capsule" to CapsuleShape(halfHeight = 0.5f, radius = 0.3f),
        "thin capsule" to CapsuleShape(halfHeight = 1e-3f, radius = 1e-3f),
        "sphere" to SphereShape(0.4f),
        "box" to BoxShape(Vec3f(0.3f, 0.5f, 0.3f)),
        "tiny box" to BoxShape(Vec3f(1e-3f, 1e-3f, 1e-3f)),
        "tiny sphere" to SphereShape(1e-3f),
    )

    /** Starts that are inside, touching, beside and above the geometry the targets share around the origin. */
    private val starts = listOf(
        Vec3f(0f, 0f, 0f),
        Vec3f(0f, 0.3f, 0f),
        Vec3f(0f, 0.8f, 0f),
        Vec3f(0.5f, 0.8f, 0.5f),
        Vec3f(1f, 0.8f, 0f),
        Vec3f(0f, 3f, 0f),
        Vec3f(-1.5f, 0.8f, 1.5f),
        Vec3f(0f, 0.8f, 1e-7f),
    )

    private val displacements = listOf(
        Vec3f(0f, 0f, 0f), // no displacement
        Vec3f(1e-9f, 0f, 0f), // below any sane epsilon
        Vec3f(0f, -1f, 0f), // into the surface
        Vec3f(1f, 0f, 0f), // along a horizontal surface
        Vec3f(0f, 0f, 2f), // along it, the other way
        Vec3f(0.1f, -0.1f, 0f), // a character-sized step
        Vec3f(0f, 1e-6f, 0f), // vertical hair
    )

    private fun targets(): Map<String, () -> PhysicsShape> = mapOf(
        "box" to { BoxShape(Vec3f(1f, 0.5f, 1f)) },
        "sphere" to { SphereShape(0.8f) },
        "mesh" to { MeshShape(QUAD, intArrayOf(0, 2, 1, 0, 3, 2)) },
        "mesh with zero-area triangles" to { MeshShape(DEGENERATE_MESH, DEGENERATE_INDICES) },
        "flat heightfield" to { HeightFieldShape(FloatArray(SAMPLES * SAMPLES), SAMPLES, Vec3f(1f, 1f, 1f)) },
        "heightfield with a vertical cell" to { cliff() },
    )

    @Test
    fun noCastAgainstAnyTargetReturnsAnUnusableContact() = runTest {
        var hits = 0
        for ((targetName, target) in targets()) {
            world(target()) { world ->
                for ((shapeName, shape) in swept) {
                    for (from in starts) {
                        for (motion in displacements) {
                            val to = Vec3f(from.x + motion.x, from.y + motion.y, from.z + motion.z)
                            val context = "$shapeName vs $targetName from=$from to=$to"
                            val hit = world.shapeCast(shape, from, to) ?: continue
                            hits++
                            assertTrue(hit.point.isFinite() && hit.normal.isFinite(), "non-finite contact: $hit, $context")
                            assertTrue(hit.fraction in 0f..1f, "fraction ${hit.fraction}, $context")
                            assertEqualsUnit(hit.normal, context)
                        }
                    }
                }
            }
        }
        assertTrue(hits > 100, "the matrix must actually exercise contacts: $hits")
    }

    @Test
    fun theControllerKeepsAFinitePositionOnEveryTarget() = runTest {
        val config = CharacterConfig(CapsuleShape(halfHeight = 0.4f, radius = 0.4f), slopeLimitRadians = 1.3f, stepHeight = 0.3f)
        for ((targetName, target) in targets()) {
            world(target()) { world ->
                for (from in starts) {
                    val character = KinematicCharacterController(world, config, from)
                    for (motion in displacements + Vec3f(-0.3f, -0.05f, 0.3f)) {
                        repeat(30) {
                            character.move(motion, 1f / 60f)
                            val p = character.position
                            assertTrue(p.isFinite(), "non-finite position $p on $targetName from=$from motion=$motion")
                        }
                    }
                    character.teleport(from)
                    character.crouch()
                    character.standUp()
                    assertTrue(character.position.isFinite(), "non-finite position after crouch/stand on $targetName from=$from")
                }
            }
        }
    }

    @Test
    fun zeroExtentShapesAndZeroScaleHeightfieldsAreRefusedBeforeAnyQuery() {
        assertFailsWith<IllegalArgumentException> { SphereShape(0f) }
        assertFailsWith<IllegalArgumentException> { SphereShape(Float.NaN) }
        assertFailsWith<IllegalArgumentException> { BoxShape(Vec3f(0f, 0.5f, 0.5f)) }
        assertFailsWith<IllegalArgumentException> { BoxShape(Vec3f(0.5f, Float.NaN, 0.5f)) }
        assertFailsWith<IllegalArgumentException> { CapsuleShape(halfHeight = 0f, radius = 0.3f) }
        assertFailsWith<IllegalArgumentException> { CapsuleShape(halfHeight = 0.5f, radius = 0f) }
        assertFailsWith<IllegalArgumentException> {
            HeightFieldShape(FloatArray(SAMPLES * SAMPLES), SAMPLES, Vec3f(0f, 1f, 1f))
        }
    }

    private suspend fun world(target: PhysicsShape, block: suspend (PhysicsWorld) -> Unit) {
        val world = createJoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
        try {
            world.createBody(target, Vec3f(0f, 0f, 0f), Quat.IDENTITY, MotionType.STATIC)
            block(world)
        } finally {
            world.destroy()
        }
    }

    /** A flat 8x8 field with one column raised 50 m, so the cells beside it are nearly vertical. */
    private fun cliff(): HeightFieldShape {
        val heights = FloatArray(SAMPLES * SAMPLES)
        heights[(SAMPLES / 2) * SAMPLES + SAMPLES / 2 + 1] = 50f
        return HeightFieldShape(heights, SAMPLES, Vec3f(1f, 1f, 1f))
    }

    private fun Vec3f.isFinite() = x.isFinite() && y.isFinite() && z.isFinite()

    private fun assertEqualsUnit(normal: Vec3f, context: String) {
        assertTrue(kotlin.math.abs(normal.length3() - 1f) < 0.001f, "normal $normal is not unit length, $context")
    }

    private companion object {
        const val SAMPLES = 8

        val QUAD = floatArrayOf(-5f, 0f, -5f, 5f, 0f, -5f, 5f, 0f, 5f, -5f, 0f, 5f)

        /** The quad, plus a triangle with a repeated vertex, a collinear one and a single-point one. */
        val DEGENERATE_MESH = QUAD + floatArrayOf(
            0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 1f,
            0f, 0f, 0f, 1f, 0f, 0f, 2f, 0f, 0f,
            0.5f, 0f, 0.5f,
        )
        val DEGENERATE_INDICES = intArrayOf(0, 2, 1, 0, 3, 2, 4, 5, 6, 7, 8, 9, 10, 10, 10)
    }
}
