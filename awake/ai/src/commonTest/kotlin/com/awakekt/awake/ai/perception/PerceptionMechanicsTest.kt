/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.perception

import com.awakekt.awake.ai.definition.AiRegistry
import com.awakekt.awake.ai.definition.BehaviorTreeDefinition
import com.awakekt.awake.ai.definition.NodeDefinition
import com.awakekt.awake.ai.definition.NodeKindId
import com.awakekt.awake.ai.definition.validate
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PerceptionMechanicsTest {
    private val looks = HashMap<Entity, Int>()

    /** Counts each perceiver's looks: the space is asked for the viewer's facing once per look. */
    private inner class CountingScene : PerceptionSpace {
        val scene = PerceptionScene()

        override fun position(world: World, entity: Entity, into: Vec3f) = scene.position(world, entity, into)

        override fun forward(world: World, entity: Entity, into: Vec3f): Boolean {
            looks[entity] = (looks[entity] ?: 0) + 1
            return scene.forward(world, entity, into)
        }
    }

    private fun crowd(size: Int, interval: Float): CountingScene {
        val counting = CountingScene()
        repeat(size) { i ->
            val agent = counting.scene.spawn(i.toFloat(), 0f, faceX = 1f, faceZ = 0f)
            counting.scene.world.add(agent, Perceiver(FactionId("crowd"), PerceptionSettings(updateInterval = interval)))
            counting.scene.world.add(agent, Perceivable(FactionId("crowd")))
        }
        return counting
    }

    @Test
    fun theBudgetCapsLooksAndServesEveryoneInTurn() {
        val counting = crowd(size = 50, interval = 0f)
        val system = PerceptionSystem(counting, maxSightUpdates = 8)
        repeat(25) {
            system.update(counting.scene.world, 0.1f)
            assertEquals(8, system.sightUpdates)
        }
        // 25 updates x 8 looks = 200 = 4 looks for each of 50.
        assertEquals(setOf(4), looks.values.toSet())
        assertEquals(50, looks.size)
    }

    @Test
    fun eachPerceiverLooksAtItsOwnRateStaggered() {
        val counting = crowd(size = 32, interval = 0.4f)
        val system = PerceptionSystem(counting)
        val perUpdate = List(40) {
            system.update(counting.scene.world, 0.1f)
            system.sightUpdates
        }
        // Every perceiver looks every 4 updates, and the first looks spread over the first interval.
        assertTrue(looks.values.all { it in 9..11 }, "$looks")
        assertTrue(perUpdate.drop(4).all { it in 4..12 }, "$perUpdate")
    }

    @Test
    fun memoryRoundTripsThroughData() {
        val scene = PerceptionScene()
        val viewer = scene.spawn(0f, 0f, faceX = 1f, faceZ = 0f)
        val other = scene.spawn(5f, 0f)
        scene.world.add(viewer, Perceiver(FactionId("a"), PerceptionSettings(updateInterval = 0f)))
        scene.world.add(other, Perceivable(FactionId("b")))
        val system = PerceptionSystem(scene, FactionTable(otherwise = Relation.Hostile))
        system.hear(SoundEvent(Vec3f(1f, 0f, 1f), loudness = 0.5f))
        system.update(scene.world, 0.5f)

        val memory = scene.world.get<Perceiver>(viewer)!!.memory
        val snapshot = memory.snapshot()
        val copy = PerceptionMemory().apply { restore(Json.decodeFromString(PerceptionMemorySnapshot.serializer(), Json.encodeToString(PerceptionMemorySnapshot.serializer(), snapshot))) }
        assertEquals(snapshot, copy.snapshot())
        assertEquals(Relation.Hostile, copy[other]!!.relation)
        assertEquals(Vec3f(1f, 0f, 1f), copy.noise!!.position)
    }

    @Test
    fun factionTablesAreDataWithDefaults() {
        val a = FactionId("a")
        val b = FactionId("b")
        val c = FactionId("c")
        val table = FactionTable(listOf(FactionRelation(a, b, Relation.Hostile, mutual = false)), otherwise = Relation.Friendly)
        assertEquals(Relation.Hostile, table.relation(a, b))
        assertEquals(Relation.Friendly, table.relation(b, a))
        assertEquals(Relation.Friendly, table.relation(c, c))
        assertEquals(table, Json.decodeFromString(FactionTable.serializer(), Json.encodeToString(FactionTable.serializer(), table)))
        assertFailsWith<IllegalArgumentException> {
            FactionTable(listOf(FactionRelation(a, b, Relation.Hostile), FactionRelation(b, a, Relation.Friendly)))
        }
        assertFailsWith<IllegalArgumentException> { SightSettings(focusAngle = 400f) }
    }

    @Test
    fun perceptionConditionsShowInTheRegistry() {
        val registry = AiRegistry()
        assertEquals(listOf("relation", "focusOnly"), registry.parameterSchema(NodeKindId("ai.perception.sees")).children.map { it.name })
        assertEquals(
            "node 0 (ai.perception.sees): unknown parameter 'relations' (did you mean 'relation'?); it takes relation, focusOnly.",
            registry.validate(
                BehaviorTreeDefinition(
                    root = NodeDefinition(NodeKindId("ai.perception.sees"), params = Json.parseToJsonElement("""{"relations":"any"}""").jsonObject),
                ),
            ).single().toString(),
        )
    }
}
