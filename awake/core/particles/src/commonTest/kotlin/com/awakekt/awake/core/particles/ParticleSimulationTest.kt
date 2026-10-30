/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.particles

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParticleSimulationTest {

    @Test
    fun alphaRampAndScaleCurvesInterpolateOverNormalizedLifetime() {
        val ramp = ParticleAlphaRamp(fadeInEnd = 0.25f, fadeOutStart = 0.75f)
        assertEquals(0f, ramp.factorAt(0f), 1e-6f)
        assertEquals(0.5f, ramp.factorAt(0.125f), 1e-6f)
        assertEquals(1f, ramp.factorAt(0.5f), 1e-6f)
        assertEquals(0.5f, ramp.factorAt(0.875f), 1e-6f)
        assertEquals(0f, ramp.factorAt(1f), 1e-6f)
        assertFailsWith<IllegalArgumentException> { ParticleAlphaRamp(fadeInEnd = 0.8f, fadeOutStart = 0.2f) }

        val sim = ParticleSimulation(maxParticles = 2, random = Random(7)).apply {
            minLifetime = 2f
            maxLifetime = 2f
            initialAlpha = 0.8f
            initialScale = 2f
            hasEndScale = true
            endScale = 6f
            frameCount = 4
            frameRate = 2f
            alphaRamp.set(ramp)
        }
        assertEquals(1, sim.emit(1))
        sim.frameOffset[0] = 1
        sim.step(0.25f) // fraction = 0.125 -> ramp = 0.5 -> alpha = 0.4, scale = 2.5
        assertEquals(0.4f, sim.alphaAt(0), 1e-5f)
        assertEquals(2.5f, sim.scaleAt(0), 1e-5f)
        assertEquals(1, sim.frameAt(0))
        sim.step(0.75f) // age = 1.0 -> fraction = 0.5 -> ramp = 1.0 -> alpha = 0.8, scale = 4.0, frame = (2 + 1) % 4 = 3
        assertEquals(0.8f, sim.alphaAt(0), 1e-5f)
        assertEquals(4f, sim.scaleAt(0), 1e-5f)
        assertEquals(3, sim.frameAt(0))
    }

    @Test
    fun emitFillsFreeSlotsCountsOverflowAndLeavesBurstLimitUntouched() {
        val sim = ParticleSimulation(maxParticles = 4, random = Random(11)).apply {
            minLifetime = 5f
            maxLifetime = 5f
            burstLimit = 2
        }
        assertEquals(0, sim.emit(0))
        assertEquals(0, sim.emit(-3))
        assertEquals(3, sim.emit(3))
        assertEquals(3, sim.liveParticleCount)
        assertEquals(0, sim.spawnedTotal, "emit(count) must not consume scheduled burstLimit")
        assertEquals(0L, sim.droppedParticleCount)

        // Request 5 when only 1 slot remains free: spawns 1 and records 4 dropped.
        assertEquals(1, sim.emit(5))
        assertEquals(4, sim.liveParticleCount)
        assertEquals(4L, sim.droppedParticleCount)
        assertFalse(sim.spawn(1f, 2f, 3f), "manual spawn returns false when pool is full")
    }

    @Test
    fun burstScheduleFiresInActiveWindowCountsDropsAndHonorsBurstLimit() {
        val schedule = ParticleBurstSchedule(
            cycleSeconds = 2f,
            activeSeconds = 1f,
            burstInterval = 0.25f,
            burstSize = 3,
        )
        assertEquals(0, schedule.burstsStartedBy(0f))
        assertEquals(4, schedule.burstsStartedBy(1f), "exact integer activeSeconds/burstInterval fires 4 bursts, not 5")
        assertEquals(4, schedule.burstsStartedBy(1.9f))
        assertEquals(5, schedule.burstsStartedBy(2.05f))

        val sim = ParticleSimulation(maxParticles = 4, random = Random(3)).apply {
            minLifetime = 10f
            maxLifetime = 10f
            burstSchedule.set(schedule)
        }
        sim.step(0.3f) // spans bursts at 0.00 and 0.25 -> 6 requested into pool of 4
        assertEquals(4, sim.liveParticleCount)
        assertEquals(4, sim.spawnedTotal)
        assertEquals(2L, sim.droppedParticleCount)

        sim.reset()
        sim.burstLimit = 2
        sim.minLifetime = 0.2f
        sim.maxLifetime = 0.2f
        sim.step(0.05f)
        assertEquals(2, sim.spawnedTotal)
        assertFalse(sim.isComplete)
        sim.step(0.5f)
        assertEquals(0, sim.liveParticleCount)
        assertTrue(sim.isComplete)
    }

    @Test
    fun randomizedLifetimeRangeDeathCallbackAndSlotReuse() {
        val sim = ParticleSimulation(maxParticles = 64, random = Random(19)).apply {
            minLifetime = 0.4f
            maxLifetime = 1.2f
        }
        assertEquals(64, sim.emit(64))
        var minSeen = Float.POSITIVE_INFINITY
        var maxSeen = Float.NEGATIVE_INFINITY
        for (i in 0 until 64) {
            minSeen = minOf(minSeen, sim.lifetime[i])
            maxSeen = maxOf(maxSeen, sim.lifetime[i])
            assertTrue(sim.lifetime[i] in 0.4f..1.2f)
        }
        assertTrue(maxSeen - minSeen > 0.4f, "expected spread across minLifetime..maxLifetime")

        var deaths = 0
        sim.step(0.8f) { _, _, _ -> deaths += 1 }
        assertTrue(deaths in 1..63, "some particles aged out before 0.8s while longer-lived ones survived")
        assertEquals(64 - deaths, sim.liveParticleCount)
        assertEquals(deaths, sim.emit(deaths), "freed slots must be immediately reusable")
    }

    @Test
    fun spawnShapesCoverPointRingsAndBox() {
        val pointSim = ParticleSimulation(maxParticles = 4, random = Random(1)).apply {
            setOrigin(3f, -2f, 5f)
            spawnShape.set(ParticleSpawnShape.Point)
        }
        pointSim.emit(4)
        for (i in 0 until 4) {
            assertEquals(3f, pointSim.posX[i])
            assertEquals(-2f, pointSim.posY[i])
            assertEquals(5f, pointSim.posZ[i])
        }

        val ringXy = ParticleSimulation(maxParticles = 64, random = Random(2)).apply {
            plane = ParticlePlane.XY
            setOrigin(10f, 20f, 0f)
            spawnShape.set(ParticleSpawnShape.RingXY(innerRadius = 2f, outerRadius = 4f))
        }
        ringXy.emit(64)
        for (i in 0 until 64) {
            val dx = ringXy.posX[i] - 10f
            val dy = ringXy.posY[i] - 20f
            val r = sqrt(dx * dx + dy * dy)
            assertTrue(r in 1.999f..4.001f, "RingXY radius $r outside 2..4")
            assertEquals(0f, ringXy.posZ[i])
        }

        val boxSim = ParticleSimulation(maxParticles = 32, random = Random(5)).apply {
            spawnShape.set(ParticleSpawnShape.Box(halfExtentX = 5f, halfExtentY = 3f, halfExtentZ = 2f))
        }
        boxSim.emit(32)
        for (i in 0 until 32) {
            assertTrue(boxSim.posX[i] in -5f..5f && boxSim.posY[i] in -3f..3f && boxSim.posZ[i] in -2f..2f)
        }
    }

    @Test
    fun rectEdgeShapePopulatesAllPerimeterSides() {
        val edgeSim = ParticleSimulation(maxParticles = 80, random = Random(9)).apply {
            plane = ParticlePlane.XY
            spawnShape.set(ParticleSpawnShape.RectEdge(halfWidth = 6f, halfHeight = 4f))
        }
        edgeSim.emit(80)
        var hitHorizontal = 0
        var hitVertical = 0
        for (i in 0 until 80) {
            val onVertical = abs(abs(edgeSim.posX[i]) - 6f) < 1e-4f && abs(edgeSim.posY[i]) <= 4.001f
            val onHorizontal = abs(abs(edgeSim.posY[i]) - 4f) < 1e-4f && abs(edgeSim.posX[i]) <= 6.001f
            assertTrue(onVertical || onHorizontal, "RectEdge sample (${edgeSim.posX[i]}, ${edgeSim.posY[i]}) not on perimeter")
            if (onVertical) hitVertical += 1
            if (onHorizontal) hitHorizontal += 1
        }
        assertTrue(hitVertical > 10 && hitHorizontal > 10, "RectEdge should populate both vertical and horizontal sides")
    }

    @Test
    fun xyPlaneFanSpreadOrientationDragAndGroundBounce() {
        val fanSim = ParticleSimulation(maxParticles = 48, random = Random(13)).apply {
            plane = ParticlePlane.XY
            baseVelX = 0f
            baseVelY = 10f
            minSpeed = 6f
            maxSpeed = 8f
            hasConeHalfAngle = true
            coneHalfAngleDegrees = 30f
            drag = 2f
        }
        fanSim.emit(48)
        val minCos = cos(30f * (PI.toFloat() / 180f)) - 1e-4f
        for (i in 0 until 48) {
            val vx = fanSim.velX[i]
            val vy = fanSim.velY[i]
            val speed = sqrt(vx * vx + vy * vy)
            assertTrue(speed in 5.999f..8.001f, "speed $speed outside 6..8")
            assertEquals(0f, fanSim.velZ[i])
            assertTrue(vy / speed >= minCos, "XY fan exceeded half-angle")
        }
        val initialSpeed0 = sqrt(fanSim.velX[0] * fanSim.velX[0] + fanSim.velY[0] * fanSim.velY[0])
        fanSim.step(0.25f) // dragFactor = 1 - 2 * 0.25 = 0.5
        val dampedSpeed0 = sqrt(fanSim.velX[0] * fanSim.velX[0] + fanSim.velY[0] * fanSim.velY[0])
        assertEquals(initialSpeed0 * 0.5f, dampedSpeed0, 1e-4f)

        val rotSim = ParticleSimulation(maxParticles = 1, random = Random(21)).apply {
            plane = ParticlePlane.XY
            baseVelX = 4f
            baseVelY = 0f
            inheritOrientation = true
            orientation.setRotationZ((PI / 2.0).toFloat())
        }
        rotSim.emit(1)
        assertEquals(0f, rotSim.velX[0], 1e-5f)
        assertEquals(4f, rotSim.velY[0], 1e-5f)

        val bounceSim = ParticleSimulation(maxParticles = 1, random = Random(31)).apply {
            setOrigin(0f, 3f, 0f)
            baseVelY = -10f
            ground.colliders = listOf(Aabb(Vec3f(-1f, 0f, -1f), Vec3f(1f, 1f, 1f)))
            ground.restitution = 0.5f
            ground.friction = 0.5f
            orientation.setFromMatrix(Mat4())
        }
        bounceSim.emit(1)
        bounceSim.step(0.5f)
        assertEquals(1f, bounceSim.posY[0], 1e-5f)
        assertEquals(5f, bounceSim.velY[0], 1e-5f)
    }

    @Test
    fun anEmptyPoolIsAllowedAndNeverHoldsAParticle() {
        assertFailsWith<IllegalArgumentException> { ParticleSimulation(maxParticles = -1) }
        val empty = ParticleSimulation(maxParticles = 0, random = Random(5)).apply {
            spawnRate = 100f
            burstLimit = 0
        }

        empty.step(0.5f)
        assertEquals(0, empty.emit(3), "an empty pool spawns nothing")
        assertEquals(3L, empty.droppedParticleCount, "the whole burst is dropped")
        assertFalse(empty.spawn(0f, 0f, 0f))
        assertEquals(0, empty.liveParticleCount)
        assertTrue(empty.isComplete, "a zero-count burst is complete at once")
    }

    @Test
    fun livenessOnlyChangesThroughThePoolSoTheLiveCountAgrees() {
        val sim = ParticleSimulation(maxParticles = 3, random = Random(4)).apply {
            minLifetime = 5f
            maxLifetime = 5f
            burstLimit = 0
        }
        assertEquals(2, sim.emit(2))
        assertTrue(sim.isAlive(0) && sim.isAlive(1))
        assertFalse(sim.isAlive(2))

        sim.clearSlot(0)
        sim.clearSlot(0)
        assertFalse(sim.isAlive(0))
        assertEquals(1, sim.liveParticleCount, "clearing a slot twice counts it out once")
        sim.clearSlot(1)
        assertEquals(0, sim.liveParticleCount)
        assertTrue(sim.isComplete, "with every slot cleared a spent pool completes")
    }

    @Test
    fun theScalarBurstScheduleIsValidatedLikeTheValueType() {
        val config = ParticleBurstConfig()
        assertFailsWith<IllegalArgumentException> { config.set(0f, 0f, 0f, 4) }
        assertFailsWith<IllegalArgumentException> { config.set(1f, 2f, 0.5f, 1) }
        assertFailsWith<IllegalArgumentException> { config.set(1f, 1f, 0f, 1) }
        assertFailsWith<IllegalArgumentException> { config.set(1f, 1f, Float.POSITIVE_INFINITY, 1) }
        assertFailsWith<IllegalArgumentException> { config.set(Float.NaN, 1f, 0.5f, 1) }
        assertFailsWith<IllegalArgumentException> { config.set(1f, 1f, 0.5f, 0) }
        assertFalse(config.enabled, "a rejected schedule leaves the holder off")

        config.set(2f, 1f, 0.25f, 3)
        assertTrue(config.enabled)
        assertEquals(3, config.burstSize)
    }

    @Test
    fun theHooksAreToldOfGroundAndDeath() {
        var queries = 0
        var deathY = Float.NaN
        val sim = ParticleSimulation(maxParticles = 1, random = Random(8)).apply {
            setOrigin(0f, 2f, 0f)
            baseVelY = -4f
            minLifetime = 1f
            maxLifetime = 1f
            ground.groundHeightProvider = ParticleGroundHeight { _, _ ->
                queries += 1
                1f
            }
        }
        sim.emit(1)
        sim.step(0.5f) { _, y, _ -> deathY = y }
        assertEquals(1, queries, "asked once for the one moving particle")
        assertEquals(1f, sim.posY[0], "landed on the provided height")
        assertTrue(sim.settled[0])

        sim.step(0.6f) { _, y, _ -> deathY = y }
        assertEquals(1f, deathY, "told where the particle died")
        assertEquals(1, queries, "a settled particle is not asked again")
    }
}
