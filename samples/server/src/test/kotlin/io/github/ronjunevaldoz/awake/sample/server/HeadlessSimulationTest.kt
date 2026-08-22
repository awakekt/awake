// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.sample.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HeadlessSimulationTest {

    @Test
    fun runsFixedTicksInOrder() {
        val updates = mutableListOf<SimulationTick>()
        val simulation = HeadlessSimulation(fixedDeltaSeconds = 0.05f) { updates += it }

        simulation.runTicks(3)

        assertEquals(3, simulation.tick)
        assertEquals(listOf(0L, 1L, 2L), updates.map(SimulationTick::number))
        assertEquals(listOf(0.05f, 0.05f, 0.05f), updates.map(SimulationTick::deltaSeconds))
    }

    @Test
    fun rejectsInvalidConfigurationWithoutAdvancing() {
        assertFailsWith<IllegalArgumentException> {
            HeadlessSimulation(fixedDeltaSeconds = 0f) { }
        }

        val simulation = HeadlessSimulation { }
        assertFailsWith<IllegalArgumentException> { simulation.runTicks(-1) }
        assertEquals(0, simulation.tick)
    }
}
