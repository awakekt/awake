/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.particles

import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [ParticleSimulation.orientSpawnShape] turns spawn offsets by [ParticleSimulation.orientation] and leaves velocities alone. */
class ParticleSpawnShapeOrientationTest {
    @Test
    fun anOrientedRingIsTurnedWhileItsVelocityIsNot() {
        val sim = ParticleSimulation(maxParticles = 8, random = Random(7)).apply {
            spawnShape.setRingXZ(2f)
            baseVelY = 3f
            orientSpawnShape = true
            orientation.setRotationZ((PI / 2.0).toFloat())
        }

        sim.emit(8)

        for (slot in 0 until 8) {
            assertTrue(abs(sim.posX[slot]) < 1e-5f, "the XZ ring turned into the YZ plane")
            assertEquals(0f, sim.velX[slot], 1e-5f)
            assertEquals(3f, sim.velY[slot], 1e-5f, "the launch velocity is not turned")
        }
        assertTrue((0 until 8).any { abs(sim.posY[it]) > 1f }, "the ring reaches up the Y axis")
    }
}
