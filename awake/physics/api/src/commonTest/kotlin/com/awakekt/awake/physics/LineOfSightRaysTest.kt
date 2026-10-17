/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LineOfSightRaysTest {
    /** A wall across x = [wallX] from y = 0 up to y = 10, the one thing a ray hits; it counts the rays. */
    private class Wall(val wallX: Float = 5f, val handle: BodyHandle = BodyHandle(7)) : RaysOnly() {
        var rays = 0
        val layersAsked = ArrayList<CollisionLayer?>()

        override fun raycast(origin: Vec3f, direction: Vec3f, maxDistance: Float, onlyLayer: CollisionLayer?): RaycastHit? {
            rays++
            layersAsked += onlyLayer
            val along = if (direction.x > 0f) (wallX - origin.x) / direction.x else -1f
            val y = origin.y + direction.y * along
            val hits = along in 0f..maxDistance && y in 0f..10f
            return if (hits) RaycastHit(handle, Vec3f(wallX, y, origin.z + direction.z * along), along) else null
        }
    }

    private val eye = Vec3f(0f, 1f, 0f)
    private val behindWall = Vec3f(10f, 1f, 0f)
    private val beforeWall = Vec3f(3f, 1f, 0f)
    private val overWall = Vec3f(10f, 30f, 0f)

    @Test
    fun aPointBehindAWallIsBlockedAndOneInFrontIsNot() {
        val rays = LineOfSightRays<String>(Wall())
        rays.nextFrame()
        assertTrue(rays.blocked("behind", eye, behindWall))
        assertFalse(rays.blocked("before", eye, beforeWall))
        assertFalse(rays.blocked("over", eye, overWall))
    }

    @Test
    fun theKeysOwnBodyHidesNothing() {
        val wall = Wall()
        val rays = LineOfSightRays<String>(wall, ownBody = { key, body -> key == "wall's label" && body == wall.handle })
        rays.nextFrame()
        assertFalse(rays.blocked("wall's label", eye, behindWall), "a ray that reaches the key's own body first finds nothing in the way")
        assertTrue(rays.blocked("someone else", eye, behindWall))
    }

    @Test
    fun aSurfaceRightAtThePointHidesNothing() {
        val rays = LineOfSightRays<String>(Wall())
        rays.nextFrame()
        assertFalse(rays.blocked("on the wall", eye, Vec3f(5f, 1f, 0f)), "a marker set on the wall's face is not behind it")
    }

    @Test
    fun anEyeInsideABodyHidesNothing() {
        val rays = LineOfSightRays<String>(Wall())
        rays.nextFrame()
        assertFalse(rays.blocked("a", Vec3f(5f, 1f, 0f), behindWall), "a ray that starts in the wall cannot see past it, so it hides nothing")
    }

    @Test
    fun anAnswerIsReusedForItsFramesBeforeItIsCastAgain() {
        val wall = Wall()
        val rays = LineOfSightRays<String>(wall, reuseFrames = 3)
        repeat(6) {
            rays.nextFrame()
            rays.blocked("a", eye, behindWall)
            rays.blocked("a", eye, behindWall)
        }
        assertEquals(2, wall.rays, "six frames at three frames an answer cast twice, and a second ask in a frame casts nothing")
    }

    @Test
    fun noFrameCastsMoreThanItsBudget() {
        val wall = Wall()
        val rays = LineOfSightRays<Int>(wall, raysPerFrame = 8, reuseFrames = 1)
        repeat(10) {
            rays.nextFrame()
            for (key in 0 until 100) rays.blocked(key, eye, behindWall)
            assertTrue(rays.raysCast <= 8, "frame $it cast ${rays.raysCast}")
        }
        assertEquals(80, wall.rays)
    }

    @Test
    fun withMoreKeysDueThanTheBudgetEveryKeyIsCastInTurn() {
        val rays = LineOfSightRays<Int>(Wall(), raysPerFrame = 10, reuseFrames = 2)
        val castIn = HashMap<Int, MutableList<Int>>()
        // A key cast in a frame sees its answer change from the one before: the first ray turns it blocked.
        repeat(40) { frame ->
            rays.nextFrame()
            val before = rays.raysCast
            for (key in 0 until 50) {
                val cast = rays.raysCast
                rays.blocked(key, eye, behindWall)
                if (rays.raysCast > cast) castIn.getOrPut(key) { ArrayList() } += frame
            }
            assertTrue(rays.raysCast - before <= 10)
        }
        assertEquals(50, castIn.size, "every key was cast, not only those asked first")
        val mostWaited = castIn.values.maxOf { frames -> frames.zipWithNext { a, b -> b - a }.maxOrNull() ?: 0 }
        assertTrue(mostWaited <= 6, "fifty keys at ten rays a frame are each cast again within a few frames, not left waiting: $mostWaited")
    }

    @Test
    fun aKeyNotYetCastIsClearUntilItsFirstRay() {
        val rays = LineOfSightRays<Int>(Wall(), raysPerFrame = 1, reuseFrames = 10)
        rays.nextFrame()
        assertTrue(rays.blocked(1, eye, behindWall))
        assertFalse(rays.blocked(2, eye, behindWall), "the budget is spent, and a key with no answer yet shows")
        rays.nextFrame()
        assertTrue(rays.blocked(1, eye, behindWall), "key 1's answer still stands")
        assertTrue(rays.blocked(2, eye, behindWall), "key 2 waited, so it is cast first")
    }

    @Test
    fun aKeyNotAskedForAWhileIsForgotten() {
        val rays = LineOfSightRays<String>(Wall())
        rays.nextFrame()
        rays.blocked("gone", eye, behindWall)
        rays.blocked("stays", eye, behindWall)
        repeat(LineOfSightRays.FORGET_FRAMES + 1) {
            rays.nextFrame()
            rays.blocked("stays", eye, behindWall)
        }
        assertEquals(1, rays.size)
    }

    @Test
    fun theRaysHitOnlyTheLayerSet() {
        val wall = Wall()
        val rays = LineOfSightRays<String>(wall, onlyLayer = CollisionLayers.World)
        rays.nextFrame()
        rays.blocked("a", eye, behindWall)
        assertEquals(listOf<CollisionLayer?>(CollisionLayers.World), wall.layersAsked)
    }
}

/** A [PhysicsWorld] whose only query is a ray, which a subclass answers; anything else fails the test. */
private abstract class RaysOnly : PhysicsWorld {
    override val layers: CollisionLayers = CollisionLayers.Default

    override fun createBody(
        shape: PhysicsShape,
        position: Vec3f,
        rotation: Quat,
        motionType: MotionType,
        layer: CollisionLayer,
        sensor: Boolean,
    ): BodyHandle = unused()

    override fun destroyBody(handle: BodyHandle) = unused()

    override fun step(deltaTime: Float) = unused()

    override fun setLinearVelocity(handle: BodyHandle, velocity: Vec3f) = unused()

    override fun getLinearVelocity(handle: BodyHandle): Vec3f = unused()

    override fun setAngularVelocity(handle: BodyHandle, velocity: Vec3f) = unused()

    override fun addImpulse(handle: BodyHandle, impulse: Vec3f) = unused()

    override fun moveKinematic(handle: BodyHandle, position: Vec3f, rotation: Quat, deltaTime: Float) = unused()

    override fun forEachBodyTransform(action: (handle: BodyHandle, position: Vec3f, rotation: Quat) -> Unit) = unused()

    override fun shiftOrigin(offset: Vec3f) = unused()

    override fun shapeCast(shape: PhysicsShape, from: Vec3f, to: Vec3f, onlyLayer: CollisionLayer?, ignore: BodyHandle?): ShapeCastHit? = unused()

    override fun overlapShape(shape: PhysicsShape, position: Vec3f, onlyLayer: CollisionLayer?, onOverlap: (BodyHandle) -> Unit) = unused()

    override fun setActive(handle: BodyHandle, active: Boolean) = unused()

    override fun isActive(handle: BodyHandle): Boolean = unused()

    override fun createConstraint(constraint: Constraint): ConstraintHandle = unused()

    override fun destroyConstraint(handle: ConstraintHandle) = unused()

    override fun applyBuoyancy(handle: BodyHandle, surfaceY: Float, buoyancy: Buoyancy, deltaTime: Float) = unused()

    override fun setContinuousCollision(handle: BodyHandle, enabled: Boolean) = unused()

    override fun setContactReporting(handle: BodyHandle, enabled: Boolean) = unused()

    override fun drainContacts(action: (ContactEvent) -> Unit) = unused()

    override fun destroy() = unused()

    private fun unused(): Nothing = error("Only raycast is asked")
}
