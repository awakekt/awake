/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.perception

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * Measures a crowd: 1,000 perceivers among 1,000 perceivable entities (the perceivers themselves),
 * looking 5 times a second at 60 updates a second, budgeted to 100 looks an update. The bound is
 * loose so a slow CI machine passes; the measured figure is printed.
 */
class PerceptionCostTest {
    @Test
    fun aThousandAgentsStayWithinAFrameBudget() {
        val scene = PerceptionScene()
        val settings = PerceptionSettings(updateInterval = 0.2f)
        repeat(1_000) { i ->
            val agent = scene.spawn((i % 40) * 3f, (i / 40) * 3f, faceX = 1f, faceZ = 0f)
            scene.world.add(agent, Perceiver(FactionId(if (i % 2 == 0) "red" else "blue"), settings))
            scene.world.add(agent, Perceivable(FactionId(if (i % 2 == 0) "red" else "blue")))
        }
        val factions = FactionTable(listOf(FactionRelation(FactionId("red"), FactionId("blue"), Relation.Hostile)))
        val system = PerceptionSystem(scene, factions, maxSightUpdates = 100)
        repeat(120) { system.update(scene.world, 1f / 60f) }

        val updates = 600
        var looks = 0
        val mark = TimeSource.Monotonic.markNow()
        repeat(updates) {
            system.update(scene.world, 1f / 60f)
            looks += system.sightUpdates
        }
        val perUpdateMs = mark.elapsedNow().inWholeMicroseconds / 1000.0 / updates
        println("Perception: ${"%.3f".format(perUpdateMs)} ms per update, ${looks / updates} looks per update, 1000 agents x 1000 targets")
        assertTrue(looks / updates <= 100)
        assertTrue(perUpdateMs < 16.0, "$perUpdateMs ms per update")
    }
}
