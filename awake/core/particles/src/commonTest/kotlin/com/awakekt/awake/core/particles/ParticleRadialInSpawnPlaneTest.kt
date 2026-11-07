/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.particles

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ParticleSimulation.radialInSpawnPlane] pushes [ParticleSimulation.radialSpeed] outward in a turned
 * spawn ring's plane. Every case turns the ring a quarter about Z, so it lies in the world's YZ plane.
 */
class ParticleRadialInSpawnPlaneTest {
    @Test
    fun byDefaultATurnedRingStillPushesHorizontally() {
        val sim = turnedRing(radius = 2f, inPlane = false)

        sim.emit(COUNT)

        for (slot in 0 until COUNT) assertEquals(0f, sim.velY[slot], 1e-5f, "no vertical push")
    }

    @Test
    fun inThePlaneThePushRunsOutwardAlongTheTurnedRing() {
        val sim = turnedRing(radius = 2f, inPlane = true)

        sim.emit(COUNT)

        for (slot in 0 until COUNT) {
            assertEquals(0f, sim.velX[slot], 1e-5f, "stays in the ring's YZ plane")
            assertEquals(SPEED, speed(sim, slot), 1e-4f)
            val outward = sim.velY[slot] * sim.posY[slot] + sim.velZ[slot] * sim.posZ[slot]
            assertEquals(SPEED * 2f, outward, 1e-3f, "along the spawn point's direction from the origin")
        }
        assertTrue((0 until COUNT).any { abs(sim.velY[it]) > 1f }, "some push runs up the slope")
    }

    @Test
    fun atTheOriginThePushStillLiesInTheTurnedPlane() {
        val sim = turnedRing(radius = 0f, inPlane = true)

        sim.emit(COUNT)

        for (slot in 0 until COUNT) {
            assertEquals(0f, sim.velX[slot], 1e-5f)
            assertEquals(SPEED, speed(sim, slot), 1e-4f)
        }
    }

    @Test
    fun anUnturnedRingIgnoresIt() {
        val sim = turnedRing(radius = 2f, inPlane = true).apply { orientSpawnShape = false }

        sim.emit(COUNT)

        for (slot in 0 until COUNT) assertEquals(0f, sim.velY[slot], 1e-5f)
    }

    private fun turnedRing(radius: Float, inPlane: Boolean) =
        ParticleSimulation(maxParticles = COUNT, random = Random(1416)).apply {
            spawnShape.setRingXZ(radius)
            baseVelY = 0f
            radialSpeed = SPEED
            orientSpawnShape = true
            radialInSpawnPlane = inPlane
            orientation.setRotationZ((PI / 2.0).toFloat())
        }

    private fun speed(sim: ParticleSimulation, slot: Int): Float =
        sqrt(sim.velX[slot] * sim.velX[slot] + sim.velY[slot] * sim.velY[slot] + sim.velZ[slot] * sim.velZ[slot])
}

private const val COUNT = 16
private const val SPEED = 3f
